# -*- coding: utf-8 -*-
# 导出点击后立即显示遮罩（防重复点击）+ 失败/完成必定关闭遮罩
p = r'C:\Users\25750\Desktop\usmind\code\web-user\src\views\Editor.vue'
s = open(p, encoding='utf-8').read()

# 1. refs 加 exporting 防重复
old_refs = """const exportLoading = ref(false)
const exportTip = ref('')"""
new_refs = """const exportLoading = ref(false)
const exporting = ref(false)
const exportTip = ref('')"""
assert old_refs in s, 'REFS NOT FOUND'
s = s.replace(old_refs, new_refs, 1)

# 2. exportChunkedImages：开头不再单独 toast/show（runExport 立即显示），失败分支关闭遮罩
old_img_head = """async function exportChunkedImages(type) {
  const name = docName.value || '思维导图'
  toast('正在生成画布数据...')
  const { str: strRaw } = await mind.value.doExport.getSvgData()
  const { W, H, X, Y } = parseSvgSize(strRaw)
  if (!W || !H) { toast('画布尺寸解析失败', 'error'); return }"""
new_img_head = """async function exportChunkedImages(type) {
  const name = docName.value || '思维导图'
  const { str: strRaw } = await mind.value.doExport.getSvgData()
  const { W, H, X, Y } = parseSvgSize(strRaw)
  if (!W || !H) { hideExportLoading(); toast('画布尺寸解析失败', 'error'); return }"""
assert old_img_head in s, 'IMG HEAD NOT FOUND'
s = s.replace(old_img_head, new_img_head, 1)

old_img_show = """  const parts = []
  showExportLoading(chunks, ext)
  for (let i = 0; i < chunks; i++) {"""
new_img_show = """  const parts = []
  for (let i = 0; i < chunks; i++) {"""
assert old_img_show in s, 'IMG SHOW NOT FOUND'
s = s.replace(old_img_show, new_img_show, 1)

# 3. exportChunkedPdf：同样处理
old_pdf_head = """async function exportChunkedPdf() {
  const name = docName.value || '思维导图'
  toast('正在生成画布数据...')
  const { str: strRaw } = await mind.value.doExport.getSvgData()
  const { W, H, X, Y } = parseSvgSize(strRaw)
  if (!W || !H) { toast('画布尺寸解析失败', 'error'); return }"""
new_pdf_head = """async function exportChunkedPdf() {
  const name = docName.value || '思维导图'
  const { str: strRaw } = await mind.value.doExport.getSvgData()
  const { W, H, X, Y } = parseSvgSize(strRaw)
  if (!W || !H) { hideExportLoading(); toast('画布尺寸解析失败', 'error'); return }"""
assert old_pdf_head in s, 'PDF HEAD NOT FOUND'
s = s.replace(old_pdf_head, new_pdf_head, 1)

old_pdf_show = """  const pdfDoc = await PDFDocument.create()
  showExportLoading(chunks, 'pdf')
  for (let i = 0; i < chunks; i++) {"""
new_pdf_show = """  const pdfDoc = await PDFDocument.create()
  for (let i = 0; i < chunks; i++) {"""
assert old_pdf_show in s, 'PDF SHOW NOT FOUND'
s = s.replace(old_pdf_show, new_pdf_show, 1)

# 4. runExport：点击立即显示遮罩 + 防重复 + finally 必关
old_run = """async function runExport(type) {
  exportOpen.value = false
  if (!mind.value) { toast('请先进入画布', 'error'); return }
  try {
    if (type === 'png' || type === 'jpg' || type === 'jpeg') {
      await exportChunkedImages(type)
    } else if (type === 'pdf') {
      await exportChunkedPdf()
    } else {
      const name = docName.value || '思维导图'
      toast('正在导出 ' + type.toUpperCase() + ' ...')
      await mind.value.doExport.export(type, true, name)
      toast('导出完成')
    }
  } catch (e) {
    toast('导出失败: ' + e.message, 'error')
  }
}"""
new_run = """async function runExport(type) {
  exportOpen.value = false
  if (!mind.value) { toast('请先进入画布', 'error'); return }
  if (exporting.value) { toast('正在导出中，请稍候...', 'warn'); return }
  exporting.value = true
  showExportLoading(0, '')
  try {
    if (type === 'png' || type === 'jpg' || type === 'jpeg') {
      await exportChunkedImages(type)
    } else if (type === 'pdf') {
      await exportChunkedPdf()
    } else {
      updateExportLoading(0, 1, '正在导出 ' + type.toUpperCase() + ' 文件...')
      const name = docName.value || '思维导图'
      await mind.value.doExport.export(type, true, name)
      updateExportLoading(1, 1, '导出完成')
    }
  } catch (e) {
    hideExportLoading()
    toast('导出失败: ' + e.message, 'error')
  } finally {
    exporting.value = false
  }
}"""
assert old_run in s, 'RUN NOT FOUND'
s = s.replace(old_run, new_run, 1)

# 5. showExportLoading 初始 tip 明确
old_show = """function showExportLoading(total, kind) {
  exportLoading.value = true
  exportProgress.value = 0
  exportTip.value = '正在生成画布数据...'
}"""
new_show = """function showExportLoading(total, kind) {
  exportLoading.value = true
  exportProgress.value = 0
  exportTip.value = '正在生成画布数据...'
  // 兜底：异常未关闭时最多展示 10 分钟后自关，避免遮罩卡死
  clearTimeout(exportLoadingTimer)
  exportLoadingTimer = setTimeout(() => { if (exportLoading.value) hideExportLoading() }, 600000)
}"""
assert old_show in s, 'SHOW NOT FOUND'
s = s.replace(old_show, new_show, 1)

old_hide = """function hideExportLoading() {
  exportLoading.value = false
  exportTip.value = ''
}"""
new_hide = """function hideExportLoading() {
  clearTimeout(exportLoadingTimer)
  exportLoading.value = false
  exportTip.value = ''
}"""
assert old_hide in s, 'HIDE NOT FOUND'
s = s.replace(old_hide, new_hide, 1)

old_timer = """let loadingToken = 0"""
new_timer = """let exportLoadingTimer = null
let loadingToken = 0"""
assert old_timer in s, 'TIMER NOT FOUND'
s = s.replace(old_timer, new_timer, 1)

open(p, 'w', encoding='utf-8', newline='\n').write(s)
print('export instant loading OK')
