# -*- coding: utf-8 -*-
import io

def patch(path, pairs):
    with io.open(path, 'r', encoding='utf-8') as f:
        s = f.read()
    for old, new, tag in pairs:
        assert s.count(old) == 1, tag + ' count=' + str(s.count(old))
        s = s.replace(old, new)
    with io.open(path, 'w', encoding='utf-8', newline='') as f:
        f.write(s)
    print('OK', path.split('\\')[-1])

# 1. SettingsServiceImpl：全局默认开关
patch(r'C:\Users\25750\Desktop\usmind\code\backend\src\main\java\com\usmind\service\impl\SettingsServiceImpl.java', [
    ('''        // 画布个数限制
        putDefaultIfAbsent("mindmapFreeCount", "5");''',
     '''        // 画布个数限制
        putDefaultIfAbsent("mindmapFreeCount", "5");
        // 节点所有权默认开关（0=关 1=开，作用于新建导图）
        putDefaultIfAbsent("ownerLockDefault", "0");''',
     'settings-ownerlock'),
])

# 2. MindMapService interface
patch(r'C:\Users\25750\Desktop\usmind\code\backend\src\main\java\com\usmind\service\MindMapService.java', [
    ('''    void unshare(long userId, Long id);
    MindMap getByShareToken(String token, String password);''',
     '''    void unshare(long userId, Long id);
    MindMap getByShareToken(String token, String password);
    /** 更新导图协作配置（节点所有权开关/审批超时/授权时长） */
    void updateCollabConfig(long userId, Long id, java.util.Map<String, Object> cfg);''',
     'service-iface'),
])

# 3. MindMapServiceImpl：create 写默认配置 + updateCollabConfig
patch(r'C:\Users\25750\Desktop\usmind\code\backend\src\main\java\com\usmind\service\impl\MindMapServiceImpl.java', [
    ('''        m.setManualStyles(req.getManualStyles() != null ? toJson(req.getManualStyles()) : null);
        m.setIsShared(0);''',
     '''        m.setManualStyles(req.getManualStyles() != null ? toJson(req.getManualStyles()) : null);
        m.setIsShared(0);
        // 协作配置默认：全局开关（后台配置 ownerLockDefault）
        String ownerLockDefault = settingsService.get("ownerLockDefault", "0");
        boolean lockOn = "1".equals(ownerLockDefault);
        m.setCollabConfig("{\\"enableOwnerLock\\":" + (lockOn ? "true" : "false")
                + ",\\"approveTimeout1\\":120,\\"approveTimeout2\\":180,\\"grantMinutes\\":10}");''',
     'create-collabcfg'),
    ('''    @Override
    public MindMap get(long userId, Long id) {''',
     '''    @Override
    public void updateCollabConfig(long userId, Long id, java.util.Map<String, Object> cfg) {
        MindMap m = mindMapMapper.selectById(id);
        if (m == null || !canAccess(userId, m)) {
            throw new BusinessException(404, "导图不存在");
        }
        if (cfg == null || cfg.isEmpty()) return;
        String cur = m.getCollabConfig();
        com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
        java.util.Map<String, Object> merged;
        try {
            merged = new java.util.HashMap<>();
            if (cur != null && !cur.isBlank()) {
                merged.putAll(om.readValue(cur, java.util.Map.class));
            }
        } catch (Exception e) {
            merged = new java.util.HashMap<>();
        }
        if (cfg.containsKey("enableOwnerLock")) merged.put("enableOwnerLock", cfg.get("enableOwnerLock"));
        if (cfg.containsKey("approveTimeout1")) merged.put("approveTimeout1", cfg.get("approveTimeout1"));
        if (cfg.containsKey("approveTimeout2")) merged.put("approveTimeout2", cfg.get("approveTimeout2"));
        if (cfg.containsKey("grantMinutes")) merged.put("grantMinutes", cfg.get("grantMinutes"));
        try {
            m.setCollabConfig(om.writeValueAsString(merged));
        } catch (Exception e) {
            throw new BusinessException(500, "协作配置格式错误");
        }
        mindMapMapper.updateById(m);
    }

    @Override
    public MindMap get(long userId, Long id) {''',
     'update-collabcfg'),
])

# 4. MindMapController：GET/PUT /collab-config
patch(r'C:\Users\25750\Desktop\usmind\code\backend\src\main\java\com\usmind\controller\MindMapController.java', [
    ('''    @DeleteMapping("/{id}/share")''',
     '''    @GetMapping("/{id}/collab-config")
    public Result<java.util.Map<String, Object>> getCollabConfig(@PathVariable Long id) {
        MindMap m = mindMapService.get(SecurityUtil.currentUserId(), id);
        java.util.Map<String, Object> cfg = new java.util.HashMap<>();
        String raw = m.getCollabConfig();
        if (raw != null && !raw.isBlank()) {
            try {
                cfg.putAll(new com.fasterxml.jackson.databind.ObjectMapper().readValue(raw, java.util.Map.class));
            } catch (Exception ignored) {
            }
        }
        return Result.ok(cfg);
    }

    @PutMapping("/{id}/collab-config")
    public Result<Void> updateCollabConfig(@PathVariable Long id, @RequestBody java.util.Map<String, Object> body) {
        mindMapService.updateCollabConfig(SecurityUtil.currentUserId(), id, body);
        return Result.ok();
    }

    @DeleteMapping("/{id}/share")''',
     'controller-collabcfg'),
])
print('ALL OK')
