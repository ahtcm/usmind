<template>
  <div class="maps-page">
    <div class="maps-header">
      <h1>我的<span class="only-pc">思维</span>导图</h1>
      <div class="user-area">
        <div class="avatar-menu" @mouseenter="menuOpen = true" @mouseleave="menuOpen = false">
          <div class="avatar-trigger">
            <div class="avatar" v-if="userStore.user?.avatar" @click.stop="avatarInput.click()" title="点击上传头像"><img :src="userStore.user.avatar" alt="avatar" /></div>
            <div class="avatar avatar-text" v-else @click.stop="avatarInput.click()" title="点击上传头像">{{ (userStore.user?.nickname || userStore.user?.username || 'U').slice(0, 1).toUpperCase() }}</div>
            <span class="user-name">{{ userStore.user?.nickname || userStore.user?.username || '' }}</span>
            <span class="caret">▾</span>
          </div>
          <input ref="avatarInput" type="file" accept="image/*" hidden @change="onAvatarPick" />
          <div class="avatar-drop" v-if="menuOpen">
            <div class="drop-item" @click="goProfile">
              <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="8" r="4"/><path d="M4 20c0-4 4-6 8-6s8 2 8 6"/></svg>
              个人信息
            </div>
            <div class="drop-item" @click="goMember" v-if="memberEnabled">
              <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 2l3 6 6 .8-4.5 4.3 1 6.4-5.5-3-5.5 3 1-6.4L3 8.8 9 8z"/></svg>
              会员中心
            </div>
            <div class="drop-item" @click="goOrgs">
              <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 21h18M5 21V7l7-4 7 4v14M9 9h.01M9 13h.01M9 17h.01M15 9h.01M15 13h.01M15 17h.01"/></svg>
              我的机构
            </div>
            <div class="drop-sep"></div>
            <div class="drop-item danger" @click="onLogout">退出登录</div>
          </div>
        </div>
        <button class="btn" @click="onNew">+ 新建导图</button>
      </div>
    </div>
    <div class="maps-grid" v-if="list.length">
      <div class="map-card" v-for="m in list" :key="m.id" @click="openMap(m)">
        <div class="mc-name">{{ m.name || '未命名' }}<span v-if="m.isCollab" class="mc-collab">协作</span></div>
        <div class="mc-meta">更新于 {{ formatTime(m.updatedAt) }}</div>
        <div class="mc-actions">
          <button @click.stop="onShare(m)" v-if="!m.isShared">分享</button>
          <button @click.stop="onStopShare(m)" v-else>取消分享</button>
          <button class="danger" @click.stop="onDelete(m)">删除</button>
        </div>
      </div>
    </div>
    <div class="maps-empty" v-else>
      还没有导图，点击右上角「新建导图」开始创作
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { mindmapApi } from '../api/mindmap'
import request from '../api/request'
import { configApi } from '../api/member'
import { toast, showConfirm, showPrompt } from '../utils/modal'
import { useUserStore } from '../stores/user'

const router = useRouter()
const userStore = useUserStore()
const list = ref([])
const menuOpen = ref(false)
const avatarInput = ref(null)
const memberEnabled = ref(true)

onMounted(() => { loadList(); loadMemberEnabled() })

async function loadMemberEnabled() {
  try {
    const c = await configApi.client()
    memberEnabled.value = c.memberEnabled !== false
  } catch (e) { /* 忽略 */ }
}

async function loadList() {
  try {
    const data = await mindmapApi.list({ page: 1, size: 100 })
    list.value = data.list || []
  } catch (e) {
    toast('加载失败: ' + e.message, 'error')
  }
}

async function onNew() {
  try {
    const data = await mindmapApi.create({ name: '未命名' })
    router.push('/editor/' + data.id)
  } catch (e) {
    toast('创建失败: ' + e.message, 'error')
  }
}

function openMap(m) {
  router.push('/editor/' + m.id)
}

