# -*- coding: utf-8 -*-
import io
p = r'C:\Users\25750\Desktop\usmind\code\backend\src\main\java\com\usmind\service\impl\NodeCommentServiceImpl.java'
s = io.open(p, encoding='utf-8').read()

# 注入 OrgService
old = '''import com.usmind.service.IpGuardService;
import com.usmind.service.NodeCommentService;
import com.usmind.service.SettingsService;'''
new = '''import com.usmind.service.IpGuardService;
import com.usmind.service.NodeCommentService;
import com.usmind.service.OrgService;
import com.usmind.service.SettingsService;'''
assert s.count(old) == 1, 'imp=' + str(s.count(old))
s = s.replace(old, new)

old = '''    private final SettingsService settingsService;
    private final IpGuardService ipGuardService;

    public NodeCommentServiceImpl(NodeCommentMapper nodeCommentMapper, MindMapMapper mindMapMapper,
                                  SysUserMapper sysUserMapper, StringRedisTemplate redisTemplate,
                                  SettingsService settingsService, IpGuardService ipGuardService) {
        this.nodeCommentMapper = nodeCommentMapper;
        this.mindMapMapper = mindMapMapper;
        this.sysUserMapper = sysUserMapper;
        this.redisTemplate = redisTemplate;
        this.settingsService = settingsService;
        this.ipGuardService = ipGuardService;
    }'''
new = '''    private final SettingsService settingsService;
    private final IpGuardService ipGuardService;
    private final OrgService orgService;

    public NodeCommentServiceImpl(NodeCommentMapper nodeCommentMapper, MindMapMapper mindMapMapper,
                                  SysUserMapper sysUserMapper, StringRedisTemplate redisTemplate,
                                  SettingsService settingsService, IpGuardService ipGuardService,
                                  OrgService orgService) {
        this.nodeCommentMapper = nodeCommentMapper;
        this.mindMapMapper = mindMapMapper;
        this.sysUserMapper = sysUserMapper;
        this.redisTemplate = redisTemplate;
        this.settingsService = settingsService;
        this.ipGuardService = ipGuardService;
        this.orgService = orgService;
    }'''
assert s.count(old) == 1, 'ctor=' + str(s.count(old))
s = s.replace(old, new)

# requireOwned：导图创建者 或 协作组织成员
old = '''    private MindMap requireOwned(long userId, Long mindMapId) {
        if (mindMapId == null) throw new BusinessException(400, "导图标识缺失");
        MindMap m = mindMapMapper.selectById(mindMapId);
        if (m == null) throw new BusinessException(404, "导图不存在");
        if (!m.getUserId().equals(userId)) throw new BusinessException(403, "无权访问该导图");
        return m;
    }'''
new = '''    private MindMap requireOwned(long userId, Long mindMapId) {
        if (mindMapId == null) throw new BusinessException(400, "导图标识缺失");
        MindMap m = mindMapMapper.selectById(mindMapId);
        if (m == null) throw new BusinessException(404, "导图不存在");
        // 导图创建者 或 协作组织成员 可访问评论（协同编辑模式下成员也能看/回复评论、接收未读提示）
        if (!m.getUserId().equals(userId) && !orgService.canAccessMindMap(userId, mindMapId)) {
            throw new BusinessException(403, "无权访问该导图");
        }
        return m;
    }'''
assert s.count(old) == 1, 'owned=' + str(s.count(old))
s = s.replace(old, new)

io.open(p, 'w', encoding='utf-8', newline='').write(s)
print('COMMENT ACCESS OK')
