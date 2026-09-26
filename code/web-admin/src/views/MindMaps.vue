<template>
  <div class="page-container">
    <el-card shadow="never">
      <div class="toolbar">
        <el-input
          v-model="query.keyword"
          placeholder="搜索导图名"
          clearable
          style="width: 260px"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        >
          <template #append>
            <el-button @click="handleSearch">搜索</el-button>
          </template>
        </el-input>
        <el-button v-if="selectedIds.length" type="danger" @click="handleBatchDelete">批量删除 ({{ selectedIds.length }})</el-button>
      </div>

      <el-table v-loading="loading" :data="tableData" border @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="42" />
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="userId" label="所属用户" width="100">
          <template #default="{ row }">{{ row.userId ?? '—' }}</template>
        </el-table-column>
        <el-table-column prop="name" label="导图名" show-overflow-tooltip />
        <el-table-column prop="layout" label="布局" width="110" />
        <el-table-column prop="theme" label="主题" width="120" show-overflow-tooltip />
        <el-table-column prop="isShared" label="是否分享" width="100">
          <template #default="{ row }">
            <el-tag :type="row.isShared ? 'success' : 'info'" size="small">
              {{ row.isShared ? '已分享' : '未分享' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="viewCount" label="浏览数" width="90">
          <template #default="{ row }">{{ row.viewCount ?? 0 }}</template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" width="170">
          <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openDetail(row)">详情</el-button>
            <el-button v-if="row.isShared" size="small" type="warning" @click="handleCancelShare(row)">取消分享</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </el-card>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detail.visible" title="导图详情" width="640px">
      <el-descriptions :column="2" border size="small" v-if="detail.data">
        <el-descriptions-item label="ID">{{ detail.data.id }}</el-descriptions-item>
        <el-descriptions-item label="名称">{{ detail.data.name }}</el-descriptions-item>
        <el-descriptions-item label="布局">{{ detail.data.layout }}</el-descriptions-item>
        <el-descriptions-item label="主题">{{ detail.data.theme }}</el-descriptions-item>
        <el-descriptions-item label="是否分享">{{ detail.data.isShared ? '是' : '否' }}</el-descriptions-item>
        <el-descriptions-item label="分享Token">{{ detail.data.shareToken || '—' }}</el-descriptions-item>
      </el-descriptions>
      <div class="json-title">JSON 数据预览：</div>
      <el-input
        v-model="detail.json"
        type="textarea"
        :rows="12"
        readonly
        class="json-box"
      />
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getMindmapList, getMindmapDetail, deleteMindmap, cancelShareMindmap, batchDeleteMindmaps } from '@/api/mindmaps'

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const query = reactive({ page: 1, size: 10, keyword: '' })
const selectedIds = ref([])

const detail = reactive({ visible: false, data: null, json: '' })

function formatTime(t) {
  if (!t) return '—'
  return String(t).replace('T', ' ').slice(0, 19)
}

async function loadData() {
  loading.value = true
  try {
    const data = await getMindmapList({ page: query.page, size: query.size, keyword: query.keyword })
    tableData.value = data.list || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.page = 1
  loadData()
}

async function openDetail(row) {
  try {
    const data = await getMindmapDetail(row.id)
    detail.data = data
    detail.json = JSON.stringify(data.data ?? data.viewData ?? {}, null, 2)
    detail.visible = true
  } catch (e) {}
}

async function handleCancelShare(row) {
  await ElMessageBox.confirm(`确定取消导图「${row.name}」的分享吗？`, '提示', { type: 'warning' })
  await cancelShareMindmap(row.id)
  ElMessage.success('已取消分享')
  loadData()
}

async function handleDelete(row) {
  await ElMessageBox.confirm(`确定删除导图「${row.name}」吗？该操作不可恢复。`, '删除确认', { type: 'warning' })
  await deleteMindmap(row.id)
  ElMessage.success('删除成功')
  loadData()
}

function handleSelectionChange(rows) {
  selectedIds.value = rows.map(r => r.id)
}

async function handleBatchDelete() {
  await ElMessageBox.confirm(`确定删除选中的 ${selectedIds.value.length} 张导图吗？该操作不可恢复。`, '批量删除', { type: 'warning' })
  await batchDeleteMindmaps(selectedIds.value)
  ElMessage.success('批量删除成功')
  selectedIds.value = []
  loadData()
}

onMounted(loadData)
</script>

<style scoped>
.json-title {
  margin: 12px 0 6px;
  font-size: 13px;
  color: #606266;
}
.json-box :deep(.el-textarea__inner) {
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
}
</style>
