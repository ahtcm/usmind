# -*- coding: utf-8 -*-
import io
p = r'C:\Users\25750\Desktop\usmind\code\web-user\src\views\Editor.vue'
s = io.open(p, encoding='utf-8').read()
old = """      // 文本变更
      const rtext = textOf(rd)
      if (rtext !== undefined && localNode.nodeData.text !== rtext) {
        if (myEditNodeId === uid) {"""
new = """      // 文本变更
      const rtext = textOf(rd)
      if (rtext !== undefined && localNode.nodeData.text !== rtext) {
        console.warn('[diff] text change uid=', uid, 'myEdit=', myEditNodeId, 'old=', localNode.nodeData.text, 'new=', rtext)
        if (myEditNodeId === uid) {"""
assert s.count(old) == 1, 'c=' + str(s.count(old))
s = s.replace(old, new)
io.open(p, 'w', encoding='utf-8', newline='').write(s)
print('LOG OK')
