# -*- coding: utf-8 -*-
"""Editor.vue: 远端结构增量应用前 flush 当前编辑内容，防止 insertChildNode 的 hideEditTextBox 吞掉正在输入的文字"""
import io
P = r'C:\Users\25750\Desktop\usmind\code\web-user\src\views\Editor.vue'
s = io.open(P, encoding='utf-8').read()

old1 = '''function applyRemoteNodeAdded(msg) {
  try {
    if (myEditNodeId === msg.nodeId) return'''
new1 = '''// 远端结构变更会触发库的 hideEditTextBox：先把正在编辑的内容 flush 到节点并立即推送，防止输入内容被吞
function flushMyEdit() {
  try {
    if (!myEditNodeId) return
    const box = document.querySelector('.smm-node-edit-wrap, .smm-text-edit, [contenteditable=true]')
    const node = mind.value.renderer.findNodeByUid(myEditNodeId)
    if (!box || !node) return
    const t = (box.textContent || '')
    if (node.nodeData.text !== t) {
      node.nodeData.text = t
      delete node.nodeData.richText
      try { mind.value.renderer.renderNode(node) } catch (e) {}
    }
    // 立即推送（不走 250ms 防抖，避免末尾编辑内容丢失）
    sendWs({ type: 'node_change', mindMapId: String(mindMapId.value), nodeId: myEditNodeId, text: t.slice(0, 8000), nodeCreatorUid: Number((node.nodeData && node.nodeData.creatorUid) || 0) })
  } catch (e) {}
}
function applyRemoteNodeAdded(msg) {
  try {
    flushMyEdit()
    if (myEditNodeId === msg.nodeId) return'''
assert s.count(old1) == 1, 'anchor1'
s = s.replace(old1, new1)

old2 = '''function applyRemoteNodeRemoved(nid) {
  try {
    if (myEditNodeId === nid) return'''
new2 = '''function applyRemoteNodeRemoved(nid) {
  try {
    flushMyEdit()
    if (myEditNodeId === nid) return'''
assert s.count(old2) == 1, 'anchor2'
s = s.replace(old2, new2)

old3 = '''function applyRemoteNodeMoved(nid, parentId) {
  try {
    if (myEditNodeId === nid) return'''
new3 = '''function applyRemoteNodeMoved(nid, parentId) {
  try {
    flushMyEdit()
    if (myEditNodeId === nid) return'''
assert s.count(old3) == 1, 'anchor3'
s = s.replace(old3, new3)

io.open(P, 'w', encoding='utf-8', newline='\n').write(s)
print('flushMyEdit OK')
