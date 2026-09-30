# -*- coding: utf-8 -*-
import io
p = r'C:\Users\25750\Desktop\usmind\code\web-user\src\views\Editor.vue'
s = io.open(p, encoding='utf-8').read()
old = "mind.value.render.getRoot"
new = "mind.value.renderer.getRoot"
n = s.count(old)
assert n == 2, 'count=' + str(n)
s = s.replace(old, new)
io.open(p, 'w', encoding='utf-8', newline='').write(s)
print('RENDER FIX OK x' + str(n))
