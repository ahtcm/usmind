# -*- coding: utf-8 -*-
import io
p = r'C:\Users\25750\Desktop\usmind\code\web-user\src\views\Editor.vue'
s = io.open(p, encoding='utf-8').read()

old = '''      // 2) 路径呼吸灯：根 → 目标的所有祖先节点矩形 + 连线
      let p = n.parent
      let guard = 0
      while (p && guard < 30) {
        const pg = p.group ? (p.group.node || p.group) : null
        if (pg) {
          const pr = pg.querySelector('rect')
          if (pr) pr.classList.add('cmt-breath-path')
        }
        if (p._lines && p._lines.length) {
          p._lines.forEach(l => {
            try {
              const el = l && l.node ? l.node : l
              if (el && el.classList) el.classList.add('cmt-breath-line')
            } catch (e) {}
          })
        }
        p = p.parent
        guard++
      }'''
new = '''      // 2) 路径呼吸灯：根 → 目标的所有祖先节点矩形 + 每层"通往目标"的那条连线
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
            } catch (e) {}
          }
        }
        child = p
        p = p.parent
        guard++
      }'''
assert s.count(old) == 1, 'old=' + str(s.count(old))
s = s.replace(old, new)
io.open(p, 'w', encoding='utf-8', newline='').write(s)
print('PATH LINE OK')
