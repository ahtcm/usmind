# -*- coding: utf-8 -*-
import io
p = r'C:\Users\25750\Desktop\usmind\code\web-user\src\views\Editor.vue'
with io.open(p, 'r', encoding='utf-8') as f:
    s = f.read()

# 1. 删掉无效的 node_add 监听（引擎无此事件）
old1 = """  mind.value.on('node_attachmentClick', (node) => openAttachmentPreview(node))
  mind.value.on('node_add', (node) => { try { if (node && node.nodeData && !node.nodeData.creatorUid) node.nodeData.creatorUid = myUid() } catch (e) {} })"""
new1 = """  mind.value.on('node_attachmentClick', (node) => openAttachmentPreview(node))"""
assert s.count(old1) == 1, 'old1'
s = s.replace(old1, new1)

# 2. afterExecCommand：INSERT 命令后给新节点补 creatorUid（历史节点已由后端补过）
old2 = """  mind.value.on('afterExecCommand', (name) => {
    if (STRUCT_CHANGE_CMDS.indexOf(name) >= 0) scheduleStructureSave()
  })"""
new2 = """  mind.value.on('afterExecCommand', (name) => {
    if (STRUCT_CHANGE_CMDS.indexOf(name) >= 0) scheduleStructureSave()
    // 新建节点标记创建者：后端 get 时已给历史节点补 creatorUid（导图创建者），
    // 这里只给仍无创建者的节点（即本次新插入的节点）补上当前用户
    if (name && name.indexOf('INSERT') === 0) {
      try {
        const rn = mind.value.render && mind.value.render.getRoot()
        if (rn) {
          const walk = (n) => {
            if (n && n.nodeData && !n.nodeData.creatorUid) n.nodeData.creatorUid = myUid()
            if (n.children) n.children.forEach(walk)
          }
          walk(rn)
        }
      } catch (e) {}
    }
  })"""
assert s.count(old2) == 1, 'old2'
s = s.replace(old2, new2)

# 3. pushNodeChange 兜底
old3 = """function pushNodeChange(node, text) {
  if (!ws || ws.readyState !== 1) return
  const nid = node && (node.uid || (node.nodeData && node.nodeData.uid))
  if (!nid) return
  if (nodeChangeTimer) clearTimeout(nodeChangeTimer)"""
new3 = """function pushNodeChange(node, text) {
  if (!ws || ws.readyState !== 1) return
  const nid = node && (node.uid || (node.nodeData && node.nodeData.uid))
  if (!nid) return
  try { if (node.nodeData && !node.nodeData.creatorUid) node.nodeData.creatorUid = myUid() } catch (e) {}
  if (nodeChangeTimer) clearTimeout(nodeChangeTimer)"""
assert s.count(old3) == 1, 'old3'
s = s.replace(old3, new3)

with io.open(p, 'w', encoding='utf-8', newline='') as f:
    f.write(s)
print('EDITOR FIX OK')
