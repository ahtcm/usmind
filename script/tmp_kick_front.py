# -*- coding: utf-8 -*-
import io
p = r'C:\Users\25750\Desktop\usmind\code\web-user\src\views\Editor.vue'
s = io.open(p, encoding='utf-8').read()

# 1) 模板：在编辑申请审批弹窗后加"机构已销毁"弹窗
old_tpl = '''  <!-- 编辑中节点被他人修改增强冲突框 -->'''
new_tpl = '''  <!-- 机构已销毁提示弹窗 -->
  <div v-if="orgDisbanded" class="share-mask">
    <div class="share-box">
      <div class="share-title">机构已销毁</div>
      <div class="share-tip">机构{{ orgDisbandedName }}已被创建者解散，你已被移出协作并返回首页。</div>
      <div class="share-actions">
        <button class="math-btn math-btn-primary" @click="goHomeAfterDisband">确定</button>
      </div>
    </div>
  </div>

  <!-- 编辑中节点被他人修改增强冲突框 -->'''
assert s.count(old_tpl) == 1, 'tpl=' + str(s.count(old_tpl))
s = s.replace(old_tpl, new_tpl)

# 2) WS onmessage 加 org_disbanded 分支（在 node_edit_result 分支后）
old_ws = '''      if (msg.event === 'node_edit_result') {
        if (String(msg.mindMapId) !== String(mindMapId.value)) return
        handleEditResultMsg(msg)
        return
      }
      if (msg.event !== 'mindmap_updated') return'''
new_ws = '''      if (msg.event === 'node_edit_result') {
        if (String(msg.mindMapId) !== String(mindMapId.value)) return
        handleEditResultMsg(msg)
        return
      }
      if (msg.event === 'org_disbanded') {
        orgDisbandedName.value = msg.orgName || ''
        orgDisbanded.value = true
        try { if (ws) ws.close() } catch (e) {}
        ws = null
        return
      }
      if (msg.event !== 'mindmap_updated') return'''
assert s.count(old_ws) == 1, 'ws=' + str(s.count(old_ws))
s = s.replace(old_ws, new_ws)

# 3) 状态定义（pendingEditReq 附近）
old_state = '''const pendingEditReq = ref(null)   // 申请确认弹窗'''
new_state = '''const pendingEditReq = ref(null)   // 申请确认弹窗
const orgDisbanded = ref(false)   // 机构已销毁提示弹窗
const orgDisbandedName = ref('')  // 机构名称'''
assert s.count(old_state) == 1, 'state=' + str(s.count(old_state))
s = s.replace(old_state, new_state)

# 4) 跳转函数（resolveEditConflict 附近找锚点：放在 confirmSendEditReq 前）
old_fn = '''function confirmSendEditReq() {'''
new_fn = '''function goHomeAfterDisband() {
  try { if (ws) ws.close() } catch (e) {}
  ws = null
  orgDisbanded.value = false
  window.location.href = '/maps'
}
function confirmSendEditReq() {'''
assert s.count(old_fn) == 1, 'fn=' + str(s.count(old_fn))
s = s.replace(old_fn, new_fn)

io.open(p, 'w', encoding='utf-8', newline='').write(s)
print('EDITOR VUE OK')
