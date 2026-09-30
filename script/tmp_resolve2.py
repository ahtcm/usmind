# -*- coding: utf-8 -*-
import io
p = r'C:\Users\25750\Desktop\usmind\code\web-user\src\views\Editor.vue'
s = io.open(p, encoding='utf-8').read()

old = """  if (choice === 'load') {
    const remoteText = c.remoteText == null ? '' : c.remoteText
    try {
      // 先把编辑框内容替换为远端文本再关闭，避免编辑框提交旧值覆盖
      const el = document.querySelector('.smm-node-edit-wrap [contenteditable=true], .smm-text-edit')
      if (el) { el.textContent = remoteText }
    } catch (e) {}
    try {
      const node = mind.value.renderer.findNodeByUid(c.nodeId)
      if (node) { wsApplying = true; mind.value.renderer.setNodeText(node, remoteText); wsApplying = false }
    } catch (e) { wsApplying = false }
    try { mind.value.renderer.textEdit.hideEditTextBox() } catch (e) {}
    try { canvasTabs.value[docIndex.value].data = mind.value.getData() } catch (e) {}
    toast('已加载最新内容')
  }"""
new = """  if (choice === 'load') {
    const remoteText = c.remoteText == null ? '' : c.remoteText
    try {
      const te = mind.value.renderer.textEdit
      if (te && te.textEditNode) {
        te.textEditNode.textContent = remoteText
        if (te.showTextEdit) { te.hideEditTextBox(); setTimeout(() => { try { if (te.showTextEdit) te.hideEditTextBox() } catch (e2) {} }, 0) }
      }
    } catch (e) {}
    try {
      const node = mind.value.renderer.findNodeByUid(c.nodeId)
      if (node) {
        wsApplying = true
        node.nodeData.text = remoteText
        delete node.nodeData.richText
        mind.value.renderer.renderNode(node)
        wsApplying = false
      }
    } catch (e) { wsApplying = false }
    // 兜底：若编辑框仍未关闭，强制移除并复位状态
    setTimeout(() => {
      try {
        const te = mind.value.renderer.textEdit
        if (te && te.textEditNode && te.textEditNode.style && te.textEditNode.style.display !== 'none') {
          te.textEditNode.style.display = 'none'
          te.textEditNode.innerHTML = ''
          te.showTextEdit = false
          te.currentNode = null
          textEditActive.value = false
          releaseNodeLock(true)
        }
      } catch (e3) {}
    }, 50)
    try { canvasTabs.value[docIndex.value].data = mind.value.getData() } catch (e) {}
    toast('已加载最新内容')
  }"""
assert s.count(old) == 1, 'c=' + str(s.count(old))
s = s.replace(old, new)
io.open(p, 'w', encoding='utf-8', newline='').write(s)
print('RESOLVE LOAD FULL FIX OK')
