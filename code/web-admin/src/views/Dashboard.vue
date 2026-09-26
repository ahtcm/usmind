<template>
  <div class="page-container" v-loading="loading">
    <el-row :gutter="16">
      <el-col :xs="12" :sm="12" :md="6" v-for="card in statCards" :key="card.label">
        <el-card class="stat-card" shadow="hover">
          <div>
            <div class="stat-label">{{ card.label }}</div>
            <div class="stat-value">{{ card.value }}</div>
          </div>
          <div class="stat-icon" :style="{ background: card.color }">
            <el-icon><component :is="card.icon" /></el-icon>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :xs="24" :md="12">
        <el-card shadow="never">
          <template #header><span>最近注册用户</span></template>
          <el-table :data="recentUsers" size="small" empty-text="暂无数据">
            <el-table-column prop="id" label="ID" width="60" />
            <el-table-column prop="username" label="用户名" />
            <el-table-column prop="nickname" label="昵称" />
            <el-table-column prop="createdAt" label="注册时间">
              <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="12">
        <el-card shadow="never">
          <template #header><span>最近更新导图</span></template>
          <el-table :data="recentMindmaps" size="small" empty-text="暂无数据">
            <el-table-column prop="id" label="ID" width="60" />
            <el-table-column prop="name" label="导图名" show-overflow-tooltip />
            <el-table-column prop="layout" label="布局" width="100" />
            <el-table-column prop="updatedAt" label="更新时间">
              <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :span="24">
        <el-card shadow="never">
          <template #header><span>存储使用情况</span></template>
          <el-descriptions :column="3" border>
            <el-descriptions-item label="当前存储类型">{{ storage.type || '—' }}</el-descriptions-item>
            <el-descriptions-item label="已用空间">{{ storage.used || '—' }}</el-descriptions-item>
            <el-descriptions-item label="Bucket">{{ storage.bucket || '—' }}</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { User, Share, FolderOpened, Files } from '@element-plus/icons-vue'
import { getUserList } from '@/api/users'
import { getMindmapList } from '@/api/mindmaps'
import { getDashboardStats } from '@/api/settings'

const loading = ref(false)
const stats = reactive({ userCount: 0, mindmapCount: 0, shareCount: 0, fileCount: 0 })
const storage = reactive({ type: '', used: '', bucket: '' })
const recentUsers = ref([])
const recentMindmaps = ref([])

const statCards = ref([
  { label: '用户总数', value: 0, icon: User, color: '#0e7c86' },
  { label: '导图总数', value: 0, icon: Share, color: '#3a8ee6' },
  { label: '分享数', value: 0, icon: FolderOpened, color: '#67c23a' },
  { label: '文件数', value: 0, icon: Files, color: '#e6a23c' }
])

function formatTime(t) {
  if (!t) return '—'
  return String(t).replace('T', ' ').slice(0, 19)
}

async function loadStats() {
  try {
    const data = await getDashboardStats()
    stats.userCount = data.userCount ?? 0
    stats.mindmapCount = data.mindmapCount ?? 0
    stats.shareCount = data.shareCount ?? 0
    stats.fileCount = data.fileCount ?? 0
    storage.type = data.storage?.type || ''
    storage.used = data.storage?.used || ''
    storage.bucket = data.storage?.bucket || ''
  } catch (e) {
    // stats 接口未就绪时静默降级
  }
  statCards.value[0].value = stats.userCount
  statCards.value[1].value = stats.mindmapCount
  statCards.value[2].value = stats.shareCount
  statCards.value[3].value = stats.fileCount
}

async function loadRecent() {
  try {
    const u = await getUserList({ page: 1, size: 5 })
    recentUsers.value = u.list || []
  } catch (e) { recentUsers.value = [] }
  try {
    const m = await getMindmapList({ page: 1, size: 5 })
    recentMindmaps.value = m.list || []
  } catch (e) { recentMindmaps.value = [] }
}

onMounted(async () => {
  loading.value = true
  await Promise.all([loadStats(), loadRecent()])
  loading.value = false
})
</script>
