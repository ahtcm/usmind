# -*- coding: utf-8 -*-
import io

# ============ A) request.js：非200响应透传 data（冲突列表等附加数据） ============
p1 = r'C:\Users\25750\Desktop\usmind\code\web-user\src\api\request.js'
s1 = io.open(p1, encoding='utf-8').read()
old = '''      const er = new Error(body.message || '请求失败')
      er.code = body.code
      er.status = body.status
      return Promise.reject(er)'''
new = '''      const er = new Error(body.message || '请求失败')
      er.code = body.code
      er.status = body.status
      er.data = body.data
      return Promise.reject(er)'''
assert s1.count(old) == 1, 'req=' + str(s1.count(old))
s1 = s1.replace(old, new)
io.open(p1, 'w', encoding='utf-8', newline='').write(s1)

# ============ B) Editor.vue ============
p = r'C:\Users\25750\Desktop\usmind\code\web-user\src\views\Editor.vue'
s = io.open(p, encoding='utf-8').read()

# B1) 右键菜单 del 按钮：无删除权限隐藏
old = '''        <button data-act="del" class="ctx-danger" @click="ctxAct('del')">删除节点</button>'''
new = '''        <button v-if="ctxNode && canDeleteNode(ctxNode)" data-act="del" class="ctx-danger" @click="ctxAct('del')">删除节点</button>'''
assert s.count(old) == 1, 'delbtn=' + str(s.count(old))
s = s.replace(old, new)

# B2) 删申请/审批弹窗 → 换成保存冲突 diff 弹窗
old = '''  <!-- 编辑申请确认弹窗 -->
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
'''
new = '''  <!-- 保存冲突 diff 弹窗（并发合并时同节点双方内容不一致） -->
  <div v-if="saveConflictDialog" class="share-mask">
    <div class="share-box" style="max-width:560px">
      <div class="share-title">保存冲突：以下节点内容不一致</div>
      <div class="share-tip">保存时导图已被其他成员修改，双方内容不同的节点如下（编辑锁定机制下通常不会出现）。其余节点将自动合并保留，互不覆盖。</div>
      <div class="conflict-list">
        <div v-for="c in saveConflictDialog.conflicts" :key="c.uid" class="conflict-item">
          <div class="conflict-node">{{ c.name }}</div>
          <div class="conflict-cols">
            <div class="conflict-col mine">
              <div class="conflict-label">我的内容</div>
              <div class="conflict-text">{{ c.myText || '（空）' }}</div>
            </div>
            <div class="conflict-col remote">
              <div class="conflict-label">对方内容</div>
              <div class="conflict-text">{{ c.remoteText || '（空）' }}</div>
            </div>
          </div>
        </div>
      </div>
      <div class="share-actions">
        <button class="math-btn math-btn-primary" @click="saveConflictResolve(true)">全部以我的为准</button>
        <button class="math-btn" @click="saveConflictResolve(false)">全部以对方的为准</button>
        <button class="math-btn" @click="saveConflictDialog = null">取消</button>
      </div>
    </div>
  </div>
'''
assert s.count(old) == 1, 'modals=' + str(s.count(old))
s = s.replace(old, new)

# B3) beforeTextEdit：删节点所有权申请拦截（保留锁拦截）
old = '''        // 节点所有权：非创建者/非导图创建者（且未获授权）需申请
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
    },'''
new = '''      } catch (e) {}
      return true
    },'''
assert s.count(old) == 1, 'btEdit=' + str(s.count(old))
s = s.replace(old, new)

# B4) 删 collabCfg 加载
old = '''  // 加载协作配置（节点所有权开关/审批超时）
  try {
    fetch('/api/mindmaps/' + mindMapId.value + '/collab-config', { headers: { 'Authorization': 'Bearer ' + (localStorage.getItem('usmind.token') || '') } })
      .then(r => r.json()).then(j => { if (j && j.data) Object.assign(collabCfg, j.data) }).catch(() => {})
  } catch (e) {}
'''
assert s.count(old) == 1, 'cfgload=' + str(s.count(old))
s = s.replace(old, '')

