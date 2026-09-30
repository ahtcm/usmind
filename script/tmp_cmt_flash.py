# -*- coding: utf-8 -*-
import io
p = r'C:\Users\25750\Desktop\usmind\code\web-user\src\views\Editor.vue'
s = io.open(p, encoding='utf-8').read()

# 1) 模板：map div 后加徽标覆盖层
old_tpl = '''      <div id="map" ref="mapEl"></div>
    </div>'''
new_tpl = '''      <div id="map" ref="mapEl"></div>
      <div id="cmt-badge-layer" class="cmt-badge-layer"></div>
    </div>'''
assert s.count(old_tpl) == 1, 'tpl=' + str(s.count(old_tpl))
s = s.replace(old_tpl, new_tpl)

# 2) applyCommentFlash 重构（呼吸灯 + 路径高亮 + HTML 徽标）
old_fn = '''function applyCommentFlash() {
  if (!mind.value || !mind.value.renderer) return
  try {
    document.querySelectorAll('.cmt-flash').forEach(el => el.classList.remove('cmt-flash'))
    Object.keys(unreadNodes).forEach(uid => {
      const cnt = unreadNodes[uid]
      if (!cnt) return
      const gEl = nodeElOf(uid)
      if (!gEl) return
      const rect = gEl.querySelector('rect')
      if (rect) rect.classList.add('cmt-flash')
      let badge = gEl.querySelector('.cmt-badge-fo')
      if (!badge) {
        const NS = 'http://www.w3.org/2000/svg'
        badge = document.createElementNS(NS, 'foreignObject')
        badge.setAttribute('class', 'cmt-badge-fo')
        const div = document.createElement('div')
        div.className = 'cmt-badge-div'
        badge.appendChild(div)
        gEl.appendChild(badge)
      }
      const div = badge.querySelector('.cmt-badge-div')
      div.textContent = cnt > 99 ? '99+' : cnt
      let bb = null
      try { bb = (rect || gEl).getBBox ? (rect || gEl).getBBox() : null } catch (e) {}
      const bx = bb ? Math.max(0, bb.x + bb.width - 6) : 0
      badge.setAttribute('x', bx)
      badge.setAttribute('y', bb ? Math.max(0, bb.y - 15) : 0)
      badge.setAttribute('width', '22')
      badge.setAttribute('height', '18')
    })
  } catch (e) {}
}'''
new_fn = '''function applyCommentFlash() {
  if (!mind.value || !mind.value.renderer) return
  try {
    // 清空旧的动画类
    document.querySelectorAll('.cmt-flash, .cmt-breath-path, .cmt-breath-line').forEach(el => {
      el.classList.remove('cmt-flash', 'cmt-breath-path', 'cmt-breath-line')
    })
    // 清空徽标层
    const layer = document.getElementById('cmt-badge-layer')
    if (layer) layer.innerHTML = ''
    const nodeCache = mind.value.renderer.nodeCache || {}
    const mapRect = mapEl.value ? mapEl.value.getBoundingClientRect() : null
    Object.keys(unreadNodes).forEach(uid => {
      const cnt = unreadNodes[uid]
      if (!cnt) return
      const n = nodeCache[uid]
      const gEl = n && n.group ? (n.group.node || n.group) : null
      if (!gEl) return
      // 1) 目标节点呼吸灯
      const rect = gEl.querySelector('rect')
      if (rect) rect.classList.add('cmt-flash')
      // 2) 路径呼吸灯：根 → 目标的所有祖先节点矩形 + 连线
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
      }
      // 3) 右上角未读数徽标（HTML 覆盖层，固定屏幕尺寸，不随画布缩放）
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
      }
    })
  } catch (e) {}
}'''
assert s.count(old_fn) == 1, 'fn=' + str(s.count(old_fn))
s = s.replace(old_fn, new_fn)

# 3) scale 监听重定位徽标
old_scale = '''  mind.value.on('scale', (s) => { zoomPct.value = Math.round(s*100) + '%'; try { updateScrollbars() } catch (e) {}; applyLazyView() })'''
new_scale = '''  mind.value.on('scale', (s) => { zoomPct.value = Math.round(s*100) + '%'; try { updateScrollbars() } catch (e) {}; applyLazyView(); try { applyCommentFlash() } catch (e) {} })'''
assert s.count(old_scale) == 1, 'scale=' + str(s.count(old_scale))
s = s.replace(old_scale, new_scale)

# 4) CSS：替换旧闪烁动画，追加呼吸灯 + 徽标层
old_css = '''/* 未读节点闪烁 + 角标 */
.cmt-flash { animation: cmtBlink 1.1s ease-in-out infinite; }
@keyframes cmtBlink { 0%, 100% { filter: none; } 50% { filter: brightness(1.45) drop-shadow(0 0 7px rgba(122, 162, 247, .75)); } }
.cmt-badge-fo { pointer-events: none; }
.cmt-badge-div { min-width: 18px; height: 18px; line-height: 18px; text-align: center; font-size: 11px; font-weight: 700; color: #fff; background: #e53935; border-radius: 9px; padding: 0 4px; box-shadow: 0 2px 6px rgba(0,0,0,.35); }'''
new_css = '''/* 未读节点：呼吸灯（节点+根路径）+ 右上角未读数徽标（HTML 覆盖层，不随画布缩放） */
.cmt-badge-layer { position: absolute; inset: 0; pointer-events: none; z-index: 2; overflow: hidden; }
.cmt-badge-el { position: absolute; min-width: 18px; height: 18px; line-height: 18px; text-align: center; font-size: 11px; font-weight: 700; color: #fff; background: linear-gradient(135deg, #ff6b6b, #e53935); border-radius: 9px; padding: 0 5px; box-shadow: 0 2px 8px rgba(229,57,53,.6); transform: translate(-50%, -50%); }
.cmt-flash { animation: cmtBreath 1.6s ease-in-out infinite; }
.cmt-breath-path { animation: cmtBreathPath 1.6s ease-in-out infinite; }
.cmt-breath-line { animation: cmtBreathLine 1.6s ease-in-out infinite; }
@keyframes cmtBreath {
  0%, 100% { filter: drop-shadow(0 0 0 rgba(255,77,79,0)); opacity: 1; }
  50% { filter: drop-shadow(0 0 9px rgba(255,77,79,.95)); opacity: .72; }
}
@keyframes cmtBreathPath {
  0%, 100% { filter: drop-shadow(0 0 0 rgba(255,77,79,0)); opacity: 1; }
  50% { filter: drop-shadow(0 0 6px rgba(255,77,79,.75)); opacity: .6; }
}
@keyframes cmtBreathLine {
  0%, 100% { stroke-opacity: .9; }
  50% { stroke-opacity: .25; }
}'''
assert s.count(old_css) == 1, 'css=' + str(s.count(old_css))
s = s.replace(old_css, new_css)

io.open(p, 'w', encoding='utf-8', newline='').write(s)
print('EDITOR FLASH OK')
