# -*- coding: utf-8 -*-
# 用户方案：先导出完整 SVG（含 foreignObject，质量高），再提取为 HTML+SVG 混合 DOM，
# 用 html2canvas 捕获成图片/PDF（绕过 SVG-as-Image 的 foreignObject 不渲染限制）
p = r'C:\Users\25750\Desktop\usmind\code\web-user\src\views\Editor.vue'
s = open(p, encoding='utf-8').read()

# 1. 加 import
old_imp = "import { PDFDocument } from 'pdf-lib'"
new_imp = "import { PDFDocument } from 'pdf-lib'\nimport html2canvas from 'html2canvas'"
assert old_imp in s, 'IMP NOT FOUND'
s = s.replace(old_imp, new_imp, 1)

# 2. 在 loadImg 后新增 collectFoItems + renderChunkByHtml
anchor = """function loadImg(url) {
  const img = new Image()
  return new Promise((res, rej) => {
    img.onload = () => {
      // SVG 作为图片源时，onload 触发不代表内部节点/渐变已绘制完成，
      // 等两帧再绘制，避免 drawImage 拿到未渲染完成的图像
      requestAnimationFrame(() => requestAnimationFrame(() => res(img)))
    }
    img.onerror = rej
    img.src = url
  })
}
"""
new_funcs = anchor + """
// ===== 用户方案：先导出完整 SVG（foreignObject 完整保留、质量高），
// 再提取为「SVG 层 + 绝对定位的 HTML 富文本层」混合 DOM，用 html2canvas 捕获成图片/PDF。
// 原理：SVG 作为 <img> 加载时 foreignObject 一律不渲染（安全限制），html2canvas 直接捕获
// 内联 SVG 时也会把 svg 序列化成图片（foreignObject 照样丢）；只有把 foreignObject 内容
// 提到 HTML 层（浏览器原生渲染 HTML + MathJax SVG），html2canvas 才能逐元素捕获。 =====

// 解析 SVG：收集所有 foreignObject（算绝对坐标 + 保留子节点），移除后返回纯 SVG 与 fo 项
function collectFoItems(svgStr) {
  const doc = new DOMParser().parseFromString(svgStr, 'image/svg+xml')
  if (doc.querySelector('parsererror')) return { svg: svgStr, items: [] }
  const root = doc.documentElement
  const fos = Array.from(doc.querySelectorAll('foreignObject'))
  const items = []
  fos.forEach((fo) => {
    const x = parseFloat(fo.getAttribute('x') || '0')
    const y = parseFloat(fo.getAttribute('y') || '0')
    // 累积祖先 transform 链（内→外应用）
    const ancestors = []
    let el = fo.parentNode
    while (el && el.nodeName !== 'svg') {
      const at = el.getAttribute('transform')
      if (at) ancestors.push(at)
      el = el.parentNode
    }
    let px = x
    let py = y
    ancestors.forEach((t) => {
      const i = t.indexOf('matrix(')
      if (i >= 0) {
        const j = t.indexOf(')', i)
        const pp = t.slice(i + 7, j).split(',').map(Number)
        const nx = pp[0] * px + pp[2] * py + pp[4]
        const ny = pp[1] * px + pp[3] * py + pp[5]
        px = nx
        py = ny
      }
    })
    items.push({ x: px, y: py, fo })
    fo.remove()
  })
  const svg = new XMLSerializer().serializeToString(doc)
  return { svg, items }
}

// 渲染一块：HTML 容器（纯 SVG 层 + fo 内容层）→ html2canvas → canvas
async function renderChunkByHtml(svgStr, W, ch, y0, X, Y, bg) {
  const { svg: svgNoFo, items } = collectFoItems(svgStr)
  const chunkSvg = makeChunkSvg(svgNoFo, W, ch, y0, X, Y)
  const wrap = document.createElement('div')
  wrap.style.cssText = 'position:fixed;left:-99999px;top:0;z-index:-1000;overflow:hidden;'
  wrap.style.width = W + 'px'
  wrap.style.height = ch + 'px'
  wrap.style.background = bg || '#ffffff'
  // SVG 层（连线/节点形状，无 foreignObject）
  const svgBox = document.createElement('div')
  svgBox.style.cssText = 'position:absolute;left:0;top:0;width:' + W + 'px;height:' + ch + 'px;'
  svgBox.innerHTML = chunkSvg
  wrap.appendChild(svgBox)
  // HTML 富文本层（原 foreignObject 内容，绝对定位到画布坐标）
  items.forEach((it) => {
    const left = it.x - (X || 0)
    const top = it.y - (Y || 0) - y0
    if (top + 20 < 0 || top > ch) return // 视口外跳过
    const holder = document.createElement('div')
    holder.style.cssText = 'position:absolute;left:' + left + 'px;top:' + top + 'px;'
    it.fo.childNodes.forEach((c) => holder.appendChild(c.cloneNode(true)))
    // 视频/音频：贴链接占位（html2canvas 无法稳定绘制媒体画面）
    holder.querySelectorAll('video, audio').forEach((m) => {
      const label = m.nodeName === 'VIDEO' ? '▶ 视频' : '♪ 音频'
      const src = m.getAttribute('src') || ''
      const nm = decodeURIComponent((src.split('/').pop() || '').replace(/\\?.*$/, ''))
      const d = document.createElement('div')
      d.textContent = label + (nm ? '：' + nm : '')
      d.style.cssText = 'font-size:13px;color:#555;padding:2px 8px;border:1px dashed #bbb;border-radius:6px;background:#fafafa;'
      if (m.parentNode) m.parentNode.replaceChild(d, m)
    })
    // MathJax 公式 svg 的 ex 单位 → px（html2canvas 序列化 svg 为图片时无字体上下文，ex 无法解析）
    const fsMatch = (it.fo.getAttribute('style') || '').match(/font-size:\\s*([\\d.]+)px/)
    const fontSize = fsMatch ? parseFloat(fsMatch[1]) : 16
    holder.querySelectorAll('svg').forEach((sv) => {
      const w = sv.getAttribute('width')
      const h = sv.getAttribute('height')
      if (w && w.indexOf('ex') >= 0) sv.setAttribute('width', String(parseFloat(w) * fontSize * 0.5))
      if (h && h.indexOf('ex') >= 0) sv.setAttribute('height', String(parseFloat(h) * fontSize * 0.5))
    })
    wrap.appendChild(holder)
  })
  document.body.appendChild(wrap)
  try {
    const canvas = await html2canvas(wrap, {
      backgroundColor: bg || '#ffffff',
      scale: 1,
      useCORS: true,
      logging: false,
      width: W,
      height: ch,
      windowWidth: W,
      windowHeight: ch,
      imageTimeout: 20000
    })
    return canvas
  } finally {
    wrap.remove()
  }
}
"""
assert anchor in s, 'ANCHOR NOT FOUND'
s = s.replace(anchor, new_funcs, 1)

