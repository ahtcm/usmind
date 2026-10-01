# -*- coding: utf-8 -*-
import io

p = r"C:\Users\25750\Desktop\usmind\code\web-user\src\views\MemberView.vue"
with open(p, "r", encoding="utf-8", newline="") as f:
    c = f.read().replace("\r\n", "\n")
ok = []

a = ".redeem-input { flex: 1; background: rgba(255,255,255,.06); border: 1px solid var(--border); color: var(--text); border-radius: 8px; padding: 9px 12px; font-size: 14px; letter-spacing: 1px; text-transform: uppercase; }"
r = ".redeem-input { flex: 1; min-width: 0; background: rgba(255,255,255,.06); border: 1px solid var(--border); color: var(--text); border-radius: 8px; padding: 9px 10px; font-size: 13px; letter-spacing: 1px; text-transform: uppercase; }"
if a in c:
    c = c.replace(a, r); ok.append("input")
else:
    print("MISS-input")

a = ".redeem-btn { background: linear-gradient(135deg, #f0b429, #f08c2e); border: none; color: #1a1c20; font-weight: 700; border-radius: 8px; padding: 0 18px; font-size: 13px; cursor: pointer; }"
r = ".redeem-btn { flex-shrink: 0; white-space: nowrap; background: linear-gradient(135deg, #f0b429, #f08c2e); border: none; color: #1a1c20; font-weight: 700; border-radius: 8px; padding: 0 14px; font-size: 13px; cursor: pointer; }"
if a in c:
    c = c.replace(a, r); ok.append("btn")
else:
    print("MISS-btn")

with open(p, "w", encoding="utf-8", newline="") as f:
    f.write(c.replace("\n", "\r\n"))
print("done: " + ", ".join(ok))
