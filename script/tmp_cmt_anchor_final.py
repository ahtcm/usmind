# -*- coding: utf-8 -*-
"""cmtAnchorOf 最终修正：getData 返回 {data:根节点, children:顶层}，递归入口修正 + 清除调试日志"""
import io
P = r'C:\Users\25750\Desktop\usmind\code\web-user\src\views\Editor.vue'
s = io.open(P, encoding='utf-8').read()

old = '''    // 2) 未命中/不可见：数据树找父链路径（目标→根），再沿路径找最近可见祖先
    try {
      const data = mind.value && mind.value.getData ? mind.value.getData() : null
      const r = data && data.root ? data.root : null
      if (uid === '7c9bdffb-5519-47f9-b04a-d0cffc1821ff') {
        console.log('[cmt-dbg4] topKeys', data ? Object.keys(data) : null)
        console.log('[cmt-dbg4] dataKeys', data && data.data ? Object.keys(data.data) : null)
        if (data && data.data) { const d = data.data; console.log('[cmt-dbg4] rootUid', d.uid, 'rootText', d.text, 'childCount', d.children ? d.children.length : -1) }
        if (data && data.children) console.log('[cmt-dbg5] topChildrenLen', data.children.length, 'first', data.children[0] && data.children[0].data ? data.children[0].data.text : null)
        if (data && data.root) console.log('[cmt-dbg4] hasRootField', typeof data.root, data.root && data.root.data ? data.root.data.text : null)
      }
      if (r) {
        const find = (n, path) => {
          if (!n) return null
          const nid = (n.data || {}).uid
          if (nid === uid) return path.concat(nid)
          for (const c of (n.children || [])) { const f = find(c, path.concat(nid)); if (f) return f }
          return null
        }
        const path = find(r, [])
        if (uid === '7c9bdffb-5519-47f9-b04a-d0cffc1821ff') console.log('[cmt-dbg2] pathLen', path ? path.length : -1)
        if (path && path.length) {
          for (let i = path.length - 1; i >= 0; i--) {
            const c = cache[path[i]]
            if (uid === '7c9bdffb-5519-47f9-b04a-d0cffc1821ff') console.log('[cmt-dbg2] try uid', String(path[i]).slice(0, 18), 'cacheHit', !!c)
            const g = vis(c)
            if (g) return { node: c, el: g }
          }
        }
      }
    } catch (e) { if (uid === '7c9bdffb-5519-47f9-b04a-d0cffc1821ff') console.log('[cmt-dbg2] ERR', String(e)) }'''
new = '''    // 2) 未命中/不可见：数据树找父链路径（目标→根），再沿路径找最近可见祖先。
    // 注意：mind.getData() 返回 { data: 根节点, children: 根的直接子节点, theme, layout... }，
    // 根节点的 children 在顶层，其余节点 children 在各自节点对象内。
    try {
      const data = mind.value && mind.value.getData ? mind.value.getData() : null
      const rootNode = data && data.data ? data.data : null
      if (rootNode) {
        if (!rootNode.children && data.children) rootNode.children = data.children
        const find = (n, path) => {
          if (!n) return null
          const nid = (n.data || {}).uid
          if (nid === uid) return path.concat(nid)
          for (const c of (n.children || [])) { const f = find(c, path.concat(nid)); if (f) return f }
          return null
        }
        const path = find(rootNode, [])
        if (path && path.length) {
          for (let i = path.length - 1; i >= 0; i--) {
            const c = cache[path[i]]
            const g = vis(c)
            if (g) return { node: c, el: g }
          }
        }
      }
    } catch (e) {}'''
assert s.count(old) == 1, 'final-anchor'
s = s.replace(old, new)

# 移除 applyCommentFlash 调试日志
old2 = '''      const anchor = cmtAnchorOf(uid)
      console.log('[cmt-dbg]', uid, 'cnt', cnt, 'anchorText', anchor && anchor.node && anchor.node.nodeData ? anchor.node.nodeData.text : (anchor ? 'ANCHOR' : 'NULL'))
      if (!anchor) return'''
new2 = '''      const anchor = cmtAnchorOf(uid)
      if (!anchor) return'''
assert s.count(old2) == 1, 'final-dbg'
s = s.replace(old2, new2)

io.open(P, 'w', encoding='utf-8', newline='\n').write(s)
print('cmtAnchorOf 最终修正 OK')
