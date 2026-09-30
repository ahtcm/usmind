<template>
  <div class="login-page">
    <div class="login-box">
      <h2>USMIND 思维导图</h2>
      <div class="login-tabs">
        <button :class="{ active: mode === 'login' }" @click="mode = 'login'">登 录</button>
        <button :class="{ active: mode === 'register' }" @click="mode = 'register'">注 册</button>
      </div>

      <!-- 登录 -->
      <template v-if="mode === 'login'">
        <div class="field">
          <label>用户名</label>
          <input v-model="username" type="text" placeholder="请输入用户名" @keyup.enter="doLogin" />
        </div>
        <div class="field">
          <label>密码</label>
          <input v-model="password" type="password" placeholder="请输入密码" @keyup.enter="doLogin" />
        </div>
        <button class="btn-login" @click="doLogin" :disabled="loading">{{ loading ? '登录中...' : '登 录' }}</button>
        <div v-if="wechatLoginEnabled" class="wx-row">
          <div class="wx-divider"><span>其他登录方式</span></div>
          <button class="btn-wx" @click="wechatLogin">
            <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor"><path d="M8.7 4C4.9 4 2 6.5 2 9.7c0 1.8 1 3.4 2.6 4.5l-.7 2 2.2-1.1c.6.2 1.2.3 1.9.3h.3c-.2-.6-.3-1.2-.3-1.8 0-2.9 2.6-5.3 5.9-5.3h.3C13.5 5.7 11.3 4 8.7 4zm-2 3.4c.5 0 .9.4.9.9s-.4.9-.9.9-.9-.4-.9-.9.4-.9.9-.9zm4.2 0c.5 0 .9.4.9.9s-.4.9-.9.9-.9-.4-.9-.9.4-.9.9-.9zM15 9.4c-3 0-5.5 2.1-5.5 4.8S12 19 15 19c.6 0 1.2-.1 1.7-.3l1.9.9-.6-1.7c1.2-.9 2-2.1 2-3.6 0-2.7-2.5-4.9-5.5-4.9zm-1.5 2.5c.5 0 .9.4.9.9s-.4.9-.9.9-.9-.4-.9-.9.4-.9.9-.9zm3 0c.5 0 .9.4.9.9s-.4.9-.9.9-.9-.4-.9-.9.4-.9.9-.9z"/></svg>
            微信扫码登录
          </button>
        </div>
      </template>

      <!-- 注册 -->
      <template v-else>
        <div class="field">
          <label>头像（可选）</label>
          <div class="avatar-upload-row">
            <label class="avatar-upload" :class="{ 'has-img': reg.avatarPreview }">
              <img v-if="reg.avatarPreview" :src="reg.avatarPreview" alt="avatar" />
              <span v-else>＋ 选择图片</span>
              <input type="file" accept="image/*" @change="onAvatarPick" />
            </label>
            <span class="avatar-tip">支持 jpg/png，≤2MB</span>
          </div>
        </div>
        <div class="field">
          <label>用户名</label>
          <input v-model="reg.username" type="text" placeholder="请输入用户名" />
        </div>
        <div class="field">
          <label>密码</label>
          <input v-model="reg.password" type="password" placeholder="至少 6 位，包含字母和数字" />
        </div>
        <div class="field">
          <label>确认密码</label>
          <input v-model="reg.password2" type="password" placeholder="再次输入密码" />
        </div>
        <div class="field">
          <label>昵称（可选）</label>
          <input v-model="reg.nickname" type="text" placeholder="默认同用户名" />
        </div>
        <div class="field">
          <label>手机号{{ smsEnabled ? '（必填）' : '（可选）' }}</label>
          <input v-model="reg.phone" type="text" placeholder="用于找回密码" />
        </div>
        <div class="field" v-if="captchaEnabled">
          <label>图形验证码</label>
          <div class="captcha-row">
            <input v-model="reg.captchaCode" type="text" placeholder="先输入图形验证码" />
            <img v-if="captchaImg" :src="captchaImg" class="captcha-img" title="点击刷新" @click="loadCaptcha" />
          </div>
        </div>
        <div class="field" v-if="smsEnabled">
          <label>短信验证码</label>
          <div class="sms-row">
            <input v-model="reg.smsCode" type="text" placeholder="输入短信验证码" />
            <button type="button" class="sms-btn" @click="sendSmsCode" :disabled="smsCountdown > 0">{{ smsCountdown > 0 ? smsCountdown + 's' : '获取验证码' }}</button>
          </div>
        </div>
        <div class="field">
          <label>邮箱（可选）</label>
          <input v-model="reg.email" type="text" placeholder="用于找回密码" />
        </div>
        <button class="btn-login" @click="doRegister" :disabled="loading">{{ loading ? '注册中...' : '注 册' }}</button>
      </template>

      <div v-if="err" class="err">{{ err }}</div>
      <div class="login-foot">© {{ new Date().getFullYear() }} USMIND · 思维导图协作平台</div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import request from '../api/request'
