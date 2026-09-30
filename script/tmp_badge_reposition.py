# -*- coding: utf-8 -*-
import io
p = r'C:\Users\25750\Desktop\usmind\code\web-user\src\views\Editor.vue'
s = io.open(p, encoding='utf-8').read()

# 1) 新增 repositionCommentBadges（只更新徽标位置，不重建、不打断呼吸灯动画）
old = '''function syncCommentFlash() {
  nextTick(() => { try { applyCommentFlash() } catch (e) {} })
}'''
new = '''function syncCommentFlash() {
  nextTick(() => { try { applyCommentFlash() } catch (e) {} })
}
/** 轻量徽标重定位：平移/缩放画布时仅更新未读徽标位置（与 applyCommentFlash 创建顺序一致），不重建、不打断呼吸灯动画 */
function repositionCommentBadges() {
  try {
    const layer = document.getElementById('cmt-badge-layer')
    if (!layer || !layer.children.length) return
    const mapRect = mapEl.value ? mapEl.value.getBoundingClientRect() : null
    if (!mapRect) return
    const nodeCache = mind.value.renderer ? (mind.value.renderer.nodeCache || {}) : {}
    const uids = Object.keys(unreadNodes).filter(uid => unreadNodes[uid])
    const badges = layer.children
    for (let i = 0; i < badges.length && i < uids.length; i++) {
      const uid = uids[i]
      if (!uid) continue
      const n = nodeCache[uid]
      const gEl = n && n.group ? (n.group.node || n.group) : null
      if (!gEl) continue
      const r = gEl.getBoundingClientRect()
      if (r.width <= 1 || r.height <= 1) continue
      const b = badges[i]
      b.style.left = (r.right - mapRect.left) + 'px'
      b.style.top = (r.top - mapRect.top) + 'px'
    }
  } catch (e) {}
}'''
assert s.count(old) == 1, 'fn=' + str(s.count(old))
s = s.replace(old, new)

# 2) view_data_change：平移/缩放视图变化时重定位徽标
old = '''  mind.value.on('view_data_change', (vd) => { try { updateScrollbars() } catch (e) {}; if (canvasTabs.value[docIndex.value]) canvasTabs.value[docIndex.value].viewData = vd; applyLazyView() })'''
new = '''  mind.value.on('view_data_change', (vd) => { try { updateScrollbars() } catch (e) {}; if (canvasTabs.value[docIndex.value]) canvasTabs.value[docIndex.value].viewData = vd; applyLazyView(); try { repositionCommentBadges() } catch (e) {} })'''
assert s.count(old) == 1, 'vdc=' + str(s.count(old))
s = s.replace(old, new)

# 3) scale 回调：改用轻量重定位（避免缩放过程中重建徽标打断呼吸灯）
old = '''  mind.value.on('scale', (s) => { zoomPct.value = Math.round(s*100) + '%'; try { updateScrollbars() } catch (e) {}; applyLazyView(); try { applyCommentFlash() } catch (e) {} })'''
new = '''  mind.value.on('scale', (s) => { zoomPct.value = Math.round(s*100) + '%'; try { updateScrollbars() } catch (e) {}; applyLazyView(); try { repositionCommentBadges() } catch (e) {} })'''
assert s.count(old) == 1, 'scale=' + str(s.count(old))
s = s.replace(old, new)

io.open(p, 'w', encoding='utf-8', newline='').write(s)
print('BADGE REPOSITION OK')
