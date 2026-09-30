# -*- coding: utf-8 -*-
import io
p = r'C:\Users\25750\Desktop\usmind\code\web-admin\src\views\MindMaps.vue'
s = io.open(p, encoding='utf-8').read()
old = "import { getMindmapList, getMindmapDetail, deleteMindmap, cancelShareMindmap, batchDeleteMindmaps } from '@/api/mindmaps'"
new = "import { getMindmapList, getMindmapDetail, deleteMindmap, cancelShareMindmap, batchDeleteMindmaps, getCollabConfig, updateCollabConfig } from '@/api/mindmaps'"
assert s.count(old) == 1
s = s.replace(old, new)
io.open(p, 'w', encoding='utf-8', newline='').write(s)
print('import OK')
