# -*- coding: utf-8 -*-
import io
p = r'C:\Users\25750\Desktop\usmind\code\web-user\src\views\Editor.vue'
s = io.open(p, encoding='utf-8').read()

old = """    if (!remoteRoot || !(remoteRoot.uid || (remoteRoot.data && remoteRoot.data.uid))) return
    const localRoot = mind.value.renderer && mind.value.renderer.root
    if (!localRoot) return
    const localMap = {}
    const depthOf = {}
    const walkLocal = (n, dep) => { if (!n || !n.uid) return; localMap[n.uid] = n; depthOf[n.uid] = dep; (n.children || []).forEach(c => walkLocal(c, dep + 1)) }
    walkLocal(localRoot, 0)
    const remoteUids = new Set()
    const uidOf = (n) => (n && (n.uid || (n.data && n.data.uid))) || ''
    const textOf = (n) => (n && (n.text !== undefined ? n.text : (n.data && n.data.text)))
    const walkRemote = (n) => { if (!n) return; const u = uidOf(n); if (u) remoteUids.add(u); (n.children || []).forEach(walkRemote) }
    walkRemote(remoteRoot)"""
new = """    if (!remoteRoot || !(remoteRoot.uid || (remoteRoot.data && remoteRoot.data.uid))) return
    const uidOf = (n) => (n && (n.uid || (n.data && n.data.uid))) || ''
    const textOf = (n) => (n && (n.text !== undefined ? n.text : (n.data && n.data.text)))
    const findRemoteByUid = (n, uid) => {
      if (!n) return null
      if (uidOf(n) === uid) return n
      for (const c of (n.children || [])) { const f = findRemoteByUid(c, uid); if (f) return f }
      return null
    }
    // 编辑中：不做树 diff（避免破坏正在编辑的本地树），只检测自己正在编辑的节点是否被远端改动→弹增强冲突框（带对方内容）
    if (myEditNodeId) {
      try {
        const myNode = mind.value.renderer && mind.value.renderer.findNodeByUid(myEditNodeId)
        if (myNode) {
          const rd = findRemoteByUid(remoteRoot, myEditNodeId)
          if (rd) {
            const rtext = textOf(rd)
            if (rtext !== undefined && myNode.nodeData.text !== rtext && !editConflict.value) {
              editConflict.value = { nodeId: myEditNodeId, remoteText: rtext == null ? '' : rtext }
            }
          }
        }
      } catch (e) {}
      return
    }
    const localRoot = mind.value.renderer && mind.value.renderer.root
    if (!localRoot) return
    const localMap = {}
    const depthOf = {}
    const walkLocal = (n, dep) => { if (!n || !n.uid) return; localMap[n.uid] = n; depthOf[n.uid] = dep; (n.children || []).forEach(c => walkLocal(c, dep + 1)) }
    walkLocal(localRoot, 0)
    const remoteUids = new Set()
    const walkRemote = (n) => { if (!n) return; const u = uidOf(n); if (u) remoteUids.add(u); (n.children || []).forEach(walkRemote) }
    walkRemote(remoteRoot)"""
assert s.count(old) == 1, 'c1=' + str(s.count(old))
s = s.replace(old, new)

# 移除临时调试日志
old2 = """        console.warn('[diff] text change uid=', uid, 'myEdit=', myEditNodeId, 'old=', localNode.nodeData.text, 'new=', rtext)
        if (myEditNodeId === uid) {"""
new2 = """        if (myEditNodeId === uid) {"""
assert s.count(old2) == 1, 'c2=' + str(s.count(old2))
s = s.replace(old2, new2)

io.open(p, 'w', encoding='utf-8', newline='').write(s)
print('EDITING GUARD OK')
