# -*- coding: utf-8 -*-
# 修复 Editor.vue：memberFeature/checkMemberFeature/loadMemberFeature 移到 script setup 顶层
import io, sys

p = r'C:\Users\25750\Desktop\usmind\code\web-user\src\views\Editor.vue'
s = io.open(p, encoding='utf-8').read()

block = """let memberFeature = { memberThemeIds: '', memberExportFormats: '', memberLevel: 0 }
function checkMemberFeature(kind, value) {
  const cfg = memberFeature
  if (kind === 'theme') {
    if (!cfg.memberThemeIds) return true
    const ids = String(cfg.memberThemeIds).split(',').map(x => x.trim())
    // 主题名 -> 主题ID 映射（后台配置为 ID 列表）
    const item = themes.value.find(t => t.name === value)
    const tid = item && item.id != null ? String(item.id) : ''
    if (!ids.includes(tid) && !ids.includes(value)) return true
  } else if (kind === 'export') {
    if (!cfg.memberExportFormats) return true
    const fmts = String(cfg.memberExportFormats).split(',').map(x => x.trim())
    if (!fmts.includes(value)) return true
  }
  return cfg.memberLevel > 0
}
async function loadMemberFeature() {
  try {
    const c = await configApi.client()
    const f = c.feature || {}
    memberFeature.memberThemeIds = f.memberThemeIds || ''
    memberFeature.memberExportFormats = f.memberExportFormats || ''
    try {
      const m = await memberApi.info()
      memberFeature.memberLevel = m && m.level ? m.level : 0
    } catch (e) { memberFeature.memberLevel = 0 }
  } catch (e) { /* 忽略 */ }
}
"""

if block not in s:
    print('BLOCK NOT FOUND'); sys.exit(1)

# 1) 从 onMounted 内删除该块
s = s.replace(block, '', 1)

# 2) 在 "// 初始化" 之前插入顶层版本
anchor = "// 初始化\nonMounted(async () => {"
if anchor not in s:
    print('ANCHOR NOT FOUND'); sys.exit(1)
s = s.replace(anchor, block + "\n" + anchor, 1)

io.open(p, 'w', encoding='utf-8', newline='\n').write(s)
print('PATCH OK')
