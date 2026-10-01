# -*- coding: utf-8 -*-
p = r"C:\Users\25750\Desktop\usmind\code\web-user\src\views\Editor.vue"
with open(p, "r", encoding="utf-8", newline="") as f:
    c = f.read().replace("\r\n", "\n")

# 1) ref：加 mathTitle（默认"公式"）
old1 = "const mathInput = ref('')\nconst mathPreview = ref('')"
new1 = "const mathInput = ref('')\nconst mathTitle = ref('公式')\nconst mathPreview = ref('')"
assert old1 in c, 'MISS ref'
c = c.replace(old1, new1)

# 2) 模板：弹窗标题统一"插入数学公式"，加标题输入框，预览区改
old2 = '''      <div class="math-title">{{ mathEditMode ? '编辑数学公式（LaTeX）' : '插入数学公式（LaTeX）' }}</div>
      <textarea v-model="mathInput" class="math-input" rows="3" placeholder="例如：E = mc^2  或  \\frac{a}{b} + \\sqrt{x}" @input="updateMathPreview"></textarea>
      <div class="math-preview">
        <span class="math-preview-label">预览</span>
        <span v-if="mathPreview" v-html="mathPreview"></span>
        <span v-else class="math-preview-empty">输入 LaTeX 后实时预览</span>
      </div>'''
new2 = '''      <div class="math-title">插入数学公式</div>
      <input v-model="mathTitle" class="math-title-input" placeholder="标题（显示在公式前，默认：公式）" @input="updateMathPreview" />
      <textarea v-model="mathInput" class="math-input" rows="3" placeholder="例如：E = mc^2  或  \\frac{a}{b} + \\sqrt{x}" @input="updateMathPreview"></textarea>
      <div class="math-preview">
        <span class="math-preview-label">预览</span>
        <span v-if="mathPreview" class="math-preview-content" v-html="mathPreview"></span>
        <span v-else class="math-preview-empty">输入标题和 LaTeX 后实时预览</span>
      </div>'''
assert old2 in c, 'MISS template'
c = c.replace(old2, new2)

# 3) updateMathPreview：预览 = 标题 + 公式
old3 = '''function updateMathPreview() {
  const raw = mathInput.value.trim()
  if (!raw) { mathPreview.value = ''; return }
  loadMathJax().then(() => {
    mathPreview.value = tex2svgHtml(raw, true)
  }).catch(() => { mathPreview.value = '' })
}'''
new3 = '''function updateMathPreview() {
  const raw = mathInput.value.trim()
  if (!raw) { mathPreview.value = ''; return }
  loadMathJax().then(() => {
    try {
      const html = tex2svgHtml(raw, true)
      const title = (mathTitle.value || '').trim() || '公式'
      const escTitle = title.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      mathPreview.value = escTitle + ' ' + html
    } catch (e) { mathPreview.value = '' }
  }).catch(() => { mathPreview.value = '' })
}'''
assert old3 in c, 'MISS updateMathPreview'
c = c.replace(old3, new3)

# 4) openMathEdit：标题默认"公式"
old4 = "  mathNode.value = node\n  mathInput.value = latex\n  mathPreview.value = ''"
new4 = "  mathNode.value = node\n  mathInput.value = latex\n  mathTitle.value = '公式'\n  mathPreview.value = ''"
assert old4 in c, 'MISS openMathEdit'
c = c.replace(old4, new4)

# 5) openMathQuick：标题默认"公式"
old5 = "  mathNode.value = node\n  mathInput.value = ''\n  mathPreview.value = ''"
new5 = "  mathNode.value = node\n  mathInput.value = ''\n  mathTitle.value = '公式'\n  mathPreview.value = ''"
assert old5 in c, 'MISS openMathQuick'
c = c.replace(old5, new5)

