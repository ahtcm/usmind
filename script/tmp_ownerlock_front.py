# -*- coding: utf-8 -*-
import io
p = r'C:\Users\25750\Desktop\usmind\code\web-user\src\views\Editor.vue'
with io.open(p, 'r', encoding='utf-8') as f:
    s = f.read()

# 1. 状态：节点所有权+编辑申请
old1 = """// ============ 节点级协作：锁定 + 实时编辑推送 ============
const nodeLocks = reactive({}) // nodeId -> { userId, userName }
let myEditNodeId = null"""
new1 = """// ============ 节点级协作：锁定 + 实时编辑推送 ============
const nodeLocks = reactive({}) // nodeId -> { userId, userName }
let myEditNodeId = null
// ---- 节点所有权 + 编辑申请 ----
const collabCfg = reactive({ enableOwnerLock: false, approveTimeout1: 120, approveTimeout2: 180, grantMinutes: 10 })
const pendingEditReq = ref(null)   // 申请确认弹窗
const approveEditReq = ref(null)   // 审批弹窗
const grantedNodes = reactive({})  // nodeId -> 本地授权截止时间戳
const sentEditReqs = reactive({})  // nodeId -> 已提交待审批
const mapOwnerId = ref(0)          // 导图创建者 uid"""
assert s.count(old1) == 1, 'old1'
s = s.replace(old1, new1)

# 2. 加载 collabConfig + mapOwnerId（loadDoc 创建 MindMap 后）
old2 = """  bindMindEvents()
  const t0name = doc.theme || themes.value[0].name"""
new2 = """  bindMindEvents()
  // 加载协作配置（节点所有权开关/审批超时）
  try {
    mapOwnerId.value = Number(doc.userId) || 0
    fetch('/api/mindmaps/' + mindMapId.value + '/collab-config', { headers: { 'Authorization': 'Bearer ' + (localStorage.getItem('usmind.token') || '') } })
      .then(r => r.json()).then(j => { if (j && j.data) Object.assign(collabCfg, j.data) }).catch(() => {})
  } catch (e) {}
  const t0name = doc.theme || themes.value[0].name"""
assert s.count(old2) == 1, 'old2'
s = s.replace(old2, new2)

# 3. creatorUid 写入：新建节点时标记创建者
old3 = """  mind.value.on('node_attachmentClick', (node) => openAttachmentPreview(node))"""
new3 = """  mind.value.on('node_attachmentClick', (node) => openAttachmentPreview(node))
  mind.value.on('node_add', (node) => { try { if (node && node.nodeData && !node.nodeData.creatorUid) node.nodeData.creatorUid = myUid() } catch (e) {} })"""
assert s.count(old3) == 1, 'old3'
s = s.replace(old3, new3)

# 4. beforeTextEdit：加节点所有权拦截
old4 = """    beforeTextEdit: (node) => {
      try {
        const nid = node.uid || (node.nodeData && node.nodeData.uid)
        if (nid && myEditNodeId !== nid) {
          const lk = nodeLocks[nid]
          if (lk && lk.userId !== myUid()) {
            toast('该节点已被 ' + lk.userName + ' 锁定，无法编辑', 'error')
            return false
          }
        }
      } catch (e) {}
      return true
    },"""
new4 = """    beforeTextEdit: (node) => {
      try {
        const nid = node.uid || (node.nodeData && node.nodeData.uid)
        if (nid && myEditNodeId !== nid) {
          const lk = nodeLocks[nid]
          if (lk && lk.userId !== myUid()) {
            toast('该节点已被 ' + lk.userName + ' 锁定，无法编辑', 'error')
            return false
          }
        }
        // 节点所有权：非创建者/非导图创建者（且未获授权）需申请
        if (collabCfg.enableOwnerLock && nid) {
          const nd = node.nodeData || {}
          const creator = Number(nd.creatorUid) || 0
          const isOwner = Number(mapOwnerId.value) === myUid()
          const isCreator = creator === myUid()
          const granted = grantedNodes[nid] && grantedNodes[nid] > Date.now()
          if (!isOwner && !isCreator && !granted) {
            requestEditPermission(nid, node)
            return false
          }
        }
      } catch (e) {}
      return true
    },"""
assert s.count(old4) == 1, 'old4'
s = s.replace(old4, new4)

