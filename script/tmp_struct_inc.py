# -*- coding: utf-8 -*-
"""Editor.vue: 结构变更 WS 增量广播 + 对端局部应用 + no-anim/wsApplying 防闪防回环"""
import io
P = r'C:\Users\25750\Desktop\usmind\code\web-user\src\views\Editor.vue'
s = io.open(P, encoding='utf-8').read()

# ---- 块1: afterExecCommand 加 wsApplying 门闩 + 结构增量广播 ----
old1 = '''  // 结构类命令（创建/删除/移动/粘贴节点）执行后立即保存：让协作端秒级收到广播，无需等自动保存周期
  mind.value.on('afterExecCommand', (name) => {
    if (STRUCT_CHANGE_CMDS.indexOf(name) >= 0) scheduleStructureSave()
    // 新建节点标记创建者：后端 get 时已给历史节点补 creatorUid（导图创建者），
    // 这里只给仍无创建者的节点（即本次新插入的节点）补上当前用户
    if (name && name.indexOf('INSERT') === 0) {
      try {
        const rn = mind.value.renderer && mind.value.renderer.root
        if (rn) {
          const walk = (n) => {
            if (n && n.nodeData && !n.nodeData.creatorUid) n.nodeData.creatorUid = myUid()
            if (n.children) n.children.forEach(walk)
          }
          walk(rn)
        }
      } catch (e) {}
    }
  })'''
new1 = '''  // 结构类命令（创建/删除/移动/粘贴节点）执行后立即保存：让协作端秒级收到广播，无需等自动保存周期
  mind.value.on('afterExecCommand', (name) => {
    // 远端增量应用触发的命令（wsApplying 门闩）：不回环保存、不二次广播
    if (wsApplying) return
    if (STRUCT_CHANGE_CMDS.indexOf(name) >= 0) {
      scheduleStructureSave()
      // 新建节点标记创建者：后端 get 时已给历史节点补 creatorUid（导图创建者），
      // 这里只给仍无创建者的节点（即本次新插入的节点）补上当前用户
      if (name && name.indexOf('INSERT') === 0) {
        try {
          const rn = mind.value.renderer && mind.value.renderer.root
          if (rn) {
            const walk = (n) => {
              if (n && n.nodeData && !n.nodeData.creatorUid) n.nodeData.creatorUid = myUid()
              if (n.children) n.children.forEach(walk)
            }
            walk(rn)
          }
        } catch (e) {}
      }
      // 结构增量实时广播：对端零延迟局部应用（不拉全量、不闪屏）
      broadcastStructDelta()
    }
  })'''
assert s.count(old1) == 1, 'anchor1'
s = s.replace(old1, new1)

# ---- 块2: ws.onmessage 增加结构增量事件分支 ----
old2 = '''      if (msg.event === 'node_change') {
        if (String(msg.mindMapId) !== String(mindMapId.value)) return
        if (Number(msg.userId) === myUid()) return
        applyRemoteNodeChange(msg.nodeId, msg.text, Number(msg.userId))
        return
      }'''
new2 = '''      if (msg.event === 'node_change') {
        if (String(msg.mindMapId) !== String(mindMapId.value)) return
        if (Number(msg.userId) === myUid()) return
        applyRemoteNodeChange(msg.nodeId, msg.text, Number(msg.userId))
        return
      }
      if (msg.event === 'node_added') {
        if (String(msg.mindMapId) !== String(mindMapId.value)) return
        if (Number(msg.userId) === myUid()) return
        if (msg.nodeDataTooLarge) return
        applyRemoteNodeAdded(msg)
        return
      }
      if (msg.event === 'node_removed') {
        if (String(msg.mindMapId) !== String(mindMapId.value)) return
        if (Number(msg.userId) === myUid()) return
        applyRemoteNodeRemoved(msg.nodeId)
        return
      }
      if (msg.event === 'node_moved') {
        if (String(msg.mindMapId) !== String(mindMapId.value)) return
        if (Number(msg.userId) === myUid()) return
        applyRemoteNodeMoved(msg.nodeId, msg.parentId)
        return
      }'''
assert s.count(old2) == 1, 'anchor2'
s = s.replace(old2, new2)

