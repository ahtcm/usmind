# -*- coding: utf-8 -*-
# 导出进度 loading：遮罩 + spinner + 当前步骤文案 + 进度条百分比
p = r'C:\Users\25750\Desktop\usmind\code\web-user\src\views\Editor.vue'
s = open(p, encoding='utf-8').read()

# 1. 模板：加导出进度遮罩（page-loading 块之后）
old_tpl = """    <div v-if="pageLoading" class="page-loading">
      <div class="pl-spinner"></div>
      <div class="pl-text">加载中...</div>
    </div>
"""
new_tpl = """    <div v-if="pageLoading" class="page-loading">
      <div class="pl-spinner"></div>
      <div class="pl-text">加载中...</div>
    </div>

    <!-- 导出进度遮罩 -->
    <div v-if="exportLoading" class="export-loading">
      <div class="pl-spinner"></div>
      <div class="el-title">正在导出</div>
      <div class="el-text">{{ exportTip }}</div>
      <div class="el-bar"><div class="el-bar-fill" :style="{ width: exportProgress + '%' }"></div></div>
      <div class="el-pct">{{ exportProgress }}%</div>
    </div>
"""
assert old_tpl in s, 'TPL NOT FOUND'
s = s.replace(old_tpl, new_tpl, 1)

# 2. script：refs
old_refs = """const pageLoading = ref(false)
let loadingToken = 0"""
new_refs = """const pageLoading = ref(false)
const exportLoading = ref(false)
const exportTip = ref('')
const exportProgress = ref(0)
function showExportLoading(total, kind) {
  exportLoading.value = true
  exportProgress.value = 0
  exportTip.value = '正在生成画布数据...'
}
function updateExportLoading(done, total, label) {
  exportProgress.value = total > 0 ? Math.round(done / total * 100) : 100
  exportTip.value = label || ''
}
function hideExportLoading() {
  exportLoading.value = false
  exportTip.value = ''
}
let loadingToken = 0"""
assert old_refs in s, 'REFS NOT FOUND'
s = s.replace(old_refs, new_refs, 1)

# 3. exportChunkedImages：分块进度
old_img = """  const isJpg = type === 'jpg' || type === 'jpeg'
  const ext = isJpg ? 'jpg' : 'png'
  const parts = []
  for (let i = 0; i < chunks; i++) {
    if (chunks > 1) toast('正在导出第 ' + (i + 1) + '/' + chunks + ' 块...')
    const y0 = i * CHUNK
    const ch = Math.min(CHUNK, H - y0)
    const canvas = await renderChunkByHtml(strRaw, W, ch, y0, X, Y, bg)
    parts.push(canvas.toDataURL(isJpg ? 'image/jpeg' : 'image/png', isJpg ? 0.92 : undefined))
  }
  if (chunks === 1) {
    downloadDataUrl(parts[0], name + '.' + ext)
    toast('导出完成')
  } else {
    const zip = new JSZip()
    parts.forEach((d, i) => zip.file(name + '_' + (i + 1) + '.' + ext, d.split(',')[1], { base64: true }))
    const blob = await zip.generateAsync({ type: 'blob' })
    downloadBlob(blob, name + '_分块导出.zip')
    toast('导出完成，共 ' + chunks + ' 块（已打包 zip）')
  }"""
new_img = """  const isJpg = type === 'jpg' || type === 'jpeg'
  const ext = isJpg ? 'jpg' : 'png'
  const parts = []
  showExportLoading(chunks, ext)
  for (let i = 0; i < chunks; i++) {
    const y0 = i * CHUNK
    const ch = Math.min(CHUNK, H - y0)
    updateExportLoading(i, chunks, '正在导出第 ' + (i + 1) + '/' + chunks + ' 块（' + ext.toUpperCase() + '）...')
    const canvas = await renderChunkByHtml(strRaw, W, ch, y0, X, Y, bg)
    parts.push(canvas.toDataURL(isJpg ? 'image/jpeg' : 'image/png', isJpg ? 0.92 : undefined))
  }
  if (chunks === 1) {
    updateExportLoading(1, 1, '正在保存图片...')
    downloadDataUrl(parts[0], name + '.' + ext)
    hideExportLoading()
    toast('导出完成')
  } else {
    updateExportLoading(chunks, chunks, '正在打包压缩包...')
    const zip = new JSZip()
    parts.forEach((d, i) => zip.file(name + '_' + (i + 1) + '.' + ext, d.split(',')[1], { base64: true }))
    const blob = await zip.generateAsync({ type: 'blob' })
    downloadBlob(blob, name + '_分块导出.zip')
    hideExportLoading()
    toast('导出完成，共 ' + chunks + ' 块（已打包 zip）')
  }"""