# 5. 申请/审批/结果处理函数（加在 acquireNodeLock 前）
old5 = """function acquireNodeLock(nid) {"""
new5 = """function requestEditPermission(nid, node) {
  if (sentEditReqs[nid]) { toast('已提交编辑申请，等待审批', 'info'); return }
  pendingEditReq.value = { nodeId: nid, nodeText: (node.nodeData && node.nodeData.text) || '' }
}
function confirmSendEditReq() {
  const req = pendingEditReq.value
  if (!req) return
  sendWs({ type: 'node_edit_request', mindMapId: String(mindMapId.value), nodeId: req.nodeId, nodeCreatorUid: 0, nodeText: (req.nodeText || '').slice(0, 200) })
  sentEditReqs[req.nodeId] = true
  pendingEditReq.value = null
  toast('已提交编辑申请，等待审批', 'info')
}
function respondEditReq(approve) {
  const req = approveEditReq.value
  if (!req) return
  sendWs({ type: 'node_edit_response', mindMapId: String(mindMapId.value), nodeId: req.nodeId, approve: approve ? 'yes' : 'no', applicantUid: req.applicantUid })
  approveEditReq.value = null
  toast(approve ? '已同意编辑申请' : '已拒绝编辑申请', approve ? 'success' : 'info')
}
function handleEditRequestMsg(msg) {
  approveEditReq.value = { nodeId: msg.nodeId, nodeText: msg.nodeText || '', applicantUid: Number(msg.applicantUid), applicantName: msg.applicantName || '', reason: msg.reason || 'new' }
}
function handleEditResultMsg(msg) {
  const nid = msg.nodeId
  if (msg.result === 'pending') {
    delete sentEditReqs[nid]
    toast('该节点已有申请正在审批中', 'warning')
    return
  }
  if (msg.result === 'sent') { toast('已提交编辑申请，等待审批', 'info'); return }
  if (!sentEditReqs[nid]) return
  if (msg.result === 'approved') {
    grantedNodes[nid] = Date.now() + (collabCfg.grantMinutes || 10) * 60 * 1000
    toast('已获授权，可编辑该节点（' + (collabCfg.grantMinutes || 10) + ' 分钟）', 'success')
  } else if (msg.result === 'rejected') {
    toast('编辑申请已被拒绝', 'error')
  } else if (msg.result === 'auto_rejected') {
    toast('申请超时未审批，已自动拒绝', 'error')
  }
  delete sentEditReqs[nid]
}
function acquireNodeLock(nid) {"""
assert s.count(old5) == 1, 'old5'
s = s.replace(old5, new5)

# 6. WS onmessage 分支：node_edit_request / node_edit_result
old6 = """      if (msg.event === 'node_change') {
        if (String(msg.mindMapId) !== String(mindMapId.value)) return
        if (Number(msg.userId) === myUid()) return
        applyRemoteNodeChange(msg.nodeId, msg.text, Number(msg.userId))
        return
      }"""
new6 = """      if (msg.event === 'node_change') {
        if (String(msg.mindMapId) !== String(mindMapId.value)) return
        if (Number(msg.userId) === myUid()) return
        applyRemoteNodeChange(msg.nodeId, msg.text, Number(msg.userId))
        return
      }
      if (msg.event === 'node_edit_request') {
        if (String(msg.mindMapId) !== String(mindMapId.value)) return
        handleEditRequestMsg(msg)
        return
      }
      if (msg.event === 'node_edit_result') {
        if (String(msg.mindMapId) !== String(mindMapId.value)) return
        handleEditResultMsg(msg)
        return
      }"""
assert s.count(old6) == 1, 'old6'
s = s.replace(old6, new6)

# 7. 模板：申请确认弹窗 + 审批弹窗（冲突弹窗后）
old7 = """  <!-- 协作实时更新提示气泡 -->"""
new7 = """  <!-- 编辑申请确认弹窗 -->
  <div v-if="pendingEditReq" class="share-mask">
    <div class="share-box">
      <div class="share-title">申请编辑节点</div>
      <div class="share-tip">该节点由其他成员创建，需要申请编辑权限。节点内容：{{ (pendingEditReq.nodeText || '（空）').slice(0, 50) }}</div>
      <div class="share-actions">
        <button class="math-btn math-btn-primary" @click="confirmSendEditReq">提交申请</button>
        <button class="math-btn" @click="pendingEditReq = null">取消</button>
      </div>
    </div>
  </div>

  <!-- 编辑申请审批弹窗 -->
  <div v-if="approveEditReq" class="share-mask">
    <div class="share-box">
      <div class="share-title">编辑申请审批</div>
      <div class="share-tip">{{ approveEditReq.applicantName }} 申请编辑节点：{{ (approveEditReq.nodeText || '（空）').slice(0, 50) }}</div>
      <div class="share-actions">
        <button class="math-btn math-btn-primary" @click="respondEditReq(true)">同意</button>
        <button class="math-btn" @click="respondEditReq(false)">拒绝</button>
      </div>
    </div>
  </div>

  <!-- 协作实时更新提示气泡 -->"""
assert s.count(old7) == 1, 'old7'
s = s.replace(old7, new7)

with io.open(p, 'w', encoding='utf-8', newline='') as f:
    f.write(s)
print('EDITOR OWNERLOCK OK')
