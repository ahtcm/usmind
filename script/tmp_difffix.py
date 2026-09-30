# -*- coding: utf-8 -*-
import io
p = r'C:\Users\25750\Desktop\usmind\code\web-user\src\views\Editor.vue'
s = io.open(p, encoding='utf-8').read()

# 1) remoteRoot 解析：标准结构为 { data:{uid,text,...}, children:[...] }，uid 在 data 层
old = """    let remoteRoot = (d.root && d.root.data) || d.root || d
    if (d._tabs && Array.isArray(d._tabs) && d._tabs.length) {
      const active = d._tabs[d._activeTab || 0]
      if (active && active.data && active.data.root) remoteRoot = active.data.root
    }
    if (!remoteRoot || !remoteRoot.uid) return"""
new = """    let remoteRoot = (d.root && d.root.data && d.root.data.uid) ? d.root : ((d.root && d.root.data) ? { data: d.root.data, children: d.root.children || [] } : (d || {}))
    if (d._tabs && Array.isArray(d._tabs) && d._tabs.length) {
      const active = d._tabs[d._activeTab || 0]
      if (active && active.data && active.data.root) remoteRoot = active.data.root
    }
    if (!remoteRoot || !(remoteRoot.uid || (remoteRoot.data && remoteRoot.data.uid))) return"""
assert s.count(old) == 1, 'c1=' + str(s.count(old))
s = s.replace(old, new)

# 2) walkRemote / applyRemote 统一 uid 读取（data 层或顶层）
old = """    const remoteUids = new Set()
    const walkRemote = (n) => { if (!n) return; if (n.uid) remoteUids.add(n.uid); (n.children || []).forEach(walkRemote) }
    walkRemote(remoteRoot)"""
new = """    const remoteUids = new Set()
    const uidOf = (n) => (n && (n.uid || (n.data && n.data.uid))) || ''
    const textOf = (n) => (n && (n.text !== undefined ? n.text : (n.data && n.data.text)))
    const walkRemote = (n) => { if (!n) return; const u = uidOf(n); if (u) remoteUids.add(u); (n.children || []).forEach(walkRemote) }
    walkRemote(remoteRoot)"""
assert s.count(old) == 1, 'c2=' + str(s.count(old))
s = s.replace(old, new)

# 3) applyRemote 内部：uid 解析 + 新增节点传 data 结构 + 文本/creatorUid 读取
old = """    const applyRemote = (rd, parentUid) => {
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
    }"""
new = """    const applyRemote = (rd, parentUid) => {
      if (!rd) return
      const uid = uidOf(rd)
      if (!uid) return
      let localNode = localMap[uid]
      if (!localNode) {
        const parentNode = parentUid ? localMap[parentUid] : null
        if (parentNode) {
          try {
            // 引擎 insertChildNode 的 appointData 是节点 data 对象（含 uid/text/creatorUid）
            const nodeData = (rd.data && typeof rd.data === 'object') ? rd.data : { uid: uid, text: textOf(rd) || '' }
            const children = rd.children || []
            wsApplying = true
            mind.value.renderer.insertChildNode(false, [parentNode], JSON.parse(JSON.stringify(nodeData)), children)
            wsApplying = false
            const nn = mind.value.renderer.findNodeByUid(uid)
            if (nn) { localMap[uid] = nn; localNode = nn }
          } catch (e) { wsApplying = false }
        }
        if (!localNode) return
      }
      // 文本变更
      const rtext = textOf(rd)
      if (rtext !== undefined && localNode.nodeData.text !== rtext) {
        if (myEditNodeId === uid) {
          // 自己正在编辑的节点被改：弹增强冲突框（带对方内容），不静默覆盖
          if (!editConflict.value) {
            editConflict.value = { nodeId: uid, remoteText: rtext == null ? '' : rtext }
          }
        } else {
          wsApplying = true
          try { mind.value.renderer.setNodeText(localNode, rtext == null ? '' : rtext) } catch (e) {}
          wsApplying = false
        }
      }
      const rcreator = rd.creatorUid || (rd.data && rd.data.creatorUid)
      if (rcreator && !localNode.nodeData.creatorUid) localNode.nodeData.creatorUid = rcreator
      ;(rd.children || []).forEach(c => applyRemote(c, uid))
    }"""
assert s.count(old) == 1, 'c3=' + str(s.count(old))
s = s.replace(old, new)

io.open(p, 'w', encoding='utf-8', newline='').write(s)
print('DIFF FIX OK')
