# -*- coding: utf-8 -*-
import io
p = r'C:\Users\25750\Desktop\usmind\code\web-user\src\views\Editor.vue'
s = io.open(p, encoding='utf-8').read()

old = '''      // 3) 右上角未读数徽标（HTML 覆盖层，固定屏幕尺寸，不随画布缩放）
      if (layer && mapRect) {
        const r = gEl.getBoundingClientRect()
        if (r.width > 1 && r.height > 1) {
          const b = document.createElement('div')
          b.className = 'cmt-badge-el'
          b.textContent = cnt > 99 ? '99+' : cnt
          b.style.left = (r.right - mapRect.left) + 'px'
          b.style.top = (r.top - mapRect.top) + 'px'
          layer.appendChild(b)
        }
      }'''
new = '''      // 3) 右上角未读数徽标（HTML 覆盖层，固定屏幕尺寸，不随画布缩放；动态元素用内联样式避开 scoped CSS）
      if (layer && mapRect) {
        const r = gEl.getBoundingClientRect()
        if (r.width > 1 && r.height > 1) {
          const b = document.createElement('div')
          b.textContent = cnt > 99 ? '99+' : cnt
          const st = b.style
          st.position = 'absolute'
          st.left = (r.right - mapRect.left) + 'px'
          st.top = (r.top - mapRect.top) + 'px'
          st.transform = 'translate(-50%, -50%)'
          st.minWidth = '18px'
          st.height = '18px'
          st.lineHeight = '18px'
          st.textAlign = 'center'
          st.fontSize = '11px'
          st.fontWeight = '700'
          st.color = '#fff'
          st.background = 'linear-gradient(135deg, #ff6b6b, #e53935)'
          st.borderRadius = '9px'
          st.padding = '0 5px'
          st.boxShadow = '0 2px 8px rgba(229,57,53,.6)'
          st.pointerEvents = 'none'
          layer.appendChild(b)
        }
      }'''
assert s.count(old) == 1, 'old=' + str(s.count(old))
s = s.replace(old, new)
io.open(p, 'w', encoding='utf-8', newline='').write(s)
print('BADGE INLINE OK')
