package com.usmind.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.usmind.common.JwtUtil;
import com.usmind.entity.MindMap;
import com.usmind.entity.SysUser;
import com.usmind.mapper.MindMapMapper;
import com.usmind.mapper.SysUserMapper;
import com.usmind.service.OrgService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 导图协作实时同步 WebSocket：
 *  - 客户端携带 ?token=JWT 握手，校验身份后订阅导图频道
 *  - 订阅时校验访问权（属主或机构协作成员）
 *  - 导图保存成功后由 MindMapServiceImpl 调用 broadcast 推送轻量变更通知
 */
@Slf4j
@Component
public class MindMapWsHandler extends TextWebSocketHandler {

    private final JwtUtil jwtUtil;
    private final MindMapMapper mindMapMapper;
    private final SysUserMapper sysUserMapper;
    private final OrgService orgService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** mindMapId -> 订阅该导图的会话集合 */
    private final Map<String, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();

    public MindMapWsHandler(JwtUtil jwtUtil, MindMapMapper mindMapMapper,
                            SysUserMapper sysUserMapper, OrgService orgService) {
        this.jwtUtil = jwtUtil;
        this.mindMapMapper = mindMapMapper;
        this.sysUserMapper = sysUserMapper;
        this.orgService = orgService;
    }

    public static final String ATTR_UID = "uid";
    public static final String ATTR_MINDMAP = "mindMapId";

    /** 广播变更通知给订阅该导图的所有会话 */
    public void broadcast(String mindMapId, String payload) {
        Set<WebSocketSession> set = sessions.get(mindMapId);
        if (set == null || set.isEmpty()) return;
        for (WebSocketSession s : set) {
            try {
                if (s.isOpen()) s.sendMessage(new TextMessage(payload));
            } catch (Exception e) {
                log.debug("ws 广播失败: {}", e.getMessage());
            }
        }
    }

    /** 当前订阅某导图的会话数（调试用） */
    public int subscriberCount(String mindMapId) {
        Set<WebSocketSession> set = sessions.get(mindMapId);
        return set == null ? 0 : set.size();
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String body = message.getPayload();
        JsonNode node;
        try {
            node = objectMapper.readTree(body);
        } catch (Exception e) {
            send(session, "{\"type\":\"error\",\"message\":\"消息格式错误\"}");
            return;
        }
        String type = node.path("type").asText();
        String mindMapId = node.path("mindMapId").asText();
        if (mindMapId == null || mindMapId.isBlank()) return;
        if (!mindMapId.matches("\\d+")) return;
        long id = Long.parseLong(mindMapId);

        if ("subscribe".equals(type)) {
            Long uid = (Long) session.getAttributes().get(ATTR_UID);
            if (uid == null) { send(session, "{\"type\":\"error\",\"message\":\"未登录\"}"); return; }
            if (!canAccess(uid, id)) {
                send(session, "{\"type\":\"error\",\"message\":\"无权访问该导图\"}");
                return;
            }
            sessions.computeIfAbsent(mindMapId, k -> new CopyOnWriteArraySet<>()).add(session);
            session.getAttributes().put(ATTR_MINDMAP, mindMapId);
            send(session, "{\"type\":\"subscribed\",\"mindMapId\":" + mindMapId + "}");
        } else if ("unsubscribe".equals(type)) {
            Set<WebSocketSession> set = sessions.get(mindMapId);
            if (set != null) set.remove(session);
            session.getAttributes().remove(ATTR_MINDMAP);
        }
    }

    private boolean canAccess(long userId, long mindMapId) {
        MindMap m = mindMapMapper.selectById(mindMapId);
        if (m == null) return false;
        if (m.getUserId() != null && m.getUserId().equals(userId)) return true;
        try {
            return orgService.canAccessMindMap(userId, mindMapId);
        } catch (Exception e) {
            return false;
        }
    }

    private void send(WebSocketSession session, String text) {
        try {
            if (session.isOpen()) session.sendMessage(new TextMessage(text));
        } catch (Exception ignored) {
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Object mm = session.getAttributes().get(ATTR_MINDMAP);
        if (mm != null) {
            Set<WebSocketSession> set = sessions.get(mm.toString());
            if (set != null) set.remove(session);
        }
        // 兜底：清理所有残留引用
        sessions.values().forEach(set -> set.remove(session));
    }

    /** 握手拦截器：从 query token 解析用户身份 */
    public static class AuthHandshakeInterceptor implements HandshakeInterceptor {
        private final JwtUtil jwtUtil;
        public AuthHandshakeInterceptor(JwtUtil jwtUtil) { this.jwtUtil = jwtUtil; }

        @Override
        public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                       WebSocketHandler wsHandler, Map<String, Object> attributes) {
            try {
                String token = null;
                if (request.getURI().getQuery() != null) {
                    for (String pair : request.getURI().getQuery().split("&")) {
                        String[] kv = pair.split("=", 2);
                        if (kv.length == 2 && "token".equals(kv[0])) { token = java.net.URLDecoder.decode(kv[1], "UTF-8"); }
                    }
                }
                if (token == null || token.isBlank()) return false;
                var claims = jwtUtil.parse(token);
                Object uid = claims.get("uid");
                if (uid == null) return false;
                attributes.put(ATTR_UID, ((Number) uid).longValue());
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        @Override
        public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Exception exception) {
        }
    }
}
