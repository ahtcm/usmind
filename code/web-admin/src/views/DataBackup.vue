<template>
  <div class="page-container">
    <!-- 备份 -->
    <el-card shadow="never" class="card">
      <template #header>
        <div class="card-title"><el-icon><Download /></el-icon> 数据备份</div>
      </template>
      <div class="desc">
        将当前数据库全部数据（用户、导图、主题、图标、系统配置、OSS 配置、资源记录）导出为 JSON 文件。
        建议在重大改动前定期备份。
      </div>
      <div class="actions">
        <el-button type="primary" :icon="Download" :loading="backupLoading" @click="doBackup">立即备份并下载</el-button>
        <span v-if="lastBackup" class="last-time">上次备份：{{ lastBackup }}</span>
      </div>
      <el-divider />
      <div class="table-info">
        <el-tag v-for="t in tableStats" :key="t.table" size="small" effect="plain" class="table-tag">
          {{ t.table }} <b>{{ t.count }}</b>
        </el-tag>
      </div>
    </el-card>

    <!-- 恢复 -->
    <el-card shadow="never" class="card">
      <template #header>
        <div class="card-title warn"><el-icon><RefreshRight /></el-icon> 数据恢复</div>
      </template>
      <div class="desc warn-desc">
        上传之前导出的备份 JSON 文件，将<b>整体覆盖</b>当前数据库的全部数据（先清空再写入备份内容）。
        该操作<b>不可撤销</b>，请务必确认备份文件来源可信。
      </div>
      <el-upload
        drag
        :auto-upload="false"
        :limit="1"
        accept=".json,application/json"
        :on-change="onFileChange"
        :on-remove="onFileRemove"
        :file-list="fileList"
        class="upload-area"
      >
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
        <div class="el-upload__text">将备份文件拖到此处，或<em>点击选择</em></div>
        <template #tip>
          <div class="el-upload__tip">仅支持 usmind 备份 JSON 文件</div>
        </template>
      </el-upload>
      <div class="actions">
        <el-button type="danger" :icon="RefreshRight" :disabled="!fileList.length" :loading="restoreLoading" @click="confirmRestore">
          开始恢复
        </el-button>
      </div>
    </el-card>

    <!-- 恢复结果 -->
    <el-dialog v-model="result.visible" title="恢复完成" width="520px">
      <div class="result-box">
        <el-alert type="success" :closable="false" title="数据已恢复" :description="`共写入 ${result.total} 条记录`" class="mb12" />
        <el-table :data="result.rows" size="small" border>
          <el-table-column prop="table" label="数据表" />
          <el-table-column prop="count" label="恢复行数" width="120" align="center" />
        </el-table>
        <el-alert type="warning" :closable="false" class="mt12"
          title="恢复后请重新登录"
          description="用户数据可能已变更，当前登录会话已失效，请重新登录管理后台。" />
      </div>
      <template #footer>
        <el-button type="primary" @click="result.visible = false">知道了</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Download, RefreshRight, UploadFilled } from '@element-plus/icons-vue'
import request from '../api/request'

const backupLoading = ref(false)
const restoreLoading = ref(false)
const lastBackup = ref('')
const fileList = ref([])
const result = reactive({ visible: false, total: 0, rows: [] })
const tableStats = ref([])

const TABLE_LABELS = {
  sys_user: '用户', mind_map: '导图', sys_config: '系统配置', sys_theme: '主题模板',
  sys_icon: '图标库', oss_config: 'OSS 配置', file_resource: '资源记录'
}

async function doBackup() {
  backupLoading.value = true
  try {
    const res = await request.get('/admin/data/backup')
    const doc = res && res.tables ? res : { type: 'usmind-backup', version: 1, tables: {}, summary: {} }
    if (!doc.tables) doc.tables = {}
    const ts = new Date()
    const pad = (n) => String(n).padStart(2, '0')
    const name = `usmind-backup-${ts.getFullYear()}${pad(ts.getMonth() + 1)}${pad(ts.getDate())}-${pad(ts.getHours())}${pad(ts.getMinutes())}${pad(ts.getSeconds())}.json`
    const blob = new Blob([JSON.stringify(doc, null, 2)], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = name
    document.body.appendChild(a)
    a.click()
    a.remove()
    URL.revokeObjectURL(url)
    lastBackup.value = name
    const summary = doc.summary || {}
    tableStats.value = Object.keys(summary).map(t => ({ table: TABLE_LABELS[t] || t, count: summary[t] }))
    ElMessage.success('备份文件已下载')
  } catch (e) {
    ElMessage.error('备份失败: ' + (e.message || '网络错误'))
  }
  backupLoading.value = false
}

function onFileChange(file) {
  fileList.value = [file]
}
function onFileRemove() {
  fileList.value = []
}

async function confirmRestore() {
  if (!fileList.value.length) return
  await ElMessageBox.confirm(
    '恢复操作将清空并覆盖当前数据库的全部数据，且不可撤销。\n请再次确认要执行恢复吗？',
    '危险操作确认',
    { type: 'warning', confirmButtonText: '确认恢复', cancelButtonText: '取消', confirmButtonClass: 'el-button--danger' }
  )
  restoreLoading.value = true
  const fd = new FormData()
  fd.append('file', fileList.value[0].raw)
  try {
    const res = await request.post('/admin/data/restore', fd)
    const restored = (res && res.restored) || {}
    result.rows = Object.keys(restored).map(t => ({ table: TABLE_LABELS[t] || t, count: restored[t] }))
    result.total = (res && res.total) || 0
    result.visible = true
    fileList.value = []
    ElMessage.success('数据已恢复')
  } catch (e) {
    ElMessage.error('恢复失败: ' + (e.message || '网络错误'))
  }
  restoreLoading.value = false
}

onMounted(() => {})
</script>

<style scoped>
.page-container {
  max-width: 860px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.card-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
}
.card-title.warn {
  color: #f56c6c;
}
.desc {
  color: #606266;
  font-size: 13px;
  line-height: 1.7;
  margin-bottom: 12px;
}
.warn-desc {
  color: #a0403a;
  background: #fef0f0;
  border: 1px solid #fde2e2;
  border-radius: 8px;
  padding: 10px 12px;
}
.actions {
  display: flex;
  align-items: center;
  gap: 14px;
}
.last-time {
  font-size: 12px;
  color: #909399;
}
.table-info {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.table-tag b {
  margin-left: 4px;
  color: #0e7c86;
}
.upload-area {
  margin: 6px 0 14px;
}
.result-box .mb12 {
  margin-bottom: 12px;
}
.result-box .mt12 {
  margin-top: 12px;
}
</style>
