<template>
  <div class="member-page">
    <div class="member-box">
      <h2>会员中心</h2>

      <div v-if="!memberEnabled" class="member-off">
        <div class="mo-icon">🔒</div>
        <div class="mo-text">会员功能暂未开放</div>
        <button class="ghost-btn" @click="router.push('/maps')">返回导图列表</button>
      </div>

      <template v-else>
      <div class="member-status" :class="{ vip: isVip }">
        <template v-if="isVip">
          <div class="ms-badge">VIP</div>
          <div class="ms-info">
            <div class="ms-name">{{ nickname }} · 会员有效</div>
            <div class="ms-expire">有效期至 {{ expireText }}</div>
          </div>
        </template>
        <template v-else>
          <div class="ms-info">
            <div class="ms-name">普通用户</div>
            <div class="ms-expire">开通会员解锁更多画布、上传与导出能力</div>
          </div>
        </template>
      </div>

      <div class="plan-grid">
        <div v-for="p in plans" :key="p.id" class="plan-card" :class="{ featured: p.days >= 365 }" @click="selectPlan(p)">
          <div class="plan-name">{{ p.name }}</div>
          <div class="plan-price">¥{{ p.price.toFixed(2) }}</div>
          <div class="plan-days">{{ p.days }} 天有效</div>
          <button class="plan-btn" :class="{ active: selected?.id === p.id }">
            {{ selected?.id === p.id ? '已选择' : '选择套餐' }}
          </button>
        </div>
      </div>

      <div class="pay-area">
        <button class="pay-btn" @click="doPay" :disabled="paying || !selected">
          {{ paying ? '下单中...' : '微信支付 ¥' + (selected ? selected.price.toFixed(2) : '0.00') }}
        </button>
        <div v-if="payHint" class="pay-hint">{{ payHint }}</div>
        <div v-if="payCodeUrl" class="pay-qr-wrap">
          <img :src="payCodeUrl" alt="微信支付二维码" class="pay-qr" />
          <div class="pay-qr-tip">请使用微信扫码支付</div>
        </div>
      </div>

      <div class="member-foot">
        <button class="ghost-btn" @click="router.push('/maps')">返回导图列表</button>
      </div>
      <div v-if="err" class="member-err">{{ err }}</div>
      </template>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { memberApi, configApi } from '../api/member'
import { useUserStore } from '../stores/user'
import { toast } from '../utils/modal'

const router = useRouter()
const userStore = useUserStore()
const plans = ref([])
const selected = ref(null)
const member = ref(null)
const paying = ref(false)
const payCodeUrl = ref('')
const payHint = ref('')
const wechatPayEnabled = ref(false)
const memberEnabled = ref(true)
const err = ref('')

const nickname = computed(() => userStore.user?.nickname || userStore.user?.username || '')
const isVip = computed(() => !!member.value && member.value.level > 0)
const expireText = computed(() => (member.value && member.value.expire) ? member.value.expire.slice(0, 10) : '')

onMounted(async () => {
  try {
    plans.value = await memberApi.plans()
    if (plans.value && plans.value.length) selected.value = plans.value[0]
  } catch (e) { /* 忽略 */ }
  try {
    const d = await memberApi.info()
    member.value = d
  } catch (e) { /* 忽略 */ }
  try {
    const c = await configApi.client()
    memberEnabled.value = c.memberEnabled !== false
    wechatPayEnabled.value = !!c.feature?.wechatPayEnabled
    if (!wechatPayEnabled.value) payHint.value = '微信支付暂未开放，请联系管理员开通'
  } catch (e) { /* 忽略 */ }
})

function selectPlan(p) {
  selected.value = p
  payCodeUrl.value = ''
}

const isMobile = /Android|iPhone|iPad|iPod|Mobile/i.test(navigator.userAgent || '')

async function doPay() {
  if (!selected.value) return
  if (!wechatPayEnabled.value) {
    toast('微信支付暂未开放', 'error')
    return
  }
  paying.value = true
  err.value = ''
  payCodeUrl.value = ''
  try {
    if (isMobile) {
      // 移动端：H5 支付，拉起微信 App 完成支付
      const d = await memberApi.wechatCreateH5(selected.value.id)
      if (d.mwebUrl) {
        location.href = d.mwebUrl
        pollStatus(d.outTradeNo)
        return
      }
      throw new Error('H5 支付链接为空，请改用电脑扫码支付')
    }
    // PC 端：Native 扫码
    const d = await memberApi.wechatCreate(selected.value.id)
    payCodeUrl.value = d.codeUrl
    toast('请使用微信扫码支付')
    pollStatus(d.outTradeNo)
  } catch (e) {
    err.value = e.message || '下单失败'
  } finally {
    paying.value = false
  }
}

