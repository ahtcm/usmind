# -*- coding: utf-8 -*-
"""评论特效落点：目标节点收起/不可见时落到最近可见祖先（展示未收起的部分）"""
import io
P = r'C:\Users\25750\Desktop\usmind\code\web-user\src\views\Editor.vue'
s = io.open(P, encoding='utf-8').read()

# 1) 新增 cmtAnchorOf 函数（放在 applyCommentFlash 定义前）
old_fn = '''function applyCommentFlash() {
  if (!mind.value || !mind.value.renderer) return'''
new_fn = '''/** 评论特效落点：目标节点可见则用目标；否则沿父链向上找最近可见节点（收起时展示到未收起的部分） */
function cmtAnchorOf(uid) {
  try {
    const cache = mind.value && mind.value.renderer ? (mind.value.renderer.nodeCache || {}) : {}
    let t = cache[uid]
    let guard = 0
    while (t && guard < 30) {
      const g = t.group ? (t.group.node || t.group) : null
      if (g) {
        const rc = g.getBoundingClientRect()
        const win = g.ownerDocument && g.ownerDocument.defaultView ? g.ownerDocument.defaultView : null
        const cs = win ? win.getComputedStyle(g) : null
        if (rc.width > 1 && rc.height > 1 && (!cs || (cs.display !== 'none' && cs.visibility !== 'hidden'))) {
          return { node: t, el: g }
        }
      }
      t = t.parent
      guard++
    }
  } catch (e) {}
  return null
}
function applyCommentFlash() {
  if (!mind.value || !mind.value.renderer) return'''
assert s.count(old_fn) == 1, 'fn-anchor'
s = s.replace(old_fn, new_fn)

# 2) applyCommentFlash 主体：目标 → 锚点
old_a = '''    Object.keys(unreadNodes).forEach(uid => {
      const cnt = unreadNodes[uid]
      if (!cnt) return
      const n = nodeCache[uid]
      const gEl = n && n.group ? (n.group.node || n.group) : null
      if (!gEl) return
      // 1) 目标节点呼吸灯
      const rect = gEl.querySelector('rect')
      if (rect) rect.classList.add('cmt-flash')
      // 2) 路径呼吸灯：根 → 目标的所有祖先节点矩形 + 每层"通往目标"的那条连线
      let p = n.parent
      let child = n'''
new_a = '''    Object.keys(unreadNodes).forEach(uid => {
      const cnt = unreadNodes[uid]
      if (!cnt) return
      const anchor = cmtAnchorOf(uid)
      if (!anchor) return
      const n = anchor.node
      const gEl = anchor.el
      // 1) 落点节点呼吸灯（节点收起时落在最近可见祖先，即"未收起的部分"）
      const rect = gEl.querySelector('rect')
      if (rect) rect.classList.add('cmt-flash')
      // 2) 路径呼吸灯：根 → 落点的所有祖先节点矩形 + 每层"通往落点"的那条连线
      let p = n.parent
      let child = n'''
assert s.count(old_a) == 1, 'apply-a'
s = s.replace(old_a, new_a)

# 3) repositionCommentBadges：目标 → 锚点
old_r = '''    const uids = Object.keys(unreadNodes).filter(uid => unreadNodes[uid])
    const badges = layer.children
    for (let i = 0; i < badges.length && i < uids.length; i++) {
      const uid = uids[i]
      if (!uid) continue
      const n = nodeCache[uid]
      const gEl = n && n.group ? (n.group.node || n.group) : null
      if (!gEl) continue
      const r = gEl.getBoundingClientRect()'''
new_r = '''    const uids = Object.keys(unreadNodes).filter(uid => unreadNodes[uid])
    const badges = layer.children
    for (let i = 0; i < badges.length && i < uids.length; i++) {
      const uid = uids[i]
      if (!uid) continue
      const anchor = cmtAnchorOf(uid)
      if (!anchor) continue
      const gEl = anchor.el
      const r = gEl.getBoundingClientRect()'''
assert s.count(old_r) == 1, 'reposition-r'
s = s.replace(old_r, new_r)

io.open(P, 'w', encoding='utf-8', newline='\n').write(s)
print('评论特效收起落点 OK')
