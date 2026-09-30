# -*- coding: utf-8 -*-
"""两个 Controller broadcast 加 targetUserIds"""
import io

files = [
    (r'C:\Users\25750\Desktop\usmind\code\backend\src\main\java\com\usmind\controller\ShareCommentController.java',
     '        m.put("createdAt", c.getCreatedAt() == null ? "" : c.getCreatedAt().toString());\n        commentWsHandler.broadcastComment(c.getMindMapId(), m);'),
    (r'C:\Users\25750\Desktop\usmind\code\backend\src\main\java\com\usmind\controller\MindMapCommentController.java',
     '        m.put("createdAt", c.getCreatedAt() == null ? "" : c.getCreatedAt().toString());\n        commentWsHandler.broadcastComment(c.getMindMapId(), m);'),
]
add = '        m.put("targetUserIds", nodeCommentService.targetUserIds(c));\n        commentWsHandler.broadcastComment(c.getMindMapId(), m);'
for p, old in files:
    s = io.open(p, encoding='utf-8').read()
    assert s.count(old) == 1, p
    s = s.replace(old, add)
    io.open(p, 'w', encoding='utf-8', newline='\n').write(s)
    print(p.split('\\')[-1], 'OK')
