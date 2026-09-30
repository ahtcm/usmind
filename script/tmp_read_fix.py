# -*- coding: utf-8 -*-
"""markNodeRead 口径修正：游标覆盖访客评论 + 其他成员评论（不含自己发的）"""
import io
P = r'C:\Users\25750\Desktop\usmind\code\backend\src\main\java\com\usmind\service\impl\NodeCommentServiceImpl.java'
s = io.open(P, encoding='utf-8').read()

old = '''        // 记录该节点当前最大评论 id 为已读游标
        Long maxId = nodeCommentMapper.selectList(new LambdaQueryWrapper<NodeComment>()
                        .eq(NodeComment::getMindMapId, m.getId())
                        .eq(NodeComment::getNodeId, nodeId.trim())
                        .eq(NodeComment::getIsAuthor, 0))
                .stream().mapToLong(NodeComment::getId).max().orElse(0L);'''
new = '''        // 记录该节点当前最大评论 id 为已读游标（口径与 unread 一致：访客评论 + 其他成员评论，不含自己发的）
        Long maxId = nodeCommentMapper.selectList(new LambdaQueryWrapper<NodeComment>()
                        .eq(NodeComment::getMindMapId, m.getId())
                        .eq(NodeComment::getNodeId, nodeId.trim())
                        .and(w -> w.eq(NodeComment::getIsAuthor, 0)
                                .or(n -> n.eq(NodeComment::getIsAuthor, 1).ne(NodeComment::getUserId, userId))))
                .stream().mapToLong(NodeComment::getId).max().orElse(0L);'''
assert s.count(old) == 1, 'anchor'
s = s.replace(old, new)
io.open(P, 'w', encoding='utf-8', newline='\n').write(s)
print('口径修正 OK')
