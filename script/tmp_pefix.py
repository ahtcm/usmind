# -*- coding: utf-8 -*-
import io
p = r'C:\Users\25750\Desktop\usmind\code\web-user\src\views\Editor.vue'
s = io.open(p, encoding='utf-8').read()

old = ".smm-mind-map-container { position: relative; z-index: 1; }"
new = ".smm-mind-map-container { position: relative; z-index: 1; pointer-events: auto; }"
assert s.count(old) == 1, 'c=' + str(s.count(old))
s = s.replace(old, new)
io.open(p, 'w', encoding='utf-8', newline='').write(s)
print('PE FIX OK')
