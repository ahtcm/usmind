<template>
  <div class="page-container">
    <el-card shadow="never">
      <div class="toolbar">
        <span style="color: #606266">共 {{ list.length }} 个存储配置</span>
        <el-button type="warning" :loading="migrating" @click="handleMigrate">迁移本地文件</el-button>
        <el-button type="primary" :icon="Plus" @click="openCreate">新增配置</el-button>
        <el-button v-if="selectedIds.length" type="danger" @click="handleBatchDelete">批量删除 ({{ selectedIds.length }})</el-button>
      </div>

      <el-table v-loading="loading" :data="list" border :row-class-name="rowClass" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="42" :selectable="row => !row.isActive" />
        <el-table-column prop="name" label="名称" width="140">
          <template #default="{ row }">
            <el-tag v-if="row.isActive" type="success" size="small" effect="dark">使用中</el-tag>
            {{ row.name }}
          </template>
        </el-table-column>
        <el-table-column prop="type" label="类型" width="110">
          <template #default="{ row }">{{ typeLabel(row.type) }}</template>
        </el-table-column>
        <el-table-column prop="endpoint" label="Endpoint" show-overflow-tooltip />
        <el-table-column prop="bucket" label="Bucket" width="140" />
        <el-table-column prop="region" label="区域" width="110" />
        <el-table-column prop="domain" label="自定义域名" show-overflow-tooltip />
        <el-table-column label="操作" width="360" fixed="right">
          <template #default="{ row }">
            <el-button
              size="small"
              type="success"
              :disabled="row.isActive"
              :loading="row._activating"
              @click="handleActivate(row)"
            >激活</el-button>
            <el-button size="small" :loading="row._testing" @click="handleTest(row)">测试连接</el-button>
            <el-button size="small" @click="openEdit(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialog.visible" :title="dialog.mode === 'create' ? '新增OSS配置' : '编辑OSS配置'" width="560px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="例如：生产阿里云OSS" />
        </el-form-item>
        <el-form-item label="类型" prop="type">
          <el-select v-model="form.type" style="width: 100%">
            <el-option label="阿里云OSS" value="ALIYUN" />
            <el-option label="腾讯COS" value="TENCENT" />
            <el-option label="七牛" value="QINIU" />
            <el-option label="MinIO" value="MINIO" />
            <el-option label="本地存储" value="LOCAL" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.type !== 'LOCAL'" label="Endpoint" prop="endpoint">
          <el-input v-model="form.endpoint" placeholder="https://oss-cn-hangzhou.aliyuncs.com" />
        </el-form-item>
        <el-form-item v-if="form.type !== 'LOCAL'" label="AccessKey" prop="accessKey">
          <el-input v-model="form.accessKey" placeholder="AccessKey" />
        </el-form-item>
        <el-form-item v-if="form.type !== 'LOCAL'" label="SecretKey" prop="secretKey">
          <el-input v-model="form.secretKey" type="password" show-password placeholder="编辑时留空则不修改" />
        </el-form-item>
        <el-form-item v-if="form.type !== 'LOCAL'" label="Bucket" prop="bucket">
          <el-input v-model="form.bucket" />
        </el-form-item>
        <el-form-item v-if="form.type !== 'LOCAL'" label="Region" prop="region">
          <el-input v-model="form.region" placeholder="cn-hangzhou" />
        </el-form-item>
        <el-form-item label="自定义域名" prop="domain">
          <el-input v-model="form.domain" placeholder="可选，CDN域名；本地存储也可填，会拼成访问前缀" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="dialog.loading" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getOssConfigs, createOssConfig, updateOssConfig, deleteOssConfig,
  activateOssConfig, testOssConfig, batchDeleteOssConfigs, migrateOss
} from '@/api/oss'

const loading = ref(false)
const list = ref([])
const selectedIds = ref([])
const migrating = ref(false)

const dialog = reactive({ visible: false, mode: 'create', loading: false })
const formRef = ref(null)
const form = reactive({
  id: null, name: '', type: 'ALIYUN', endpoint: '',
  accessKey: '', secretKey: '', bucket: '', region: '', domain: ''
})
const rules = computed(() => ({
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  type: [{ required: true, message: '请选择类型', trigger: 'change' }],
  endpoint: form.type === 'LOCAL' ? [] : [{ required: true, message: '请输入Endpoint', trigger: 'blur' }],
  bucket: form.type === 'LOCAL' ? [] : [{ required: true, message: '请输入Bucket', trigger: 'blur' }]
}))

