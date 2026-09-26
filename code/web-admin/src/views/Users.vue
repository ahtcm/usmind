<template>
  <div class="page-container">
    <el-card shadow="never">
      <div class="toolbar">
        <el-input
          v-model="query.keyword"
          placeholder="搜索用户名/昵称"
          clearable
          style="width: 260px"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        >
          <template #append>
            <el-button @click="handleSearch">搜索</el-button>
          </template>
        </el-input>
        <el-button type="primary" :icon="Plus" @click="openCreate">新增用户</el-button>
      </div>

      <el-table v-loading="loading" :data="tableData" border>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="username" label="用户名" width="120" />
        <el-table-column prop="nickname" label="昵称" width="120" />
        <el-table-column prop="email" label="邮箱" show-overflow-tooltip />
        <el-table-column prop="phone" label="手机" width="130" />
        <el-table-column prop="role" label="角色" width="90">
          <template #default="{ row }">
            <el-tag :type="row.role === 'ADMIN' ? 'danger' : 'info'">{{ row.role }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status === 1 || row.status === 'ACTIVE'"
              @change="(val) => toggleStatus(row, val)"
            />
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="170">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openEdit(row)">编辑</el-button>
            <el-button size="small" type="warning" @click="openReset(row)">重置密码</el-button>
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

    <!-- 新增/编辑用户弹窗 -->
    <el-dialog v-model="dialog.visible" :title="dialog.mode === 'create' ? '新增用户' : '编辑用户'" width="480px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" :disabled="dialog.mode === 'edit'" />
        </el-form-item>
        <el-form-item v-if="dialog.mode === 'create'" label="密码" prop="password">
          <el-input v-model="form.password" type="password" show-password />
        </el-form-item>
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="form.nickname" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" />
        </el-form-item>
        <el-form-item label="手机" prop="phone">
          <el-input v-model="form.phone" />
        </el-form-item>
        <el-form-item label="角色" prop="role">
          <el-select v-model="form.role" style="width: 100%">
            <el-option label="普通用户" value="USER" />
            <el-option label="管理员" value="ADMIN" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="dialog.loading" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>

    <!-- 重置密码弹窗 -->
    <el-dialog v-model="resetDialog.visible" title="重置密码" width="420px">
      <el-form label-width="90px">
        <el-form-item label="新密码" required>
          <el-input v-model="resetDialog.password" type="password" show-password placeholder="请输入新密码" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resetDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="resetDialog.loading" @click="submitReset">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getUserList, createUser, updateUser, deleteUser, resetUserPassword
} from '@/api/users'

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const query = reactive({ page: 1, size: 10, keyword: '' })

const dialog = reactive({ visible: false, mode: 'create', loading: false })
const formRef = ref(null)
const form = reactive({ id: null, username: '', password: '', nickname: '', email: '', phone: '', role: 'USER' })
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, min: 6, message: '密码至少6位', trigger: 'blur' }],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }]
}

const resetDialog = reactive({ visible: false, loading: false, id: null, password: '' })

function formatTime(t) {
  if (!t) return '—'
  return String(t).replace('T', ' ').slice(0, 19)
}

async function loadData() {
  loading.value = true
  try {
    const data = await getUserList({ page: query.page, size: query.size, keyword: query.keyword })
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
  Object.assign(form, { id: null, username: '', password: '', nickname: '', email: '', phone: '', role: 'USER' })
}

function openEdit(row) {
  dialog.mode = 'edit'
  dialog.visible = true
  Object.assign(form, {
    id: row.id, username: row.username, password: '',
    nickname: row.nickname || '', email: row.email || '', phone: row.phone || '', role: row.role
  })
}

async function submitForm() {
  await formRef.value.validate()
  dialog.loading = true
  try {
    if (dialog.mode === 'create') {
      await createUser({ ...form })
      ElMessage.success('创建成功')
    } else {
      await updateUser(form.id, {
        nickname: form.nickname, email: form.email, phone: form.phone, role: form.role
      })
      ElMessage.success('更新成功')
    }
    dialog.visible = false
    loadData()
  } finally {
    dialog.loading = false
  }
}

async function toggleStatus(row, val) {
  try {
    await updateUser(row.id, { status: val ? 1 : 0 })
    row.status = val ? 1 : 0
    ElMessage.success(val ? '已启用' : '已禁用')
  } catch (e) {
    // 失败回退由表格刷新处理
  }
}

async function openReset(row) {
  resetDialog.id = row.id
  resetDialog.password = ''
  resetDialog.visible = true
}

async function submitReset() {
  if (!resetDialog.password || resetDialog.password.length < 6) {
    ElMessage.warning('新密码至少6位')
    return
  }
  resetDialog.loading = true
  try {
    await resetUserPassword(resetDialog.id, { password: resetDialog.password })
    ElMessage.success('密码已重置')
    resetDialog.visible = false
  } finally {
    resetDialog.loading = false
  }
}

async function handleDelete(row) {
  await ElMessageBox.confirm(`确定删除用户「${row.username}」吗？该操作不可恢复。`, '删除确认', { type: 'warning' })
  await deleteUser(row.id)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(loadData)
</script>
