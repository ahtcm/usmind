# -*- coding: utf-8 -*-
# 更新 CHANGELOG / FRONTEND / README 的导出方案描述
import io

# ---- CHANGELOG.md：顶部加条目 ----
p = r'C:\Users\25750\Desktop\usmind\docs\CHANGELOG.md'
s = open(p, encoding='utf-8').read()
old = """# USMIND 变更记录

## 2026-09-28 · 移除便携绿色版（按需求）"""
new = """# USMIND 变更记录

## 2026-09-29 · 导出重构：html2canvas 混合 DOM 方案（公式/图片完整导出 + 进度遮罩）

- **背景**：43 号"数学公式"导图导出 PNG/PDF 出现黑块（MathJax 大公式外层 svg + 内层嵌套 svg 被旧 `liftFormulaSvg` 同时提升到根级，同位置叠加渲染成黑块）；50 号"图片"导图导出失败（simple-mind-map 输出 SVG 中空元素未自闭合导致 XML 解析失败，Image 加载失败回退到 SVG blob）。
- **方案（用户拍板：先导出完整 SVG，再用图片/PDF 装这个 SVG）**：SVG 作为 `<img>` 加载时 foreignObject 一律不渲染（浏览器安全限制），html2canvas 直接捕获内联 SVG 时也会把 svg 序列化成图片（foreignObject 照样丢）。最终实现为**混合 DOM 捕获**：
  - `collectFoItems`：DOMParser 解析完整 SVG，收集所有 `foreignObject`（按祖先 matrix 变换链内→外累积到画布绝对坐标），移除后得到纯 SVG（连线 / 节点形状）。
  - `renderChunkByHtml`：离屏容器 = SVG 层（innerHTML 纯块 SVG，viewBox 裁剪到分块视口）+ HTML 富文本层（原 foreignObject 内容绝对定位到画布坐标，浏览器原生渲染文字 / 公式 / 图片）；MathJax 公式 svg 的 `ex` 单位按 foreignObject 的 font-size 换算为 px（html2canvas 序列化 svg 为图片时无字体上下文）；视频 / 音频按用户方案贴「▶视频 / ♪音频：文件名」占位；`html2canvas(wrap, { useCORS, scale:1 })` 逐块捕获。
  - `exportChunkedImages` / `exportChunkedPdf`：不再走 `loadImg(blobURL)+drawImage` 大 SVG 渲染，逐块调 `renderChunkByHtml`（CHUNK=5000，超大画布分块 PNG 打包 zip / PDF 逐页）。
- **进度遮罩**：导出点击后**立即**显示全屏遮罩（`export-loading`，与页面加载同风格：spinner + 当前步骤文案 + 百分比进度条），先 `nextTick + 60ms` 让出主线程再开始 `getSvgData`（大导图 SVG 序列化是同步操作会阻塞 Vue 渲染）；导出中 `exporting` 标志防重复点击，失败 / 异常兜底自关（10 分钟超时）。
- **实测**：50 图片导图 PNG 584×524（彩色图片正常显示）、PDF 合法；43 公式导图 PNG 分块 zip 4 块全部公式完整、无黑块、PDF 1.4MB；进度遮罩点击后 2ms 出现、25%→50%→75%→完成自动关闭；控制台无报错。
- **依赖**：新增 `html2canvas`（npm 国内镜像安装）。
- **提交**：`d29e335`（方案落地）/ `8c51113`（进度遮罩）/ `4ac6a18`（立即显示+防重复）/ `53d0f1e`（先让出主线程，遮罩 2ms 渲染）。

## 2026-09-28 · 移除便携绿色版（按需求）"""
assert old in s, 'CHANGELOG HEAD NOT FOUND'
s = s.replace(old, new, 1)
open(p, 'w', encoding='utf-8', newline='\n').write(s)

# ---- FRONTEND.md ⑧ 导入导出：更新导出机制 ----
p2 = r'C:\Users\25750\Desktop\usmind\docs\FRONTEND.md'
s2 = open(p2, encoding='utf-8').read()
old2 = """#### ⑧ 导入导出
- **导出**：`runExport` —— JSON（当前数据）、PNG / JPG / JPEG / SVG / PDF（`html2canvas` 画布截图，PDF 逐页拼图）、Markdown（节点树转 md 列表）。"""
new2 = """#### ⑧ 导入导出
- **导出**：`runExport` —— JSON（当前数据）、PNG / JPG / JPEG / SVG / PDF、Markdown（节点树转 md 列表）。
  - **PNG/JPG/PDF 走 html2canvas 混合 DOM 方案**：先 `doExport.getSvgData()` 取完整 SVG（foreignObject 完整保留、质量高）→ `collectFoItems` 把每个 foreignObject 提取为 HTML 富文本层（绝对定位到画布坐标，浏览器原生渲染文字 / 公式 / 图片），SVG 层只留连线与节点形状 → 两层混合后用 `html2canvas` 逐元素捕获。公式 svg 的 `ex` 单位按 font-size 换算 px；视频 / 音频贴「▶视频 / ♪音频：文件名」占位；超大画布按 5000px 分块（PNG 打包 zip / PDF 逐页）。
  - **进度遮罩**：`export-loading` 全屏遮罩显示当前步骤（"正在导出第 X/Y 块（PNG）..."）与百分比进度条；点击导出立即显示（先 `nextTick + 60ms` 让出主线程再开始 `getSvgData`，避免同步序列化阻塞 Vue 渲染）；`exporting` 标志防重复点击；失败 / 异常兜底自关。"""
assert old2 in s2, 'FRONTEND EXPORT NOT FOUND'
s2 = s2.replace(old2, new2, 1)
open(p2, 'w', encoding='utf-8', newline='\n').write(s2)

# ---- README.md L19 导入导出：补充一句话 ----
p3 = r'C:\Users\25750\Desktop\usmind\README.md'
s3 = open(p3, encoding='utf-8').read()
old3 = """- **导入导出**：导出 JSON / PNG / JPG / JPEG / SVG / PDF / Markdown；`.usmind` 资源包（JSON + 资源文件夹打包，可整体迁移到其它系统）；JSON 导入。"""
new3 = """- **导入导出**：导出 JSON / PNG / JPG / JPEG / SVG / PDF / Markdown；`.usmind` 资源包（JSON + 资源文件夹打包，可整体迁移到其它系统）；JSON 导入。PNG / JPG / PDF 导出采用 html2canvas 混合 DOM 方案（完整 SVG 提取 foreignObject 为 HTML 层再逐元素捕获），公式 / 图片等富内容完整显示、超大画布自动分块打包并带进度遮罩提示。"""
assert old3 in s3, 'README EXPORT NOT FOUND'
s3 = s3.replace(old3, new3, 1)
open(p3, 'w', encoding='utf-8', newline='\n').write(s3)

print('docs OK')
