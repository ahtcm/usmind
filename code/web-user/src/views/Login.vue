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
      </template>

      <!-- 注册 -->
      <template v-else>
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
          <label>手机号（可选）</label>
          <input v-model="reg.phone" type="text" placeholder="用于找回密码" />
        </div>
        <div class="field">
          <label>邮箱（可选）</label>
          <input v-model="reg.email" type="text" placeholder="用于找回密码" />
        </div>
        <div class="field" v-if="captchaEnabled">
          <label>图形验证码</label>
          <div class="captcha-row">
            <input v-model="reg.captchaCode" type="text" placeholder="输入右侧验证码" />
            <img v-if="captchaImg" :src="captchaImg" class="captcha-img" title="点击刷新" @click="loadCaptcha" />
          </div>
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

const reg = ref({ username: '', password: '', password2: '', nickname: '', phone: '', email: '', captchaCode: '' })

onMounted(async () => {
  try {
    const c = await configApi.client()
    captchaEnabled.value = !!c.authCaptchaEnabled
    if (captchaEnabled.value) loadCaptcha()
  } catch (e) { /* 忽略 */ }
})

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
  loading.value = true
  err.value = ''
  try {
    await authApi.register({
      username: r.username,
      password: r.password,
      nickname: r.nickname || undefined,
      phone: r.phone || undefined,
      email: r.email || undefined,
      captchaUuid: captchaUuid.value || undefined,
      captchaCode: r.captchaCode || undefined
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
.login-foot { text-align: center; margin-top: 18px; font-size: 12px; color: var(--text-dim); opacity: .8; }
</style>