function typeLabel(t) {
  return { ALIYUN: '阿里云OSS', TENCENT: '腾讯COS', QINIU: '七牛', MINIO: 'MinIO', LOCAL: '本地' }[t] || t
}

function rowClass({ row }) {
  return row.isActive ? 'active-row' : ''
}

async function loadData() {
  loading.value = true
  try {
    const data = await getOssConfigs()
    list.value = (data.list || []).map(item => ({ ...item, _activating: false, _testing: false }))
  } finally {
    loading.value = false
  }
}

function openCreate() {
  dialog.mode = 'create'
  dialog.visible = true
  Object.assign(form, {
    id: null, name: '', type: 'ALIYUN', endpoint: '', accessKey: '', secretKey: '', bucket: '', region: '', domain: ''
  })
}

function openEdit(row) {
  dialog.mode = 'edit'
  dialog.visible = true
  Object.assign(form, {
    id: row.id, name: row.name, type: row.type, endpoint: row.endpoint,
    accessKey: '', secretKey: '', bucket: row.bucket, region: row.region, domain: row.domain || ''
  })
}

async function submitForm() {
  await formRef.value.validate()
  dialog.loading = true
  try {
    if (dialog.mode === 'create') {
      await createOssConfig({ ...form })
      ElMessage.success('创建成功')
    } else {
      const payload = { ...form }
      if (!payload.secretKey) delete payload.secretKey
      await updateOssConfig(form.id, payload)
      ElMessage.success('更新成功')
    }
    dialog.visible = false
    loadData()
  } finally {
    dialog.loading = false
  }
}

async function handleActivate(row) {
  await ElMessageBox.confirm(`确定将「${row.name}」设为当前激活存储吗？其他配置将自动失活。`, '激活确认', { type: 'warning' })
  row._activating = true
  try {
    await activateOssConfig(row.id)
    ElMessage.success('已切换存储配置（热插拔成功）')
    loadData()
  } finally {
    row._activating = false
  }
}

async function handleTest(row) {
  row._testing = true
  try {
    const data = await testOssConfig(row.id)
    if (data.success) ElMessage.success(data.message || '连接成功')
    else ElMessage.error(data.message || '连接失败')
  } catch (e) {
    // 拦截器已提示
  } finally {
    row._testing = false
  }
}

async function handleDelete(row) {
  if (row.isActive) {
    ElMessage.warning('当前激活配置不能删除，请先切换到其他配置')
    return
  }
  await ElMessageBox.confirm(`确定删除配置「${row.name}」吗？`, '删除确认', { type: 'warning' })
  await deleteOssConfig(row.id)
  ElMessage.success('删除成功')
  loadData()
}

async function handleMigrate() {
  const active = list.value.find(r => r.isActive)
  if (!active) {
    ElMessage.warning('请先在列表里激活一个OSS/MinIO/COS/七牛配置')
    return
  }
  if ((active.type || '').toUpperCase() === 'LOCAL') {
    ElMessage.warning('当前激活的是本地存储，请先激活云端存储配置再迁移')
    return
  }
  await ElMessageBox.confirm(
    `将把本地已上传的全部文件（图片/附件/音视频等）迁移到「${active.name}」，并自动更新所有导图数据和用户头像中的引用。\n本地文件默认保留（可稍后手动清理），迁移过程可重复执行、已迁移的自动跳过。`,
    '迁移本地文件到云端', { type: 'warning', confirmButtonText: '开始迁移' }
  )
  migrating.value = true
  try {
    const data = await migrateOss({ deleteAfter: false })
    const errTip = data.failed || data.errors?.length ? `，失败 ${data.failed} 个（${(data.errors || []).slice(0, 3).join('；')}）` : ''
    ElMessage.success(`迁移完成：成功 ${data.files} 个${errTip}；导图更新 ${data.mindmaps} 个、头像更新 ${data.users} 个；本地文件保留 ${data.deletedLocal || 0}（未删除）`)
    loadData()
  } catch (e) {
    // 拦截器已提示
  } finally {
    migrating.value = false
  }
}

function handleSelectionChange(rows) {
  selectedIds.value = rows.map(r => r.id)
}

async function handleBatchDelete() {
  await ElMessageBox.confirm(`确定删除选中的 ${selectedIds.value.length} 个配置吗？（使用中的配置已自动排除）`, '批量删除', { type: 'warning' })
  await batchDeleteOssConfigs(selectedIds.value)
  ElMessage.success('批量删除成功')
  selectedIds.value = []
  loadData()
}

onMounted(loadData)
</script>

<style scoped>
:deep(.active-row) {
  background: #f0f9eb !important;
}
:deep(.active-row td) {
  background: #f0f9eb !important;
}
</style>