# 3. 改 exportChunkedImages
old_img = """  const { str: strRaw } = await mind.value.doExport.getSvgData()
  const str = liftFormulaSvg(strRaw)
  const { W, H, X, Y } = parseSvgSize(str)
  if (!W || !H) { toast('画布尺寸解析失败', 'error'); return }
  const bg = (mind.value.themeConfig && mind.value.themeConfig.backgroundColor) || '#ffffff'
  const CHUNK = 5000
  const chunks = Math.max(1, Math.ceil(H / CHUNK))
  const isJpg = type === 'jpg' || type === 'jpeg'
  const ext = isJpg ? 'jpg' : 'png'
  const parts = []
  for (let i = 0; i < chunks; i++) {
    if (chunks > 1) toast('正在导出第 ' + (i + 1) + '/' + chunks + ' 块...')
    const y0 = i * CHUNK
    const ch = Math.min(CHUNK, H - y0)
    const chunkSvg = makeChunkSvg(str, W, ch, y0, X, Y)
    const url = URL.createObjectURL(new Blob([chunkSvg], { type: 'image/svg+xml' }))
    const img = await loadImg(url)
    const canvas = document.createElement('canvas')
    canvas.width = W
    canvas.height = ch
    const ctx = canvas.getContext('2d')
    ctx.fillStyle = bg
    ctx.fillRect(0, 0, W, ch)
    ctx.drawImage(img, 0, 0)
    URL.revokeObjectURL(url)
    parts.push(canvas.toDataURL(isJpg ? 'image/jpeg' : 'image/png', isJpg ? 0.92 : undefined))
    canvas.width = 1
    canvas.height = 1
  }"""
