# -*- coding: utf-8 -*-
"""评论定向提醒：后端 targetUserIds 计算"""
import io, sys

P = r'C:\Users\25750\Desktop\usmind\code\backend\src\main\java\com\usmind\service\impl\NodeCommentServiceImpl.java'
s = io.open(P, encoding='utf-8').read()

# 1) imports
old_imp = """import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.usmind.common.BusinessException;"""
new_imp = """import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.usmind.common.BusinessException;"""
assert s.count(old_imp) == 1, 'import anchor'
s = s.replace(old_imp, new_imp)

old_il = """import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;"""
new_il = """import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;"""
assert s.count(old_il) == 1, 'import list anchor'
s = s.replace(old_il, new_il)

# 2) 字段 + 构造器注入 ObjectMapper
old_f = """    private final OrgService orgService;

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
    }"""
new_f = """    private final OrgService orgService;
    private final ObjectMapper objectMapper = new ObjectMapper();

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
    }"""
assert s.count(old_f) == 1, 'ctor anchor'
s = s.replace(old_f, new_f)

# 3) 类末尾追加 targetUserIds 实现（在最后一个 } 前）
impl = """
    /** 评论提醒目标：特效只发给这些人，其他人仅收到评论信息 */
    @Override
    public List<Long> targetUserIds(NodeComment c) {
        List<Long> targets = new ArrayList<>();
        if (c == null || c.getMindMapId() == null) return targets;
        MindMap m = mindMapMapper.selectById(c.getMindMapId());
        if (m == null) return targets;
        // 1) 回复指定评论 → 仅被回复人（有账号）；被回复人是访客(userId=0) → 退回节点/导图提醒
        if (c.getParentId() != null) {
            NodeComment p = nodeCommentMapper.selectById(c.getParentId());
            if (p != null && p.getUserId() != null && p.getUserId() > 0 && !p.getUserId().equals(c.getUserId())) {
                targets.add(p.getUserId());
                return targets;
            }
        }
        // 2) 组织内节点评论 → 导图所有者 + 节点创建者；分享评论 → 导图所有者
        if ("org".equals(c.getSource())) {
            addTarget(targets, m.getUserId(), c.getUserId());
            addTarget(targets, findNodeCreator(m, c.getNodeId()), c.getUserId());
        } else {
            addTarget(targets, m.getUserId(), c.getUserId());
        }
        return targets;
    }

    private void addTarget(List<Long> list, Long uid, Long selfId) {
        if (uid == null || uid <= 0) return;
        if (selfId != null && uid.equals(selfId)) return;
        if (!list.contains(uid)) list.add(uid);
    }

    /** 从导图 data 递归查找节点 creatorUid（历史节点无 creatorUid 视为导图所有者创建） */
    private long findNodeCreator(MindMap m, String nodeId) {
        if (nodeId == null || nodeId.isBlank() || m.getData() == null) return 0;
        try {
            JsonNode root = objectMapper.readTree(m.getData());
            if (root != null && root.has("root")) root = root.get("root");
            return findCreatorIn(root, nodeId);
        } catch (Exception e) {
            return 0;
        }
    }

    private long findCreatorIn(JsonNode n, String nodeId) {
        if (n == null || !n.isObject()) return 0;
        JsonNode d = n.path("data");
        String uid = d.isObject() ? d.path("uid").asText("") : n.path("uid").asText("");
        if (nodeId.equals(uid)) {
            if (d.isObject() && d.has("creatorUid")) return d.path("creatorUid").asLong(0);
            return n.path("creatorUid").asLong(0);
        }
        JsonNode children = n.path("children");
        if (children.isArray()) {
            for (JsonNode ch : children) {
                long r = findCreatorIn(ch, nodeId);
                if (r != 0) return r;
            }
        }
        return 0;
    }
}
"""
assert s.rstrip().endswith('}'), 'class end anchor'
s = s.rstrip()[:-1].rstrip() + '\n' + impl
io.open(P, 'w', encoding='utf-8', newline='\n').write(s)
print('NodeCommentServiceImpl OK')
