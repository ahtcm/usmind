// 公式渲染工具：MathJax v3 tex-svg（SVG 输出，兼容 SVG foreignObject 渲染）
// KaTeX 的 HTML+inline-block 结构在 SVG foreignObject 内多行矩阵会行重叠塌陷，
// MathJax 输出纯 SVG，在 foreignObject 内渲染 100% 正常。

let mathJaxPromise = null

export function loadMathJax() {
  if (!mathJaxPromise) {
    mathJaxPromise = import(/* webpackIgnore: true */ 'mathjax/es5/tex-svg.js')
      .then(() => {
        if (window.MathJax && window.MathJax.startup && window.MathJax.startup.promise) {
          return window.MathJax.startup.promise
        }
        return Promise.resolve()
      })
  }
  return mathJaxPromise
}

// 渲染 LaTeX → SVG HTML 字符串（含容器）
export function tex2svgHtml(tex, display = true) {
  const mj = window.MathJax
  if (!mj || !mj.tex2svg) return '<span style="color:#e74c3c">[公式未就绪]</span>'
  const node = mj.tex2svg(tex, { display: !!display })
  // 移除辅助数学标记（assistive MML），避免部分浏览器下与主公式叠加显示成"两层"
  try { node.querySelectorAll('mjx-assistive-mml').forEach(a => a.remove()) } catch (e) {}
  // 附加 LaTeX 源码：data 属性（供双击编辑回填）。
  // 注意：不再写入 SVG <title>——部分浏览器/内置 WebView 会把 SVG title 的 LaTeX 源码显示在节点上，表现为"公式显示源码/乱码"。
  try { node.setAttribute('data-math-latex', tex) } catch (e) {}
  const wrapper = document.createElement('div')
  wrapper.appendChild(node)
  return wrapper.innerHTML
}

// 从任意公式 HTML（KaTeX 或 MathJax）提取 LaTeX
export function extractLatexFromHtml(html) {
  if (!html) return ''
  // 1. MathJax data-math-latex 属性
  const dm = html.match(/data-math-latex="([^"]*)"/)
  if (dm) return dm[1].replace(/&amp;/g, '&').replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&quot;/g, '"')
  // 2. KaTeX annotation
  const a = html.match(/<annotation[^>]*>([\s\S]*?)<\/annotation>/)
  if (a) return a[1].replace(/&amp;/g, '&')
  // 3. SVG <title>
  const t = html.match(/<title>([\s\S]*?)<\/title>/s)
  if (t) {
    return t[1].replace(/&amp;/g, '&').replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&quot;/g, '"')
  }
  return ''
}

// 判断一段 HTML 是否含公式
export function isMathHtml(html) {
  return !!(html && (html.indexOf('katex') >= 0 || html.indexOf('MathJax') >= 0 || html.indexOf('<math') >= 0 || html.indexOf('mjx-container') >= 0))
}

// 迁移 KaTeX HTML → MathJax SVG（加载旧数据时用）
export async function migrateKatexToSvg(html) {
  if (!html || html.indexOf('katex') < 0) return html
  // 提取所有 KaTeX 片段
  const fragment = document.createElement('div')
  fragment.innerHTML = html
  const katexEls = fragment.querySelectorAll('.katex')
  if (!katexEls.length) return html
  await loadMathJax()
  katexEls.forEach((el) => {
    const tex = extractLatexFromHtml(el.outerHTML)
    if (tex) {
      const isDisplay = !!el.closest('.katex-display') || el.classList.contains('katex-display')
      const svgHtml = tex2svgHtml(tex, isDisplay)
      const wrap = document.createElement('span')
      wrap.innerHTML = svgHtml
      // 标记为公式（供双击编辑识别）
      const container = wrap.querySelector('mjx-container')
      if (container) {
        container.setAttribute('data-math-latex', tex)
      }
      el.replaceWith(...wrap.childNodes.length ? Array.from(wrap.childNodes) : [wrap])
    }
  })
  return fragment.innerHTML
}
