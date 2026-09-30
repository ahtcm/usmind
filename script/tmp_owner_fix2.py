# -*- coding: utf-8 -*-
import io
p = r'C:\Users\25750\Desktop\usmind\code\web-user\src\views\Editor.vue'
s = io.open(p, encoding='utf-8').read()
old = "  try {\n    mapOwnerId.value = Number(doc.userId) || 0\n    fetch('/api/mindmaps/' + mindMapId.value + '/collab-config', { headers: { 'Authorization': 'Bearer ' + (localStorage.getItem('usmind.token') || '') } })"
new = "  try {\n    fetch('/api/mindmaps/' + mindMapId.value + '/collab-config', { headers: { 'Authorization': 'Bearer ' + (localStorage.getItem('usmind.token') || '') } })"
assert s.count(old) == 1, 'count=' + str(s.count(old))
s = s.replace(old, new)
io.open(p, 'w', encoding='utf-8', newline='').write(s)
print('CREATE_MIND OWNER FIX OK')
