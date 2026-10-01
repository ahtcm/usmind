# -*- coding: utf-8 -*-
import io

# 1) mathRenderer.js：tex2svgHtml 不再添加 <title>（部分浏览器/WebView 会把 SVG title 文本（LaTeX 源码）显示出来）
p1 = r"C:\Users\25750\Desktop\usmind\code\web-user\src\utils\mathRenderer.js"
with open(p1, "r", encoding="utf-8", newline="") as f:
    c1 = f.read().replace("\r\n", "\n")

old1 = """  // 附加 LaTeX 源码：data 属性 + SVG <title>，供双击编辑回填
  try { node.setAttribute('data-math-latex', tex) } catch (e) {}
  const svg = node.querySelector('svg')
  if (svg && !svg.querySelector('title')) {
    try {
      const title = document.createElementNS('http://www.w3.org/2000/svg', 'title')
      title.textContent = tex
      svg.insertBefore(title, svg.firstChild)
    } catch (e) {}
  }
  const wrapper = document.createElement('div')"""

new1 = """  // 附加 LaTeX 源码：data 属性（供双击编辑回填）。
  // 注意：不再写入 SVG <title>——部分浏览器/内置 WebView 会把 SVG title 的 LaTeX 源码显示在节点上，表现为"公式显示源码/乱码"。
  try { node.setAttribute('data-math-latex', tex) } catch (e) {}
  const wrapper = document.createElement('div')"""

assert old1 in c1, 'MISS mathRenderer tex2svgHtml'
c1 = c1.replace(old1, new1)
with open(p1, "w", encoding="utf-8", newline="") as f:
    f.write(c1.replace("\n", "\r\n"))
print("OK mathRenderer: 不再添加 title")

# 2) Editor.vue migrateDocMath：迁移时移除旧数据中 MathJax SVG 的 <title>（含 LaTeX 源码）
p2 = r"C:\Users\25750\Desktop\usmind\code\web-user\src\views\Editor.vue"
with open(p2, "r", encoding="utf-8", newline="") as f:
    c2 = f.read().replace("\r\n", "\n")

old2 = """async function migrateDocMath(doc) {
  const root = doc && doc.data && doc.data.root
  if (!root) return
  const nodes = []
  const walk = (n) => {
    if (!n || !n.data) return
    const t = n.data.text || ''
    if (n.data.richText === true && t.indexOf('katex') >= 0) nodes.push(n)
    if (n.children) n.children.forEach(walk)
  }
  walk(root)
  if (!nodes.length) return
  await loadMathJax()
  let changed = false
  for (const n of nodes) {
    const t = n.data.text || ''
    if (t.indexOf('katex') >= 0) {
      try {
        const migrated = await migrateKatexToSvg(t)
        if (migrated !== t) { n.data.text = migrated; changed = true }
      } catch (e) { console.warn('公式迁移失败', e) }
    }
  }"""

new2 = """async function migrateDocMath(doc) {
  const root = doc && doc.data && doc.data.root
  if (!root) return
  const nodes = []
  const walk = (n) => {
    if (!n || !n.data) return
    const t = n.data.text || ''
    if (n.data.richText === true && (t.indexOf('katex') >= 0 || t.indexOf('mjx-container') >= 0)) nodes.push(n)
    if (n.children) n.children.forEach(walk)
  }
  walk(root)
  if (!nodes.length) return
  await loadMathJax()
  let changed = false
  for (const n of nodes) {
    const t = n.data.text || ''
    if (t.indexOf('katex') >= 0) {
      try {
        const migrated = await migrateKatexToSvg(t)
        if (migrated !== t) { n.data.text = migrated; changed = true }
      } catch (e) { console.warn('公式迁移失败', e) }
    }
    // 移除 MathJax SVG 内嵌 <title>（LaTeX 源码）：部分浏览器/WebView 会把 title 文本显示在节点上（源码乱码）
    if (t.indexOf('mjx-container') >= 0 && t.indexOf('<title>') >= 0) {
      try {
        const div = document.createElement('div')
        div.innerHTML = t
        let removed = false
        div.querySelectorAll('mjx-container svg title').forEach(x => { x.remove(); removed = true })
        if (removed && div.innerHTML !== t) { n.data.text = div.innerHTML; changed = true }
      } catch (e) { console.warn('移除公式title失败', e) }
    }
  }"""

assert old2 in c2, 'MISS migrateDocMath'
c2 = c2.replace(old2, new2)
with open(p2, "w", encoding="utf-8", newline="") as f:
    f.write(c2.replace("\n", "\r\n"))
print("OK Editor: migrateDocMath 移除 MathJax title")
