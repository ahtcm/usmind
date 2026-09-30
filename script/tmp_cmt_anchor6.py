# -*- coding: utf-8 -*-
"""applyCommentFlash 分组重构：同锚点合并（呼吸灯/路径一次 + 徽标计数相加），避免折叠后多个未读节点重叠徽标"""
import io
P = r'C:\Users\25750\Desktop\usmind\code\web-user\src\views\Editor.vue'
s = io.open(P, encoding='utf-8').read()

old = '''    Object.keys(unreadNodes).forEach(uid => {
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
      let child = n
      let guard = 0
      while (p && guard < 30) {
        const pg = p.group ? (p.group.node || p.group) : null
        if (pg) {
          const pr = pg.querySelector('rect')
          if (pr) pr.classList.add('cmt-breath-path')
        }
        // 精确连线：child 在 p.children 中的下标 → 对应 p._lines 中那条线
        if (p._lines && p._lines.length && p.children && p.children.length) {
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
      }
      // 3) 右上角未读数徽标（HTML 覆盖层，固定屏幕尺寸，不随画布缩放；动态元素用内联样式避开 scoped CSS）
      if (layer && mapRect) {
        const r = gEl.getBoundingClientRect()
        if (r.width > 1 && r.height > 1) {
          const b = document.createElement('div')
          b.textContent = cnt > 99 ? '99+' : cnt'''
new = '''    // 按锚点分组：同一落点（如收起时多个未读节点落到同一可见祖先）合并呼吸灯/路径/徽标，避免重叠
    const groups = new Map()
    Object.keys(unreadNodes).forEach(uid => {
      const cnt = unreadNodes[uid]
      if (!cnt) return
      const anchor = cmtAnchorOf(uid)
      if (!anchor) return
      const key = anchor.el
      const g0 = groups.get(key)
      if (g0) { g0.cnt += cnt; return }
      groups.set(key, { node: anchor.node, el: key, cnt })
    })
    groups.forEach((g0) => {
      const n = g0.node
      const gEl = g0.el
      const cnt = g0.cnt
      // 1) 落点节点呼吸灯（节点收起时落在最近可见祖先，即"未收起的部分"）
      const rect = gEl.querySelector('rect')
      if (rect) rect.classList.add('cmt-flash')
      // 2) 路径呼吸灯：根 → 落点的所有祖先节点矩形 + 每层"通往落点"的那条连线
      let p = n.parent
      let child = n
      let guard = 0
      while (p && guard < 30) {
        const pg = p.group ? (p.group.node || p.group) : null
        if (pg) {
          const pr = pg.querySelector('rect')
          if (pr) pr.classList.add('cmt-breath-path')
        }
        // 精确连线：child 在 p.children 中的下标 → 对应 p._lines 中那条线
        if (p._lines && p._lines.length && p.children && p.children.length) {
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
      }
      // 3) 右上角未读数徽标（HTML 覆盖层，固定屏幕尺寸，不随画布缩放；动态元素用内联样式避开 scoped CSS）
      if (layer && mapRect) {
        const r = gEl.getBoundingClientRect()
        if (r.width > 1 && r.height > 1) {
          const b = document.createElement('div')
          b.textContent = cnt > 99 ? '99+' : cnt'''
assert s.count(old) == 1, 'group'
s = s.replace(old, new)

# 徽标段尾部：forEach 结束 → groups.forEach 结束
old2 = '''          st.pointerEvents = 'none'
          layer.appendChild(b)
        }
      }
    })
  } catch (e) {}
}'''
new2 = '''          st.pointerEvents = 'none'
          layer.appendChild(b)
        }
      }
    })
  } catch (e) {}
}'''
assert s.count(old2) == 1, 'tail'
# 无需改动尾部（forEach 已替换为 groups.forEach，闭合一致）
io.open(P, 'w', encoding='utf-8', newline='\n').write(s)
print('分组重构 OK')
