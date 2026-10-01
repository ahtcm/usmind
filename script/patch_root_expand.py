# -*- coding: utf-8 -*-
# 中心节点（根节点）支持折叠/展开，折叠后显示子节点数
base = r"C:\Users\25750\Desktop\usmind\code\web-user\node_modules\simple-mind-map\src"

def patch(path, pairs):
    with open(path, "r", encoding="utf-8", newline="") as f:
        c = f.read().replace("\r\n", "\n")
    for old, new in pairs:
        assert old in c, f"MISS {path}: {old[:60]}"
        c = c.replace(old, new)
    with open(path, "w", encoding="utf-8", newline="") as f:
        f.write(c.replace("\n", "\r\n"))
    print("OK", path.split("simple-mind-map")[-1])

# 1) 展开按钮占位元素：根节点也渲染
patch(base + r"\core\render\node\nodeExpandBtnPlaceholderRect.js", [
    ("if (this.getChildrenLength() <= 0 || this.isRoot) {",
     "if (this.getChildrenLength() <= 0) {"),
])

# 2) 展开按钮本体：根节点也渲染
patch(base + r"\core\render\node\nodeExpandBtn.js", [
    ("if (this.getChildrenLength() <= 0 || this.isRoot) {",
     "if (this.getChildrenLength() <= 0) {"),
])

# 3) 快捷键切换展开（空格/快捷键）：根节点也允许
patch(base + r"\core\render\Render.js", [
    ("if (node.nodeData.children.length <= 0 || node.isRoot) {",
     "if (node.nodeData.children.length <= 0) {"),
])

print("ALL OK 根节点折叠/展开已放开")
