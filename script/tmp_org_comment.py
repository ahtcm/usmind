# -*- coding: utf-8 -*-
import io
p = r'C:\Users\25750\Desktop\usmind\code\backend\src\main\java\com\usmind\service\impl\NodeCommentServiceImpl.java'
s = io.open(p, encoding='utf-8').read()

# 1) build() 增加 source/userId
old = '''    private NodeComment build(Long mindMapId, String nodeId, Long parentId, String nick,
                              String content, int isAuthor, String ip) {
        NodeComment c = new NodeComment();
        c.setMindMapId(mindMapId);
        c.setNodeId(nodeId);
        c.setParentId(parentId);
        c.setUserName(nick);
        c.setContent(content);
        c.setIsAuthor(isAuthor);
        c.setIp(ip == null ? "" : ip);
        c.setCreatedAt(LocalDateTime.now());
        return c;
    }'''
new = '''    private NodeComment build(Long mindMapId, String nodeId, Long parentId, String nick,
                              String content, int isAuthor, long userId, String source, String ip) {
        NodeComment c = new NodeComment();
        c.setMindMapId(mindMapId);
        c.setNodeId(nodeId);
        c.setParentId(parentId);
        c.setUserName(nick);
        c.setContent(content);
        c.setIsAuthor(isAuthor);
        c.setUserId(userId);
        c.setSource(source);
        c.setIp(ip == null ? "" : ip);
        c.setCreatedAt(LocalDateTime.now());
        return c;
    }'''
assert s.count(old) == 1, 'build=' + str(s.count(old))
s = s.replace(old, new)

# 2) addShareComment：source=share, userId=0
old = '''        NodeComment c = build(m.getId(), nodeId.trim(), parentId, nick.trim(), content.trim(), 0, ip);
        nodeCommentMapper.insert(c);
        return c;'''
new = '''        NodeComment c = build(m.getId(), nodeId.trim(), parentId, nick.trim(), content.trim(), 0, 0L, "share", ip);
        nodeCommentMapper.insert(c);
        return c;'''
assert s.count(old) == 1, 'share=' + str(s.count(old))
s = s.replace(old, new)

# 3) authorReply：userId=登录用户，source 继承目标评论
old = '''        NodeComment c = build(m.getId(), target.getNodeId(), commentId, nick, content.trim(), 1, ip);
        nodeCommentMapper.insert(c);
        return c;'''
new = '''        String src = target.getSource() == null ? "share" : target.getSource();
        NodeComment c = build(m.getId(), target.getNodeId(), commentId, nick, content.trim(), 1, userId, src, ip);
        nodeCommentMapper.insert(c);
        return c;'''
assert s.count(old) == 1, 'reply=' + str(s.count(old))
s = s.replace(old, new)

# 4) 新增 addOrgComment（插在 markNodeRead 前）
old = '''    @Override
    public void markNodeRead(long userId, Long mindMapId, String nodeId) {'''
new = '''    @Override
    public NodeComment addOrgComment(long userId, Long mindMapId, String nodeId,
                                     Long parentId, String content, String ip) {
        MindMap m = requireOwned(userId, mindMapId);
        rateLimit(ip);
        if (!StringUtils.hasText(nodeId)) throw new BusinessException(400, "节点标识缺失");
        if (!StringUtils.hasText(content)) throw new BusinessException(400, "评论内容不能为空");
        if (content.trim().length() > MAX_CONTENT) throw new BusinessException(400, "评论最多 " + MAX_CONTENT + " 字");
        checkParent(m, parentId);
        SysUser u = sysUserMapper.selectById(userId);
        String nick = u != null && StringUtils.hasText(u.getNickname()) ? u.getNickname()
                : (u != null && StringUtils.hasText(u.getUsername()) ? u.getUsername() : "成员");
        NodeComment c = build(m.getId(), nodeId.trim(), parentId, nick, content.trim(), 1, userId, "org", ip);
        nodeCommentMapper.insert(c);
        return c;
    }

    @Override
    public void markNodeRead(long userId, Long mindMapId, String nodeId) {'''
assert s.count(old) == 1, 'org=' + str(s.count(old))
s = s.replace(old, new)

# 5) unreadByNode：只排除自己发的评论（isAuthor=1 && userId==me）；分享评论 userId=0 不受影响
old = '''        // 作者自己的回复不算未读提醒（只统计访客产生的评论）
        all.removeIf(c -> c.getIsAuthor() != null && c.getIsAuthor() == 1);'''
new = '''        // 排除自己发的评论（自己回复/自己组织评论不算自己的未读）；分享访客评论 userId=0 不受影响
        all.removeIf(c -> (c.getIsAuthor() != null && c.getIsAuthor() == 1)
                && c.getUserId() != null && c.getUserId() == userId);'''
assert s.count(old) == 1, 'unread=' + str(s.count(old))
s = s.replace(old, new)

io.open(p, 'w', encoding='utf-8', newline='').write(s)
print('SERVICE PATCH OK')
