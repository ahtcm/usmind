# -*- coding: utf-8 -*-
import io

# ===== Editor.vue：showCtxMenu 自适应展开方向 =====
p = r'C:\Users\25750\Desktop\usmind\code\web-user\src\views\Editor.vue'
s = io.open(p, encoding='utf-8').read()

old = '''// 右键菜单
function showCtxMenu(e, node) {
  ctxMode.value = 'node'
  ctxNode = node
  ctxNoAnim.value = false
  ctxClosing.value = false
  ctxHidden.value = false
  const x = e.clientX != null ? e.clientX : e.pageX
  const y = e.clientY != null ? e.clientY : e.pageY
  ctxStyle.value = { left: Math.min(x, window.innerWidth - 200) + 'px', top: Math.min(y, window.innerHeight - 400) + 'px' }
}'''
new = '''// 右键菜单：按鼠标位置自适应展开方向（靠近上方向下展开、靠近下方向上展开，右侧不足向左）
function showCtxMenu(e, node) {
  ctxMode.value = 'node'
  ctxNode = node
  ctxNoAnim.value = false
  ctxClosing.value = false
  ctxHidden.value = false
  const x = e.clientX != null ? e.clientX : e.pageX
  const y = e.clientY != null ? e.clientY : e.pageY
  ctxStyle.value = { left: x + 'px', top: y + 'px' }
  nextTick(() => {
    const menu = document.getElementById('ctx-menu')
    if (!menu) return
    const mw = menu.offsetWidth || 200
    const mh = menu.offsetHeight || 320
    const gap = 8
    let left = x
    let top = y
    // 右侧空间不足 → 向左展开；左侧仍不足 → 贴左边界
    if (left + mw > window.innerWidth - gap) left = Math.max(gap, window.innerWidth - mw - gap)
    // 下方空间不足 → 向上展开（菜单底部在鼠标位置）；上方仍不足 → 贴顶
    if (top + mh > window.innerHeight - gap) top = Math.max(gap, y - mh)
    ctxStyle.value = { left: left + 'px', top: top + 'px' }
  })
}'''
assert s.count(old) == 1, 'show=' + str(s.count(old))
s = s.replace(old, new)
io.open(p, 'w', encoding='utf-8', newline='').write(s)
print('EDITOR OK')

# ===== style.css：#ctx-menu 可滚动 + 过渡动画 =====
p2 = r'C:\Users\25750\Desktop\usmind\code\web-user\src\assets\style.css'
s2 = io.open(p2, encoding='utf-8').read()
old2 = '''#ctx-menu {
  position: fixed;
  z-index: 3000;
  min-width: 168px;
  padding: 6px;
  background: rgba(28, 31, 38, .92);
  backdrop-filter: blur(14px);
  -webkit-backdrop-filter: blur(14px);
  border: 1px solid rgba(255,255,255,.10);
  border-radius: 12px;
  box-shadow: 0 12px 32px rgba(0,0,0,.45), 0 2px 8px rgba(0,0,0,.3);
  animation: ctxIn .12s ease-out;
}'''
new2 = '''#ctx-menu {
  position: fixed;
  z-index: 3000;
  min-width: 168px;
  max-width: min(300px, calc(100vw - 16px));
  max-height: calc(100vh - 24px);
  overflow-y: auto;
  padding: 6px;
  background: rgba(28, 31, 38, .92);
  backdrop-filter: blur(14px);
  -webkit-backdrop-filter: blur(14px);
  border: 1px solid rgba(255,255,255,.10);
  border-radius: 12px;
  box-shadow: 0 12px 32px rgba(0,0,0,.45), 0 2px 8px rgba(0,0,0,.3);
  animation: ctxIn .12s ease-out;
}
#ctx-menu::-webkit-scrollbar { width: 6px; }
#ctx-menu::-webkit-scrollbar-thumb { background: rgba(255,255,255,.18); border-radius: 3px; }
#ctx-menu::-webkit-scrollbar-thumb:hover { background: rgba(255,255,255,.32); }
#ctx-menu::-webkit-scrollbar-track { background: transparent; }'''
assert s2.count(old2) == 1, 'css=' + str(s2.count(old2))
s2 = s2.replace(old2, new2)
io.open(p2, 'w', encoding='utf-8', newline='').write(s2)
print('CSS OK')
