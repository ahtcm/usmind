# -*- coding: utf-8 -*-
import io
p = r'C:\Users\25750\Desktop\usmind\code\web-user\src\views\Editor.vue'
s = io.open(p, encoding='utf-8').read()

old = """  if (choice === 'load') {
    try {
      const node = mind.value.renderer.findNodeByUid(c.nodeId)
      if (node) { wsApplying = true; mind.value.renderer.setNodeText(node, c.remoteText == null ? '' : c.remoteText); wsApplying = false }
    } catch (e) { wsApplying = false }
    try { canvasTabs.value[docIndex.value].data = mind.value.getData() } catch (e) {}
    toast('已加载最新内容')
  }"""
new = """  if (choice === 'load') {
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
assert s.count(old) == 1, 'c=' + str(s.count(old))
s = s.replace(old, new)
io.open(p, 'w', encoding='utf-8', newline='').write(s)
print('RESOLVE FIX OK')