# B5) WS 处理：删 node_edit_request / node_edit_result 分支
old = '''      if (msg.event === 'node_edit_request') {
        if (String(msg.mindMapId) !== String(mindMapId.value)) return
        handleEditRequestMsg(msg)
        return
      }
      if (msg.event === 'node_edit_result') {
        if (String(msg.mindMapId) !== String(mindMapId.value)) return
        handleEditResultMsg(msg)
        return
      }
'''
assert s.count(old) == 1, 'wsbr=' + str(s.count(old))
s = s.replace(old, '')

# B6) state 定义：删审批相关，加 saveConflictDialog / deletedUids
old = '''// ---- 节点所有权 + 编辑申请 ----
const collabCfg = reactive({ enableOwnerLock: false, approveTimeout1: 120, approveTimeout2: 180, grantMinutes: 10 })
const pendingEditReq = ref(null)   // 申请确认弹窗
const orgDisbanded = ref(false)   // 机构已销毁提示弹窗
const orgDisbandedName = ref('')  // 机构名称
const approveEditReq = ref(null)   // 审批弹窗
const editConflict = ref(null)     // 编辑中节点被他人修改的增强冲突框
const grantedNodes = reactive({})  // nodeId -> 本地授权截止时间戳
const sentEditReqs = reactive({})  // nodeId -> 已提交待审批
const mapOwnerId = ref(0)          // 导图创建者 uid'''
new = '''const orgDisbanded = ref(false)   // 机构已销毁提示弹窗
const orgDisbandedName = ref('')  // 机构名称
const editConflict = ref(null)     // 编辑中节点被他人修改的增强冲突框
const saveConflictDialog = ref(null) // 保存冲突 diff 弹窗 { conflicts, serverVersion }
const mapOwnerId = ref(0)          // 导图创建者 uid
const deletedUids = new Set()      // 本次会话删除的节点 uid（含子树），保存时提交供并发合并区分「我方删除」与「对方新增」'''
assert s.count(old) == 1, 'state=' + str(s.count(old))
s = s.replace(old, new)

# B7) 函数：删 requestEditPermission / confirmSendEditReq / respondEditReq / handleEditRequestMsg / handleEditResultMsg
old = '''function requestEditPermission(nid, node) {
  if (sentEditReqs[nid]) { toast('已提交编辑申请，等待审批', 'info'); return }
  pendingEditReq.value = { nodeId: nid, nodeText: (node.nodeData && node.nodeData.text) || '' }
}
function goHomeAfterDisband() {'''
new = '''function goHomeAfterDisband() {'''
assert s.count(old) == 1, 'reqperm=' + str(s.count(old))
s = s.replace(old, new)

old = '''function confirmSendEditReq() {
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
function acquireNodeLock(nid) {'''
new = '''function acquireNodeLock(nid) {'''
assert s.count(old) == 1, 'fns=' + str(s.count(old))
s = s.replace(old, new)