assert old_img in s, 'IMG PROG NOT FOUND'
s = s.replace(old_img, new_img, 1)

# 4. exportChunkedPdf：分页进度
old_pdf = """  const pdfDoc = await PDFDocument.create()
  for (let i = 0; i < chunks; i++) {
    if (chunks > 1) toast('正在生成 PDF 第 ' + (i + 1) + '/' + chunks + ' 页...')
    const y0 = i * CHUNK
    const ch = Math.min(CHUNK, H - y0)
    const canvas = await renderChunkByHtml(strRaw, W, ch, y0, X, Y, bg)
    const b64 = canvas.toDataURL('image/png').split(',')[1]
    const bytes = Uint8Array.from(atob(b64), c => c.charCodeAt(0))
    const pngImage = await pdfDoc.embedPng(bytes)
    const page = pdfDoc.addPage([W, ch])
    page.drawImage(pngImage, { x: 0, y: 0, width: W, height: ch })
  }
  const pdfBytes = await pdfDoc.save()
  downloadBlob(new Blob([pdfBytes], { type: 'application/pdf' }), name + '.pdf')
  toast('导出完成，共 ' + chunks + ' 页')"""
new_pdf = """  const pdfDoc = await PDFDocument.create()
  showExportLoading(chunks, 'pdf')
  for (let i = 0; i < chunks; i++) {
    const y0 = i * CHUNK
    const ch = Math.min(CHUNK, H - y0)
    updateExportLoading(i, chunks, '正在生成 PDF 第 ' + (i + 1) + '/' + chunks + ' 页...')
    const canvas = await renderChunkByHtml(strRaw, W, ch, y0, X, Y, bg)
    const b64 = canvas.toDataURL('image/png').split(',')[1]
    const bytes = Uint8Array.from(atob(b64), c => c.charCodeAt(0))
    const pngImage = await pdfDoc.embedPng(bytes)
    const page = pdfDoc.addPage([W, ch])
    page.drawImage(pngImage, { x: 0, y: 0, width: W, height: ch })
  }
  updateExportLoading(chunks, chunks, '正在保存 PDF 文件...')
  const pdfBytes = await pdfDoc.save()
  downloadBlob(new Blob([pdfBytes], { type: 'application/pdf' }), name + '.pdf')
  hideExportLoading()
  toast('导出完成，共 ' + chunks + ' 页')"""
assert old_pdf in s, 'PDF PROG NOT FOUND'
s = s.replace(old_pdf, new_pdf, 1)

# 5. 样式：导出进度遮罩（page-loading 样式后）
old_css = """.pl-text { font-size: 14px; color: #667; }
@keyframes pl-rot { to { transform: rotate(360deg) } }"""
new_css = """.pl-text { font-size: 14px; color: #667; }
@keyframes pl-rot { to { transform: rotate(360deg) } }
.export-loading { position: absolute; inset: 0; z-index: 101; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 12px; background: rgba(255,255,255,.72); backdrop-filter: blur(2px); }
.el-title { font-size: 16px; font-weight: 600; color: #333; }
.el-text { font-size: 13px; color: #667; }
.el-bar { width: 260px; height: 6px; border-radius: 999px; background: rgba(74,92,247,.15); overflow: hidden; }
.el-bar-fill { height: 100%; border-radius: 999px; background: linear-gradient(90deg, #4A5CF7, #7c6cf0); transition: width .25s ease; }
.el-pct { font-size: 12px; color: #4A5CF7; font-weight: 600; }"""
assert old_css in s, 'CSS NOT FOUND'
s = s.replace(old_css, new_css, 1)

open(p, 'w', encoding='utf-8', newline='\n').write(s)
print('export loading OK')