# ---- 块3: 应用函数 + 快照/序列化/广播函数（插在 applyRemoteNodeChange 定义之后） ----
old3 = '''function applyRemoteLock(nid, action, userId, userName) {'''
new3 = '''// ============ 结构变更 WS 增量：快照 diff 广播 + 对端局部应用 ============
let structSnap = {} // uid -> { p: parentUid|'', i: index }
function snapshotStruct() {
  const snap = {}
  const rn = mind.value && mind.value.renderer && mind.value.renderer.root
  if (!rn) return snap
  const walk = (n) => {
    if (!n) return
    const kids = n.children || []
    kids.forEach((c, i) => {
      const cu = c.uid || (c.nodeData && c.nodeData.uid)
      if (cu) snap[cu] = { p: (n.uid || (n.nodeData && n.nodeData.uid)) || '', i }
      walk(c)
    })
  }
  walk(rn)
  return snap
}
function toDataStruct(n) {
  if (!n) return null
  return { data: n.nodeData || {}, children: (n.children || []).map(toDataStruct) }
}
function broadcastStructDelta() {
  try {
    if (!ws || ws.readyState !== 1 || !mind.value || !mind.value.renderer) return
    const ns = snapshotStruct()
    const old = structSnap || {}
    const mm = String(mindMapId.value)
    // 删除
    for (const uid in old) if (!ns[uid]) sendWs({ type: 'node_removed', mindMapId: mm, nodeId: uid })
    // 新增
    for (const uid in ns) if (!old[uid]) {
      const node = mind.value.renderer.findNodeByUid(uid)
      if (!node) continue
      sendWs({ type: 'node_added', mindMapId: mm, nodeId: uid, parentId: ns[uid].p || '', index: ns[uid].i, nodeData: node.nodeData || {}, children: (node.children || []).map(toDataStruct) })
    }
    // 移动（父节点或同级索引变化）
    for (const uid in old) if (ns[uid] && (ns[uid].p !== old[uid].p || ns[uid].i !== old[uid].i)) {
      sendWs({ type: 'node_moved', mindMapId: mm, nodeId: uid, parentId: ns[uid].p || '', index: ns[uid].i })
    }
    structSnap = ns
  } catch (e) { console.warn('broadcastStructDelta err', e) }
}
function applyRemoteNodeAdded(msg) {
  try {
    if (myEditNodeId === msg.nodeId) return
    if (mind.value.renderer.findNodeByUid(msg.nodeId)) return
    const parentNode = msg.parentId ? mind.value.renderer.findNodeByUid(msg.parentId) : null
    if (!parentNode) return // 父节点尚未同步：交给保存广播全量兜底
    const nd = msg.nodeData || {}
    if (!nd.uid) nd.uid = msg.nodeId
    if (!nd.creatorUid) nd.creatorUid = Number(msg.userId) || 0
    wsApplying = true
    mapEl.value.classList.add('no-anim')
    mind.value.renderer.insertChildNode(false, [parentNode], nd, msg.children || [])
    mapEl.value.classList.remove('no-anim')
    wsApplying = false
    suppressDirtyUntil = Date.now() + 1500
    try { canvasTabs.value[docIndex.value].data = mind.value.getData() } catch (e) {}
  } catch (e) { wsApplying = false; try { mapEl.value.classList.remove('no-anim') } catch (e2) {} }
}
function applyRemoteNodeRemoved(nid) {
  try {
    if (myEditNodeId === nid) return
    const node = mind.value.renderer.findNodeByUid(nid)
    if (!node || !node.parent) return
    wsApplying = true
    mapEl.value.classList.add('no-anim')
    mind.value.renderer.removeNode(node)
    mapEl.value.classList.remove('no-anim')
    wsApplying = false
    suppressDirtyUntil = Date.now() + 1500
    try { canvasTabs.value[docIndex.value].data = mind.value.getData() } catch (e) {}
  } catch (e) { wsApplying = false; try { mapEl.value.classList.remove('no-anim') } catch (e2) {} }
}
function applyRemoteNodeMoved(nid, parentId) {
  try {
    if (myEditNodeId === nid) return
    const node = mind.value.renderer.findNodeByUid(nid)
    const parent = parentId ? mind.value.renderer.findNodeByUid(parentId) : null
    if (!node || !parent || node.isRoot || node.parent === parent) return
    wsApplying = true
    mapEl.value.classList.add('no-anim')
    // 从原父节点摘除
    const oldParent = node.parent
    if (oldParent && oldParent.children) oldParent.children = oldParent.children.filter(c => c !== node)
    // 挂到目标父节点（追加末尾；同级精确顺序由保存广播校准）
    if (!parent.nodeData.children) parent.nodeData.children = []
    const exists = parent.nodeData.children.some(c => (c.uid || (c.nodeData && c.nodeData.uid)) === nid)
    if (!exists) parent.nodeData.children.push(node)
    try { mind.value.reRender() } catch (e) { try { mind.value.renderer.renderNode(parent) } catch (e2) {} }
    mapEl.value.classList.remove('no-anim')
    wsApplying = false
    suppressDirtyUntil = Date.now() + 1500
    try { canvasTabs.value[docIndex.value].data = mind.value.getData() } catch (e) {}
  } catch (e) { wsApplying = false; try { mapEl.value.classList.remove('no-anim') } catch (e2) {} }
}

function applyRemoteLock(nid, action, userId, userName) {'''
assert s.count(old3) == 1, 'anchor3'
s = s.replace(old3, new3)

# ---- 块4: loadDoc setData 完成后刷新结构快照 ----
old4 = '''  }
  applyBranchRainbow()
  markSaved()
  clearTimeout(autoSaveTimer)'''
new4 = '''  }
  applyBranchRainbow()
  try { structSnap = snapshotStruct() } catch (e) {}
  markSaved()
  clearTimeout(autoSaveTimer)'''
assert s.count(old4) == 1, 'anchor4'
s = s.replace(old4, new4)

# ---- 块5: diffApplyLatest 应用完后刷新结构快照 ----
old5 = '''    suppressDirtyUntil = Date.now() + 3000
    try { canvasTabs.value[docIndex.value].data = mind.value.getData() } catch (e) {}
  } catch (e) { console.warn('diffApplyLatest err', e) }
}'''
new5 = '''    suppressDirtyUntil = Date.now() + 3000
    try { canvasTabs.value[docIndex.value].data = mind.value.getData() } catch (e) {}
    try { structSnap = snapshotStruct() } catch (e) {}
  } catch (e) { console.warn('diffApplyLatest err', e) }
}'''
assert s.count(old5) == 1, 'anchor5'
s = s.replace(old5, new5)

io.open(P, 'w', encoding='utf-8', newline='\n').write(s)
print('Editor.vue 全部替换 OK')
