# -*- coding: utf-8 -*-
import io
p = r'C:\Users\25750\Desktop\usmind\code\backend\src\main\java\com\usmind\ws\MindMapWsHandler.java'
with io.open(p, 'r', encoding='utf-8') as f:
    s = f.read()

# 1. 类成员区：加 userSessions / editRequests / DEFAULT_COLLAB_CFG / EditRequest
old1 = """    /** mindMapId -> 订阅该导图的会话集合 */
    private final Map<String, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();

    /** 节点锁：mindMapId -> nodeId -> 锁信息 */
    private final Map<String, Map<String, LockEntry>> nodeLocks = new ConcurrentHashMap<>();

    /** 锁有效期（毫秒），编辑中客户端每 20s 续期 */
    private static final long LOCK_TTL_MS = 60_000L;"""
new1 = """    /** mindMapId -> 订阅该导图的会话集合 */
    private final Map<String, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();

    /** uid -> 该用户的所有在线会话（在线状态判断用） */
    private final Map<Long, Set<WebSocketSession>> userSessions = new ConcurrentHashMap<>();

    /** 节点锁：mindMapId -> nodeId -> 锁信息 */
    private final Map<String, Map<String, LockEntry>> nodeLocks = new ConcurrentHashMap<>();

    /** 编辑申请：mindMapId -> nodeId -> 申请信息 */
    private final Map<String, Map<String, EditRequest>> editRequests = new ConcurrentHashMap<>();

    /** 锁有效期（毫秒），编辑中客户端每 20s 续期 */
    private static final long LOCK_TTL_MS = 60_000L;

    /** 默认协作配置 */
    private static final String DEFAULT_COLLAB_CFG = "{\\"enableOwnerLock\\":false,\\"approveTimeout1\\":120,\\"approveTimeout2\\":180,\\"grantMinutes\\":10}";

    /** 编辑申请信息 */
    static class EditRequest {
        long applicantUid;
        String applicantName;
        long nodeCreatorUid;
        long mapOwnerUid;
        String nodeText;
        long createTime;
        long approveToUid;      // 当前审批人
        long grantedUntil;      // 批准后的临时授权截止（0=未批准）
        boolean autoRejected;   // 已自动拒绝
        EditRequest(long applicantUid, String applicantName, long nodeCreatorUid,
                    long mapOwnerUid, String nodeText) {
            this.applicantUid = applicantUid;
            this.applicantName = applicantName == null ? "" : applicantName;
            this.nodeCreatorUid = nodeCreatorUid;
            this.mapOwnerUid = mapOwnerUid;
            this.nodeText = nodeText == null ? "" : nodeText;
            this.createTime = System.currentTimeMillis();
            this.approveToUid = 0;
            this.grantedUntil = 0;
            this.autoRejected = false;
        }
    }"""
assert s.count(old1) == 1, 'old1'
s = s.replace(old1, new1)

# 2. subscribe 分支注册 userSessions
old2 = """            sessions.computeIfAbsent(mindMapId, k -> new CopyOnWriteArraySet<>()).add(session);
            session.getAttributes().put(ATTR_MINDMAP, mindMapId);"""
new2 = """            sessions.computeIfAbsent(mindMapId, k -> new CopyOnWriteArraySet<>()).add(session);
            userSessions.computeIfAbsent(uid, k -> new CopyOnWriteArraySet<>()).add(session);
            session.getAttributes().put(ATTR_MINDMAP, mindMapId);"""
assert s.count(old2) == 1, 'old2'
s = s.replace(old2, new2)

# 3. node_change 分支：加所有权校验（在锁校验通过后）
old3 = """            // 校验发送者确有锁（防滥用）
            Map<String, LockEntry> locks = nodeLocks.get(mindMapId);
            LockEntry entry = locks == null ? null : locks.get(nodeId);
            if (entry == null || entry.userId != uid) return;
            String userName = getUserName(uid);"""
new3 = """            // 校验发送者确有锁（防滥用）
            Map<String, LockEntry> locks = nodeLocks.get(mindMapId);
            LockEntry entry = locks == null ? null : locks.get(nodeId);
            if (entry == null || entry.userId != uid) return;
            // 节点所有权校验：开启时仅 节点创建者/导图创建者/已获临时授权 可改
            if (!canEditNodeByOwner(id, uid, nodeId, node.path("nodeCreatorUid").asLong(0))) return;
            String userName = getUserName(uid);"""
