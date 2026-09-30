# -*- coding: utf-8 -*-
import io
p = r'C:\Users\25750\Desktop\usmind\code\web-user\src\views\ShareView.vue'
s = io.open(p, encoding='utf-8').read()

old = '''.cmt-author-tag { font-size: 11px; color: #fff; background: #f59e0b; border-radius: 4px; padding: 1px 5px; }'''
new = '''.cmt-author-tag { font-size: 11px; color: #fff; background: #f59e0b; border-radius: 4px; padding: 1px 5px; }
.cmt-src { display: inline-block; font-size: 10px; line-height: 16px; height: 16px; padding: 0 6px; border-radius: 4px; font-weight: 600; }
.cmt-src-share { background: rgba(74,92,247,.18); color: #9fb0ff; }
.cmt-src-org { background: rgba(14,124,134,.18); color: #3ec5cf; }'''
assert s.count(old) == 1, 'style=' + str(s.count(old))
s = s.replace(old, new)

io.open(p, 'w', encoding='utf-8', newline='').write(s)
print('SHAREVIEW STYLE OK')
