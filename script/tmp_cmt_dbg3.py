# -*- coding: utf-8 -*-
"""打印 getData 结构"""
import io
P = r'C:\Users\25750\Desktop\usmind\code\web-user\src\views\Editor.vue'
s = io.open(P, encoding='utf-8').read()
old = '''      if (uid === '7c9bdffb-5519-47f9-b04a-d0cffc1821ff') console.log('[cmt-dbg2] data?', !!data, 'root?', !!r, 'rootUid', r && r.data ? r.data.uid : null)'''
new = '''      if (uid === '7c9bdffb-5519-47f9-b04a-d0cffc1821ff') console.log('[cmt-dbg2] data?', !!data, 'keys', data ? Object.keys(data).slice(0,10) : null, 'root?', !!r, 'rootKeys', r ? Object.keys(r).slice(0,8) : null, 'dataKeys', data && data.data ? Object.keys(data.data).slice(0,8) : null)'''
assert s.count(old) == 1, 'dbg3'
s = s.replace(old, new)
io.open(P, 'w', encoding='utf-8', newline='\n').write(s)
print('dbg3 OK')
