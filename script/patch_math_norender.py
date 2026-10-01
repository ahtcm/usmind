# -*- coding: utf-8 -*-
import io

p = r"C:\Users\25750\Desktop\usmind\code\web-user\src\views\Editor.vue"
with open(p, "r", encoding="utf-8", newline="") as f:
    c = f.read().replace("\r\n", "\n")
ok = []

# 1) 取消：去掉 render()（render() 全量重渲染会使 MathJax SVG 失效显示空白/模糊），只关弹窗
a = """function mathCancel() {
  mathDialog.value = false
  // 关闭弹窗后延迟重渲染，恢复弹窗操作可能引起的公式渲染异常
  setTimeout(() => { try { if (mind.value) mind.value.render() } catch (e) {} }, 400)
}"""
r = """function mathCancel() {
  mathDialog.value = false
}"""
if a in c:
    c = c.replace(a, r); ok.append("cancel-norender")
else:
    print("MISS-cancel-norender")

# 2) 编辑模式替换（第一个 render 调用点）
a = """    if (replaced) {
      mind.value.renderer.setNodeDataRender(node, { text: replaced, richText: true })
      mathEditMode.value = false
      mathDialog.value = false
      saveDoc()
      setTimeout(() => { try { if (mind.value) mind.value.render() } catch (e) {} }, 30)
      return
    }"""
r = """    if (replaced) {
      mind.value.renderer.setNodeDataRender(node, { text: replaced, richText: true })
      mathEditMode.value = false
      mathDialog.value = false
      saveDoc()
      // 不调用全量 render()：全量重渲染会使 MathJax SVG 失效（公式空白/模糊），刷新后才恢复。
      // setNodeDataRender 已局部更新节点并触发布局。
      return
    }"""
if a in c:
    c = c.replace(a, r); ok.append("edit-render1")
else:
    print("MISS-edit-render1")

# 3) 编辑模式整体替换（第二个 render 调用点）
a = """    mind.value.renderer.setNodeDataRender(node, { text: editHtml, richText: true })
    mathEditMode.value = false
    mathDialog.value = false
    saveDoc()
    setTimeout(() => { try { if (mind.value) mind.value.render() } catch (e) {} }, 400)
    return
  }"""
r = """    mind.value.renderer.setNodeDataRender(node, { text: editHtml, richText: true })
    mathEditMode.value = false
    mathDialog.value = false
    saveDoc()
    return
  }"""
if a in c:
    c = c.replace(a, r); ok.append("edit-render2")
else:
    print("MISS-edit-render2")

# 4) 追加插入分支（第三个 render 调用点，2217-2232 附近）
a = """  mathDialog.value = false
  saveDoc()
  // 公式写入后自动重渲染画布，避免公式节点塌陷（替代整页刷新）
  setTimeout(() => { try { if (mind.value) mind.value.render() } catch (e) {} }, 30)
}"""
r = """  mathDialog.value = false
  saveDoc()
  // 不调用全量 render()：全量重渲染会使 MathJax SVG 失效（公式空白/模糊），刷新后才恢复。
}"""
if a in c:
    c = c.replace(a, r); ok.append("append-render")
else:
    print("MISS-append-render")

with open(p, "w", encoding="utf-8", newline="") as f:
    f.write(c.replace("\n", "\r\n"))
print("done: " + ", ".join(ok))
