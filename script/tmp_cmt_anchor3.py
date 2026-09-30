# -*- coding: utf-8 -*-
"""cmtAnchorOf v3：数据树找父链 uid 路径 + nodeCache 可见性（收起节点从渲染树摘除的兜底）"""
import io
P = r'C:\Users\25750\Desktop\usmind\code\web-user\src\views\Editor.vue'
s = io.open(P, encoding='utf-8').read()

old = '''/** 评论特效落点：目标节点可见则用目标；否则沿父链向上找最近可见节点（收起时展示到未收起的部分）。
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
new = '''/** 评论特效落点：目标节点可见则用目标；否则沿父链向上找最近可见节点（收起时展示到未收起的部分）。
 * 注意：折叠后收起节点会从 nodeCache 与渲染树移除，此时从 getData() 数据树（始终保留全部节点）
 * 找到目标→根的父链 uid 路径，再沿路径向下找第一个在 nodeCache 中可见的祖先作为落点。 */
function cmtAnchorOf(uid) {
  try {
    const cache = mind.value && mind.value.renderer ? (mind.value.renderer.nodeCache || {}) : {}
    const vis = (c) => {
      if (!c || !c.group) return null
      const g = c.group.node || c.group
      try {
        const rc = g.getBoundingClientRect()
        const win = g.ownerDocument && g.ownerDocument.defaultView ? g.ownerDocument.defaultView : null
        const cs = win ? win.getComputedStyle(g) : null
        if (rc.width > 1 && rc.height > 1 && (!cs || (cs.display !== 'none' && cs.visibility !== 'hidden'))) return g
      } catch (e) {}
      return null
    }
    // 1) cache 直接命中（目标可见）
    let t = cache[uid] || null
    if (t) {
      const g = vis(t)
      if (g) return { node: t, el: g }
    }
    // 2) 未命中/不可见：数据树找父链路径（目标→根），再沿路径找最近可见祖先
    try {
      const data = mind.value && mind.value.getData ? mind.value.getData() : null
      const r = data && data.root ? data.root : null
      if (r) {
        const find = (n, path) => {
          if (!n) return null
          const nid = (n.data || {}).uid
          if (nid === uid) return path.concat(nid)
          for (const c of (n.children || [])) { const f = find(c, path.concat(nid)); if (f) return f }
          return null
        }
        const path = find(r, [])
        if (path && path.length) {
          for (let i = path.length - 1; i >= 0; i--) {
            const c = cache[path[i]]
            const g = vis(c)
            if (g) return { node: c, el: g }
          }
        }
      }
    } catch (e) {}
    // 3) 兜底：沿 node.parent 链
    let guard = 0
    while (t && guard < 30) {
      const g = vis(t)
      if (g) return { node: t, el: g }
      t = t.parent
      guard++
    }
  } catch (e) {}
  return null
}'''
assert s.count(old) == 1, 'anchor-v3'
s = s.replace(old, new)
io.open(P, 'w', encoding='utf-8', newline='\n').write(s)
print('cmtAnchorOf v3 OK')
