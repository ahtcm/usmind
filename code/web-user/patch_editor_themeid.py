# -*- coding: utf-8 -*-
# 修复 Editor.vue 主题会员拦截：主题名 -> 主题ID 映射
import io, sys

p = r'C:\Users\25750\Desktop\usmind\code\web-user\src\views\Editor.vue'
s = io.open(p, encoding='utf-8').read()

old = """  if (kind === 'theme') {
    if (!cfg.memberThemeIds) return true
    const ids = String(cfg.memberThemeIds).split(',').map(x => x.trim())
    if (!ids.includes(value)) return true
  } else if (kind === 'export') {"""

new = """  if (kind === 'theme') {
    if (!cfg.memberThemeIds) return true
    const ids = String(cfg.memberThemeIds).split(',').map(x => x.trim())
    // 主题名 -> 主题ID 映射（后台配置为 ID 列表）
    const item = themes.value.find(t => t.name === value)
    const key = item ? String(item.key || '') : ''
    const tid = key.startsWith('theme-') ? key.slice(6) : key
    if (!ids.includes(tid) && !ids.includes(value)) return true
  } else if (kind === 'export') {"""

if old not in s:
    print('PATTERN NOT FOUND'); sys.exit(1)
s = s.replace(old, new, 1)
io.open(p, 'w', encoding='utf-8', newline='\n').write(s)
print('PATCH OK')
