# -*- coding: utf-8 -*-
# 中心节点（根节点）支持折叠/展开，折叠后显示子节点数
# 幂等：已应用则跳过；构建/重装依赖后运行本脚本即可
import os

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(SCRIPT_DIR, "..", ".."))
base = os.path.join(ROOT, "code", "web-user", "node_modules", "simple-mind-map", "src")

def patch(rel, pairs):
    path = os.path.join(base, *rel.split("/"))
    with open(path, "r", encoding="utf-8", newline="") as f:
        c = f.read().replace("\r\n", "\n")
    changed = 0
    for old, new in pairs:
        if old in c:
            c = c.replace(old, new)
            changed += 1
    if changed:
        with open(path, "w", encoding="utf-8", newline="") as f:
            f.write(c.replace("\n", "\r\n"))
        print("PATCHED", rel)
    else:
        print("SKIP(已应用)", rel)

# 1) 展开按钮占位元素：根节点也渲染
patch("core/render/node/nodeExpandBtnPlaceholderRect.js", [
    ("if (this.getChildrenLength() <= 0 || this.isRoot) {",
     "if (this.getChildrenLength() <= 0) {"),
])

# 2) 展开按钮本体：根节点也渲染
patch("core/render/node/nodeExpandBtn.js", [
    ("if (this.getChildrenLength() <= 0 || this.isRoot) {",
     "if (this.getChildrenLength() <= 0) {"),
])

# 3) 快捷键切换展开：根节点也允许
patch("core/render/Render.js", [
    ("if (node.nodeData.children.length <= 0 || node.isRoot) {",
     "if (node.nodeData.children.length <= 0) {"),
])

print("ALL DONE")
