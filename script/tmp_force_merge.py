# -*- coding: utf-8 -*-
"""MindMapServiceImpl: force=true 也走 git 式合并（resolve=mine 保留对方新增节点，仅文本以客户端为准）"""
import io
P = r'C:\Users\25750\Desktop\usmind\code\backend\src\main\java\com\usmind\service\impl\MindMapServiceImpl.java'
s = io.open(P, encoding='utf-8').read()

old = '''        int cur = m.getVersion() == null ? 0 : m.getVersion();
        boolean force = req.getForce() != null && req.getForce();
        // 并发合并条件：版本不一致，或显式携带冲突解决方式（409 后重提时 baseVersion 可能已等于最新版本，仍需按 resolve 合并而非整树覆盖）
        boolean resolveFlag = req.getResolveConflicts() != null && !req.getResolveConflicts().isBlank();
        boolean concurrent = !force && (resolveFlag || (req.getBaseVersion() != null && req.getBaseVersion() != cur));
        Object finalData = req.getData();
        if (concurrent) {'''
new = '''        int cur = m.getVersion() == null ? 0 : m.getVersion();
        boolean force = req.getForce() != null && req.getForce();
        // 并发合并条件：版本不一致，或显式携带冲突解决方式（409 后重提时 baseVersion 可能已等于最新版本，仍需按 resolve 合并而非整树覆盖）
        boolean resolveFlag = req.getResolveConflicts() != null && !req.getResolveConflicts().isBlank();
        boolean concurrent = resolveFlag || (req.getBaseVersion() != null && req.getBaseVersion() != cur);
        // force（"以我为准覆盖"）也走 uid 并集合并：保留对方新增节点不丢数据，仅同节点文本以客户端为准
        Object finalData = req.getData();
        if (concurrent || force) {'''
assert s.count(old) == 1, 'anchor1'
s = s.replace(old, new)

old2 = '''            if (serverRoot != null && clientRoot != null) {
                JsonNode merged = mergeTrees(serverRoot, clientRoot,
                        req.getDeletedUids(), req.getResolveConflicts(), conflicts);
                if (!conflicts.isEmpty()) {
                    throw new com.usmind.common.ConflictException(
                            "导图已被其他成员修改，以下节点存在内容冲突，请选择以谁的为准", conflicts, cur);
                }
                finalData = buildMergedData(req.getData(), merged);
            }'''
new2 = '''            if (serverRoot != null && clientRoot != null) {
                String resolve = force ? "mine" : req.getResolveConflicts();
                JsonNode merged = mergeTrees(serverRoot, clientRoot,
                        req.getDeletedUids(), resolve, conflicts);
                if (!conflicts.isEmpty() && !force) {
                    throw new com.usmind.common.ConflictException(
                            "导图已被其他成员修改，以下节点存在内容冲突，请选择以谁的为准", conflicts, cur);
                }
                finalData = buildMergedData(req.getData(), merged);
            }'''
assert s.count(old2) == 1, 'anchor2'
s = s.replace(old2, new2)
io.open(P, 'w', encoding='utf-8', newline='\n').write(s)
print('MindMapServiceImpl OK')
