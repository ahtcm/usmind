# -*- coding: utf-8 -*-
import io

p = r"C:\Users\25750\Desktop\usmind\code\web-user\src\views\Editor.vue"
with open(p, "r", encoding="utf-8", newline="") as f:
    c = f.read().replace("\r\n", "\n")
ok = []

# 1) 取消按钮：关闭弹窗后延迟重渲染，恢复弹窗操作可能引起的公式渲染异常
a = """        <button class="math-btn" @click="mathDialog = false">取消</button>"""
r = """        <button class="math-btn" @click="mathCancel">取消</button>"""
if a in c:
    c = c.replace(a, r); ok.append("cancel-btn")
else:
    print("MISS-cancel-btn")

# 2) 新增 mathCancel 函数（放在 openMathQuick 之前）
a = """function openMathQuick() {"""
r = """function mathCancel() {
  mathDialog.value = false
  // 关闭弹窗后延迟重渲染，恢复弹窗操作可能引起的公式渲染异常
  setTimeout(() => { try { if (mind.value) mind.value.render() } catch (e) {} }, 400)
}

function openMathQuick() {"""
if a in c:
    c = c.replace(a, r); ok.append("mathCancel")
else:
    print("MISS-mathCancel")

# 3) 编辑模式插入：找不到公式元素时整体替换为公式（防止"源码+新公式"混合乱码）
a = """    mathEditMode.value = false
  }
  let html = ''
  try {
    html = tex2svgHtml(raw, true)
  } catch (err) {
    html = '<span style="color:#e74c3c">[公式错误]</span>'
  }"""
r = """    mathEditMode.value = false
  }
  // 编辑模式但节点内没有公式元素（纯源码/文字节点）：整体替换为公式，防止源码与新公式混合
  if (mathEditMode.value) {
    let editHtml = ''
    try { editHtml = tex2svgHtml(raw, true) } catch (err) { editHtml = '<span style="color:#e74c3c">[公式错误]</span>' }
    mind.value.renderer.setNodeDataRender(node, { text: editHtml, richText: true })
    mathEditMode.value = false
    mathDialog.value = false
    saveDoc()
    setTimeout(() => { try { if (mind.value) mind.value.render() } catch (e) {} }, 400)
    return
  }
  let html = ''
  try {
    html = tex2svgHtml(raw, true)
  } catch (err) {
    html = '<span style="color:#e74c3c">[公式错误]</span>'
  }"""
if a in c:
    c = c.replace(a, r); ok.append("edit-replace")
else:
    print("MISS-edit-replace")

with open(p, "w", encoding="utf-8", newline="") as f:
    f.write(c.replace("\n", "\r\n"))
print("done: " + ", ".join(ok))
