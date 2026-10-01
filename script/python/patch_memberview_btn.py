# -*- coding: utf-8 -*-
import io

p = r"C:\Users\25750\Desktop\usmind\code\web-user\src\views\MemberView.vue"
with open(p, "r", encoding="utf-8", newline="") as f:
    c = f.read().replace("\r\n", "\n")
ok = []

# 1) 模板：卡片结构统一（原价独立占位行 + 选中卡 class + 按钮文案）
a = '''        <div v-for="p in filteredPlans" :key="p.id" class="plan-card" :class="{ featured: p.days >= 365 }" @click="selectPlan(p)">
          <div class="plan-name">
            {{ p.name }}
            <span v-if="p.discountPercent > 0" class="plan-tag">-{{ p.discountPercent }}%</span>
          </div>
          <div class="plan-price">
            ¥{{ Number(p.price || 0).toFixed(2) }}
            <span v-if="p.originalPrice > 0 && Number(p.originalPrice) > Number(p.price)" class="plan-original">¥{{ Number(p.originalPrice).toFixed(2) }}</span>
          </div>
          <div class="plan-days">{{ p.days }} 天有效</div>
          <button class="plan-btn" :class="{ active: selected?.id === p.id }">
            {{ selected?.id === p.id ? '已选择' : '选择套餐' }}
          </button>
        </div>'''
r = '''        <div v-for="p in filteredPlans" :key="p.id" class="plan-card" :class="{ selected: selected?.id === p.id, featured: p.days >= 365 }" @click="selectPlan(p)">
          <div class="plan-name">
            {{ p.name }}
            <span v-if="p.discountPercent > 0" class="plan-tag">-{{ p.discountPercent }}%</span>
          </div>
          <div class="plan-price">¥{{ Number(p.price || 0).toFixed(2) }}</div>
          <div class="plan-original-row">
            <span v-if="p.originalPrice > 0 && Number(p.originalPrice) > Number(p.price)" class="plan-original">¥{{ Number(p.originalPrice).toFixed(2) }}</span>
          </div>
          <div class="plan-days">{{ p.days }} 天有效</div>
          <button class="plan-btn" :class="{ active: selected?.id === p.id }">
            {{ selected?.id === p.id ? '✓ 已选择' : '选择套餐' }}
          </button>
        </div>'''
if a in c:
    c = c.replace(a, r); ok.append("template")
else:
    print("MISS-template")

# 2) style：卡片统一列布局 + 按钮底部对齐 + 原价行固定高 + 选中高亮
a = ".plan-card { flex: 0 0 150px; width: 150px; background: rgba(255,255,255,.05); border: 1px solid var(--border); border-radius: 12px; padding: 14px 10px; text-align: center; cursor: pointer; transition: all .15s; }"
r = """.plan-card { flex: 0 0 150px; width: 150px; display: flex; flex-direction: column; min-height: 170px; background: rgba(255,255,255,.05); border: 1px solid var(--border); border-radius: 12px; padding: 14px 10px; text-align: center; cursor: pointer; transition: all .18s; }
.plan-card:hover { border-color: #4a5262; transform: translateY(-2px); }
.plan-card.selected { border-color: rgba(240,180,41,.65); box-shadow: 0 0 0 1px rgba(240,180,41,.35), 0 8px 20px rgba(0,0,0,.35); }
.plan-original-row { height: 18px; display: flex; align-items: center; justify-content: center; }"""
if a in c:
    c = c.replace(a, r); ok.append("plan-card")
else:
    print("MISS-plan-card")

# 3) plan-btn 底部固定
a = ".plan-btn { width: 100%; border: 1px solid var(--border); background: transparent; color: var(--text); border-radius: 8px; padding: 7px 0; font-size: 12px; cursor: pointer; }"
r = ".plan-btn { width: 100%; margin-top: auto; border: 1px solid var(--border); background: transparent; color: var(--text); border-radius: 8px; padding: 7px 0; font-size: 12px; cursor: pointer; }"
if a in c:
    c = c.replace(a, r); ok.append("plan-btn")
else:
    print("MISS-plan-btn")

# 4) active 按钮金色渐变
a = ".plan-btn.active { background: var(--accent); border-color: transparent; color: #fff; }"
r = ".plan-btn.active { background: linear-gradient(135deg, #f0b429, #f08c2e); border-color: transparent; color: #1a1c20; font-weight: 700; }"
if a in c:
    c = c.replace(a, r); ok.append("active")
else:
    print("MISS-active")

with open(p, "w", encoding="utf-8", newline="") as f:
    f.write(c.replace("\n", "\r\n"))
print("done: " + ", ".join(ok))