async function onDelete(m) {
  if (!(await showConfirm({ title: '删除导图', message: '删除导图「' + (m.name || '未命名') + '」？' }))) return
  try {
    await mindmapApi.remove(m.id)
    loadList()
  } catch (e) {
    toast('删除失败: ' + e.message, 'error')
  }
}

async function onShare(m) {
  const pwd = await showPrompt({ title: '设置分享密码', message: '设置分享密码（留空则无密码）', placeholder: '留空则无密码' })
  try {
    const data = await mindmapApi.share(m.id, pwd || undefined)
    toast('分享链接已复制到剪贴板：' + location.origin + '/share/' + data.shareToken)
    loadList()
  } catch (e) {
    toast('分享失败: ' + e.message, 'error')
  }
}

async function onStopShare(m) {
  try {
    await mindmapApi.stopShare(m.id)
    loadList()
  } catch (e) {
    toast('操作失败: ' + e.message, 'error')
  }
}

function goProfile() {
  menuOpen.value = false
  router.push('/profile')
}

function goMember() {
  menuOpen.value = false
  router.push('/member')
}

function goOrgs() {
  menuOpen.value = false
  router.push('/orgs')
}

async function onAvatarPick(e) {
  const f = e.target && e.target.files && e.target.files[0]
  e.target.value = ''
  if (!f) return
  if (!f.type.startsWith('image/')) { toast('请选择图片文件', 'error'); return }
  if (f.size > 2 * 1024 * 1024) { toast('头像不能超过 2MB', 'error'); return }
  const fd = new FormData()
  fd.append('file', f)
  try {
    const up = await request.post('/file/upload-avatar', fd)
    userStore.setUser({ ...userStore.user, avatar: up.url || '' })
    toast('头像已更新')
  } catch (e2) {
    toast((e2 && e2.message) || '头像上传失败', 'error')
  }
}

function onLogout() {
  userStore.logout()
  router.push('/login')
}

function formatTime(t) {
  if (!t) return ''
  const d = new Date(t)
  return (d.getMonth() + 1) + '/' + d.getDate() + ' ' + d.getHours() + ':' + String(d.getMinutes()).padStart(2, '0')
}
</script>

<style scoped>
.avatar-menu { position: relative; }
.avatar-trigger { display: flex; align-items: center; gap: 8px; cursor: pointer; padding: 4px 8px; border-radius: 10px; transition: background .15s; }
.avatar-trigger:hover { background: rgba(255,255,255,.08); }
.avatar { width: 30px; height: 30px; border-radius: 50%; overflow: hidden; background: linear-gradient(135deg, #4f6ef7, #6a5cf6); display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.avatar img { width: 100%; height: 100%; object-fit: cover; }
.avatar-text { font-size: 13px; font-weight: 700; color: #fff; }
.caret { font-size: 10px; color: var(--text-dim); }
.avatar-drop { position: absolute; top: calc(100% + 6px); right: 0; min-width: 160px; background: rgba(28,31,38,.96); backdrop-filter: blur(18px); border: 1px solid rgba(255,255,255,.12); border-radius: 12px; box-shadow: 0 12px 32px rgba(0,0,0,.45); padding: 6px; z-index: 300; animation: umToastIn .18s ease; }
.drop-item { display: flex; align-items: center; gap: 8px; padding: 9px 12px; border-radius: 8px; color: var(--text); font-size: 13px; cursor: pointer; transition: all .12s; white-space: nowrap; }
.drop-item:hover { background: var(--accent); color: #fff; }
.drop-item.danger:hover { background: #e5484d; }
.drop-sep { height: 1px; margin: 5px 8px; background: rgba(255,255,255,.08); }
.mc-collab { margin-left: 8px; font-size: 10px; color: #f5c96b; background: rgba(245,201,107,.15); border-radius: 6px; padding: 1px 7px; vertical-align: middle; }
@keyframes umToastIn { from { opacity: 0; transform: translateY(-8px); } to { opacity: 1; transform: translateY(0); } }
</style>
