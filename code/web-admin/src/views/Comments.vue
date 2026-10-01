<template>
  <div class="page-container">
    <el-card shadow="never">
      <div class="toolbar">
        <el-select v-model="query.source" placeholder="全部来源" clearable style="width: 120px" @change="handleSearch">
          <el-option label="分享页" value="share" />
          <el-option label="组织内" value="org" />
        </el-select>
        <el-select
          v-model="query.mindMapId"
          placeholder="全部脑图"
          clearable
          filterable
          style="width: 180px"
          @change="handleSearch"
        >
          <el-option v-for="m in mindMaps" :key="m.id" :label="m.name" :value="m.id" />
        </el-select>
        <el-select
          v-model="query.orgId"
          placeholder="全部组织"
          clearable
          filterable
          style="width: 160px"
          @change="handleSearch"
        >
          <el-option v-for="o in orgs" :key="o.id" :label="o.name" :value="o.id" />
        </el-select>
        <el-input
          v-model="query.keyword"
          placeholder="搜索评论者 / 评论内容"
          clearable
          style="width: 240px"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        >
          <template #append>
            <el-button @click="handleSearch">搜索</el-button>
          </template>
        </el-input>
        <el-button type="danger" :disabled="selectedIds.length === 0" @click="handleBatchDelete">
          批量删除（{{ selectedIds.length }}）
        </el-button>
      </div>

      <el-table v-loading="loading" :data="tableData" border @selection-change="onSelectionChange">
        <el-table-column type="selection" width="46" />
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="userName" label="评论者" width="110" show-overflow-tooltip />
        <el-table-column prop="content" label="内容" min-width="240" show-overflow-tooltip />
        <el-table-column prop="mindMapTitle" label="所在导图" width="160" show-overflow-tooltip />
        <el-table-column prop="source" label="来源" width="90">
          <template #default="{ row }">
            <el-tag :type="row.source === 'org' ? 'primary' : 'success'" size="small">
              {{ row.source === 'org' ? '组织内' : '分享页' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="isAuthor" label="身份" width="90">
          <template #default="{ row }">
            <el-tag :type="row.isAuthor ? 'warning' : 'info'" size="small">{{ row.isAuthor ? '登录用户' : '访客' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="ip" label="IP" width="120" show-overflow-tooltip />
        <el-table-column prop="createdAt" label="时间" width="165">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="danger" link @click="handleDelete(row)">删除</el-button>
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
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getCommentList, deleteComment, batchDeleteComments } from '@/api/comments'
import { getMindmapList } from '@/api/mindmaps'
import { getOrgList } from '@/api/orgs'

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const mindMaps = ref([])
const orgs = ref([])
const selectedIds = ref([])
const query = reactive({ page: 1, size: 10, keyword: '', source: '', mindMapId: null, orgId: null })

function formatTime(t) {
  if (!t) return '—'
  return String(t).replace('T', ' ').slice(0, 19)
}

function onSelectionChange(rows) {
  selectedIds.value = rows.map((r) => r.id)
}

async function loadMindMaps() {
  try {
    const data = await getMindmapList({ page: 1, size: 500 })
    mindMaps.value = data.list || []
  } catch (e) {
    mindMaps.value = []
  }
}

async function loadOrgs() {
  try {
    const data = await getOrgList({ page: 1, size: 500 })
    orgs.value = data.list || []
  } catch (e) {
    orgs.value = []
  }
}

async function loadData() {
  loading.value = true
  try {
    const params = { page: query.page, size: query.size }
    if (query.keyword) params.keyword = query.keyword
    if (query.source) params.source = query.source
    if (query.mindMapId) params.mindMapId = query.mindMapId
    if (query.orgId) params.orgId = query.orgId
    const data = await getCommentList(params)
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

async function handleDelete(row) {
  await ElMessageBox.confirm(`确定删除该评论吗？`, '删除确认', { type: 'warning' })
  await deleteComment(row.id)
  ElMessage.success('删除成功')
  loadData()
}

async function handleBatchDelete() {
  if (selectedIds.value.length === 0) return
  await ElMessageBox.confirm(`确定删除选中的 ${selectedIds.value.length} 条评论吗？`, '批量删除确认', { type: 'warning' })
  await batchDeleteComments(selectedIds.value)
  ElMessage.success('批量删除成功')
  loadData()
}

onMounted(() => {
  loadData()
  loadMindMaps()
  loadOrgs()
})
</script>

<style scoped>
.toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
</style>