function pollStatus(outTradeNo) {
  const timer = setInterval(async () => {
    try {
      const d = await memberApi.info()
      if (d.level > 0) {
        clearInterval(timer)
        member.value = d
        payCodeUrl.value = ''
        toast('支付成功，已开通会员')
        userStore.setUser({ ...userStore.user, memberLevel: d.level, memberExpire: d.expire })
      }
    } catch (e) { /* 忽略 */ }
  }, 3000)
  setTimeout(() => clearInterval(timer), 5 * 60 * 1000)
}
</script>

<style scoped>
.member-page { height: 100vh; display: flex; align-items: center; justify-content: center; background: radial-gradient(1200px 800px at 50% 40%, #1e2229 0%, #16181d 60%); overflow-y: auto; }
.member-box { width: 480px; max-width: 94vw; background: rgba(38,42,49,.9); border: 1px solid var(--border); border-radius: 16px; padding: 28px; box-shadow: 0 18px 50px rgba(0,0,0,.5); margin: 24px 0; }
.member-box h2 { margin-bottom: 18px; font-size: 20px; }
.member-status { display: flex; align-items: center; gap: 12px; padding: 14px; border-radius: 12px; background: rgba(255,255,255,.05); border: 1px solid var(--border); margin-bottom: 18px; }
.member-status.vip { background: linear-gradient(135deg, rgba(240,180,41,.16), rgba(240,140,46,.1)); border-color: rgba(240,180,41,.4); }
.ms-badge { background: linear-gradient(135deg, #f0b429, #f08c2e); color: #1a1c20; font-size: 13px; font-weight: 800; padding: 4px 10px; border-radius: 8px; flex-shrink: 0; }
.ms-name { font-size: 14px; font-weight: 600; }
.ms-expire { font-size: 12px; color: var(--text-dim); margin-top: 3px; }
.plan-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; margin-bottom: 16px; }
.plan-card { background: rgba(255,255,255,.05); border: 1px solid var(--border); border-radius: 12px; padding: 14px 10px; text-align: center; cursor: pointer; transition: all .15s; }
.plan-card:hover { border-color: #4a5262; transform: translateY(-2px); }
.plan-card.featured { border-color: rgba(240,180,41,.5); }
.plan-name { font-size: 13px; font-weight: 600; }
.plan-price { font-size: 20px; font-weight: 800; color: #f0b429; margin: 8px 0 2px; }
.plan-days { font-size: 11px; color: var(--text-dim); margin-bottom: 10px; }
.plan-btn { width: 100%; border: 1px solid var(--border); background: transparent; color: var(--text); border-radius: 8px; padding: 7px 0; font-size: 12px; cursor: pointer; }
.plan-btn.active { background: var(--accent); border-color: transparent; color: #fff; }
.pay-area { margin-top: 4px; }
.pay-btn { width: 100%; background: linear-gradient(135deg, #07c160, #06ad56); border: none; color: #fff; border-radius: 10px; padding: 12px 0; font-size: 15px; font-weight: 700; cursor: pointer; }
.pay-btn:disabled { opacity: .5; cursor: not-allowed; }
.pay-btn:hover:not(:disabled) { filter: brightness(1.08); }
.pay-hint { font-size: 12px; color: var(--text-dim); text-align: center; margin-top: 10px; }
.pay-qr-wrap { display: flex; flex-direction: column; align-items: center; margin-top: 14px; }
.pay-qr { width: 180px; height: 180px; border-radius: 10px; border: 1px solid var(--border); background: #fff; padding: 6px; }
.pay-qr-tip { font-size: 12px; color: var(--text-dim); margin-top: 8px; }
.member-foot { display: flex; justify-content: center; margin-top: 18px; }
.ghost-btn { background: rgba(255,255,255,.08); border: none; color: var(--text); border-radius: 8px; padding: 8px 20px; font-size: 13px; cursor: pointer; }
.ghost-btn:hover { background: rgba(255,255,255,.14); }
.member-err { color: #f26d6d; font-size: 13px; text-align: center; margin-top: 12px; }
.member-off { display: flex; flex-direction: column; align-items: center; gap: 12px; padding: 40px 0 28px; }
.mo-icon { font-size: 40px; opacity: .8; }
.mo-text { font-size: 15px; color: var(--text-dim); }
</style>
