<template>
  <div class="page-container">
    <el-card shadow="never">
      <!-- 筛选栏 -->
      <div class="toolbar">
        <el-input v-model="query.username" placeholder="用户" clearable style="width: 150px" @keyup.enter="handleSearch" @clear="handleSearch" />
        <el-select
          v-model="query.path"
          placeholder="接口（下拉）"
          clearable
          filterable
          remote
          :remote-method="loadInterfaces"
          style="width: 260px"
        >
          <el-option v-for="p in interfaceList" :key="p" :label="p" :value="p" />
        </el-select>
        <el-select v-model="query.level" placeholder="级别" clearable style="width: 110px">
          <el-option label="正常" value="info" />
          <el-option label="警告" value="warn" />
          <el-option label="错误" value="error" />
        </el-select>
        <el-select v-model="query.success" placeholder="状态" clearable style="width: 110px">
          <el-option label="正常" :value="1" />
          <el-option label="异常" :value="0" />
        </el-select>
        <el-select v-model="query.source" placeholder="来源" clearable style="width: 110px">
          <el-option label="后端" value="backend" />
          <el-option label="前端" value="frontend" />
        </el-select>
        <el-date-picker v-model="dateRange" type="daterange" range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期" value-format="YYYY-MM-DD" style="width: 260px" />
        <el-button type="primary" @click="handleSearch">搜索</el-button>
        <el-button @click="handleReset">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="tableData" border>
        <el-table-column label="时间" width="160">
          <template #default="{ row }">{{ formatTime(row.requestAt) }}</template>
        </el-table-column>
        <el-table-column label="用户" width="110">
          <template #default="{ row }">{{ row.username || (row.userId ? ('#' + row.userId) : '匿名') }}</template>
        </el-table-column>
        <el-table-column label="接口" min-width="240">
          <template #default="{ row }">
            <el-tag size="small" :type="methodType(row.method)" style="margin-right:6px">{{ row.method }}</el-tag>
            <span class="path-text">{{ row.path }}</span>
          </template>
        </el-table-column>
        <el-table-column label="级别" width="80">
          <template #default="{ row }">
            <el-tag size="small" :type="levelType(row.level)">{{ levelLabel(row.level) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag size="small" :type="row.success === 1 ? 'success' : 'danger'">{{ row.success === 1 ? '正常' : '异常' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="HTTP" width="70">
          <template #default="{ row }">{{ row.statusCode || '—' }}</template>
        </el-table-column>
        <el-table-column label="耗时(ms)" width="90" sortable :sort-by="'costMs'">
          <template #default="{ row }">{{ row.costMs }}</template>
        </el-table-column>
        <el-table-column prop="ip" label="IP" width="120" show-overflow-tooltip />
        <el-table-column label="来源" width="80">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ row.source === 'frontend' ? '前端' : '后端' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          :total="total"
          :page-sizes="[20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </el-card>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detail.visible" title="日志详情" width="720px" append-to-body>
      <template v-if="detail.data">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="时间">{{ formatTime(detail.data.requestAt) }}</el-descriptions-item>
          <el-descriptions-item label="用户">{{ detail.data.username || '匿名' }}</el-descriptions-item>
          <el-descriptions-item label="接口">{{ detail.data.method }} {{ detail.data.path }}</el-descriptions-item>
          <el-descriptions-item label="来源">{{ detail.data.source === 'frontend' ? '前端上报' : '后端请求' }}</el-descriptions-item>
          <el-descriptions-item label="HTTP 状态">{{ detail.data.statusCode || '—' }}</el-descriptions-item>
          <el-descriptions-item label="耗时">{{ detail.data.costMs }} ms</el-descriptions-item>
          <el-descriptions-item label="IP">{{ detail.data.ip || '—' }}</el-descriptions-item>
          <el-descriptions-item label="User-Agent" :span="2">{{ detail.data.ua || '—' }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.data.query" label="Query" :span="2">{{ detail.data.query }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.data.errorMsg" label="错误信息" :span="2">
            <span class="err-text">{{ detail.data.errorMsg }}</span>
          </el-descriptions-item>
          <el-descriptions-item v-if="detail.data.reqHeaders" label="请求头" :span="2">
            <pre class="detail-pre">{{ detail.data.reqHeaders }}</pre>
          </el-descriptions-item>
          <el-descriptions-item v-if="detail.data.reqBody" label="请求体" :span="2">
            <pre class="detail-pre">{{ detail.data.reqBody }}</pre>
          </el-descriptions-item>
          <el-descriptions-item v-if="detail.data.respBody" label="返回结果" :span="2">
            <pre class="detail-pre resp-pre">{{ detail.data.respBody }}</pre>
          </el-descriptions-item>
          <el-descriptions-item v-if="detail.data.detail" label="异常堆栈" :span="2">
            <pre class="detail-pre">{{ detail.data.detail }}</pre>
          </el-descriptions-item>
        </el-descriptions>

        <!-- 关联前端日志 -->
        <template v-if="detail.frontendLogs && detail.frontendLogs.length">
          <div class="front-title">关联前端上报日志（同接口最近 {{ detail.frontendLogs.length }} 条）</div>
          <el-table :data="detail.frontendLogs" border size="small">
            <el-table-column label="时间" width="150">
              <template #default="{ row }">{{ formatTime(row.requestAt) }}</template>
            </el-table-column>
            <el-table-column label="级别" width="80">
              <template #default="{ row }">
                <el-tag size="small" :type="levelType(row.level)">{{ levelLabel(row.level) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="errorMsg" label="错误信息" min-width="200" show-overflow-tooltip />
          </el-table>
        </template>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getLogList, getInterfaceList, getLogDetail } from '@/api/logs'

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const interfaceList = ref([])
const dateRange = ref(null)
const query = reactive({ page: 1, size: 20, username: '', path: '', level: '', success: null, source: '', startDate: '', endDate: '' })
const detail = reactive({ visible: false, data: null, frontendLogs: [] })

function methodType(m) {
  if (m === 'GET') return 'success'
  if (m === 'POST') return 'primary'
  if (m === 'PUT' || m === 'PATCH') return 'warning'
  if (m === 'DELETE') return 'danger'
  return 'info'
}
function levelType(l) {
  if (l === 'error') return 'danger'
  if (l === 'warn') return 'warning'
  return 'info'
}
function levelLabel(l) {
  if (l === 'error') return '错误'
  if (l === 'warn') return '警告'
  return '正常'
}
function formatTime(t) {
  if (!t) return '—'
  return String(t).replace('T', ' ').slice(0, 19)
}

async function loadInterfaces(kw) {
  try {
    interfaceList.value = await getInterfaceList(kw || '')
  } catch (e) { /* 忽略 */ }
}

async function loadData() {
  loading.value = true
  try {
    const params = { page: query.page, size: query.size }
    if (query.username) params.username = query.username
    if (query.path) params.path = query.path
    if (query.level) params.level = query.level
    if (query.success !== null && query.success !== '') params.success = query.success
    if (query.source) params.source = query.source
    if (dateRange.value && dateRange.value.length === 2) {
      params.startDate = dateRange.value[0]
      params.endDate = dateRange.value[1]
    }
    const data = await getLogList(params)
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

function handleReset() {
  query.username = ''
  query.path = ''
  query.level = ''
  query.success = null
  query.source = ''
  dateRange.value = null
  handleSearch()
}

async function openDetail(row) {
  try {
    const d = await getLogDetail(row.id)
    detail.data = d.log
    detail.frontendLogs = d.frontendLogs || []
    detail.visible = true
  } catch (e) { /* 拦截器已提示 */ }
}

onMounted(() => {
  loadData()
  loadInterfaces('')
})
</script>

<style scoped>
.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 16px;
  align-items: center;
}
.path-text {
  font-family: monospace;
  font-size: 13px;
}
.pagination-wrap {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
.err-text {
  color: #f56c6c;
}
.detail-pre {
  white-space: pre-wrap;
  word-break: break-all;
  background: #f5f7fa;
  border-radius: 6px;
  padding: 8px;
  font-size: 12px;
  margin: 0;
  max-height: 220px;
  overflow-y: auto;
}
.resp-pre { color: #67c23a; }
.front-title {
  margin: 14px 0 8px;
  font-weight: 600;
  font-size: 13px;
  color: #606266;
}
</style>
