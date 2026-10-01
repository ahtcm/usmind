# -*- coding: utf-8 -*-
import io

p = r"C:\Users\25750\Desktop\usmind\code\web-user\src\views\MemberView.vue"
with open(p, "r", encoding="utf-8", newline="") as f:
    c = f.read().replace("\r\n", "\n")
ok = []

# 1) plan-grid -> 横向滑动 + 隐藏滚动条
a = ".plan-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; margin-bottom: 16px; }"
r = """.plan-grid { display: flex; gap: 10px; margin-bottom: 16px; overflow-x: auto; padding-bottom: 2px; scrollbar-width: none; -ms-overflow-style: none; }
.plan-grid::-webkit-scrollbar { display: none; }"""
if a in c:
    c = c.replace(a, r); ok.append("plan-grid")
else:
    print("MISS-plan-grid")

# 2) plan-card -> 固定宽度横滑单元
a = ".plan-card { background: rgba(255,255,255,.05); border: 1px solid var(--border); border-radius: 12px; padding: 14px 10px; text-align: center; cursor: pointer; transition: all .15s; }"
r = ".plan-card { flex: 0 0 150px; width: 150px; background: rgba(255,255,255,.05); border: 1px solid var(--border); border-radius: 12px; padding: 14px 10px; text-align: center; cursor: pointer; transition: all .15s; }"
if a in c:
    c = c.replace(a, r); ok.append("plan-card")
else:
    print("MISS-plan-card")

# 3) plan-name -> 名称与折扣标签同一行
a = ".plan-name { font-size: 13px; font-weight: 600; }"
r = """.plan-name { display: flex; align-items: center; justify-content: center; gap: 4px; white-space: nowrap; font-size: 13px; font-weight: 600; }
.plan-tag { flex-shrink: 0; }"""
if a in c:
    c = c.replace(a, r); ok.append("plan-name")
else:
    print("MISS-plan-name")

with open(p, "w", encoding="utf-8", newline="") as f:
    f.write(c.replace("\n", "\r\n"))
print("done: " + ", ".join(ok))
