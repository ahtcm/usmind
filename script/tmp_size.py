# -*- coding: utf-8 -*-
p = r'C:\Users\25750\Desktop\usmind\code\web\src\mediastore.js'
s = open(p, encoding='utf-8').read()
old = "const MAX_DISPLAY = 240 // 图片节点内展示最长边"
new = "const MAX_DISPLAY = 210 // 图片节点内展示最长边(缩小适配节点框)"
if old in s:
    s = s.replace(old, new, 1)
    open(p, 'w', encoding='utf-8', newline='\n').write(s)
    print('mediastore OK')
else:
    print('mediastore pattern not found')
