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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 导图协作实时同步 WebSocket：
 *  - 客户端携带 ?token=JWT 握手，校验身份后订阅导图频道
 *  - 订阅时校验访问权（属主或机构协作成员）
 *  - 导图保存成功后由 MindMapServiceImpl 调用 broadcast 推送轻量变更通知
 *  - 节点级协作：节点锁定（acquire/release）、实时编辑内容推送（node_change）
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

    /** uid -> 该用户的所有在线会话（在线状态判断用） */
    private final Map<Long, Set<WebSocketSession>> userSessions = new ConcurrentHashMap<>();

    /** 节点锁：mindMapId -> nodeId -> 锁信息 */
    private final Map<String, Map<String, LockEntry>> nodeLocks = new ConcurrentHashMap<>();

    /** 锁有效期（毫秒），编辑中客户端每 20s 续期 */
    private static final long LOCK_TTL_MS = 60_000L;

    /** 默认协作配置 */
    private static final String DEFAULT_COLLAB_CFG = "{\"enableOwnerLock\":false}";

    public MindMapWsHandler(JwtUtil jwtUtil, MindMapMapper mindMapMapper,
                            SysUserMapper sysUserMapper, OrgService orgService) {
        this.jwtUtil = jwtUtil;
        this.mindMapMapper = mindMapMapper;
        this.sysUserMapper = sysUserMapper;
        this.orgService = orgService;
    }

    public static final String ATTR_UID = "uid";
    public static final String ATTR_MINDMAP = "mindMapId";

    static class LockEntry {
        long userId;
        String userName;
        long expireAt;
        LockEntry(long userId, String userName, long expireAt) {
            this.userId = userId; this.userName = userName; this.expireAt = expireAt;
        }
    }

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

    /** 机构解散时强制踢出成员：推送"机构已销毁"提示后关闭连接 */
    public void kickOrgMembers(long orgId, String orgName, java.util.List<Long> memberUids) {
        if (memberUids == null) return;
        String payload;
        try {
            payload = objectMapper.writeValueAsString(Map.of(
                    "event", "org_disbanded",
                    "orgId", orgId,
                    "orgName", orgName == null ? "" : orgName
            ));
        } catch (Exception e) {
            payload = "{\"event\":\"org_disbanded\"}";
        }
        for (Long uid : memberUids) {
            if (uid == null) continue;
            Set<WebSocketSession> set = userSessions.get(uid);
            if (set == null || set.isEmpty()) continue;
            for (WebSocketSession s : set) {
                try {
                    if (s.isOpen()) {
                        s.sendMessage(new TextMessage(payload));
                        s.close(CloseStatus.NORMAL);
                    }
                } catch (Exception ignored) {
                }
            }
        }
    }

    private String buildError(String message) {
        try {
            return objectMapper.writeValueAsString(Map.of("type", "error", "message", message == null ? "" : message));
        } catch (Exception e) {
            return "{\"type\":\"error\",\"message\":\"\"}";
        }
    }

    /** 当前订阅某导图的会话数（调试用） */
    public int subscriberCount(String mindMapId) {
        Set<WebSocketSession> set = sessions.get(mindMapId);
        return set == null ? 0 : set.size();
    }

    /**
     * 该导图当前在线的订阅者是否只有指定用户自己。
     * 同一用户开多个标签页/多设备（多个 WS 会话但 userId 相同）不算"其他成员"，
     * 保存冲突时用于判断是否需要弹冲突框：只有自己在线 → 自动合并不弹窗。
     * 无人订阅（WS 未连接）时也视为只有自己，避免老页面/无协作场景误弹。
     */
    public boolean onlySelfOnline(String mindMapId, long userId) {
        Set<WebSocketSession> set = sessions.get(mindMapId);
        if (set == null || set.isEmpty()) return true;
        for (WebSocketSession s : set) {
            if (!s.isOpen()) continue;
            Object uid = s.getAttributes().get(ATTR_UID);
            if (uid instanceof Long && ((Long) uid) != userId) return false;
        }
        return true;
    }

    /** 定期清理过期节点锁并广播释放 */
    @Scheduled(fixedDelay = 30_000)
    public void cleanExpiredLocks() {
        long now = System.currentTimeMillis();
        List<String> toRemoveMindMaps = new ArrayList<>();
        for (Map.Entry<String, Map<String, LockEntry>> e : nodeLocks.entrySet()) {
            Map<String, LockEntry> locks = e.getValue();
            boolean changed = false;
            for (Map.Entry<String, LockEntry> le : new ArrayList<>(locks.entrySet())) {
                if (le.getValue().expireAt < now) {
                    LockEntry entry = locks.remove(le.getKey());
                    if (entry != null) {
                        changed = true;
                        broadcast(e.getKey(), buildLockEvent(e.getKey(), le.getKey(), "release", entry.userId, entry.userName));
                    }
                }
            }
            if (changed && locks.isEmpty()) toRemoveMindMaps.add(e.getKey());
        }
        toRemoveMindMaps.forEach(nodeLocks::remove);
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
        Long uid = (Long) session.getAttributes().get(ATTR_UID);

        if ("subscribe".equals(type)) {
            if (uid == null) { send(session, "{\"type\":\"error\",\"message\":\"未登录\"}"); return; }
            if (!canAccess(uid, id)) {
                send(session, "{\"type\":\"error\",\"message\":\"无权访问该导图\"}");
                return;
            }
            sessions.computeIfAbsent(mindMapId, k -> new CopyOnWriteArraySet<>()).add(session);
            userSessions.computeIfAbsent(uid, k -> new CopyOnWriteArraySet<>()).add(session);
            session.getAttributes().put(ATTR_MINDMAP, mindMapId);
            send(session, "{\"type\":\"subscribed\",\"mindMapId\":" + mindMapId + "}");
            // 订阅后回发当前所有节点锁状态，保证新进入的成员能看到既有锁定
            pushCurrentLocks(session, mindMapId);
        } else if ("unsubscribe".equals(type)) {
            Set<WebSocketSession> set = sessions.get(mindMapId);
            if (set != null) set.remove(session);
            session.getAttributes().remove(ATTR_MINDMAP);
        } else if ("lock".equals(type) && uid != null) {
            String nodeId = node.path("nodeId").asText();
            if (nodeId.isBlank()) return;
            String action = node.path("action").asText();
            String userName = getUserName(uid);
            long now = System.currentTimeMillis();
            Map<String, LockEntry> locks = nodeLocks.computeIfAbsent(mindMapId, k -> new ConcurrentHashMap<>());
            if ("acquire".equals(action)) {
                LockEntry existing = locks.get(nodeId);
                if (existing != null && existing.userId != uid && existing.expireAt >= now) {
                    // 已被他人锁定：仅告知请求者
                    send(session, buildLockEvent(mindMapId, nodeId, "denied", existing.userId, existing.userName));
                    return;
                }
                locks.put(nodeId, new LockEntry(uid, userName, now + LOCK_TTL_MS));
                broadcast(mindMapId, buildLockEvent(mindMapId, nodeId, "acquire", uid, userName));
            } else if ("release".equals(action)) {
                LockEntry existing = locks.get(nodeId);
                if (existing != null && existing.userId == uid) {
                    locks.remove(nodeId);
                    broadcast(mindMapId, buildLockEvent(mindMapId, nodeId, "release", uid, userName));
                }
            }
        } else if (("node_added".equals(type) || "node_removed".equals(type) || "node_moved".equals(type)) && uid != null) {
            String nodeId = node.path("nodeId").asText();
            if (nodeId.isBlank()) return;
            if (!canAccess(uid, id)) {
                send(session, buildError("无权编辑该导图"));
                return;
            }
            String userName = getUserName(uid);
            com.fasterxml.jackson.databind.node.ObjectNode payload = objectMapper.createObjectNode();
            payload.put("event", type);
            payload.put("mindMapId", id);
            payload.put("nodeId", nodeId);
            payload.put("userId", uid);
            payload.put("userName", userName == null ? "" : userName);
            payload.put("ts", System.currentTimeMillis());
            if ("node_added".equals(type)) {
                String parentId = node.path("parentId").asText();
                payload.put("parentId", parentId == null ? "" : parentId);
                payload.put("index", node.path("index").asInt(-1));
                JsonNode nd = node.get("nodeData");
                // 子树过大（含富文本/公式/资源）时降级为保存广播兜底，避免 WS 消息过大
                if (nd != null && nd.toString().length() <= 200_000) {
                    payload.set("nodeData", nd);
                } else {
                    payload.put("nodeDataTooLarge", true);
                }
            } else if ("node_moved".equals(type)) {
                String parentId = node.path("parentId").asText();
                payload.put("parentId", parentId == null ? "" : parentId);
                payload.put("index", node.path("index").asInt(-1));
            }
            broadcast(mindMapId, objectMapper.writeValueAsString(payload));
        } else if ("node_change".equals(type) && uid != null) {
            String nodeId = node.path("nodeId").asText();
            String text = node.path("text").asText();
            if (nodeId.isBlank()) return;
            // 权限校验：机构解散/成员被移出后拒绝实时编辑（属主始终可编辑）
            if (!canAccess(uid, id)) {
                send(session, buildError("无权编辑该导图"));
                return;
            }
            // 校验发送者确有锁（防滥用）
            Map<String, LockEntry> locks = nodeLocks.get(mindMapId);
            LockEntry entry = locks == null ? null : locks.get(nodeId);
            if (entry == null || entry.userId != uid) return;
            String userName = getUserName(uid);
            String payload;
            try {
                payload = objectMapper.writeValueAsString(Map.of(
                        "event", "node_change",
                        "mindMapId", id,
                        "nodeId", nodeId,
                        "text", text == null ? "" : text,
                        "userId", uid,
                        "userName", userName == null ? "" : userName,
                        "ts", System.currentTimeMillis()
                ));
            } catch (Exception e) {
                return;
            }
            broadcast(mindMapId, payload);
        }
    }

    /** 协作配置 */
    static class CollabCfg {
        boolean enableOwnerLock = false;
        long approveTimeout1 = 120;
        long approveTimeout2 = 180;
        long grantMinutes = 10;
    }

    private CollabCfg readCfg(long mindMapId) {
        CollabCfg cfg = new CollabCfg();
        try {
            MindMap m = mindMapMapper.selectById(mindMapId);
            String raw = m == null ? null : m.getCollabConfig();
            if (raw == null || raw.isBlank()) return cfg;
            JsonNode n = objectMapper.readTree(raw);
            if (n.has("enableOwnerLock")) cfg.enableOwnerLock = n.path("enableOwnerLock").asBoolean(false);
            if (n.has("approveTimeout1")) cfg.approveTimeout1 = n.path("approveTimeout1").asLong(120);
            if (n.has("approveTimeout2")) cfg.approveTimeout2 = n.path("approveTimeout2").asLong(180);
            if (n.has("grantMinutes")) cfg.grantMinutes = n.path("grantMinutes").asLong(10);
        } catch (Exception ignored) {
        }
        return cfg;
    }

    /** 用户是否有在线 WS 会话 */
    private boolean isUserOnline(long uid) {
        Set<WebSocketSession> set = userSessions.get(uid);
        if (set == null || set.isEmpty()) return false;
        for (WebSocketSession s : set) {
            if (s.isOpen()) return true;
        }
        return false;
    }

    /** 定向发送给某用户的所有在线会话 */
    private void sendToUser(long uid, String payload) {
        Set<WebSocketSession> set = userSessions.get(uid);
        if (set == null) return;
        for (WebSocketSession s : set) {
            try {
                if (s.isOpen()) s.sendMessage(new TextMessage(payload));
            } catch (Exception ignored) {
            }
        }
    }

    /** 回发当前导图全部节点锁状态（新订阅者进入时） */
    private void pushCurrentLocks(WebSocketSession session, String mindMapId) {
        Map<String, LockEntry> locks = nodeLocks.get(mindMapId);
        if (locks == null || locks.isEmpty()) return;
        long now = System.currentTimeMillis();
        for (Map.Entry<String, LockEntry> e : locks.entrySet()) {
            LockEntry le = e.getValue();
            if (le.expireAt < now) continue;
            send(session, buildLockEvent(mindMapId, e.getKey(), "acquire", le.userId, le.userName));
        }
    }

    private String buildLockEvent(String mindMapId, String nodeId, String action, long userId, String userName) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "event", "node_lock",
                    "mindMapId", Long.parseLong(mindMapId),
                    "nodeId", nodeId,
                    "action", action,
                    "userId", userId,
                    "userName", userName == null ? "" : userName,
                    "ts", System.currentTimeMillis()
            ));
        } catch (Exception e) {
            return "{\"event\":\"node_lock\"}";
        }
    }

    private String getUserName(long userId) {
        try {
            SysUser u = sysUserMapper.selectById(userId);
            if (u == null) return "";
            String name = u.getNickname();
            if (name == null || name.isBlank()) name = u.getUsername();
            return name == null ? "" : name;
        } catch (Exception e) {
            return "";
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
            // 断开时释放该用户在该导图持有的所有节点锁
            Long uid = (Long) session.getAttributes().get(ATTR_UID);
            if (uid != null) {
                Map<String, LockEntry> locks = nodeLocks.get(mm.toString());
                if (locks != null && !locks.isEmpty()) {
                    List<String> released = new ArrayList<>();
                    for (Map.Entry<String, LockEntry> e : locks.entrySet()) {
                        if (e.getValue().userId == uid) {
                            locks.remove(e.getKey());
                            released.add(e.getKey());
                        }
                    }
                    for (String nid : released) {
                        broadcast(mm.toString(), buildLockEvent(mm.toString(), nid, "release", uid, ""));
                    }
                }
            }
        }
        // 兜底：清理所有残留引用
        sessions.values().forEach(set -> set.remove(session));
        Long uid0 = (Long) session.getAttributes().get(ATTR_UID);
        if (uid0 != null) {
            Set<WebSocketSession> us = userSessions.get(uid0);
            if (us != null) {
                us.remove(session);
                if (us.isEmpty()) userSessions.remove(uid0);
            }
        }
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
