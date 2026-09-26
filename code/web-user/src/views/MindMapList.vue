<template>
  <div class="maps-page">
    <div class="maps-header">
      <h1>我的思维导图</h1>
      <div class="user-area">
        <span>{{ userStore.user?.nickname || userStore.user?.username || '' }}</span>
        <button class="btn ghost" @click="onLogout">退出</button>
        <button class="btn" @click="onNew">+ 新建导图</button>
      </div>
    </div>
    <div class="maps-grid" v-if="list.length">
      <div class="map-card" v-for="m in list" :key="m.id" @click="openMap(m)">
        <div class="mc-name">{{ m.name || '未命名' }}</div>
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
import { toast, showConfirm, showPrompt } from '../utils/modal'
import { useUserStore } from '../stores/user'

const router = useRouter()
const userStore = useUserStore()
const list = ref([])

onMounted(loadList)

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
