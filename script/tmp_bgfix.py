# -*- coding: utf-8 -*-
import io
p = r'C:\Users\25750\Desktop\usmind\code\web-user\src\views\Editor.vue'
s = io.open(p, encoding='utf-8').read()

old = """.canvas-bg { position: absolute; inset: 0; overflow: hidden; }
.canvas-bg-color { position: absolute; inset: 0; }
.canvas-bg-image { position: absolute; inset: 0; background-size: cover; background-position: center; background-repeat: no-repeat; }
.canvas-bg-video { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; object-position: center center; pointer-events: none; z-index: 0; }
.canvas-bg-video { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; object-position: center center; pointer-events: none; z-index: 0; }"""
new = """.canvas-bg { position: absolute; inset: 0; overflow: hidden; pointer-events: none; z-index: 0; }
.canvas-bg-color { position: absolute; inset: 0; pointer-events: none; z-index: 0; }
.canvas-bg-image { position: absolute; inset: 0; background-size: cover; background-position: center; background-repeat: no-repeat; pointer-events: none; z-index: 0; }
.canvas-bg-video { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; object-position: center center; pointer-events: none; z-index: 0; }
.smm-mind-map-container { position: relative; z-index: 1; }"""
assert s.count(old) == 1, 'c=' + str(s.count(old))
s = s.replace(old, new)
io.open(p, 'w', encoding='utf-8', newline='').write(s)
print('BG LAYER FIX OK')
