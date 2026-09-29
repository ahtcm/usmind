<!-- 机构管理 -->
<template>
  <div class="page-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-head">
          <span><b>机构列表</b><span class="tip">解散机构将同时删除成员与协作关系；创建/协作会员门槛可在「支付与验证码 → 机构协作」配置</span></span>
          <div class="head-ops">
            <el-input v-model="keyword" placeholder="机构名 / 邀请码" clearable style="width: 200px" @keyup.enter="load" @clear="load" />
            <el-button type="primary" @click="load">查询</el-button>
          </div>
        </div>
      </template>
      <el-table :data="list" v-loading="loading" style="width: 100%">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="name" label="机构名称" min-width="140" />
        <el-table-column label="邀请码" width="110">
          <template #default="{ row }">
            <el-tag size="small" type="warning">{{ row.inviteCode }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="ownerNick" label="创建者" width="120" />
        <el-table-column prop="memberCount" label="成员数" width="80" />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '正常' : '已解散' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="160" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="showMembers(row)">成员</el-button>
            <el-button size="small" type="danger" @click="disband(row)">解散</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination background layout="total, prev, pager, next" :total="total" :page-size="pageSize"
          :current-page="page" @current-change="(p) => { page = p; load() }" />
      </div>
    </el-card>

    <el-dialog v-model="memberDialog" :title="'成员列表 — ' + (curOrg ? curOrg.name : '')" width="480px">
      <el-table :data="members" size="small">
        <el-table-column prop="userId" label="用户ID" width="80" />
        <el-table-column prop="nickname" label="昵称" min-width="120" />
        <el-table-column label="角色" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.role === 'owner' ? 'warning' : 'info'">{{ row.role === 'owner' ? '创建者' : '成员' }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getOrgList, getOrgMembers, disbandOrg } from '@/api/orgs'

const list = ref([])
const loading = ref(false)
const page = ref(1)
const pageSize = 10
const total = ref(0)
const keyword = ref('')
const memberDialog = ref(false)
const members = ref([])
const curOrg = ref(null)

onMounted(load)

async function load() {
  loading.value = true
  try {
    const d = await getOrgList({ page: page.value, size: pageSize, keyword: keyword.value || undefined })
    list.value = d.list || []
    total.value = d.total || 0
  } catch (e) {
    ElMessage.error((e && e.message) || '加载失败')
  } finally {
    loading.value = false
  }
}

async function showMembers(row) {
  curOrg.value = row
  try {
    members.value = await getOrgMembers(row.id)
    memberDialog.value = true
  } catch (e) {
    ElMessage.error((e && e.message) || '加载成员失败')
  }
}

function disband(row) {
  ElMessageBox.confirm(`确定解散机构「${row.name}」？将删除机构、全部成员与协作关系，不可恢复。`, '解散机构', { type: 'warning' })
    .then(async () => {
      await disbandOrg(row.id)
      ElMessage.success('已解散')
      load()
    })
    .catch(() => {})
}
</script>

<style scoped>
.card-head { display: flex; align-items: center; justify-content: space-between; }
.tip { font-weight: normal; font-size: 12px; color: #909399; margin-left: 12px; }
.head-ops { display: flex; gap: 8px; }
.pager { margin-top: 14px; display: flex; justify-content: flex-end; }
</style>
