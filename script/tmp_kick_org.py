# -*- coding: utf-8 -*-
import io
p = r'C:\Users\25750\Desktop\usmind\code\backend\src\main\java\com\usmind\service\impl\OrgServiceImpl.java'
s = io.open(p, encoding='utf-8').read()

# 1) 注入 MindMapWsHandler（@Lazy 避免循环依赖）
old_import = 'import com.usmind.service.OrgService;\nimport com.usmind.service.SettingsService;'
new_import = 'import com.usmind.service.OrgService;\nimport com.usmind.service.SettingsService;\nimport com.usmind.ws.MindMapWsHandler;\nimport org.springframework.context.annotation.Lazy;'
assert s.count(old_import) == 1, 'imp=' + str(s.count(old_import))
s = s.replace(old_import, new_import)

old_field = '''    private final SettingsService settingsService;

    public OrgServiceImpl(OrgMapper orgMapper, OrgMemberMapper orgMemberMapper, OrgCollabMapper orgCollabMapper,
                          com.usmind.mapper.SysUserMapper sysUserMapper, com.usmind.mapper.MindMapMapper mindMapMapper,
                          SettingsService settingsService) {
        this.orgMapper = orgMapper;
        this.orgMemberMapper = orgMemberMapper;
        this.orgCollabMapper = orgCollabMapper;
        this.sysUserMapper = sysUserMapper;
        this.mindMapMapper = mindMapMapper;
        this.settingsService = settingsService;
    }'''
new_field = '''    private final SettingsService settingsService;
    private final MindMapWsHandler wsHandler;

    public OrgServiceImpl(OrgMapper orgMapper, OrgMemberMapper orgMemberMapper, OrgCollabMapper orgCollabMapper,
                          com.usmind.mapper.SysUserMapper sysUserMapper, com.usmind.mapper.MindMapMapper mindMapMapper,
                          SettingsService settingsService, @Lazy MindMapWsHandler wsHandler) {
        this.orgMapper = orgMapper;
        this.orgMemberMapper = orgMemberMapper;
        this.orgCollabMapper = orgCollabMapper;
        this.sysUserMapper = sysUserMapper;
        this.mindMapMapper = mindMapMapper;
        this.settingsService = settingsService;
        this.wsHandler = wsHandler;
    }'''
assert s.count(old_field) == 1, 'field=' + str(s.count(old_field))
s = s.replace(old_field, new_field)

# 2) disband 收集成员并踢出
old_disband = '''    @Override
    @Transactional
    public void disband(Long orgId) {
        Org o = orgMapper.selectById(orgId);
        if (o == null) return;
        orgMemberMapper.delete(new LambdaQueryWrapper<OrgMember>().eq(OrgMember::getOrgId, orgId));
        orgCollabMapper.delete(new LambdaQueryWrapper<OrgCollab>().eq(OrgCollab::getOrgId, orgId));
        orgMapper.deleteById(orgId);
    }'''
new_disband = '''    @Override
    @Transactional
    public void disband(Long orgId) {
        Org o = orgMapper.selectById(orgId);
        if (o == null) return;
        // 先收集非创建者成员（用于解散后强制踢出在线会话）
        List<Long> memberUids = new ArrayList<>();
        List<OrgMember> members = orgMemberMapper.selectList(new LambdaQueryWrapper<OrgMember>().eq(OrgMember::getOrgId, orgId));
        for (OrgMember m : members) {
            if (!"owner".equals(m.getRole()) && m.getUserId() != null) memberUids.add(m.getUserId());
        }
        orgMemberMapper.delete(new LambdaQueryWrapper<OrgMember>().eq(OrgMember::getOrgId, orgId));
        orgCollabMapper.delete(new LambdaQueryWrapper<OrgCollab>().eq(OrgCollab::getOrgId, orgId));
        String orgName = o.getName();
        orgMapper.deleteById(orgId);
        // 强制踢出在线成员（推送提示后断开连接）
        if (!memberUids.isEmpty()) {
            try {
                wsHandler.kickOrgMembers(orgId, orgName, memberUids);
            } catch (Exception e) {
                // 踢人失败不影响解散结果
            }
        }
    }'''
assert s.count(old_disband) == 1, 'disband=' + str(s.count(old_disband))
s = s.replace(old_disband, new_disband)

io.open(p, 'w', encoding='utf-8', newline='').write(s)
print('ORG SERVICE OK')
