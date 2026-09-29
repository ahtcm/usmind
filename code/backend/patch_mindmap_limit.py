# -*- coding: utf-8 -*-
p = r'C:\Users\25750\Desktop\usmind\code\backend\src\main\java\com\usmind\service\impl\MindMapServiceImpl.java'
s = open(p, encoding='utf-8').read()

old_ctor = """    private final MindMapMapper mindMapMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MindMapServiceImpl(MindMapMapper mindMapMapper) {
        this.mindMapMapper = mindMapMapper;
    }"""
new_ctor = """    private final MindMapMapper mindMapMapper;
    private final com.usmind.mapper.SysUserMapper sysUserMapper;
    private final com.usmind.service.SettingsService settingsService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MindMapServiceImpl(MindMapMapper mindMapMapper,
                              com.usmind.mapper.SysUserMapper sysUserMapper,
                              com.usmind.service.SettingsService settingsService) {
        this.mindMapMapper = mindMapMapper;
        this.sysUserMapper = sysUserMapper;
        this.settingsService = settingsService;
    }

    private boolean isVip(Long userId) {
        com.usmind.entity.SysUser u = sysUserMapper.selectById(userId);
        if (u == null || u.getMemberLevel() == null || u.getMemberLevel() <= 0) return false;
        if (u.getMemberExpire() == null) return false;
        return u.getMemberExpire().isAfter(java.time.LocalDateTime.now());
    }"""
assert old_ctor in s, 'CTOR NOT FOUND'
s = s.replace(old_ctor, new_ctor, 1)

old_create = """    @Override
    public Long create(long userId, MindMapSaveRequest req) {
        MindMap m = new MindMap();"""
new_create = """    @Override
    public Long create(long userId, MindMapSaveRequest req) {
        // 画布个数限制：非会员 mindmapFreeCount / 会员 mindmapVipCount
        boolean vip = isVip(userId);
        int maxCount = settingsService.getInt(vip ? "mindmapVipCount" : "mindmapFreeCount", 200);
        Long count = mindMapMapper.selectCount(new LambdaQueryWrapper<MindMap>().eq(MindMap::getUserId, userId));
        if (count != null && count >= maxCount) {
            throw new BusinessException(400, "画布数量已达上限(" + maxCount + "个)" + (vip ? "" : "，升级会员可提升上限"));
        }
        MindMap m = new MindMap();"""
assert old_create in s, 'CREATE NOT FOUND'
s = s.replace(old_create, new_create, 1)

open(p, 'w', encoding='utf-8', newline='\n').write(s)
print('mindmap limit OK')
