# -*- coding: utf-8 -*-
"""Login.vue 补短信验证码 UI：authSmsEnabled 开启时显示短信验证码行（获取验证码+倒计时），提交带 smsCode"""
import io
P = r'C:\Users\25750\Desktop\usmind\code\web-user\src\views\Login.vue'
s = io.open(P, encoding='utf-8').read()

# 1) 模板：手机号标签动态 + 短信验证码行
old = '''        <div class="field">
          <label>手机号（可选）</label>
          <input v-model="reg.phone" type="text" placeholder="用于找回密码" />
        </div>'''
new = '''        <div class="field">
          <label>手机号{{ smsEnabled ? '（必填）' : '（可选）' }}</label>
          <input v-model="reg.phone" type="text" placeholder="用于找回密码" />
        </div>
        <div class="field" v-if="smsEnabled">
          <label>短信验证码</label>
          <div class="sms-row">
            <input v-model="reg.smsCode" type="text" placeholder="输入短信验证码" />
            <button type="button" class="sms-btn" @click="sendSmsCode" :disabled="smsCountdown > 0">{{ smsCountdown > 0 ? smsCountdown + 's' : '获取验证码' }}</button>
          </div>
        </div>'''
assert s.count(old) == 1, 'sms-row'
s = s.replace(old, new)

# 2) script：短信相关状态
old2 = '''const captchaEnabled = ref(false)
const captchaImg = ref('')
const captchaUuid = ref('')'''
new2 = '''const captchaEnabled = ref(false)
const captchaImg = ref('')
const captchaUuid = ref('')
const smsEnabled = ref(false)
const smsCountdown = ref(0)
let smsTimer = null'''
assert s.count(old2) == 1, 'sms-state'
s = s.replace(old2, new2)

# 3) onMounted 读配置
old3 = '''    captchaEnabled.value = !!c.authCaptchaEnabled
    wechatLoginEnabled.value = !!c.wechatLoginEnabled
    if (captchaEnabled.value) loadCaptcha()'''
new3 = '''    captchaEnabled.value = !!c.authCaptchaEnabled
    wechatLoginEnabled.value = !!c.wechatLoginEnabled
    smsEnabled.value = !!c.authSmsEnabled
    if (captchaEnabled.value) loadCaptcha()'''
assert s.count(old3) == 1, 'sms-config'
s = s.replace(old3, new3)

# 4) 新增 sendSmsCode 函数（放 loadCaptcha 后）
old4 = '''async function loadCaptcha() {'''
new4 = '''async function sendSmsCode() {
  if (!reg.value.phone || reg.value.phone.length < 6) { err.value = '请先输入正确的手机号'; return }
  err.value = ''
  try {
    await request.post('/auth/send-code', { phone: reg.value.phone })
    smsCountdown.value = 60
    if (smsTimer) clearInterval(smsTimer)
    smsTimer = setInterval(() => {
      smsCountdown.value--
      if (smsCountdown.value <= 0 && smsTimer) { clearInterval(smsTimer); smsTimer = null }
    }, 1000)
  } catch (e) {
    err.value = (e && e.message) || '验证码发送失败'
  }
}

async function loadCaptcha() {'''
assert s.count(old4) == 1, 'sms-fn'
s = s.replace(old4, new4)

# 5) doRegister 校验 + 提交带 smsCode
old5 = '''  if (captchaEnabled.value && !r.captchaCode) { err.value = '请输入图形验证码'; return }'''
new5 = '''  if (captchaEnabled.value && !r.captchaCode) { err.value = '请输入图形验证码'; return }
  if (smsEnabled.value && !r.smsCode) { err.value = '请输入短信验证码'; return }
  if (smsEnabled.value && !r.phone) { err.value = '请输入手机号'; return }'''
assert s.count(old5) == 1, 'sms-valid'
s = s.replace(old5, new5)

old6 = '''      captchaUuid: captchaUuid.value || undefined,
      captchaCode: r.captchaCode || undefined'''
new6 = '''      captchaUuid: captchaUuid.value || undefined,
      captchaCode: r.captchaCode || undefined,
      smsCode: r.smsCode || undefined'''
assert s.count(old6) == 1, 'sms-submit'
s = s.replace(old6, new6)

# 6) CSS：短信行样式（与 captcha-row 一致）
old7 = '''.captcha-img { width: 108px; height: 40px; border-radius: 8px; border: 1px solid var(--border); cursor: pointer; background: rgba(255,255,255,.06); object-fit: cover; flex-shrink: 0; }'''
new7 = '''.captcha-img { width: 108px; height: 40px; border-radius: 8px; border: 1px solid var(--border); cursor: pointer; background: rgba(255,255,255,.06); object-fit: cover; flex-shrink: 0; }
.sms-row { display: flex; gap: 8px; }
.sms-row input { flex: 1; }
.sms-btn { flex-shrink: 0; padding: 0 12px; height: 40px; border-radius: 8px; border: 1px solid var(--border); background: linear-gradient(135deg, #4f8cff, #3b6ef5); color: #fff; cursor: pointer; font-size: 13px; white-space: nowrap; }
.sms-btn:disabled { opacity: .6; cursor: not-allowed; }'''
assert s.count(old7) == 1, 'sms-css'
s = s.replace(old7, new7)

io.open(P, 'w', encoding='utf-8', newline='\n').write(s)
print('Login.vue 短信验证码 UI OK')
