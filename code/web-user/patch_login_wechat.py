# -*- coding: utf-8 -*-
# Login.vue：微信登录按钮 + 回调处理 + 注册头像上传
import io, sys

p = r'C:\Users\25750\Desktop\usmind\code\web-user\src\views\Login.vue'
s = io.open(p, encoding='utf-8').read()

def rep(old, new):
    global s
    if old not in s:
        print('NOT FOUND ::', old[:60]); sys.exit(1)
    s = s.replace(old, new, 1)

# 1) 登录 tab 底部加微信登录
rep("""        <button class="btn-login" @click="doLogin" :disabled="loading">{{ loading ? '登录中...' : '登 录' }}</button>
      </template>""",
"""        <button class="btn-login" @click="doLogin" :disabled="loading">{{ loading ? '登录中...' : '登 录' }}</button>
        <div v-if="wechatLoginEnabled" class="wx-row">
          <div class="wx-divider"><span>其他登录方式</span></div>
          <button class="btn-wx" @click="wechatLogin">
            <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor"><path d="M8.7 4C4.9 4 2 6.5 2 9.7c0 1.8 1 3.4 2.6 4.5l-.7 2 2.2-1.1c.6.2 1.2.3 1.9.3h.3c-.2-.6-.3-1.2-.3-1.8 0-2.9 2.6-5.3 5.9-5.3h.3C13.5 5.7 11.3 4 8.7 4zm-2 3.4c.5 0 .9.4.9.9s-.4.9-.9.9-.9-.4-.9-.9.4-.9.9-.9zm4.2 0c.5 0 .9.4.9.9s-.4.9-.9.9-.9-.4-.9-.9.4-.9.9-.9zM15 9.4c-3 0-5.5 2.1-5.5 4.8S12 19 15 19c.6 0 1.2-.1 1.7-.3l1.9.9-.6-1.7c1.2-.9 2-2.1 2-3.6 0-2.7-2.5-4.9-5.5-4.9zm-1.5 2.5c.5 0 .9.4.9.9s-.4.9-.9.9-.9-.4-.9-.9.4-.9.9-.9zm3 0c.5 0 .9.4.9.9s-.4.9-.9.9-.9-.4-.9-.9.4-.9.9-.9z"/></svg>
            微信扫码登录
          </button>
        </div>
      </template>""")

# 2) 注册表单头部加头像上传
rep("""      <template v-else>
        <div class="field">
          <label>用户名</label>""",
"""      <template v-else>
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
          <label>用户名</label>""")

# 3) script：加微信登录 + 头像上传逻辑
rep("""const mode = ref('login')
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
})""",
"""const mode = ref('login')
const username = ref('')
const password = ref('')
const err = ref('')
const loading = ref(false)
const captchaEnabled = ref(false)
const captchaImg = ref('')
const captchaUuid = ref('')
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
  if (!/^image\\//.test(f.type)) { err.value = '仅支持图片文件'; return }
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
}""")

# 4) doRegister 带 avatar
rep("""    await authApi.register({
      username: r.username,
      password: r.password,
      nickname: r.nickname || undefined,
      phone: r.phone || undefined,
      email: r.email || undefined,
      captchaUuid: captchaUuid.value || undefined,
      captchaCode: r.captchaCode || undefined
    })""",
"""    await authApi.register({
      username: r.username,
      password: r.password,
      nickname: r.nickname || undefined,
      phone: r.phone || undefined,
      email: r.email || undefined,
      avatar: r.avatar || undefined,
      captchaUuid: captchaUuid.value || undefined,
      captchaCode: r.captchaCode || undefined
    })""")

# 5) import request
rep("""import { authApi } from '../api/auth'
import { configApi } from '../api/member'""",
"""import request from '../api/request'
import { authApi } from '../api/auth'
import { configApi } from '../api/member'""")

# 6) style：微信按钮 + 头像上传
rep(""".login-foot { text-align: center; margin-top: 18px; font-size: 12px; color: var(--text-dim); opacity: .8; }""",
""".login-foot { text-align: center; margin-top: 18px; font-size: 12px; color: var(--text-dim); opacity: .8; }
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
.avatar-tip { font-size: 12px; color: var(--text-dim); }""")

io.open(p, 'w', encoding='utf-8', newline='\n').write(s)
print('LOGIN PATCH OK')