import { authApi } from '../api/auth'
import { configApi } from '../api/member'
import { useUserStore } from '../stores/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const mode = ref('login')
const username = ref('')
const password = ref('')
const err = ref('')
const loading = ref(false)
const captchaEnabled = ref(false)
const captchaImg = ref('')
const captchaUuid = ref('')
const smsEnabled = ref(false)
const smsCountdown = ref(0)
let smsTimer = null
const wechatLoginEnabled = ref(false)

const reg = ref({ username: '', password: '', password2: '', nickname: '', phone: '', email: '', captchaCode: '', avatar: '', avatarPreview: '' })

onMounted(async () => {
  // 微信登录回调：/login?code=xxx&state=xxx
  if (route.query.code && route.query.state) {
    try {
      const d = await authApi.wechatCallback(route.query.code, route.query.state)
      userStore.setToken(d.token)
      userStore.setUser(d.user)
      router.replace('/maps')
      return
    } catch (e) {
      err.value = e.message || '微信登录失败'
      router.replace({ path: '/login', query: {} })
      return
    }
  }
  try {
    const c = await configApi.client()
    captchaEnabled.value = !!c.authCaptchaEnabled
    wechatLoginEnabled.value = !!c.wechatLoginEnabled
    smsEnabled.value = !!c.authSmsEnabled
    if (captchaEnabled.value) loadCaptcha()
  } catch (e) { /* 忽略 */ }
})

async function wechatLogin() {
  err.value = ''
  try {
    const d = await authApi.wechatUrl()
    // 新窗口打开微信授权页；授权后微信跳回 redirect_uri（本页 /login?code=&state=）
    window.open(d.url, '_blank')
  } catch (e) {
    err.value = e.message || '微信登录未开启'
  }
}

