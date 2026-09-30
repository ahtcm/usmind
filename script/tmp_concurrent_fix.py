# -*- coding: utf-8 -*-
import io
p = r'C:\Users\25750\Desktop\usmind\code\backend\src\main\java\com\usmind\service\impl\MindMapServiceImpl.java'
s = io.open(p, encoding='utf-8').read()
old = '''        int cur = m.getVersion() == null ? 0 : m.getVersion();
        boolean force = req.getForce() != null && req.getForce();
        boolean concurrent = !force && req.getBaseVersion() != null && req.getBaseVersion() != cur;'''
new = '''        int cur = m.getVersion() == null ? 0 : m.getVersion();
        boolean force = req.getForce() != null && req.getForce();
        // 并发合并条件：版本不一致，或显式携带冲突解决方式（409 后重提时 baseVersion 可能已等于最新版本，仍需按 resolve 合并而非整树覆盖）
        boolean resolveFlag = req.getResolveConflicts() != null && !req.getResolveConflicts().isBlank();
        boolean concurrent = !force && (resolveFlag || (req.getBaseVersion() != null && req.getBaseVersion() != cur));'''
assert s.count(old) == 1, 'conc=' + str(s.count(old))
s = s.replace(old, new)
io.open(p, 'w', encoding='utf-8', newline='').write(s)
print('CONCURRENT FIX OK')
