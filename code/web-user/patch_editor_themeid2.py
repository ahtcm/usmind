# -*- coding: utf-8 -*-
# 修复 Editor.vue 主题会员拦截：用主题 id 而非 themeKey
import io, sys

p = r'C:\Users\25750\Desktop\usmind\code\web-user\src\views\Editor.vue'
s = io.open(p, encoding='utf-8').read()

# 1) item 增加 id 字段
old1 = """        const item = { name: t.name, key: t.themeKey || ('theme-' + t.id), branchColors: t.branchColors || [], config: cfg }"""
new1 = """        const item = { name: t.name, id: t.id, key: t.themeKey || ('theme-' + t.id), branchColors: t.branchColors || [], config: cfg }"""
if old1 not in s:
    print('P1 NOT FOUND'); sys.exit(1)
s = s.replace(old1, new1, 1)

# 2) checkMemberFeature 用 id
old2 = """    // 主题名 -> 主题ID 映射（后台配置为 ID 列表）
    const item = themes.value.find(t => t.name === value)
    const key = item ? String(item.key || '') : ''
    const tid = key.startsWith('theme-') ? key.slice(6) : key
    if (!ids.includes(tid) && !ids.includes(value)) return true"""
new2 = """    // 主题名 -> 主题ID 映射（后台配置为 ID 列表）
    const item = themes.value.find(t => t.name === value)
    const tid = item && item.id != null ? String(item.id) : ''
    if (!ids.includes(tid) && !ids.includes(value)) return true"""
if old2 not in s:
    print('P2 NOT FOUND'); sys.exit(1)
s = s.replace(old2, new2, 1)

io.open(p, 'w', encoding='utf-8', newline='\n').write(s)
print('PATCH OK')
