# -*- coding: utf-8 -*-
# Editor.vue 会员拦截 v2
p = r'C:\Users\25750\Desktop\usmind\code\web-user\src\views\Editor.vue'
s = open(p, encoding='utf-8').read()

# 1. 导入
old_imp = """import { toast, showConfirm, showPrompt } from '../utils/modal'"""
new_imp = """import { toast, showConfirm, showPrompt } from '../utils/modal'
import { memberApi, configApi } from '../api/member'"""
assert old_imp in s, 'IMP NOT FOUND'
s = s.replace(old_imp, new_imp, 1)

# 2. runExport 校验
old_export = """async function runExport(type) {
  exportOpen.value = false
  if (!mind.value) { toast('请先进入画布', 'error'); return }
  if (exporting.value) { toast('正在导出中，请稍候...', 'warn'); return }"""
new_export = """async function runExport(type) {
  exportOpen.value = false
  if (!mind.value) { toast('请先进入画布', 'error'); return }
  if (exporting.value) { toast('正在导出中，请稍候...', 'warn'); return }
  if (!checkMemberFeature('export', type)) { toast('该导出格式为会员专属，开通会员后可用', 'error'); return }"""
assert old_export in s, 'EXPORT NOT FOUND'
s = s.replace(old_export, new_export, 1)

# 3. applyTheme 校验
old_theme = """function applyTheme(name) {
  canvasTabs.value[docIndex.value].theme = name"""
new_theme = """function applyTheme(name) {
  if (!checkMemberFeature('theme', name)) { toast('该主题为会员专属，开通会员后可用', 'error'); return }
  canvasTabs.value[docIndex.value].theme = name"""
assert old_theme in s, 'THEME NOT FOUND'
s = s.replace(old_theme, new_theme, 1)

# 4. helper 函数插到主题加载注释前
anchor = """  // 拉取后台配置的主题模板并合并（同 key 覆盖内置，新增追加）"""
assert anchor in s, 'ANCHOR NOT FOUND'
helper = """let memberFeature = { memberThemeIds: '', memberExportFormats: '', memberLevel: 0 }
function checkMemberFeature(kind, value) {
  const cfg = memberFeature
  if (kind === 'theme') {
    if (!cfg.memberThemeIds) return true
    const ids = String(cfg.memberThemeIds).split(',').map(x => x.trim())
    if (!ids.includes(value)) return true
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
""" + anchor
s = s.replace(anchor, helper, 1)

# 5. 主题合并后调用 loadMemberFeature
old_call = """  } catch (e) { /* 后端不可用则仅用内置主题 */ }"""
new_call = """  } catch (e) { /* 后端不可用则仅用内置主题 */ }
  loadMemberFeature()"""
assert old_call in s, 'CALL NOT FOUND'
s = s.replace(old_call, new_call, 1)

open(p, 'w', encoding='utf-8', newline='\n').write(s)
print('editor member lock OK')
