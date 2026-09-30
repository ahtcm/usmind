# -*- coding: utf-8 -*-
import io, os
p = r'C:\Users\25750\Desktop\usmind\code\backend\src\main\java\com\usmind\ws\MindMapWsHandler.java'
s = io.open(p, encoding='utf-8').read()

# 1) node_change 加 canAccess 校验
old = '''        } else if ("node_change".equals(type) && uid != null) {
            String nodeId = node.path("nodeId").asText();
            String text = node.path("text").asText();
            if (nodeId.isBlank()) return;
            // 校验发送者确有锁（防滥用）'''
new = '''        } else if ("node_change".equals(type) && uid != null) {
            String nodeId = node.path("nodeId").asText();
            String text = node.path("text").asText();
            if (nodeId.isBlank()) return;
            // 权限校验：机构解散/成员被移出后拒绝实时编辑（属主始终可编辑）
            if (!canAccess(uid, id)) {
                send(session, buildError("无权编辑该导图"));
                return;
            }
            // 校验发送者确有锁（防滥用）'''
assert s.count(old) == 1, 'nc=' + str(s.count(old))
s = s.replace(old, new)

# 2) 在 broadcast 方法后追加 kickOrgMembers + buildError（插在 subscriberCount 前）
anchor = '''    /** 当前订阅某导图的会话数（调试用） */'''
add = '''    /** 机构解散时强制踢出成员：推送"机构已销毁"提示后关闭连接 */
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
            payload = "{\\"event\\":\\"org_disbanded\\"}";
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
            return "{\\"type\\":\\"error\\",\\"message\\":\\"\\"}";
        }
    }

'''
assert s.count(anchor) == 1, 'anchor=' + str(s.count(anchor))
s = s.replace(anchor, add + anchor)

io.open(p, 'w', encoding='utf-8', newline='').write(s)
print('WS HANDLER OK')
