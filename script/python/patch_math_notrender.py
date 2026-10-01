# -*- coding: utf-8 -*-
p = r"C:\Users\25750\Desktop\usmind\code\web-user\src\views\Editor.vue"
with open(p, "r", encoding="utf-8", newline="") as f:
    c = f.read().replace("\r\n", "\n")

# 公式插入 3 处 setNodeDataRender 传 notRender=true：
# 节点尺寸变化时库默认会触发 mindMap.render() 全量重绘，导致 MathJax SVG 失效（公式塌陷/空白），
# 无字节点(0->大)必触发；传 true 后仅做节点级 reRender，公式保持有效。
fixes = [
    ("        mind.value.renderer.setNodeDataRender(node, { text: replaced, richText: true })\n        mathEditMode.value = false",
     "        mind.value.renderer.setNodeDataRender(node, { text: replaced, richText: true }, true)\n        mathEditMode.value = false"),
    ("    mind.value.renderer.setNodeDataRender(node, { text: editHtml, richText: true })\n    mathEditMode.value = false",
     "    mind.value.renderer.setNodeDataRender(node, { text: editHtml, richText: true }, true)\n    mathEditMode.value = false"),
    ("    mind.value.renderer.setNodeDataRender(node, { text: newText, richText: true })\n  }\n  mathDialog.value = false",
     "    mind.value.renderer.setNodeDataRender(node, { text: newText, richText: true }, true)\n  }\n  mathDialog.value = false"),
]
for old, new in fixes:
    assert old in c, 'MISS: ' + old[:80]
    c = c.replace(old, new)

with open(p, "w", encoding="utf-8", newline="") as f:
    f.write(c.replace("\n", "\r\n"))
print("OK 公式插入3处 setNodeDataRender 加 notRender=true")
