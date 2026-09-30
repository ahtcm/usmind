# -*- coding: utf-8 -*-
"""修正乐观锁：WHERE 用旧版本（m.version 保持 cur），DB 自增 version；retry 同理"""
import io
P = r'C:\Users\25750\Desktop\usmind\code\backend\src\main\java\com\usmind\service\impl\MindMapServiceImpl.java'
s = io.open(P, encoding='utf-8').read()

old = '''        m.setVersion(cur + 1);
        int rows = mindMapMapper.updateByIdAndVersion(m, cur);
        if (rows == 0) {
            // 乐观锁失败：版本已被他人推进（并发竞态）——重读最新版本、重新做并集合并，再尝试一次；
            // 仍失败则抛冲突由前端选择以谁的为准
            MindMap fresh = mindMapMapper.selectById(id);
            if (fresh == null) throw new BusinessException(404, "导图不存在");
            int cur2 = fresh.getVersion() == null ? 0 : fresh.getVersion();
            JsonNode serverRoot2 = rootOf(fresh.getData());
            JsonNode clientRoot2 = rootOf(req.getData());
            List<Map<String, Object>> conflicts2 = new java.util.ArrayList<>();
            String resolve2 = force ? "mine" : req.getResolveConflicts();
            Object finalData2 = req.getData();
            if (serverRoot2 != null && clientRoot2 != null) {
                JsonNode merged2 = mergeTrees(serverRoot2, clientRoot2,
                        req.getDeletedUids(), resolve2, conflicts2);
                if (!conflicts2.isEmpty() && !force) {
                    throw new com.usmind.common.ConflictException(
                            "导图已被其他成员修改，以下节点存在内容冲突，请选择以谁的为准", conflicts2, cur2);
                }
                finalData2 = buildMergedData(req.getData(), merged2);
            }
            validateDeletes(userId, fresh, req.getDeletedUids());
            if (req.getName() != null) fresh.setName(req.getName());
            if (finalData2 != null) fresh.setData(toJson(finalData2));
            if (req.getLayout() != null) fresh.setLayout(req.getLayout());
            if (req.getTheme() != null) fresh.setTheme(req.getTheme());
            if (req.getViewData() != null) fresh.setViewData(toJson(req.getViewData()));
            if (req.getManualStyles() != null) fresh.setManualStyles(toJson(req.getManualStyles()));
            fresh.setVersion(cur2 + 1);
            rows = mindMapMapper.updateByIdAndVersion(fresh, cur2);
            if (rows == 0) {
                throw new com.usmind.common.ConflictException(
                        "并发保存繁忙，请稍后重试", new java.util.ArrayList<>(), cur2);
            }
            m = fresh;
        }'''
new = '''        // 乐观锁：m.version 保持旧版本用于 WHERE 条件，DB 侧 version=version+1 自增
        int rows = mindMapMapper.updateByIdAndVersion(m);
        if (rows == 0) {
            // 乐观锁失败：版本已被他人推进（并发竞态）——重读最新版本、重新做并集合并，再尝试一次；
            // 仍失败则抛冲突由前端选择以谁的为准
            MindMap fresh = mindMapMapper.selectById(id);
            if (fresh == null) throw new BusinessException(404, "导图不存在");
            int cur2 = fresh.getVersion() == null ? 0 : fresh.getVersion();
            JsonNode serverRoot2 = rootOf(fresh.getData());
            JsonNode clientRoot2 = rootOf(req.getData());
            List<Map<String, Object>> conflicts2 = new java.util.ArrayList<>();
            String resolve2 = force ? "mine" : req.getResolveConflicts();
            Object finalData2 = req.getData();
            if (serverRoot2 != null && clientRoot2 != null) {
                JsonNode merged2 = mergeTrees(serverRoot2, clientRoot2,
                        req.getDeletedUids(), resolve2, conflicts2);
                if (!conflicts2.isEmpty() && !force) {
                    throw new com.usmind.common.ConflictException(
                            "导图已被其他成员修改，以下节点存在内容冲突，请选择以谁的为准", conflicts2, cur2);
                }
                finalData2 = buildMergedData(req.getData(), merged2);
            }
            validateDeletes(userId, fresh, req.getDeletedUids());
            if (req.getName() != null) fresh.setName(req.getName());
            if (finalData2 != null) fresh.setData(toJson(finalData2));
            if (req.getLayout() != null) fresh.setLayout(req.getLayout());
            if (req.getTheme() != null) fresh.setTheme(req.getTheme());
            if (req.getViewData() != null) fresh.setViewData(toJson(req.getViewData()));
            if (req.getManualStyles() != null) fresh.setManualStyles(toJson(req.getManualStyles()));
            rows = mindMapMapper.updateByIdAndVersion(fresh);
            if (rows == 0) {
                throw new com.usmind.common.ConflictException(
                        "并发保存繁忙，请稍后重试", new java.util.ArrayList<>(), cur2);
            }
            m = fresh;
            m.setVersion(cur2 + 1);
        } else {
            m.setVersion(cur + 1);
        }'''
assert s.count(old) == 1, 'anchor'
s = s.replace(old, new)
io.open(P, 'w', encoding='utf-8', newline='\n').write(s)
print('乐观锁修正 OK')
