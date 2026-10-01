# -*- coding: utf-8 -*-
import io

p = r"C:\Users\25750\Desktop\usmind\code\web-user\src\views\Editor.vue"
with open(p, "r", encoding="utf-8", newline="") as f:
    c = f.read().replace("\r\n", "\n")
ok = []

# 1) 双击公式识别放宽：不依赖 richText 标记（防 richText 丢失时进入文本编辑显示 SVG 乱码）
a = """      if (rich && (text.indexOf('katex') >= 0 || text.indexOf('mjx-container') >= 0 || text.indexOf('MathJax') >= 0)) {
        try { mind.value.renderer.textEdit.hideEditTextBox() } catch (err) {}
        acquireNodeLock(nid0)
        openMathEdit(node)
        return
      }"""
r = """      if (text.indexOf('katex') >= 0 || text.indexOf('mjx-container') >= 0 || text.indexOf('MathJax') >= 0) {
        try { mind.value.renderer.textEdit.hideEditTextBox() } catch (err) {}
        acquireNodeLock(nid0)
        openMathEdit(node)
        return
      }"""
if a in c:
    c = c.replace(a, r); ok.append("dblclick")
else:
    print("MISS-dblclick")

# 2) openMathEdit 打开时立即渲染预览（不依赖用户编辑后再预览）
a = """  mathNode.value = node
  mathInput.value = latex
  mathPreview.value = ''
  mathEditMode.value = true
  mathDialog.value = true
  setTimeout(() => { try { const el = document.querySelector('.math-input'); if (el) { el.focus(); el.setSelectionRange(el.value.length, el.value.length) } } catch (err) {} }, 50)"""
r = """  mathNode.value = node
  mathInput.value = latex
  mathPreview.value = ''
  mathEditMode.value = true
  mathDialog.value = true
  // 打开即预览已有公式（不依赖用户再次编辑）
  if (latex && latex.trim()) {
    loadMathJax().then(() => {
      try { mathPreview.value = tex2svgHtml(latex.trim(), true) } catch (e) { mathPreview.value = '' }
    }).catch(() => { mathPreview.value = '' })
  }
  setTimeout(() => { try { const el = document.querySelector('.math-input'); if (el) { el.focus(); el.setSelectionRange(el.value.length, el.value.length) } } catch (err) {} }, 50)"""
if a in c:
    c = c.replace(a, r); ok.append("preview")
else:
    print("MISS-preview")

with open(p, "w", encoding="utf-8", newline="") as f:
    f.write(c.replace("\n", "\r\n"))
print("done: " + ", ".join(ok))
