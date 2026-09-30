# -*- coding: utf-8 -*-
"""MindMapWsHandler: 增加 node_added/node_removed/node_moved 结构增量广播"""
import io
P = r'C:\Users\25750\Desktop\usmind\code\backend\src\main\java\com\usmind\ws\MindMapWsHandler.java'
s = io.open(P, encoding='utf-8').read()

old = '''        } else if ("node_change".equals(type) && uid != null) {'''
new = '''        } else if (("node_added".equals(type) || "node_removed".equals(type) || "node_moved".equals(type)) && uid != null) {
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
        } else if ("node_change".equals(type) && uid != null) {'''
assert s.count(old) == 1, 'anchor1'
s = s.replace(old, new)
io.open(P, 'w', encoding='utf-8', newline='\n').write(s)
print('MindMapWsHandler OK')
