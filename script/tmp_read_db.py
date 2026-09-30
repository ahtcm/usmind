# -*- coding: utf-8 -*-
"""已读游标 Redis → MySQL 持久化（node_comment_read 表）"""
import io
P = r'C:\Users\25750\Desktop\usmind\code\backend\src\main\java\com\usmind\service\impl\NodeCommentServiceImpl.java'
s = io.open(P, encoding='utf-8').read()

# 1) import
old_imp = '''import com.usmind.entity.NodeComment;
import com.usmind.entity.SysUser;'''
new_imp = '''import com.usmind.entity.NodeComment;
import com.usmind.entity.NodeCommentRead;
import com.usmind.entity.SysUser;'''
assert s.count(old_imp) == 1, 'imp'
s = s.replace(old_imp, new_imp)

old_imp2 = '''import com.usmind.mapper.NodeCommentMapper;
import com.usmind.mapper.SysUserMapper;'''
new_imp2 = '''import com.usmind.mapper.NodeCommentMapper;
import com.usmind.mapper.NodeCommentReadMapper;
import com.usmind.mapper.SysUserMapper;'''
assert s.count(old_imp2) == 1, 'imp2'
s = s.replace(old_imp2, new_imp2)

# 2) 字段 + 构造器
old_f = '''    private final NodeCommentMapper nodeCommentMapper;
    private final MindMapMapper mindMapMapper;'''
new_f = '''    private final NodeCommentMapper nodeCommentMapper;
    private final NodeCommentReadMapper nodeCommentReadMapper;
    private final MindMapMapper mindMapMapper;'''
assert s.count(old_f) == 1, 'field'
s = s.replace(old_f, new_f)

old_c = '''    public NodeCommentServiceImpl(NodeCommentMapper nodeCommentMapper, MindMapMapper mindMapMapper,
                                  SysUserMapper sysUserMapper, StringRedisTemplate redisTemplate,
                                  SettingsService settingsService, IpGuardService ipGuardService,
                                  OrgService orgService) {
        this.nodeCommentMapper = nodeCommentMapper;
        this.mindMapMapper = mindMapMapper;'''
new_c = '''    public NodeCommentServiceImpl(NodeCommentMapper nodeCommentMapper, NodeCommentReadMapper nodeCommentReadMapper,
                                  MindMapMapper mindMapMapper, SysUserMapper sysUserMapper,
                                  StringRedisTemplate redisTemplate,
                                  SettingsService settingsService, IpGuardService ipGuardService,
                                  OrgService orgService) {
        this.nodeCommentMapper = nodeCommentMapper;
        this.nodeCommentReadMapper = nodeCommentReadMapper;
        this.mindMapMapper = mindMapMapper;'''
assert s.count(old_c) == 1, 'ctor'
s = s.replace(old_c, new_c)

# 3) markNodeRead → DB upsert
old_m = '''        if (maxId <= 0) return;
        redisTemplate.opsForHash().put("cmt:read:" + userId + ":" + m.getId(), nodeId.trim(), String.valueOf(maxId));
    }'''
new_m = '''        if (maxId <= 0) return;
        // 持久化到 MySQL（刷新/重启不丢失已读状态）；存在则只推进游标
        NodeCommentRead rec = nodeCommentReadMapper.selectOne(new LambdaQueryWrapper<NodeCommentRead>()
                .eq(NodeCommentRead::getUserId, userId)
                .eq(NodeCommentRead::getMindMapId, m.getId())
                .eq(NodeCommentRead::getNodeId, nodeId.trim()));
        if (rec == null) {
            NodeCommentRead nr = new NodeCommentRead();
            nr.setUserId(userId);
            nr.setMindMapId(m.getId());
            nr.setNodeId(nodeId.trim());
            nr.setLastReadId(maxId);
            nodeCommentReadMapper.insert(nr);
        } else if (maxId > (rec.getLastReadId() == null ? 0L : rec.getLastReadId())) {
            rec.setLastReadId(maxId);
            nodeCommentReadMapper.update(rec, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<NodeCommentRead>()
                    .eq(NodeCommentRead::getUserId, userId)
                    .eq(NodeCommentRead::getMindMapId, m.getId())
                    .eq(NodeCommentRead::getNodeId, nodeId.trim()));
        }
    }'''
assert s.count(old_m) == 1, 'mark'
s = s.replace(old_m, new_m)

# 4) unreadByNode → DB 读游标
old_u = '''        Map<Object, Object> read = redisTemplate.opsForHash().entries("cmt:read:" + userId + ":" + m.getId());
        Map<String, Long> lastRead = new HashMap<>();
        read.forEach((k, v) -> {
            try { lastRead.put(String.valueOf(k), Long.parseLong(String.valueOf(v))); } catch (Exception ignored) {}
        });'''
new_u = '''        List<NodeCommentRead> reads = nodeCommentReadMapper.selectList(new LambdaQueryWrapper<NodeCommentRead>()
                .eq(NodeCommentRead::getUserId, userId)
                .eq(NodeCommentRead::getMindMapId, m.getId()));
        Map<String, Long> lastRead = new HashMap<>();
        for (NodeCommentRead r : reads) {
            lastRead.put(r.getNodeId(), r.getLastReadId() == null ? 0L : r.getLastReadId());
        }'''
assert s.count(old_u) == 1, 'unread'
s = s.replace(old_u, new_u)

io.open(P, 'w', encoding='utf-8', newline='\n').write(s)
print('已读游标DB化 OK')
