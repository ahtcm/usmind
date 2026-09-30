# -*- coding: utf-8 -*-
import io
p = r'C:\Users\25750\Desktop\usmind\code\backend\src\main\java\com\usmind\service\impl\MindMapServiceImpl.java'
s = io.open(p, encoding='utf-8').read()
old = '''        Object finalData = req.getData();
        if (concurrent) {
            // 并发保存：按 uid 并集合并——本地新增/对方新增都保留（不覆盖未冲突节点）；
            // 同节点内容不同 → 冲突：resolveConflicts=mine 用客户端、theirs 用服务端、否则抛 409 返回冲突列表由前端选择
            JsonNode serverRoot = rootOf(m.getData());
            JsonNode clientRoot = rootOf(req.getData());
            List<Map<String, Object>> conflicts = new java.util.ArrayList<>();
            if (serverRoot != null && clientRoot != null) {
                JsonNode merged = mergeTrees(serverRoot, clientRoot,
                        req.getDeletedUids(), req.getResolveConflicts(), conflicts);
                if (!conflicts.isEmpty()) {
                    throw new com.usmind.common.ConflictException(
                            "导图已被其他成员修改，以下节点存在内容冲突，请选择以谁的为准", conflicts, cur);
                }
                finalData = buildMergedData(req.getData(), merged);
            }
        } else {
            // 非并发/强制覆盖：校验删除权限（被删节点须为本人创建，或其双亲节点由本人创建；根节点仅导图创建者可删）
            validateDeletes(userId, m, req.getDeletedUids());
        }'''
new = '''        Object finalData = req.getData();
        if (concurrent) {
            // 并发保存：按 uid 并集合并——本地新增/对方新增都保留（不覆盖未冲突节点）；
            // 同节点内容不同 → 冲突：resolveConflicts=mine 用客户端、theirs 用服务端、否则抛 409 返回冲突列表由前端选择
            JsonNode serverRoot = rootOf(m.getData());
            JsonNode clientRoot = rootOf(req.getData());
            List<Map<String, Object>> conflicts = new java.util.ArrayList<>();
            if (serverRoot != null && clientRoot != null) {
                JsonNode merged = mergeTrees(serverRoot, clientRoot,
                        req.getDeletedUids(), req.getResolveConflicts(), conflicts);
                if (!conflicts.isEmpty()) {
                    throw new com.usmind.common.ConflictException(
                            "导图已被其他成员修改，以下节点存在内容冲突，请选择以谁的为准", conflicts, cur);
                }
                finalData = buildMergedData(req.getData(), merged);
            }
        }
        // 无论并发与否，删除权限都必须校验（防止过期版本删除他人节点/根节点导致数据被破坏）
        validateDeletes(userId, m, req.getDeletedUids());'''
assert s.count(old) == 1, 'vdel=' + str(s.count(old))
s = s.replace(old, new)
io.open(p, 'w', encoding='utf-8', newline='').write(s)
print('VALIDATE FIX OK')