# B8) saveDoc：参数化 + deletedUids + 409 conflicts diff
old = '''async function saveDoc() {
  if (!mind.value || !mindMapId.value) return
  const doc = canvasTabs.value[docIndex.value]
  const prevRoot = doc.data && doc.data.root && doc.data.root.data
  const data = mind.value.getData()
  // setData 重建可能丢失根节点边框/形状字段，保存前补写：用户本次设置的边框，或加载时的原边框
  if (data && data.data) {
    if (styleBorder.value > 0) {
      data.data.borderWidth = styleBorder.value
      data.data.borderColor = styleBorderColor.value
    } else if (prevRoot && prevRoot.borderWidth > 0 && !data.data.borderWidth) {
      data.data.borderWidth = prevRoot.borderWidth
      data.data.borderColor = prevRoot.borderColor
    }
  }
  if (data && data.data && styleRadius.value > 0 && !data.data.shape) {
    data.data.shape = 'rectangle'
  }
  doc.data = { root: data }
  doc.viewData = mind.value.view.getTransformData()
  doc.manualStyles = Array.from(manualStyled)
  doc.styleState = { radius: styleRadius.value, font: styleFont.value, link: styleLink.value, border: styleBorder.value, borderColor: styleBorderColor.value }
  doc.name = docName.value
  doc.layout = mind.value.opt.layout || 'logicalStructure'
  doc.theme = mind.value.opt.theme || themes.value[0].name
  try {
    const r = await mindmapApi.update(mindMapId.value, {
      name: docName.value,
      data: { root: data, _tabs: canvasTabs.value, _activeTab: docIndex.value },
      layout: doc.layout,
      theme: doc.theme,
      viewData: doc.viewData,
      manualStyles: Array.from(manualStyled),
      baseVersion: curVersion.value
    })
    if (r && r.version != null) curVersion.value = r.version
    markSaved()
  } catch (e) {
    if (e && e.code === 409) {
      conflictDialog.value = true
    } else {
      console.warn('保存失败:', e)
      toast((e && e.message) || '保存失败', 'error')
    }
  }
}'''
new = '''async function saveDoc(opts = {}) {
  if (!mind.value || !mindMapId.value) return
  const doc = canvasTabs.value[docIndex.value]
  const prevRoot = doc.data && doc.data.root && doc.data.root.data
  const data = mind.value.getData()
  // setData 重建可能丢失根节点边框/形状字段，保存前补写：用户本次设置的边框，或加载时的原边框
  if (data && data.data) {
    if (styleBorder.value > 0) {
      data.data.borderWidth = styleBorder.value
      data.data.borderColor = styleBorderColor.value
    } else if (prevRoot && prevRoot.borderWidth > 0 && !data.data.borderWidth) {
      data.data.borderWidth = prevRoot.borderWidth
      data.data.borderColor = prevRoot.borderColor
    }
  }
  if (data && data.data && styleRadius.value > 0 && !data.data.shape) {
    data.data.shape = 'rectangle'
  }
  doc.data = { root: data }
  doc.viewData = mind.value.view.getTransformData()
  doc.manualStyles = Array.from(manualStyled)
  doc.styleState = { radius: styleRadius.value, font: styleFont.value, link: styleLink.value, border: styleBorder.value, borderColor: styleBorderColor.value }
  doc.name = docName.value
  doc.layout = mind.value.opt.layout || 'logicalStructure'
  doc.theme = mind.value.opt.theme || themes.value[0].name
  try {
    const r = await mindmapApi.update(mindMapId.value, {
      name: docName.value,
      data: { root: data, _tabs: canvasTabs.value, _activeTab: docIndex.value },
      layout: doc.layout,
      theme: doc.theme,
      viewData: doc.viewData,
      manualStyles: Array.from(manualStyled),
      baseVersion: opts.baseVersion != null ? opts.baseVersion : curVersion.value,
      deletedUids: deletedUids.size ? Array.from(deletedUids) : [],
      resolveConflicts: opts.resolveConflicts || undefined
    })
    if (r && r.version != null) curVersion.value = r.version
    deletedUids.clear()
    markSaved()
  } catch (e) {
    if (e && e.code === 409 && e.data && e.data.conflicts && e.data.conflicts.length) {
      // 并发合并冲突：弹 diff 框由用户选择以谁为准（其余节点自动合并保留）
      saveConflictDialog.value = { conflicts: e.data.conflicts, serverVersion: e.data.serverVersion }
    } else if (e && e.code === 409) {
      conflictDialog.value = true
    } else {
      console.warn('保存失败:', e)
      toast((e && e.message) || '保存失败', 'error')
    }
  }
}

/** 保存冲突 diff 弹窗：选择以谁为准后重新保存（其余未冲突节点仍合并保留） */
async function saveConflictResolve(useMine) {
  const dlg = saveConflictDialog.value
  if (!dlg) return
  saveConflictDialog.value = null
  await saveDoc({ resolveConflicts: useMine ? 'mine' : 'theirs', baseVersion: dlg.serverVersion })
}'''
assert s.count(old) == 1, 'savedoc=' + str(s.count(old))
s = s.replace(old, new)

