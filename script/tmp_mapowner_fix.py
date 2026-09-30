# -*- coding: utf-8 -*-
import io
p = r'C:\Users\25750\Desktop\usmind\code\web-user\src\views\Editor.vue'
s = io.open(p, encoding='utf-8').read()
# 打开导图主路径：设置导图创建者 uid（之前仅 createMind 新建时设置，打开已有导图恒为 0 导致创建者也被拦截）
old = "  curVersion.value = data.version || 0\n"
new = "  curVersion.value = data.version || 0\n  mapOwnerId.value = Number(data.userId) || 0\n"
assert s.count(old) == 1, 'count=' + str(s.count(old))
s = s.replace(old, new)
# loadLatest / diffApplyLatest 同样补（fresh.userId）
old2 = "    curVersion.value = fresh.version || 0\n    suppressDirtyUntil = Date.now() + 8000\n    await loadDoc(silent)"
new2 = "    curVersion.value = fresh.version || 0\n    mapOwnerId.value = Number(fresh.userId) || 0\n    suppressDirtyUntil = Date.now() + 8000\n    await loadDoc(silent)"
assert s.count(old2) == 1, 'count2=' + str(s.count(old2))
s = s.replace(old2, new2)
old3 = "    curVersion.value = fresh.version || 0\n    let d = fresh.data || {}"
new3 = "    curVersion.value = fresh.version || 0\n    mapOwnerId.value = Number(fresh.userId) || 0\n    let d = fresh.data || {}"
assert s.count(old3) == 1, 'count3=' + str(s.count(old3))
s = s.replace(old3, new3)
io.open(p, 'w', encoding='utf-8', newline='').write(s)
print('MAPOWNER FIX OK')