# 6) insertMathToNode：统一「标题 + 公式」；编辑态有实际内容 → 光标处插入，空/占位 → 整体替换
old6_start = "async function insertMathToNode() {"
old6_end = "  mathDialog.value = false\n  saveDoc()\n  // 不调用全量 render()：全量重渲染会使 MathJax SVG 失效（公式空白/模糊），刷新后才恢复。\n}"
i1 = c.index(old6_start)
i2 = c.index(old6_end) + len(old6_end)
old6 = c[i1:i2]
new6 = '''async function insertMathToNode() {
  const node = mathNode.value
  const raw = mathInput.value.trim()
  let title = (mathTitle.value || '').trim() || '公式'
  try { await loadMathJax() } catch (e) {}
  if (!node || !raw) { mathDialog.value = false; return }
  let html = ''
  try { html = tex2svgHtml(raw, true) } catch (err) { html = '<span style="color:#e74c3c">[公式错误]</span>' }
  const escTitle = title.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
  const titleHtml = escTitle + ' '
  // 编辑态（TextEdit 正在编辑且有实际内容）：标题+公式插入光标处；空/占位节点：整体替换为 标题+公式
  const te = mind.value.renderer.textEdit
  if (te && te.textEditNode && te.showTextEdit) {
    const teNode = te.textEditNode
    let curText = ''
    try { curText = (teNode.innerText || '').trim() } catch (e) {}
    const isPlaceholder = curText === '' || curText === '新主题' || curText === '这里可以输入文字'
    if (!isPlaceholder) {
      teNode.focus()
      let range = null
      if (savedMathRange && savedMathRange.commonAncestorContainer && teNode.contains && teNode.contains(savedMathRange.commonAncestorContainer)) {
        range = savedMathRange
      } else {
        range = document.createRange()
        range.selectNodeContents(teNode)
        range.collapse(false)
      }
      const sel = window.getSelection()
      sel.removeAllRanges()
      sel.addRange(range)
      try {
        const frag = range.createContextualFragment(titleHtml + html)
        range.insertNode(frag)
        const after = document.createRange()
        after.selectNodeContents(teNode)
        after.collapse(false)
        sel.removeAllRanges()
        sel.addRange(after)
      } catch (err) { console.warn('公式插入失败', err) }
      teNode.dispatchEvent(new InputEvent('input', { bubbles: true }))
      mathDialog.value = false
      saveDoc()
      return
    }
  }
  // 整体替换：标题 + 公式（notRender=true 避免尺寸变化触发全量渲染导致 MathJax SVG 失效）
  mind.value.renderer.setNodeDataRender(node, { text: titleHtml + html, richText: true }, true)
  mathDialog.value = false
  saveDoc()
}'''
c = c[:i1] + new6 + c[i2:]

# 7) CSS：标题输入框 + 预览白底正常色
old7 = ".math-input { width: 100%; box-sizing: border-box; border: 1px solid #ddd; border-radius: 8px; padding: 8px 10px; font-size: 14px; font-family: Consolas, Menlo, monospace; resize: vertical; outline: none; }\n.math-input:focus { border-color: #5b8ff9; }\n.math-preview { min-height: 44px; margin-top: 10px; padding: 10px 12px; background: #f7f8fa; border-radius: 8px; display: flex; align-items: center; gap: 10px; overflow-x: auto; }\n.math-preview-label { font-size: 12px; color: #909399; white-space: nowrap; }\n.math-preview-empty { color: #b0b3ba; font-size: 13px; }"
new7 = ".math-title-input { width: 100%; box-sizing: border-box; border: 1px solid #ddd; border-radius: 8px; padding: 7px 10px; font-size: 14px; outline: none; margin-bottom: 8px; }\n.math-title-input:focus { border-color: #5b8ff9; }\n.math-input { width: 100%; box-sizing: border-box; border: 1px solid #ddd; border-radius: 8px; padding: 8px 10px; font-size: 14px; font-family: Consolas, Menlo, monospace; resize: vertical; outline: none; }\n.math-input:focus { border-color: #5b8ff9; }\n.math-preview { min-height: 44px; margin-top: 10px; padding: 10px 12px; background: #fff; border: 1px solid #eee; border-radius: 8px; display: flex; align-items: center; gap: 10px; overflow-x: auto; flex-wrap: wrap; }\n.math-preview-label { font-size: 12px; color: #909399; white-space: nowrap; }\n.math-preview-content { color: #303133; font-size: 14px; line-height: 1.5; }\n.math-preview-empty { color: #b0b3ba; font-size: 13px; }"
assert old7 in c, 'MISS css'
c = c.replace(old7, new7)

with open(p, "w", encoding="utf-8", newline="") as f:
    f.write(c.replace("\n", "\r\n"))
print("OK 公式弹窗：标题输入框默认公式 + 统一插入(标题+公式) + 预览白底")
