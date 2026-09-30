# -*- coding: utf-8 -*-
"""cmtAnchorOf 修复：nodeCache 移除的收起节点改从 renderer.root 数据树查找，再沿父链找可见祖先"""
import io
P = r'C:\Users\25750\Desktop\usmind\code\web-user\src\views\Editor.vue'
s = io.open(P, encoding='utf-8').read()

old = '''/** 评论特效落点：目标节点可见则用目标；否则沿父链向上找最近可见节点（收起时展示到未收起的部分） */
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
}'''
new = '''/** 评论特效落点：目标节点可见则用目标；否则沿父链向上找最近可见节点（收起时展示到未收起的部分）。
 * 注意：折叠后收起节点会从 nodeCache 移除，须先从 renderer.root 数据树按 uid 找回节点（数据树始终保留），
 * 其 group 可能为 null，再沿 parent 链找第一个有可见 group 的祖先作为特效落点。 */
function cmtAnchorOf(uid) {
  try {
    const r = mind.value && mind.value.renderer ? mind.value.renderer.root : null
    let t = null
    if (r) {
      const find = (n) => {
        if (!n) return null
        if ((n.nodeData || {}).uid === uid) return n
        for (const c of (n.children || [])) { const f = find(c); if (f) return f }
        return null
      }
      t = find(r)
    }
    if (!t && mind.value && mind.value.renderer) {
      const cache = mind.value.renderer.nodeCache || {}
      t = cache[uid]
    }
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
}'''
assert s.count(old) == 1, 'anchor-fn'
s = s.replace(old, new)
io.open(P, 'w', encoding='utf-8', newline='\n').write(s)
print('cmtAnchorOf 数据树查找 OK')