assert s.count(old3) == 1, 'old3'
s = s.replace(old3, new3)

# 4. handleTextMessage 末尾（node_change 分支后）加 申请/审批 分支
old4 = """            broadcast(mindMapId, payload);
        }
    }

    /** 回发当前导图全部节点锁状态（新订阅者进入时） */"""
new4 = """            broadcast(mindMapId, payload);
        } else if ("node_edit_request".equals(type) && uid != null) {
            handleEditRequest(session, id, uid, node);
        } else if ("node_edit_response".equals(type) && uid != null) {
            handleEditResponse(session, id, uid, node);
        }
    }

    /** 处理编辑申请：按优先级（节点创建者→导图创建者）分派给在线审批人 */
    private void handleEditRequest(WebSocketSession session, long mindMapId, long uid, JsonNode node) {
        String nodeId = node.path("nodeId").asText();
        if (nodeId.isBlank()) return;
        long nodeCreatorUid = node.path("nodeCreatorUid").asLong(0);
        String nodeText = node.path("nodeText").asText();
        String applicantName = getUserName(uid);
        MindMap m = mindMapMapper.selectById(mindMapId);
        if (m == null) return;
        long mapOwnerUid = m.getUserId() == null ? 0 : m.getUserId();
        Map<String, EditRequest> reqs = editRequests.computeIfAbsent(String.valueOf(mindMapId), k -> new ConcurrentHashMap<>());
        EditRequest er = reqs.get(nodeId);
        if (er != null && !er.autoRejected && er.grantedUntil <= 0) {
            // 已有未决申请
            send(session, buildEditResult(mindMapId, nodeId, "pending", "", "该节点已有申请正在审批中"));
            return;
        }
        er = new EditRequest(uid, applicantName, nodeCreatorUid, mapOwnerUid, nodeText);
        reqs.put(nodeId, er);
        // 优先级：节点创建者在线 → 节点创建者；否则导图创建者
        long approver = nodeCreatorUid;
        if (approver <= 0 || !isUserOnline(approver)) approver = mapOwnerUid;
        if (approver > 0) {
            er.approveToUid = approver;
            notifyApprover(mindMapId, nodeId, er, "new");
        }
        send(session, buildEditResult(mindMapId, nodeId, "sent", "", "已提交编辑申请，等待审批"));
    }

    /** 处理审批结果 */
    private void handleEditResponse(WebSocketSession session, long mindMapId, long uid, JsonNode node) {
        String nodeId = node.path("nodeId").asText();
        if (nodeId.isBlank()) return;
        String approve = node.path("approve").asText();
        Map<String, EditRequest> reqs = editRequests.get(String.valueOf(mindMapId));
        EditRequest er = reqs == null ? null : reqs.get(nodeId);
        if (er == null || er.autoRejected || er.grantedUntil > 0) return;
        if (er.approveToUid != uid) return; // 仅当前审批人可批
        if ("yes".equals(approve)) {
            long grantMs = readCfg(mindMapId).grantMinutes * 60_000L;
            er.grantedUntil = System.currentTimeMillis() + grantMs;
            // 通知申请人
            sendToUser(er.applicantUid, buildEditResult(mindMapId, nodeId, "approved",
                    getUserName(uid), "已获授权，可编辑该节点（" + (grantMs / 60000) + " 分钟）"));
        } else {
            er.autoRejected = true;
            sendToUser(er.applicantUid, buildEditResult(mindMapId, nodeId, "rejected",
                    getUserName(uid), "编辑申请已被拒绝"));
            reqs.remove(nodeId);
        }
    }

    /** 定时调度：申请转派/自动拒绝、授权过期清理 */
    @Scheduled(fixedDelay = 15_000)
    public void processEditRequests() {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Map<String, EditRequest>> e : new ArrayList<>(editRequests.entrySet())) {
            long mindMapId = Long.parseLong(e.getKey());
            for (Map.Entry<String, EditRequest> re : new ArrayList<>(e.getValue().entrySet())) {
                EditRequest er = re.getValue();
                if (er.grantedUntil > 0) {
                    if (er.grantedUntil < now) e.getValue().remove(re.getKey()); // 授权过期移除
                    continue;
                }
                long ageSec = (now - er.createTime) / 1000;
                CollabCfg cfg = readCfg(mindMapId);
                // 超时2：自动拒绝
                if (ageSec >= cfg.approveTimeout2) {
                    er.autoRejected = true;
                    sendToUser(er.applicantUid, buildEditResult(mindMapId, re.getKey(), "auto_rejected", "", "申请超时未审批，已自动拒绝"));
                    e.getValue().remove(re.getKey());
                    continue;
                }
                // 当前审批人不在线 或 超时1：转派导图创建者
                if (er.approveToUid != er.mapOwnerUid &&
                        (ageSec >= cfg.approveTimeout1 || !isUserOnline(er.approveToUid))) {
                    er.approveToUid = er.mapOwnerUid;
                    if (er.mapOwnerUid > 0) notifyApprover(mindMapId, re.getKey(), er, "transfer");
                }
            }
            if (e.getValue().isEmpty()) editRequests.remove(e.getKey());
        }
    }

    private void notifyApprover(long mindMapId, String nodeId, EditRequest er, String reason) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "event", "node_edit_request",
                    "mindMapId", mindMapId,
                    "nodeId", nodeId,
                    "nodeText", er.nodeText == null ? "" : er.nodeText,
                    "applicantUid", er.applicantUid,
                    "applicantName", er.applicantName == null ? "" : er.applicantName,
                    "nodeCreatorUid", er.nodeCreatorUid,
                    "reason", reason,
                    "ts", System.currentTimeMillis()
            ));
            sendToUser(er.approveToUid, payload);
        } catch (Exception ignored) {
        }
    }

    private String buildEditResult(long mindMapId, String nodeId, String result, String approverName, String message) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "event", "node_edit_result",
                    "mindMapId", mindMapId,
                    "nodeId", nodeId,
                    "result", result,
                    "approverName", approverName == null ? "" : approverName,
                    "message", message == null ? "" : message,
                    "ts", System.currentTimeMillis()
            ));
        } catch (Exception e) {
            return "{\\"event\\":\\"node_edit_result\\"}";
        }
    }

    /** 节点所有权校验：关闭时放行；开启时仅 节点创建者/导图创建者/获临时授权 可改 */
    private boolean canEditNodeByOwner(long mindMapId, long uid, String nodeId, long nodeCreatorUid) {
        CollabCfg cfg = readCfg(mindMapId);
        if (!cfg.enableOwnerLock) return true;
        MindMap m = mindMapMapper.selectById(mindMapId);
        if (m == null) return true;
        long owner = m.getUserId() == null ? 0 : m.getUserId();
        if (owner == uid) return true;
        if (nodeCreatorUid > 0 && nodeCreatorUid == uid) return true;
        Map<String, EditRequest> reqs = editRequests.get(String.valueOf(mindMapId));
        EditRequest er = reqs == null ? null : reqs.get(nodeId);
        return er != null && er.applicantUid == uid && er.grantedUntil > System.currentTimeMillis();
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

    /** 回发当前导图全部节点锁状态（新订阅者进入时） */"""
assert s.count(old4) == 1, 'old4'
s = s.replace(old4, new4)

# 5. afterConnectionClosed：清理 userSessions
old5 = """        // 兜底：清理所有残留引用
        sessions.values().forEach(set -> set.remove(session));
    }"""
new5 = """        // 兜底：清理所有残留引用
        sessions.values().forEach(set -> set.remove(session));
        Long uid0 = (Long) session.getAttributes().get(ATTR_UID);
        if (uid0 != null) {
            Set<WebSocketSession> us = userSessions.get(uid0);
            if (us != null) {
                us.remove(session);
                if (us.isEmpty()) userSessions.remove(uid0);
            }
        }
    }"""
assert s.count(old5) == 1, 'old5'
s = s.replace(old5, new5)

with io.open(p, 'w', encoding='utf-8', newline='') as f:
    f.write(s)
print('WS HANDLER OK')
