# -*- coding: utf-8 -*-
import io
p = r'C:\Users\25750\Desktop\usmind\code\web-user\src\views\Editor.vue'
with io.open(p, 'r', encoding='utf-8') as f:
    s = f.read()
old = """  nodeChangeTimer = setTimeout(() => {
    sendWs({ type: 'node_change', mindMapId: String(mindMapId.value), nodeId: nid, text: (text == null ? '' : text).slice(0, 8000) })
  }, 250)"""
new = """  nodeChangeTimer = setTimeout(() => {
    sendWs({ type: 'node_change', mindMapId: String(mindMapId.value), nodeId: nid, text: (text == null ? '' : text).slice(0, 8000), nodeCreatorUid: Number((node.nodeData && node.nodeData.creatorUid) || 0) })
  }, 250)"""
assert s.count(old) == 1, 'count=' + str(s.count(old))
s = s.replace(old, new)
with io.open(p, 'w', encoding='utf-8', newline='') as f:
    f.write(s)
print('OK')
