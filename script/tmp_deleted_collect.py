# -*- coding: utf-8 -*-
"""Editor.vue: 删除节点时收集子树 uid 到 deletedUids（配合后端并发合并防止删除被对方旧版本复活）"""
import io
P = r'C:\Users\25750\Desktop\usmind\code\web-user\src\views\Editor.vue'
s = io.open(P, encoding='utf-8').read()

old1 = '''    // 删除
    for (const uid in old) if (!ns[uid]) sendWs({ type: 'node_removed', mindMapId: mm, nodeId: uid })'''
new1 = '''    // 删除：广播 + 收集被删子树 uid（含后代）到 deletedUids，防止并发合并时被对方旧版本"复活"
    for (const uid in old) if (!ns[uid]) {
      sendWs({ type: 'node_removed', mindMapId: mm, nodeId: uid })
      collectDeletedDesc(uid, old)
    }'''
assert s.count(old1) == 1, 'anchor1'
s = s.replace(old1, new1)

old2 = '''function toDataStruct(n) {
  if (!n) return null
  return { data: n.nodeData || {}, children: (n.children || []).map(toDataStruct) }
}'''
new2 = '''function toDataStruct(n) {
  if (!n) return null
  return { data: n.nodeData || {}, children: (n.children || []).map(toDataStruct) }
}
function collectDeletedDesc(uid, old) {
  try {
    deletedUids.add(uid)
    for (const u2 in old) if (old[u2].p === uid) collectDeletedDesc(u2, old)
  } catch (e) {}
}'''
assert s.count(old2) == 1, 'anchor2'
s = s.replace(old2, new2)

io.open(P, 'w', encoding='utf-8', newline='\n').write(s)
print('deletedUids 收集 OK')