async function onAvatarPick(e) {
  const f = e.target.files && e.target.files[0]
  if (!f) return
  if (!/^image\//.test(f.type)) { err.value = '仅支持图片文件'; return }
  if (f.size > 2 * 1024 * 1024) { err.value = '头像不能超过 2MB'; return }
  err.value = ''
  try {
    const fd = new FormData()
    fd.append('file', f)
    const up = await request.post('/file/upload-avatar', fd)
    reg.value.avatar = up.url || ''
    reg.value.avatarPreview = URL.createObjectURL(f)
  } catch (e2) {
    err.value = (e2 && e2.message) || '头像上传失败'
  }
}

async function sendSmsCode() {
  if (!reg.value.phone || reg.value.phone.length < 6) { err.value = '请先输入正确的手机号'; return }
  if (captchaEnabled.value && !reg.value.captchaCode) { err.value = '请先输入图形验证码'; return }
  err.value = ''
  try {
    await request.post('/auth/send-code', {
      phone: reg.value.phone,
      captchaUuid: captchaEnabled.value ? captchaUuid.value : undefined,
      captchaCode: captchaEnabled.value ? reg.value.captchaCode : undefined
    })
    smsCountdown.value = 60
    if (smsTimer) clearInterval(smsTimer)
    smsTimer = setInterval(() => {
      smsCountdown.value--
      if (smsCountdown.value <= 0 && smsTimer) { clearInterval(smsTimer); smsTimer = null }
    }, 1000)
  } catch (e) {
    err.value = (e && e.message) || '验证码发送失败'
    if ((e && e.message || '').includes('图形验证码')) loadCaptcha()
  }
}

async function loadCaptcha() {
  try {
    const d = await authApi.captcha()
    captchaUuid.value = d.uuid
    captchaImg.value = d.image
  } catch (e) { /* 忽略 */ }
}

async function doLogin() {
  if (!username.value || !password.value) {
    err.value = '请输入用户名和密码'
    return
  }
  loading.value = true
  err.value = ''
  try {
    const data = await authApi.login({ username: username.value, password: password.value })
    userStore.setToken(data.token)
    userStore.setUser(data.user)
    router.push(route.query.redirect || '/maps')
  } catch (e) {
    err.value = e.message || '登录失败'
  } finally {
    loading.value = false
  }
}

async function doRegister() {
  const r = reg.value
  if (!r.username || !r.password) { err.value = '请输入用户名和密码'; return }
  if (r.password.length < 6 || !/[A-Za-z]/.test(r.password) || !/\d/.test(r.password)) {
    err.value = '密码至少 6 位且需包含字母和数字'; return
  }
  if (r.password !== r.password2) { err.value = '两次输入的密码不一致'; return }
  if (captchaEnabled.value && !r.captchaCode) { err.value = '请输入图形验证码'; return }
  if (smsEnabled.value && !r.smsCode) { err.value = '请输入短信验证码'; return }
  if (smsEnabled.value && !r.phone) { err.value = '请输入手机号'; return }
  loading.value = true
  err.value = ''
  try {
    await authApi.register({
      username: r.username,
      password: r.password,
      nickname: r.nickname || undefined,
      phone: r.phone || undefined,
      email: r.email || undefined,
      avatar: r.avatar || undefined,
      captchaUuid: captchaUuid.value || undefined,
      captchaCode: r.captchaCode || undefined,
      smsCode: r.smsCode || undefined
    })
    // 注册成功自动登录
    const data = await authApi.login({ username: r.username, password: r.password })
    userStore.setToken(data.token)
    userStore.setUser(data.user)
    router.push('/maps')
  } catch (e) {
    err.value = e.message || '注册失败'
    if (captchaEnabled.value) loadCaptcha()
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-tabs { display: flex; gap: 4px; background: rgba(255,255,255,.06); border-radius: 10px; padding: 3px; margin-bottom: 18px; }
.login-tabs button { flex: 1; background: transparent; border: none; color: var(--text-dim); padding: 7px 0; border-radius: 8px; cursor: pointer; font-size: 14px; font-weight: 600; transition: all .15s; }
.login-tabs button.active { background: var(--accent); color: #fff; }
.captcha-row { display: flex; gap: 8px; }
.captcha-row input { flex: 1; }
.captcha-img { width: 108px; height: 40px; border-radius: 8px; border: 1px solid var(--border); cursor: pointer; background: rgba(255,255,255,.06); object-fit: cover; flex-shrink: 0; }
.sms-row { display: flex; gap: 8px; }
.sms-row input { flex: 1; }
.sms-btn { flex-shrink: 0; padding: 0 12px; height: 40px; border-radius: 8px; border: 1px solid var(--border); background: linear-gradient(135deg, #4f8cff, #3b6ef5); color: #fff; cursor: pointer; font-size: 13px; white-space: nowrap; }
.sms-btn:disabled { opacity: .6; cursor: not-allowed; }
.login-foot { text-align: center; margin-top: 18px; font-size: 12px; color: var(--text-dim); opacity: .8; }
.wx-row { margin-top: 14px; }
.wx-divider { display: flex; align-items: center; gap: 10px; color: var(--text-dim); font-size: 12px; margin-bottom: 10px; }
.wx-divider::before, .wx-divider::after { content: ''; flex: 1; height: 1px; background: var(--border); }
.btn-wx { width: 100%; display: flex; align-items: center; justify-content: center; gap: 8px; background: #07c160; color: #fff; border: none; border-radius: 8px; padding: 10px 0; font-size: 14px; font-weight: 600; cursor: pointer; transition: filter .15s; }
.btn-wx:hover { filter: brightness(1.08); }
.avatar-upload-row { display: flex; align-items: center; gap: 12px; }
.avatar-upload { width: 64px; height: 64px; border-radius: 50%; border: 1px dashed var(--border); display: flex; align-items: center; justify-content: center; overflow: hidden; cursor: pointer; background: rgba(255,255,255,.05); flex-shrink: 0; }
.avatar-upload img { width: 100%; height: 100%; object-fit: cover; }
.avatar-upload span { font-size: 12px; color: var(--text-dim); }
.avatar-upload input { display: none; }
.avatar-tip { font-size: 12px; color: var(--text-dim); }
</style>
