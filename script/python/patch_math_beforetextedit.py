# -*- coding: utf-8 -*-
import io

p = r"C:\Users\25750\Desktop\usmind\code\web-user\src\views\Editor.vue"
with open(p, "r", encoding="utf-8", newline="") as f:
    c = f.read().replace("\r\n", "\n")
ok = []

a = """    beforeTextEdit: (node) => {
      try {
        const nid = node.uid || (node.nodeData && node.nodeData.uid)
        if (nid && myEditNodeId !== nid) {
          const lk = nodeLocks[nid]
          if (lk && lk.userId !== myUid()) {
            toast('该节点已被 ' + lk.userName + ' 锁定，无法编辑', 'error')
            return false
          }
        }
      } catch (e) {}
      return true
    },"""
r = """    beforeTextEdit: (node) => {
      try {
        // 公式节点：双击走公式编辑弹窗，禁止库自动进入文本编辑（避免编辑框显示 SVG 源码乱码）
        const ndText = (node.nodeData && node.nodeData.text) || ''
        const getText = (() => { try { return node.getData ? node.getData('text') : '' } catch (e) { return '' } })()
        const text = ndText || getText || ''
        if (text && (text.indexOf('katex') >= 0 || text.indexOf('mjx-container') >= 0 || text.indexOf('MathJax') >= 0)) {
          return false
        }
        const nid = node.uid || (node.nodeData && node.nodeData.uid)
        if (nid && myEditNodeId !== nid) {
          const lk = nodeLocks[nid]
          if (lk && lk.userId !== myUid()) {
            toast('该节点已被 ' + lk.userName + ' 锁定，无法编辑', 'error')
            return false
          }
        }
      } catch (e) {}
      return true
    },"""
if a in c:
    c = c.replace(a, r); ok.append("beforeTextEdit")
else:
    print("MISS-beforeTextEdit")

with open(p, "w", encoding="utf-8", newline="") as f:
    f.write(c.replace("\n", "\r\n"))
print("done: " + ", ".join(ok))
