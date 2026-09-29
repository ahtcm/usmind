<template>
  <div class="uc-page">
    <div class="uc-box">
      <h2>个人信息</h2>

      <div class="uc-avatar-row">
        <div class="uc-avatar" v-if="userStore.user?.avatar">
          <img :src="userStore.user.avatar" alt="avatar" />
        </div>
        <div class="uc-avatar uc-avatar-text" v-else>{{ (userStore.user?.nickname || userStore.user?.username || 'U').slice(0, 1).toUpperCase() }}</div>
        <div class="uc-avatar-info">
          <div class="uc-name">{{ userStore.user?.nickname || userStore.user?.username }}</div>
          <div class="uc-vip" :class="{ vip: isVip }">
            <span v-if="isVip">会员 · 到期 {{ expireText }}</span>
            <span v-else>普通用户</span>
          </div>
        </div>
      </div>

      <div class="uc-field">
        <label>头像</label>
        <div class="uc-avatar-row">
          <label class="uc-avatar-upload" :class="{ 'has-img': form.avatar }">
            <img v-if="form.avatar" :src="form.avatar" alt="avatar" />
            <span v-else>＋ 上传</span>
            <input type="file" accept="image/*" @change="onAvatarPick" />
          </label>
          <span class="uc-tip">支持 jpg/png，≤2MB</span>
        </div>
      </div>
      <div class="uc-field">
        <label>昵称</label>
        <input v-model="form.nickname" type="text" placeholder="请输入昵称" />
      </div>
      <div class="uc-field">
        <label>手机号</label>
        <input v-model="form.phone" type="text" placeholder="请输入手机号" />
      </div>
      <div class="uc-field">
        <label>邮箱</label>
        <input v-model="form.email" type="text" placeholder="请输入邮箱" />
      </div>

      <div class="uc-btns">
        <button class="uc-btn primary" @click="save" :disabled="saving">{{ saving ? '保存中...' : '保 存' }}</button>
        <button class="uc-btn ghost" @click="router.push('/maps')">返 回</button>
      </div>
      <div v-if="err" class="uc-err">{{ err }}</div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import request from '../api/request'
import { authApi } from '../api/auth'
import { memberApi } from '../api/member'
import { useUserStore } from '../stores/user'
import { toast } from '../utils/modal'

const router = useRouter()
const userStore = useUserStore()
const form = ref({ nickname: '', phone: '', email: '', avatar: '' })
const member = ref(null)
const saving = ref(false)
const err = ref('')

const isVip = computed(() => !!member.value && member.value.level > 0)
const expireText = computed(() => (member.value && member.value.expire) ? member.value.expire.slice(0, 10) : '')

onMounted(async () => {
  const u = userStore.user || {}
  form.value = { nickname: u.nickname || '', phone: u.phone || '', email: u.email || '', avatar: u.avatar || '' }
  try {
    const d = await memberApi.info()
    member.value = d
  } catch (e) { /* 忽略 */ }
})

async function save() {
  saving.value = true
  err.value = ''
  try {
    const u = await authApi.updateProfile({
      nickname: form.value.nickname,
      phone: form.value.phone,
      email: form.value.email,
      avatar: form.value.avatar
    })
    userStore.setUser({ ...userStore.user, ...u })
    toast('保存成功')
  } catch (e) {
    err.value = e.message || '保存失败'
  } finally {
    saving.value = false
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
    form.value.avatar = up.url || ''
  } catch (e2) {
    err.value = (e2 && e2.message) || '头像上传失败'
  }
}
</script>

<style scoped>
.uc-page { height: 100vh; display: flex; align-items: center; justify-content: center; background: radial-gradient(1200px 800px at 50% 40%, #1e2229 0%, #16181d 60%); overflow-y: auto; }
.uc-box { width: 400px; background: rgba(38,42,49,.9); border: 1px solid var(--border); border-radius: 16px; padding: 28px; box-shadow: 0 18px 50px rgba(0,0,0,.5); margin: 24px 0; }
.uc-box h2 { margin-bottom: 20px; font-size: 20px; }
.uc-avatar-row { display: flex; align-items: center; gap: 14px; margin-bottom: 20px; }
.uc-avatar { width: 56px; height: 56px; border-radius: 50%; overflow: hidden; background: linear-gradient(135deg, #4f6ef7, #6a5cf6); display: flex; align-items: center; justify-content: center; flex-shrink: 0; box-shadow: 0 6px 18px rgba(79,110,247,.35); }
.uc-avatar img { width: 100%; height: 100%; object-fit: cover; }
.uc-avatar-text { font-size: 22px; font-weight: 700; color: #fff; }
.uc-avatar-upload { width: 56px; height: 56px; border-radius: 50%; border: 1px dashed var(--border); display: flex; align-items: center; justify-content: center; overflow: hidden; cursor: pointer; background: rgba(255,255,255,.06); flex-shrink: 0; }
.uc-avatar-upload img { width: 100%; height: 100%; object-fit: cover; }
.uc-avatar-upload span { font-size: 12px; color: var(--text-dim); }
.uc-avatar-upload input { display: none; }
.uc-tip { font-size: 12px; color: var(--text-dim); }
.uc-name { font-size: 16px; font-weight: 600; }
.uc-vip { font-size: 12px; color: var(--text-dim); margin-top: 4px; }
.uc-vip.vip { color: #f0b429; }
.uc-field { margin-bottom: 14px; }
.uc-field label { display: block; font-size: 12px; color: var(--text-dim); margin-bottom: 6px; }
.uc-field input { width: 100%; background: rgba(255,255,255,.06); border: 1px solid var(--border); border-radius: 8px; color: var(--text); padding: 10px 12px; font-size: 14px; outline: none; box-sizing: border-box; }
.uc-field input:focus { border-color: var(--accent); }
.uc-btns { display: flex; gap: 10px; margin-top: 18px; }
.uc-btn { flex: 1; border: none; border-radius: 8px; padding: 10px 0; font-size: 14px; font-weight: 600; cursor: pointer; transition: filter .15s; }
.uc-btn.primary { background: linear-gradient(135deg, #4f6ef7, #6a5cf6); color: #fff; }
.uc-btn.ghost { background: rgba(255,255,255,.08); color: var(--text); }
.uc-btn:hover { filter: brightness(1.1); }
.uc-err { color: #f26d6d; font-size: 13px; text-align: center; margin-top: 12px; }
</style>
