# -*- coding: utf-8 -*-
import io

p = r"C:\Users\25750\Desktop\usmind\code\web-user\src\views\MemberView.vue"
with open(p, "r", encoding="utf-8", newline="") as f:
    c = f.read().replace("\r\n", "\n")
ok = []

# 1) 查询下拉 + 套餐卡原价/折扣（替换 plan-grid 整块）
a = '''      <div class="plan-grid">
        <div v-for="p in plans" :key="p.id" class="plan-card" :class="{ featured: p.days >= 365 }" @click="selectPlan(p)">
          <div class="plan-name">{{ p.name }}</div>
          <div class="plan-price">¥{{ p.price.toFixed(2) }}</div>
          <div class="plan-days">{{ p.days }} 天有效</div>
          <button class="plan-btn" :class="{ active: selected?.id === p.id }">
            {{ selected?.id === p.id ? '已选择' : '选择套餐' }}
          </button>
        </div>
      </div>'''
r = '''      <div class="plan-filter">
        <select v-model="filterPlan" class="plan-select">
          <option value="">全部套餐</option>
          <option value="month">月度会员</option>
          <option value="quarter">季度会员</option>
          <option value="year">年度会员</option>
        </select>
        <span class="plan-filter-tip">{{ filterPlan ? filteredPlans.length + ' 个套餐' : plans.length + ' 个套餐' }}</span>
      </div>

      <div class="plan-grid">
        <div v-for="p in filteredPlans" :key="p.id" class="plan-card" :class="{ featured: p.days >= 365 }" @click="selectPlan(p)">
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
        </div>
      </div>'''
if a in c:
    c = c.replace(a, r); ok.append("plan-grid")
else:
    print("MISS-plan-grid")

# 2) 兑换码区：放在 pay-area 前
a = '''      <div class="pay-area">'''
r = '''      <div class="redeem-area">
        <div class="redeem-title">有兑换码？输入即可开通会员</div>
        <div class="redeem-row">
          <input v-model="redeemCode" class="redeem-input" placeholder="请输入兑换码" maxlength="32" />
          <button class="redeem-btn" @click="doRedeem" :disabled="redeeming || !redeemCode.trim()">
            {{ redeeming ? '兑换中...' : '立即兑换' }}
          </button>
        </div>
      </div>

      <div class="pay-area">'''
if a in c:
    c = c.replace(a, r); ok.append("redeem-area")
else:
    print("MISS-redeem-area")

# 3) script: add refs
a = "const plans = ref([])\nconst selected = ref(null)"
r = "const plans = ref([])\nconst selected = ref(null)\nconst filterPlan = ref('')\nconst redeemCode = ref('')\nconst redeeming = ref(false)"
if a in c:
    c = c.replace(a, r); ok.append("refs")
else:
    print("MISS-refs")

# 4) script: add filteredPlans computed
a = "const expireText = computed(() => (member.value && member.value.expire) ? member.value.expire.slice(0, 10) : '')"
r = """const expireText = computed(() => (member.value && member.value.expire) ? member.value.expire.slice(0, 10) : '')
const filteredPlans = computed(() => {
  if (!filterPlan.value) return plans.value || []
  const daysMap = { month: 30, quarter: 90, year: 365 }
  const d = daysMap[filterPlan.value]
  return (plans.value || []).filter(p => p.days === d)
})"""
if a in c:
    c = c.replace(a, r); ok.append("computed")
else:
    print("MISS-computed")

# 5) script: add doRedeem before doPay
a = "async function doPay() {"
r = """async function doRedeem() {
  if (!redeemCode.value.trim()) return
  redeeming.value = true
  err.value = ''
  try {
    const d = await memberApi.redeem(redeemCode.value.trim())
    member.value = { ...(member.value || {}), level: d.level, expire: d.expire, source: d.source, isVip: true }
    userStore.setUser({ ...userStore.user, memberLevel: d.level, memberExpire: d.expire })
    toast('兑换成功，会员已开通')
    redeemCode.value = ''
  } catch (e) {
    err.value = e.message || '兑换失败'
  } finally {
    redeeming.value = false
  }
}

async function doPay() {"""
if a in c:
    c = c.replace(a, r); ok.append("doRedeem")
else:
    print("MISS-doRedeem")

# 6) styles: add redeem/plan filter/tag styles before .member-off
a = ".member-off { display: flex;"
r = """.plan-filter { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; }
.plan-select { background: rgba(255,255,255,.06); border: 1px solid var(--border); color: var(--text); border-radius: 8px; padding: 6px 10px; font-size: 13px; }
.plan-select option { background: #22262d; color: #e6e8eb; }
.plan-filter-tip { font-size: 12px; color: var(--text-dim); }
.plan-tag { display: inline-block; background: #f26d6d; color: #fff; font-size: 10px; border-radius: 4px; padding: 1px 5px; margin-left: 4px; vertical-align: middle; }
.plan-original { font-size: 12px; color: var(--text-dim); text-decoration: line-through; font-weight: 400; margin-left: 4px; }
.redeem-area { margin: 14px 0 4px; padding: 12px; border: 1px dashed var(--border); border-radius: 10px; background: rgba(255,255,255,.03); }
.redeem-title { font-size: 12px; color: var(--text-dim); margin-bottom: 8px; }
.redeem-row { display: flex; gap: 8px; }
.redeem-input { flex: 1; background: rgba(255,255,255,.06); border: 1px solid var(--border); color: var(--text); border-radius: 8px; padding: 9px 12px; font-size: 14px; letter-spacing: 1px; text-transform: uppercase; }
.redeem-input::placeholder { letter-spacing: 0; }
.redeem-btn { background: linear-gradient(135deg, #f0b429, #f08c2e); border: none; color: #1a1c20; font-weight: 700; border-radius: 8px; padding: 0 18px; font-size: 13px; cursor: pointer; }
.redeem-btn:disabled { opacity: .5; cursor: not-allowed; }
.member-off { display: flex;"""
if a in c:
    c = c.replace(a, r); ok.append("styles")
else:
    print("MISS-styles")

with open(p, "w", encoding="utf-8", newline="") as f:
    f.write(c.replace("\n", "\r\n"))
print("done: " + ", ".join(ok))
