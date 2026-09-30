# -*- coding: utf-8 -*-
"""Editor.vue: diffApplyLatest 删除分支只删 deletedUids 明确的 + 新增分支防复活"""
import io
P = r'C:\Users\25750\Desktop\usmind\code\web-user\src\views\Editor.vue'
s = io.open(P, encoding='utf-8').read()

old1 = '''    // 1. 删除本地多余节点（先删深层）
    const toRemove = Object.keys(localMap)
      .filter(uid => !remoteUids.has(uid) && localMap[uid].parent)
      .sort((a, b) => (depthOf[b] || 0) - (depthOf[a] || 0))
      .map(uid => localMap[uid])'''
new1 = '''    // 1. 删除本地多余节点（先删深层）：只删本地明确删除（deletedUids）的，其余保留
    //    （防止对方旧版本全量同步时把自己刚新增但尚未保存的节点删掉）
    const toRemove = Object.keys(localMap)
      .filter(uid => !remoteUids.has(uid) && localMap[uid].parent && deletedUids.has(uid))
      .sort((a, b) => (depthOf[b] || 0) - (depthOf[a] || 0))
      .map(uid => localMap[uid])'''
assert s.count(old1) == 1, 'anchor1'
s = s.replace(old1, new1)

old2 = '''      let localNode = localMap[uid]
      if (!localNode) {'''
new2 = '''      // 本地明确删除过的节点不复活（防对方旧版本带回来）
      if (deletedUids.has(uid)) return
      let localNode = localMap[uid]
      if (!localNode) {'''
assert s.count(old2) == 1, 'anchor2'
s = s.replace(old2, new2)

io.open(P, 'w', encoding='utf-8', newline='\n').write(s)
print('diffApplyLatest 保护 OK')
