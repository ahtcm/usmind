# -*- coding: utf-8 -*-
"""后端乐观锁保存：MindMapMapper 加 updateByIdAndVersion；MindMapServiceImpl.update 改为乐观锁+重读重算重试"""
import io

# ---- 1. MindMapMapper ----
P1 = r'C:\Users\25750\Desktop\usmind\code\backend\src\main\java\com\usmind\mapper\MindMapMapper.java'
s1 = io.open(P1, encoding='utf-8').read()
if 'updateByIdAndVersion' not in s1:
    old1 = '''public interface MindMapMapper extends BaseMapper<MindMap> {
}'''
    new1 = '''public interface MindMapMapper extends BaseMapper<MindMap> {
    /** 乐观锁保存：仅当数据库版本仍为 #{version} 时更新并 version+1（防止并发保存互相覆盖） */
    @org.apache.ibatis.annotations.Update("UPDATE mind_map SET name=#{name}, data=#{data}, layout=#{layout}, theme=#{theme}, view_data=#{viewData}, manual_styles=#{manualStyles}, collab_config=#{collabConfig}, version=version+1, updated_at=NOW() WHERE id=#{id} AND version=#{version}")
    int updateByIdAndVersion(MindMap m);
}'''
    assert s1.count(old1) == 1, 'mapper anchor'
    s1 = s1.replace(old1, new1)
    io.open(P1, 'w', encoding='utf-8', newline='\n').write(s1)
    print('Mapper OK')
else:
    print('Mapper already')

# ---- 2. MindMapServiceImpl.update 乐观锁 ----
P2 = r'C:\Users\25750\Desktop\usmind\code\backend\src\main\java\com\usmind\service\impl\MindMapServiceImpl.java'
s2 = io.open(P2, encoding='utf-8').read()
old2 = '''        m.setVersion(cur + 1);
        mindMapMapper.updateById(m);'''
new2 = '''        m.setVersion(cur + 1);
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
assert s2.count(old2) == 1, 'impl anchor'
s2 = s2.replace(old2, new2)
io.open(P2, 'w', encoding='utf-8', newline='\n').write(s2)
print('Impl OK')
