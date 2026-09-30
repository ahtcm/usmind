# -*- coding: utf-8 -*-
import io
p = r'C:\Users\25750\Desktop\usmind\code\web-user\src\views\Editor.vue'
s = io.open(p, encoding='utf-8').read()
old = "mind.value.render && mind.value.renderer.getRoot()"
new = "mind.value.renderer && mind.value.renderer.root"
n = s.count(old)
assert n == 2, 'count=' + str(n)
s = s.replace(old, new)
io.open(p, 'w', encoding='utf-8', newline='').write(s)
print('ROOT API FIX OK x' + str(n))
