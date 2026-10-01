<!-- 会员管理 -->
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
      </div>

      <el-table v-loading="loading" :data="tableData" border>
        <el-table-column prop="id" label="ID" min-width="70" />
        <el-table-column prop="username" label="用户名" min-width="130" />
        <el-table-column prop="nickname" label="昵称" min-width="120" />
        <el-table-column label="会员等级" min-width="100">
          <template #default="{ row }">
            <el-tag v-if="row.memberLevel > 0" type="warning">Lv{{ row.memberLevel }}</el-tag>
            <el-tag v-else type="info">普通</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="会员到期" min-width="170">
          <template #default="{ row }">{{ formatTime(row.memberExpire) }}</template>
        </el-table-column>
        <el-table-column label="会员来源" min-width="110">
          <template #default="{ row }">
            <template v-if="row.memberLevel > 0">
              <el-tag v-if="row.memberSource === 'redeem'" type="success" size="small">兑换码</el-tag>
              <el-tag v-else-if="row.memberSource === 'pay'" type="primary" size="small">支付</el-tag>
              <el-tag v-else type="info" size="small">后台</el-tag>
            </template>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column prop="source" label="注册来源" min-width="100" />
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="warning" @click="openGrant(row)">开通/续期</el-button>
            <el-button size="small" type="danger" :disabled="!row.memberLevel || row.memberLevel <= 0" @click="cancelMember(row)">取消会员</el-button>
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

    <el-dialog v-model="grantDialog.visible" title="开通 / 续期会员" width="420px">
      <el-form label-width="90px">
        <el-form-item label="用户">
          <span>{{ grantDialog.username }}</span>
        </el-form-item>
        <el-form-item label="会员等级" required>
          <el-select v-model="grantDialog.level" style="width: 100%">
            <el-option label="Lv1 标准会员" :value="1" />
          </el-select>
        </el-form-item>
        <el-form-item label="有效期(天)" required>
          <el-input-number v-model="grantDialog.days" :min="1" :max="3650" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="grantDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="grantDialog.loading" @click="submitGrant">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getMemberList, setMember } from '@/api/members'

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const query = reactive({ page: 1, size: 10, keyword: '' })

const grantDialog = reactive({ visible: false, loading: false, id: null, username: '', level: 1, days: 30 })

function formatTime(t) {
  if (!t) return '—'
  return String(t).replace('T', ' ').slice(0, 19)
}

async function loadData() {
  loading.value = true
  try {
    const data = await getMemberList({ page: query.page, size: query.size, keyword: query.keyword })
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

function openGrant(row) {
  grantDialog.id = row.id
  grantDialog.username = row.nickname || row.username
  grantDialog.level = 1
  grantDialog.days = 30
  grantDialog.visible = true
}

async function submitGrant() {
  grantDialog.loading = true
  try {
    await setMember(grantDialog.id, { level: grantDialog.level, days: grantDialog.days })
    ElMessage.success('会员已开通/续期')
    grantDialog.visible = false
    loadData()
  } finally {
    grantDialog.loading = false
  }
}

async function cancelMember(row) {
  await ElMessageBox.confirm(`确定取消「${row.username}」的会员资格吗？`, '取消会员', { type: 'warning' })
  await setMember(row.id, { level: 0 })
  ElMessage.success('已取消会员')
  loadData()
}

onMounted(loadData)
</script>

<style scoped>
:deep(.el-table__inner-wrapper table),
:deep(.el-table__header-wrapper table),
:deep(.el-table__body-wrapper table) {
  width: 100% !important;
  table-layout: fixed;
}
</style>
