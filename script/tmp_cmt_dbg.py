# -*- coding: utf-8 -*-
"""临时调试：cmtAnchorOf 折叠路径日志"""
import io
P = r'C:\Users\25750\Desktop\usmind\code\web-user\src\views\Editor.vue'
s = io.open(P, encoding='utf-8').read()
old = '''      const anchor = cmtAnchorOf(uid)
      if (!anchor) return'''
new = '''      const anchor = cmtAnchorOf(uid)
      console.log('[cmt-dbg]', uid, 'cnt', cnt, 'anchorText', anchor && anchor.node && anchor.node.nodeData ? anchor.node.nodeData.text : (anchor ? 'ANCHOR' : 'NULL'))
      if (!anchor) return'''
assert s.count(old) == 1, 'dbg'
s = s.replace(old, new)
io.open(P, 'w', encoding='utf-8', newline='\n').write(s)
print('dbg OK')
