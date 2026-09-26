<template>
  <div class="page-container">
    <el-card shadow="never">
      <div class="toolbar">
        <el-input
          v-model="query.keyword"
          placeholder="搜索文件名"
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
        <el-table-column label="预览" width="110">
          <template #default="{ row }">
            <div class="thumb-wrap">
              <img v-if="isImage(row.mimeType) && row.url" :src="row.url" class="thumb" @click="openPreview(row)" />
              <video v-else-if="isVideo(row.mimeType) && row.url" :src="row.url" class="thumb" muted @click="openPreview(row)" />
              <el-icon v-else-if="isAudio(row.mimeType)" :size="26" class="type-icon"><Service /></el-icon>
              <el-icon v-else :size="26" class="type-icon"><Document /></el-icon>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="originalName" label="文件名" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <el-link type="primary" :underline="false" @click="openPreview(row)">{{ row.originalName }}</el-link>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="typeTag(row.mimeType)">{{ typeLabel(row.mimeType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="大小" width="100">
          <template #default="{ row }">{{ formatSize(row.size) }}</template>
        </el-table-column>
        <el-table-column prop="username" label="上传者" width="120" />
        <el-table-column prop="storageType" label="存储" width="90">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ row.storageType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="上传时间" width="170">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openPreview(row)">预览</el-button>
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

    <!-- 预览弹窗 -->
    <el-dialog v-model="preview.visible" title="资源预览" width="640px" append-to-body>
      <div class="preview-body">
        <img v-if="preview.row && isImage(preview.row.mimeType)" :src="preview.row.url" class="preview-media" />
        <video v-else-if="preview.row && isVideo(preview.row.mimeType)" :src="preview.row.url" class="preview-media" controls />
        <audio v-else-if="preview.row && isAudio(preview.row.mimeType)" :src="preview.row.url" controls style="width:100%" />
        <div v-else-if="preview.row" class="preview-file">
          <el-icon :size="48"><Document /></el-icon>
          <p>{{ preview.row.originalName }}</p>
          <el-link :href="preview.row.url" target="_blank" type="primary">点击下载 / 打开</el-link>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Document, Service } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getFileList, deleteFile, batchDeleteFiles } from '@/api/files'

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const query = reactive({ page: 1, size: 10, keyword: '' })
const selectedIds = ref([])
const preview = reactive({ visible: false, row: null })

function isImage(mime) { return (mime || '').startsWith('image/') }
function isVideo(mime) { return (mime || '').startsWith('video/') }
function isAudio(mime) { return (mime || '').startsWith('audio/') }
function typeLabel(mime) {
  if (isImage(mime)) return '图片'
  if (isVideo(mime)) return '视频'
  if (isAudio(mime)) return '音频'
  return '文件'
}
function typeTag(mime) {
  if (isImage(mime)) return 'success'
  if (isVideo(mime)) return 'warning'
  if (isAudio(mime)) return 'info'
  return 'primary'
}
function formatSize(bytes) {
  if (!bytes && bytes !== 0) return '—'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1024 / 1024).toFixed(1) + ' MB'
}
function formatTime(t) {
  if (!t) return '—'
  return String(t).replace('T', ' ').slice(0, 19)
}

async function loadData() {
  loading.value = true
  try {
    const data = await getFileList({ page: query.page, size: query.size, keyword: query.keyword })
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

function openPreview(row) {
  preview.row = row
  preview.visible = true
}

async function handleDelete(row) {
  await ElMessageBox.confirm(`确定删除资源「${row.originalName}」吗？删除后引用该资源的节点将无法显示。`, '删除确认', { type: 'warning' })
  await deleteFile(row.id)
  ElMessage.success('删除成功')
  loadData()
}

function handleSelectionChange(rows) {
  selectedIds.value = rows.map(r => r.id)
}

async function handleBatchDelete() {
  await ElMessageBox.confirm(`确定删除选中的 ${selectedIds.value.length} 个资源吗？删除后引用这些资源的节点将无法显示。`, '批量删除', { type: 'warning' })
  await batchDeleteFiles(selectedIds.value)
  ElMessage.success('批量删除成功')
  selectedIds.value = []
  loadData()
}

onMounted(loadData)
</script>

<style scoped>
.toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}
.thumb-wrap {
  width: 80px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f7fa;
  border-radius: 4px;
  overflow: hidden;
}
.thumb {
  width: 100%;
  height: 100%;
  object-fit: cover;
  cursor: pointer;
}
.type-icon {
  color: #909399;
}
.preview-body {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  min-height: 120px;
}
.preview-media {
  max-width: 100%;
  max-height: 420px;
  border-radius: 8px;
}
.preview-file {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  color: #606266;
}
.preview-file p {
  margin: 0;
}
</style>
