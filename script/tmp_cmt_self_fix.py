# -*- coding: utf-8 -*-
"""修复自回复也收到特效：1) 广播补 userId 字段；2) 空 targets 视为无人提醒（不闪）"""
import io

# 1) 后端两个 controller broadcast 加 userId
for p in [
    r'C:\Users\25750\Desktop\usmind\code\backend\src\main\java\com\usmind\controller\ShareCommentController.java',
    r'C:\Users\25750\Desktop\usmind\code\backend\src\main\java\com\usmind\controller\MindMapCommentController.java',
]:
    s = io.open(p, encoding='utf-8').read()
    old = '        m.put("isAuthor", c.getIsAuthor());'
    new = '        m.put("isAuthor", c.getIsAuthor());\n        m.put("userId", c.getUserId());'
    assert s.count(old) == 1, p
    s = s.replace(old, new)
    io.open(p, 'w', encoding='utf-8', newline='\n').write(s)
    print(p.split('\\')[-1], 'OK')

# 2) 前端 handleCommentNew：空数组 = 无人提醒（不闪），仅缺失时回退全闪
P = r'C:\Users\25750\Desktop\usmind\code\web-user\src\views\Editor.vue'
s = io.open(P, encoding='utf-8').read()
old_f = """  // 定向提醒：后端已算好 targetUserIds，特效（电流+音效+角标）只发给提醒目标，其他人仅收到信息
  const targets = c.targetUserIds
  if (Array.isArray(targets) && targets.length > 0) {
    const isTarget = me.id != null && targets.some(t => Number(t) === Number(me.id))
    if (!isTarget) {
      if (cmtPanel.open && c.nodeId === cmtPanel.nodeId) refreshCmtList()
      return
    }
  }"""
new_f = """  // 定向提醒：后端已算好 targetUserIds（数组存在即权威——空数组=无人需提醒，不闪；仅缺失=旧广播，回退全闪）
  const targets = c.targetUserIds
  if (Array.isArray(targets)) {
    const isTarget = me.id != null && targets.some(t => Number(t) === Number(me.id))
    if (!isTarget) {
      if (cmtPanel.open && c.nodeId === cmtPanel.nodeId) refreshCmtList()
      return
    }
  }"""
assert s.count(old_f) == 1, 'front anchor'
s = s.replace(old_f, new_f)
io.open(P, 'w', encoding='utf-8', newline='\n').write(s)
print('Editor.vue OK')
