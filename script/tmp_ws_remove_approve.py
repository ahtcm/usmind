# -*- coding: utf-8 -*-
import io
p = r'C:\Users\25750\Desktop\usmind\code\backend\src\main\java\com\usmind\ws\MindMapWsHandler.java'
s = io.open(p, encoding='utf-8').read()

# 1) 删 editRequests 字段
old = '''    /** 编辑申请：mindMapId -> nodeId -> 申请信息 */
    private final Map<String, Map<String, EditRequest>> editRequests = new ConcurrentHashMap<>();

'''
assert s.count(old) == 1, 'field=' + str(s.count(old))
s = s.replace(old, '')

# 2) 删 EditRequest 类
old = '''    /** 编辑申请信息 */
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
    }

'''
assert s.count(old) == 1, 'class=' + str(s.count(old))
s = s.replace(old, '')

# 3) 默认协作配置：去掉审批字段
old = '''    private static final String DEFAULT_COLLAB_CFG = "{\\"enableOwnerLock\\":false,\\"approveTimeout1\\":120,\\"approveTimeout2\\":180,\\"grantMinutes\\":10}";'''
new = '''    private static final String DEFAULT_COLLAB_CFG = "{\\"enableOwnerLock\\":false}";'''
assert s.count(old) == 1, 'cfg=' + str(s.count(old))
s = s.replace(old, new)

# 4) 消息分发：删 node_edit_request / node_edit_response 分支
old = '''            broadcast(mindMapId, payload);
        } else if ("node_edit_request".equals(type) && uid != null) {
            handleEditRequest(session, id, uid, node);
        } else if ("node_edit_response".equals(type) && uid != null) {
            handleEditResponse(session, id, uid, node);
        }'''
new = '''            broadcast(mindMapId, payload);
        }'''
assert s.count(old) == 1, 'dispatch=' + str(s.count(old))
s = s.replace(old, new)

# 5) node_change：删所有权/审批校验（保留锁校验）
old = '''            // 节点所有权校验：开启时仅 节点创建者/导图创建者/已获临时授权 可改
            if (!canEditNodeByOwner(id, uid, nodeId, node.path("nodeCreatorUid").asLong(0))) return;
'''
assert s.count(old) == 1, 'ownerlock=' + str(s.count(old))
s = s.replace(old, '')

# 6) 删整个审批方法区：handleEditRequest .. canEditNodeByOwner 结束（282 行起至 412 行止）
start_marker = '''    /** 处理编辑申请：按优先级（节点创建者→导图创建者）分派给在线审批人 */'''
end_marker = '''    /** 协作配置 */'''
si = s.find(start_marker)
ei = s.find(end_marker)
assert si != -1 and ei != -1 and si < ei, 'method region'
s = s[:si] + s[ei:]

io.open(p, 'w', encoding='utf-8', newline='').write(s)
print('WS HANDLER OK')
