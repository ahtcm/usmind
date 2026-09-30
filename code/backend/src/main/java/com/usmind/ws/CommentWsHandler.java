package com.usmind.ws;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.usmind.common.JwtUtil;
import com.usmind.entity.MindMap;
import com.usmind.mapper.MindMapMapper;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 评论实时推送通道：/ws/comment。
 * 作者（JWT + mindMapId）与分享访客（shareToken + password）均可订阅导图评论频道；
 * 评论入库后由 Controller 调用 broadcastComment 推送 comment_new，前端实时展示。
 * 断线后前端用 afterId 增量拉取兜底（原轮询接口保留）。
 */
@Component
public class CommentWsHandler extends TextWebSocketHandler {

    public static final String ATTR_MINDMAP = "mindMapId";
    public static final String ATTR_UID = "uid";

    private final ObjectMapper objectMapper = new ObjectMapper();
    /** mindMapId -> 订阅会话集合 */
    private final Map<String, Set<WebSocketSession>> commentSessions = new ConcurrentHashMap<>();
    private final MindMapMapper mindMapMapper;
    private final JwtUtil jwtUtil;

    public CommentWsHandler(MindMapMapper mindMapMapper, JwtUtil jwtUtil) {
        this.mindMapMapper = mindMapMapper;
        this.jwtUtil = jwtUtil;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Object mm = session.getAttributes().get(ATTR_MINDMAP);
        if (mm == null) {
            try { session.close(CloseStatus.NOT_ACCEPTABLE); } catch (Exception ignored) {}
            return;
        }
        commentSessions.computeIfAbsent(String.valueOf(mm), k -> ConcurrentHashMap.newKeySet()).add(session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // 评论通道为下行推送，忽略上行业务消息（保持心跳即可）
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Object mm = session.getAttributes().get(ATTR_MINDMAP);
        if (mm != null) {
            String key = String.valueOf(mm);
            Set<WebSocketSession> set = commentSessions.get(key);
            if (set != null) {
                set.remove(session);
                if (set.isEmpty()) commentSessions.remove(key);
            }
        }
    }

    /** 广播新评论给该导图的评论订阅会话 */
    public void broadcastComment(Long mindMapId, Map<String, Object> comment) {
        if (mindMapId == null) return;
        Set<WebSocketSession> set = commentSessions.get(String.valueOf(mindMapId));
        if (set == null || set.isEmpty()) return;
        String payload;
        try {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("event", "comment_new");
            m.put("mindMapId", mindMapId);
            m.put("comment", comment == null ? Map.of() : comment);
            payload = objectMapper.writeValueAsString(m);
        } catch (Exception e) {
            return;
        }
        for (WebSocketSession s : set) {
            try {
                if (s.isOpen()) s.sendMessage(new TextMessage(payload));
            } catch (Exception ignored) {}
        }
    }

    /** 握手拦截器：JWT（作者，带 mindMapId）或 shareToken+password（游客） */
    public static class CommentHandshakeInterceptor implements HandshakeInterceptor {
        private final JwtUtil jwtUtil;
        private final MindMapMapper mindMapMapper;

        public CommentHandshakeInterceptor(JwtUtil jwtUtil, MindMapMapper mindMapMapper) {
            this.jwtUtil = jwtUtil;
            this.mindMapMapper = mindMapMapper;
        }

        @Override
        public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                       org.springframework.web.socket.WebSocketHandler wsHandler,
                                       Map<String, Object> attributes) {
            URI uri = request.getURI();
            Map<String, String> q = queryMap(uri.getRawQuery());
            String token = q.get("token");
            if (!StringUtils.hasText(token)) return false;
            // 1) JWT 作者：需携带 mindMapId，且导图存在
            try {
                var claims = jwtUtil.parse(token);
                Object uidObj = claims.get("uid");
                if (uidObj != null) {
                    String mm = q.get("mindMapId");
                    if (!StringUtils.hasText(mm)) return false;
                    MindMap m = mindMapMapper.selectById(Long.valueOf(mm));
                    if (m == null) return false;
                    attributes.put(ATTR_MINDMAP, mm);
                    attributes.put(ATTR_UID, ((Number) uidObj).longValue());
                    return true;
                }
            } catch (Exception ignored) {}
            // 2) shareToken 游客：导图已分享 + 密码校验（与 getShare 同规则）
            MindMap m = mindMapMapper.selectOne(new LambdaQueryWrapper<MindMap>()
                    .eq(MindMap::getShareToken, token)
                    .eq(MindMap::getIsShared, 1)
                    .last("LIMIT 1"));
            if (m == null) return false;
            String stored = m.getSharePassword();
            if (StringUtils.hasText(stored)) {
                boolean ok;
                if (stored.startsWith("$2")) {
                    ok = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder()
                            .matches(passwordOf(q), stored);
                } else {
                    ok = stored.equals(passwordOf(q));
                }
                if (!ok) return false;
            }
            attributes.put(ATTR_MINDMAP, String.valueOf(m.getId()));
            attributes.put(ATTR_UID, 0L);
            return true;
        }

        @Override
        public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   org.springframework.web.socket.WebSocketHandler wsHandler,
                                   Exception exception) {
        }

        private static Map<String, String> queryMap(String raw) {
            Map<String, String> map = new HashMap<>();
            if (raw == null || raw.isBlank()) return map;
            for (String kv : raw.split("&")) {
                int i = kv.indexOf('=');
                String k = i > 0 ? kv.substring(0, i) : kv;
                String v = i > 0 ? kv.substring(i + 1) : "";
                try { v = URLDecoder.decode(v, StandardCharsets.UTF_8.name()); } catch (Exception ignored) {}
                map.put(k, v);
            }
            return map;
        }

        private static String passwordOf(Map<String, String> q) {
            String p = q.get("password");
            return p == null ? "" : p;
        }
    }
}
