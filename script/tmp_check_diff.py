# -*- coding: utf-8 -*-
"""Editor.vue: diffApplyLatest 只补缺不删多余——本地多余节点（刚新增未保存/未同步）保留，防止被对方旧版本全量同步覆盖掉"""
import io
P = r'C:\Users\25750\Desktop\usmind\code\web-user\src\views\Editor.vue'
s = io.open(P, encoding='utf-8').read()

old = '''    // 远端没有而本地有的：只有本地明确删除的（deletedUids）才移除，其余保留
    // （防止对方旧版本全量同步时把自己刚新增但尚未保存的节点删掉）
    const toRemove = []
    for (const uid in localMap) if (!remoteMap[uid]) {
      if (deletedUids.has(uid)) toRemove.push(uid)
    }
    toRemove.forEach(uid => { const n = mind.value.renderer.findNodeByUid(uid); if (n) removeNode(n) })'''
new = '''    // 远端没有而本地有的：只有本地明确删除的（deletedUids）才移除，其余保留
    // （防止对方旧版本全量同步时把自己刚新增但尚未保存的节点删掉）
    const toRemove = []
    for (const uid in localMap) if (!remoteMap[uid]) {
      if (deletedUids.has(uid)) toRemove.push(uid)
    }
    toRemove.forEach(uid => { const n = mind.value.renderer.findNodeByUid(uid); if (n) removeNode(n) })'''
if s.count(old) == 1:
    # 已经是目标代码，跳过
    print('already patched')
else:
    # 找当前实际代码
    import re
    m = re.search(r'    // 远端没有而本地有的[^\n]*\n(?:.*\n){0,6}', s)
    if m:
        print('当前代码段:', m.group(0)[:300])
    raise SystemExit('anchor mismatch')
