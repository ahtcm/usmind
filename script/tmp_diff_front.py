# -*- coding: utf-8 -*-
import io
p = r'C:\Users\25750\Desktop\usmind\code\web-user\src\views\Editor.vue'
with io.open(p, 'r', encoding='utf-8') as f:
    s = f.read()

# 1. mindmap_updated 处理：静默 diff 局部应用（不再弹气泡/全量刷新）
old1 = """      if (msg.event !== 'mindmap_updated') return
      if (String(msg.mindMapId) !== String(mindMapId.value)) return
      let myUidVal = 0
      try { myUidVal = Number((JSON.parse(localStorage.getItem('usmind.user') || '{}')).id) || 0 } catch (e) { myUidVal = 0 }
      if (myUidVal && Number(msg.updatedBy) === myUidVal) return
      const v = Number(msg.version)
      if (!v || v <= curVersion.value) return
      if (dirty.value) {
        collabToast.text = (msg.updatedByName || '协作成员') + ' 已更新该导图（v' + v + '）'
        collabToast.version = v
        collabToast.show = true
      } else {
        loadLatest(true)
      }"""
new1 = """      if (msg.event !== 'mindmap_updated') return
      if (String(msg.mindMapId) !== String(mindMapId.value)) return
      let myUidVal = 0
      try { myUidVal = Number((JSON.parse(localStorage.getItem('usmind.user') || '{}')).id) || 0 } catch (e) { myUidVal = 0 }
      if (myUidVal && Number(msg.updatedBy) === myUidVal) return
      const v = Number(msg.version)
      if (!v || v <= curVersion.value) return
      // 静默 diff 局部应用：不弹提示、不全量刷新、保留视图位置（自己正在编辑的节点被改则弹增强冲突框）
      diffApplyLatest()"""
assert s.count(old1) == 1, 'old1'
s = s.replace(old1, new1)

