# -*- coding: utf-8 -*-
"""cmtAnchorOf 内部详细调试日志"""
import io
P = r'C:\Users\25750\Desktop\usmind\code\web-user\src\views\Editor.vue'
s = io.open(P, encoding='utf-8').read()

old = '''    // 2) 未命中/不可见：数据树找父链路径（目标→根），再沿路径找最近可见祖先
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
    } catch (e) {}'''
new = '''    // 2) 未命中/不可见：数据树找父链路径（目标→根），再沿路径找最近可见祖先
    try {
      const data = mind.value && mind.value.getData ? mind.value.getData() : null
      const r = data && data.root ? data.root : null
      if (uid === '7c9bdffb-5519-47f9-b04a-d0cffc1821ff') console.log('[cmt-dbg2] data?', !!data, 'root?', !!r, 'rootUid', r && r.data ? r.data.uid : null)
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
assert s.count(old) == 1, 'dbg2'
s = s.replace(old, new)
io.open(P, 'w', encoding='utf-8', newline='\n').write(s)
print('dbg2 OK')
