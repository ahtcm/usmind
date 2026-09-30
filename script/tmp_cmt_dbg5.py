# -*- coding: utf-8 -*-
"""打印 data.children 顶层结构"""
import io
P = r'C:\Users\25750\Desktop\usmind\code\web-user\src\views\Editor.vue'
s = io.open(P, encoding='utf-8').read()
old = '''        if (data && data.data) { const d = data.data; console.log('[cmt-dbg4] rootUid', d.uid, 'rootText', d.text, 'childCount', d.children ? d.children.length : -1) }'''
new = '''        if (data && data.data) { const d = data.data; console.log('[cmt-dbg4] rootUid', d.uid, 'rootText', d.text, 'childCount', d.children ? d.children.length : -1) }
        if (data && data.children) console.log('[cmt-dbg5] topChildrenLen', data.children.length, 'first', data.children[0] && data.children[0].data ? data.children[0].data.text : null)'''
assert s.count(old) == 1, 'dbg5'
s = s.replace(old, new)
io.open(P, 'w', encoding='utf-8', newline='\n').write(s)
print('dbg5 OK')