# 2. diffApplyLatest + 增强冲突框处理函数（加在 loadLatest 后）
old2 = """function scheduleAutoSave() {"""
new2 = """/** 远端更新 diff 局部应用：不重建整图、保留视图 transform、不闪屏 */
async function diffApplyLatest() {
  try {
    const fresh = await mindmapApi.get(mindMapId.value, { params: { _t: Date.now() } })
    curVersion.value = fresh.version || 0
    let d = fresh.data || {}
    if (typeof d === 'string' && d) { try { d = JSON.parse(d) } catch (e2) { d = {} } }
    let remoteRoot = (d.root && d.root.data) || d.root || d
    if (d._tabs && Array.isArray(d._tabs) && d._tabs.length) {
      const active = d._tabs[d._activeTab || 0]
      if (active && active.data && active.data.root) remoteRoot = active.data.root
    }
    if (!remoteRoot || !remoteRoot.uid) return
    const localRoot = mind.value.render && mind.value.render.getRoot()
    if (!localRoot) return
    const localMap = {}
    const depthOf = {}
    const walkLocal = (n, dep) => { if (!n || !n.uid) return; localMap[n.uid] = n; depthOf[n.uid] = dep; (n.children || []).forEach(c => walkLocal(c, dep + 1)) }
    walkLocal(localRoot, 0)
    const remoteUids = new Set()
    const walkRemote = (n) => { if (!n) return; if (n.uid) remoteUids.add(n.uid); (n.children || []).forEach(walkRemote) }
    walkRemote(remoteRoot)
    // 1. 删除本地多余节点（先删深层）
    const toRemove = Object.keys(localMap)
      .filter(uid => !remoteUids.has(uid) && localMap[uid].parent)
      .sort((a, b) => (depthOf[b] || 0) - (depthOf[a] || 0))
      .map(uid => localMap[uid])
    if (toRemove.length) {
      wsApplying = true
      try { toRemove.forEach(nd => { try { if (nd.parent) mind.value.renderer.removeNode(nd) } catch (e) {} }) } finally { wsApplying = false }
    }
    // 2. 新增节点 + 文本变更 + creatorUid 同步
    const applyRemote = (rd, parentUid) => {
      if (!rd || !rd.uid) return
      const uid = rd.uid
      let localNode = localMap[uid]
      if (!localNode) {
        const parentNode = parentUid ? localMap[parentUid] : null
        if (parentNode) {
          try {
            const clone = JSON.parse(JSON.stringify(rd))
            const children = clone.children || []
            delete clone.children
            wsApplying = true
            mind.value.renderer.insertChildNode(false, [parentNode], clone, children)
            wsApplying = false
            const nn = mind.value.renderer.findNodeByUid(uid)
            if (nn) { localMap[uid] = nn; localNode = nn }
          } catch (e) { wsApplying = false }
        }
        if (!localNode) return
      }
      // 文本变更
      if (rd.text !== undefined && localNode.nodeData.text !== rd.text) {
        if (myEditNodeId === uid) {
          // 自己正在编辑的节点被改：弹增强冲突框（带对方内容），不静默覆盖
          if (!editConflict.value) {
            editConflict.value = { nodeId: uid, remoteText: rd.text == null ? '' : rd.text }
          }
        } else {
          wsApplying = true
          try { mind.value.renderer.setNodeText(localNode, rd.text == null ? '' : rd.text) } catch (e) {}
          wsApplying = false
        }
      }
      if (rd.creatorUid && !localNode.nodeData.creatorUid) localNode.nodeData.creatorUid = rd.creatorUid
      ;(rd.children || []).forEach(c => applyRemote(c, uid))
    }
    applyRemote(remoteRoot, null)
    suppressDirtyUntil = Date.now() + 3000
    try { canvasTabs.value[docIndex.value].data = mind.value.getData() } catch (e) {}
  } catch (e) { console.warn('diffApplyLatest err', e) }
}

/** 增强冲突框：加载最新 / 以我为准 */
function resolveEditConflict(choice) {
  const c = editConflict.value
  if (!c) return
  if (choice === 'load') {
    try {
      const node = mind.value.renderer.findNodeByUid(c.nodeId)
      if (node) { wsApplying = true; mind.value.renderer.setNodeText(node, c.remoteText == null ? '' : c.remoteText); wsApplying = false }
    } catch (e) { wsApplying = false }
    try { canvasTabs.value[docIndex.value].data = mind.value.getData() } catch (e) {}
    toast('已加载最新内容')
  }
  editConflict.value = null
}

function scheduleAutoSave() {"""
assert s.count(old2) == 1, 'old2'
s = s.replace(old2, new2)

# 3. editConflict 状态（加在 approveEditReq 后）
old3 = """const approveEditReq = ref(null)   // 审批弹窗"""
new3 = """const approveEditReq = ref(null)   // 审批弹窗
const editConflict = ref(null)     // 编辑中节点被他人修改的增强冲突框"""
assert s.count(old3) == 1, 'old3'
s = s.replace(old3, new3)

# 4. 模板：增强冲突框（审批弹窗后）
old4 = """  <!-- 协作实时更新提示气泡 -->"""
new4 = """  <!-- 编辑中节点被他人修改增强冲突框 -->
  <div v-if="editConflict" class="share-mask">
    <div class="share-box">
      <div class="share-title">节点被其他成员修改</div>
      <div class="share-tip">你正在编辑的节点已被其他成员更新为：{{ (editConflict.remoteText || '（空）').slice(0, 60) }}</div>
      <div class="share-actions">
        <button class="math-btn math-btn-primary" @click="resolveEditConflict('load')">加载最新内容</button>
        <button class="math-btn" @click="resolveEditConflict('mine')">以我的为准</button>
      </div>
    </div>
  </div>

  <!-- 协作实时更新提示气泡 -->"""
assert s.count(old4) == 1, 'old4'
s = s.replace(old4, new4)

with io.open(p, 'w', encoding='utf-8', newline='') as f:
    f.write(s)
print('EDITOR DIFF OK')
