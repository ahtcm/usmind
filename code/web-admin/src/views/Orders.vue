<template>
  <div class="page-container">
    <el-card shadow="never">
      <div class="toolbar">
        <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 140px" @change="handleSearch">
          <el-option label="待支付" value="created" />
          <el-option label="已支付" value="paid" />
          <el-option label="已取消" value="closed" />
          <el-option label="已退款" value="refunded" />
        </el-select>
        <el-input
          v-model="query.keyword"
          placeholder="搜索订单号 / 微信交易号"
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
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="outTradeNo" label="订单号" min-width="180" show-overflow-tooltip />
        <el-table-column prop="username" label="用户" width="110" show-overflow-tooltip />
        <el-table-column prop="planName" label="套餐" width="110" show-overflow-tooltip />
        <el-table-column prop="amount" label="金额(元)" width="100">
          <template #default="{ row }">￥{{ Number(row.amount || 0).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column prop="channel" label="渠道" width="90">
          <template #default="{ row }">{{ row.channel === 'wechat' ? '微信' : row.channel }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="165">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column prop="paidAt" label="支付时间" width="165">
          <template #default="{ row }">{{ formatTime(row.paidAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
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
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getOrderList, deleteOrder } from '@/api/orders'

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const query = reactive({ page: 1, size: 10, keyword: '', status: '' })

function formatTime(t) {
  if (!t) return '—'
  return String(t).replace('T', ' ').slice(0, 19)
}

function statusText(s) {
  return { created: '待支付', paid: '已支付', closed: '已取消', refunded: '已退款' }[s] || s
}
function statusType(s) {
  return { paid: 'success', created: 'warning', closed: 'info', refunded: 'danger' }[s] || 'info'
}

async function loadData() {
  loading.value = true
  try {
    const params = { page: query.page, size: query.size }
    if (query.keyword) params.keyword = query.keyword
    if (query.status) params.status = query.status
    const data = await getOrderList(params)
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
  await ElMessageBox.confirm(`确定删除订单「${row.outTradeNo}」吗？`, '删除确认', { type: 'warning' })
  await deleteOrder(row.id)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(loadData)
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
