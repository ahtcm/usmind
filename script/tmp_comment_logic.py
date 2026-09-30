# -*- coding: utf-8 -*-
import io
p = r'C:\Users\25750\Desktop\usmind\code\web-user\src\views\Editor.vue'
s = io.open(p, encoding='utf-8').read()

# 1) submitAuthorReply -> submitComment（有回复目标走回复，否则组织内发新评论）
old = '''async function submitAuthorReply() {
  const content = (cmtPanel.input || '').trim()
  if (!content) return
  if (content.length > 128) { cmtPanel.cmtErr = '回复最多 128 字'; return }
  const target = cmtPanel.replyTo
  if (!target) { cmtPanel.cmtErr = '请先选择要回复的评论'; return }
  cmtPanel.cmtErr = ''
  try {
    const c = await commentApi.reply(mindMapId.value, target.id, content)
    cmtPanel.list.push(c)
    cmtPanel.input = ''
    cmtPanel.replyTo = null
  } catch (e) { cmtPanel.cmtErr = e.message || '回复失败（可能评论过于频繁）' }
}'''
new = '''async function submitComment() {
  const content = (cmtPanel.input || '').trim()
  if (!content) return
  if (content.length > 128) { cmtPanel.cmtErr = '最多 128 字'; return }
  const target = cmtPanel.replyTo
  cmtPanel.cmtErr = ''
  try {
    let c
    if (target) {
      c = await commentApi.reply(mindMapId.value, target.id, content)
    } else {
      c = await commentApi.create(mindMapId.value, { nodeId: cmtPanel.nodeId, content })
    }
    if (c && c.id) {
      cmtPanel.list.push(c)
      // 组织内评论也计入未读统计（自己发的除外，后端已按 userId 排除）
    }
    cmtPanel.input = ''
    cmtPanel.replyTo = null
  } catch (e) { cmtPanel.cmtErr = e.message || '发送失败（可能评论过于频繁）' }
}'''
assert s.count(old) == 1, 'submit=' + str(s.count(old))
s = s.replace(old, new)

# 2) handleCommentNew：排除自己发的评论（isAuthor=1 且 userId=当前用户）
old = '''function handleCommentNew(c) {
  if (!c || !c.id) return
  if (c.id > cmtAfterId) cmtAfterId = c.id
  // 作者自己的回复（isAuthor=1）不触发闪烁；访客新评论 → 节点未读+1
  if (Number(c.isAuthor) === 1) return
  unreadNodes[c.nodeId] = (unreadNodes[c.nodeId] || 0) + 1
  syncCommentFlash()
  if (cmtPanel.open && c.nodeId === cmtPanel.nodeId) refreshCmtList()
}'''
new = '''function handleCommentNew(c) {
  if (!c || !c.id) return
  if (c.id > cmtAfterId) cmtAfterId = c.id
  // 排除自己发的评论（自己回复/自己组织评论不触发自己的未读）；访客/他人新评论 → 未读+1
  if (Number(c.isAuthor) === 1) {
    try {
      const me = JSON.parse(localStorage.getItem('usmind.user') || '{}')
      if (me && c.userId != null && Number(c.userId) === Number(me.id)) return
    } catch (e) { return }
  }
  unreadNodes[c.nodeId] = (unreadNodes[c.nodeId] || 0) + 1
  syncCommentFlash()
  if (cmtPanel.open && c.nodeId === cmtPanel.nodeId) refreshCmtList()
}'''
assert s.count(old) == 1, 'handle=' + str(s.count(old))
s = s.replace(old, new)

# 3) applyCommentFlash：清空时同时移除流动线；为每条呼吸灯连线 clone 流动电子线
old = '''    // 清空旧的动画类
    document.querySelectorAll('.cmt-flash, .cmt-breath-path, .cmt-breath-line').forEach(el => {
      el.classList.remove('cmt-flash', 'cmt-breath-path', 'cmt-breath-line')
    })'''
new = '''    // 清空旧的动画类与流动电子线（clone 出来的纯视觉元素）
    document.querySelectorAll('.cmt-flash, .cmt-breath-path, .cmt-breath-line, .cmt-flow-line').forEach(el => {
      if (el.classList.contains('cmt-flow-line')) { try { el.remove() } catch (e) {} return }
      el.classList.remove('cmt-flash', 'cmt-breath-path', 'cmt-breath-line')
    })'''
assert s.count(old) == 1, 'clean=' + str(s.count(old))
s = s.replace(old, new)

old = '''        if (p._lines && p._lines.length && p.children && p.children.length) {
          const idx = p.children.indexOf(child)
          if (idx >= 0 && idx < p._lines.length) {
            const l = p._lines[idx]
            try {
              const el = l && l.node ? l.node : l
              if (el && el.classList) el.classList.add('cmt-breath-line')
            } catch (e) {}
          }
        }
        child = p
        p = p.parent
        guard++
      }'''
new = '''        if (p._lines && p._lines.length && p.children && p.children.length) {
          const idx = p.children.indexOf(child)
          if (idx >= 0 && idx < p._lines.length) {
            const l = p._lines[idx]
            try {
              const el = l && l.node ? l.node : l
              if (el && el.classList) el.classList.add('cmt-breath-line')
              // 流动电子线：clone 原连线（同组，自动跟随画布缩放/平移），加流动动画类
              try {
                if (el && el.tagName === 'path' && el.parentNode) {
                  const flow = el.cloneNode(false)
                  flow.removeAttribute('class')
                  flow.removeAttribute('style')
                  flow.classList.add('cmt-flow-line')
                  el.parentNode.appendChild(flow)
                }
              } catch (e) {}
            } catch (e) {}
          }
        }
        child = p
        p = p.parent
        guard++
      }'''
assert s.count(old) == 1, 'flow=' + str(s.count(old))
s = s.replace(old, new)

io.open(p, 'w', encoding='utf-8', newline='').write(s)
print('LOGIC OK')
