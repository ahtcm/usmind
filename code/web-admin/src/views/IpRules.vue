<!-- IP 黑白名单 -->
<template>
  <div class="page-container">
    <el-card shadow="never">
      <div class="toolbar">
        <el-input
          v-model="query.keyword"
          placeholder="搜索目标值/备注"
          clearable
          style="width: 260px"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        >
          <template #append>
            <el-button @click="handleSearch">搜索</el-button>
          </template>
        </el-input>
        <el-button type="primary" :icon="Plus" @click="openCreate">新增规则</el-button>
        <el-button v-if="selectedIds.length" type="danger" @click="handleBatchDelete">批量删除 ({{ selectedIds.length }})</el-button>
      </div>

      <el-table v-loading="loading" :data="tableData" border @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="42" />
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column label="规则类型" width="110">
          <template #default="{ row }">
            <el-tag :type="row.ruleType === 'black' ? 'danger' : 'success'">{{ row.ruleType === 'black' ? '黑名单' : '白名单' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="目标类型" width="110">
          <template #default="{ row }">
            <el-tag :type="row.targetType === 'user_id' ? 'warning' : 'info'">{{ row.targetType === 'user_id' ? '用户ID' : 'IP' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="targetValue" label="目标值" width="180" />
        <el-table-column prop="remark" label="备注" show-overflow-tooltip />
        <el-table-column label="启用" width="80">
          <template #default="{ row }">
            <el-switch :model-value="row.enabled === 1" @change="(val) => toggleEnabled(row, val)" />
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="170">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openEdit(row)">编辑</el-button>
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

    <el-dialog v-model="dialog.visible" :title="dialog.mode === 'create' ? '新增规则' : '编辑规则'" width="440px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="规则类型" prop="ruleType">
          <el-select v-model="form.ruleType" style="width: 100%">
            <el-option label="黑名单" value="black" />
            <el-option label="白名单" value="white" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标类型" prop="targetType">
          <el-select v-model="form.targetType" style="width: 100%">
            <el-option label="IP" value="ip" />
            <el-option label="用户ID" value="user_id" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标值" prop="targetValue">
          <el-input v-model="form.targetValue" placeholder="如 1.2.3.4 或用户ID" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" placeholder="可选" />
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
import { ref, reactive, onMounted } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getIpRules, createIpRule, updateIpRule, deleteIpRule, batchDeleteIpRules } from '@/api/members'

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const query = reactive({ page: 1, size: 10, keyword: '' })
const selectedIds = ref([])

const dialog = reactive({ visible: false, mode: 'create', loading: false })
const formRef = ref(null)
const form = reactive({ id: null, ruleType: 'black', targetType: 'ip', targetValue: '', remark: '' })
const rules = {
  ruleType: [{ required: true, message: '请选择规则类型', trigger: 'change' }],
  targetType: [{ required: true, message: '请选择目标类型', trigger: 'change' }],
  targetValue: [{ required: true, message: '请输入目标值', trigger: 'blur' }]
}

function formatTime(t) {
  if (!t) return '—'
  return String(t).replace('T', ' ').slice(0, 19)
}

async function loadData() {
  loading.value = true
  try {
    const data = await getIpRules({ page: query.page, size: query.size, keyword: query.keyword })
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

function openCreate() {
  dialog.mode = 'create'
  dialog.visible = true
  Object.assign(form, { id: null, ruleType: 'black', targetType: 'ip', targetValue: '', remark: '' })
}

function openEdit(row) {
  dialog.mode = 'edit'
  dialog.visible = true
  Object.assign(form, { id: row.id, ruleType: row.ruleType, targetType: row.targetType, targetValue: row.targetValue, remark: row.remark || '' })
}

async function submitForm() {
  await formRef.value.validate()
  dialog.loading = true
  try {
    if (dialog.mode === 'create') {
      await createIpRule({ ...form })
      ElMessage.success('创建成功')
    } else {
      await updateIpRule(form.id, { ruleType: form.ruleType, targetType: form.targetType, targetValue: form.targetValue, remark: form.remark })
      ElMessage.success('更新成功')
    }
    dialog.visible = false
    loadData()
  } finally {
    dialog.loading = false
  }
}

async function toggleEnabled(row, val) {
  try {
    await updateIpRule(row.id, { enabled: val ? 1 : 0 })
    row.enabled = val ? 1 : 0
    ElMessage.success(val ? '已启用' : '已停用')
  } catch (e) { /* 忽略 */ }
}

async function handleDelete(row) {
  await ElMessageBox.confirm(`确定删除规则「${row.targetValue}」吗？`, '删除确认', { type: 'warning' })
  await deleteIpRule(row.id)
  ElMessage.success('删除成功')
  loadData()
}

function handleSelectionChange(rows) {
  selectedIds.value = rows.map(r => r.id)
}

async function handleBatchDelete() {
  await ElMessageBox.confirm(`确定删除选中的 ${selectedIds.value.length} 条规则吗？`, '批量删除', { type: 'warning' })
  await batchDeleteIpRules(selectedIds.value)
  ElMessage.success('批量删除成功')
  selectedIds.value = []
  loadData()
}

onMounted(loadData)
</script>