# B9) 删除权限 + deletedUids 收集 + doDeleteNode 改造
old = '''function doDeleteNode(node) {
  node = node || getActiveNode()
  if (!node) { toast('请先选中节点再删除'); return }
  try { mind.value.execCommand('SET_NODE_ACTIVE', node, true) } catch (e) {}
  try { mind.value.execCommand('REMOVE_NODE', [node]) } catch (e) { console.warn('del remove err', e) }
  saveDoc()
}'''
new = '''/** 删除权限：仅 该节点创建人 或 其双亲节点创建人 可删；根节点（无父）仅导图创建者可删 */
function canDeleteNode(node) {
  try {
    if (!node) return false
    const me = myUid()
    if (!me) return false
    if (!node.parent) return Number(mapOwnerId.value) === me
    const nodeCreator = Number(node.getData('creatorUid')) || 0
    const parentCreator = Number(node.parent.getData('creatorUid')) || 0
    return me === nodeCreator || me === parentCreator
  } catch (e) { return false }
}

/** 收集节点及其整棵子树的 uid（删除时记录，供保存合并区分删除 vs 对方新增） */
function collectSubtreeUids(node, set) {
  if (!node) return
  const nid = node.uid || (node.nodeData && node.nodeData.uid)
  if (nid) set.add(nid)
  ;(node.children || []).forEach(c => collectSubtreeUids(c, set))
}

function doDeleteNode(node) {
  node = node || getActiveNode()
  if (!node) { toast('请先选中节点再删除'); return }
  if (!canDeleteNode(node)) {
    toast('无权限删除该节点（仅该节点创建者或其双亲节点创建者可删除）', 'error')
    return
  }
  collectSubtreeUids(node, deletedUids)
  try { mind.value.execCommand('SET_NODE_ACTIVE', node, true) } catch (e) {}
  try { mind.value.execCommand('REMOVE_NODE', [node]) } catch (e) { console.warn('del remove err', e) }
  saveDoc()
}'''
assert s.count(old) == 1, 'del=' + str(s.count(old))
s = s.replace(old, new)

# B10) CSS：追加保存冲突弹窗样式（scoped style 末尾——找 style 块的结束）
css_marker = '''/* 未读节点：呼吸灯（节点+根路径）+ 右上角未读数徽标（HTML 覆盖层，不随画布缩放） */'''
assert s.count(css_marker) == 1, 'cssmarker=' + str(s.count(css_marker))
css_add = css_marker + '''
/* 保存冲突 diff 弹窗 */
.conflict-list { max-height: 42vh; overflow-y: auto; margin: 10px 0; display: flex; flex-direction: column; gap: 8px; }
.conflict-item { border: 1px solid rgba(255,255,255,.12); border-radius: 10px; padding: 8px 10px; background: rgba(255,255,255,.04); }
.conflict-node { font-size: 13px; font-weight: 700; color: #e6b422; margin-bottom: 6px; word-break: break-all; }
.conflict-cols { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
.conflict-col { min-width: 0; }
.conflict-col.mine .conflict-label { color: #4fc3f7; }
.conflict-col.remote .conflict-label { color: #ff8a65; }
.conflict-label { font-size: 11px; font-weight: 600; margin-bottom: 3px; }
.conflict-text { font-size: 12px; color: #e6e6e6; background: rgba(0,0,0,.25); border-radius: 6px; padding: 5px 7px; word-break: break-all; max-height: 90px; overflow-y: auto; white-space: pre-wrap; }
@media (max-width: 480px) {
  .conflict-cols { grid-template-columns: 1fr; }
}'''
s = s.replace(css_marker, css_add)

io.open(p, 'w', encoding='utf-8', newline='').write(s)
print('EDITOR ALL OK')