new_img = """  const { str: strRaw } = await mind.value.doExport.getSvgData()
  const { W, H, X, Y } = parseSvgSize(strRaw)
  if (!W || !H) { toast('画布尺寸解析失败', 'error'); return }
  const bg = (mind.value.themeConfig && mind.value.themeConfig.backgroundColor) || '#ffffff'
  const CHUNK = 5000
  const chunks = Math.max(1, Math.ceil(H / CHUNK))
  const isJpg = type === 'jpg' || type === 'jpeg'
  const ext = isJpg ? 'jpg' : 'png'
  const parts = []
  for (let i = 0; i < chunks; i++) {
    if (chunks > 1) toast('正在导出第 ' + (i + 1) + '/' + chunks + ' 块...')
    const y0 = i * CHUNK
    const ch = Math.min(CHUNK, H - y0)
    const canvas = await renderChunkByHtml(strRaw, W, ch, y0, X, Y, bg)
    parts.push(canvas.toDataURL(isJpg ? 'image/jpeg' : 'image/png', isJpg ? 0.92 : undefined))
  }"""
assert old_img in s, 'IMG BLOCK NOT FOUND'
s = s.replace(old_img, new_img, 1)

# 4. 改 exportChunkedPdf
old_pdf = """  const { str: strRaw } = await mind.value.doExport.getSvgData()
  const str = liftFormulaSvg(strRaw)
  const { W, H, X, Y } = parseSvgSize(str)
  if (!W || !H) { toast('画布尺寸解析失败', 'error'); return }
  const bg = (mind.value.themeConfig && mind.value.themeConfig.backgroundColor) || '#ffffff'
  const CHUNK = 5000
  const chunks = Math.max(1, Math.ceil(H / CHUNK))
  const pdfDoc = await PDFDocument.create()
  for (let i = 0; i < chunks; i++) {
    if (chunks > 1) toast('正在生成 PDF 第 ' + (i + 1) + '/' + chunks + ' 页...')
    const y0 = i * CHUNK
    const ch = Math.min(CHUNK, H - y0)
    const chunkSvg = makeChunkSvg(str, W, ch, y0, X, Y)
    const url = URL.createObjectURL(new Blob([chunkSvg], { type: 'image/svg+xml' }))
    const img = await loadImg(url)
    const canvas = document.createElement('canvas')
    canvas.width = W
    canvas.height = ch
    const ctx = canvas.getContext('2d')
    ctx.fillStyle = bg
    ctx.fillRect(0, 0, W, ch)
    ctx.drawImage(img, 0, 0)
    URL.revokeObjectURL(url)
    const b64 = canvas.toDataURL('image/png').split(',')[1]
    const bytes = Uint8Array.from(atob(b64), c => c.charCodeAt(0))
    const pngImage = await pdfDoc.embedPng(bytes)
    const page = pdfDoc.addPage([W, ch])
    page.drawImage(pngImage, { x: 0, y: 0, width: W, height: ch })
    canvas.width = 1
    canvas.height = 1
  }"""
new_pdf = """  const { str: strRaw } = await mind.value.doExport.getSvgData()
  const { W, H, X, Y } = parseSvgSize(strRaw)
  if (!W || !H) { toast('画布尺寸解析失败', 'error'); return }
  const bg = (mind.value.themeConfig && mind.value.themeConfig.backgroundColor) || '#ffffff'
  const CHUNK = 5000
  const chunks = Math.max(1, Math.ceil(H / CHUNK))
  const pdfDoc = await PDFDocument.create()
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
  }"""
assert old_pdf in s, 'PDF BLOCK NOT FOUND'
s = s.replace(old_pdf, new_pdf, 1)

open(p, 'w', encoding='utf-8', newline='\n').write(s)
print('html2canvas export OK')
