<template>
  <div class="page-container" v-loading="loading">
    <!-- 检索栏 -->
    <div class="dash-toolbar">
      <div class="range-box">
        <span class="range-label">统计区间</span>
        <el-date-picker v-model="range" type="daterange" range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期"
                        value-format="YYYY-MM-DD" :clearable="false" @change="loadBusiness" style="width: 260px" />
        <el-button-group style="margin-left: 8px">
          <el-button size="small" @click="quickRange(7)">近7天</el-button>
          <el-button size="small" @click="quickRange(30)">近30天</el-button>
          <el-button size="small" @click="quickRange(90)">近90天</el-button>
        </el-button-group>
      </div>
      <el-button type="primary" :icon="Refresh" circle @click="loadBusiness" />
    </div>

    <!-- 指标卡 -->
    <el-row :gutter="12">
      <el-col :xs="12" :sm="12" :md="6" :lg="6" v-for="card in statCards" :key="card.label">
        <el-card class="stat-card" shadow="hover">
          <div class="stat-left">
            <div class="stat-label">{{ card.label }}</div>
            <div class="stat-value" :style="{ color: card.color }">{{ card.value }}</div>
            <div class="stat-sub">{{ card.sub }}</div>
          </div>
          <div class="stat-icon" :style="{ background: card.color }">
            <el-icon><component :is="card.icon" /></el-icon>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 趋势折线 -->
    <el-card shadow="never" class="dash-card">
      <template #header><span>注册 / 付费 / 收入趋势（{{ rangeText }}）</span></template>
      <div ref="trendRef" class="chart chart-trend"></div>
    </el-card>

    <!-- 分布饼图 -->
    <el-row :gutter="12" style="margin-top: 12px">
      <el-col :xs="24" :sm="8">
        <el-card shadow="never" class="dash-card">
          <template #header><span>会员等级分布</span></template>
          <div ref="levelRef" class="chart"></div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="8">
        <el-card shadow="never" class="dash-card">
          <template #header><span>套餐销量分布</span></template>
          <div ref="planRef" class="chart"></div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="8">
        <el-card shadow="never" class="dash-card">
          <template #header><span>注册来源分布</span></template>
          <div ref="sourceRef" class="chart"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 表格区 -->
    <el-row :gutter="12" style="margin-top: 12px">
      <el-col :xs="24" :md="12">
        <el-card shadow="never" class="dash-card">
          <template #header><span>最近支付订单</span></template>
          <el-table :data="recentOrders" size="small" empty-text="暂无支付记录">
            <el-table-column prop="id" label="ID" width="60" />
            <el-table-column prop="userId" label="用户ID" width="80" />
            <el-table-column prop="planId" label="套餐" width="70">
              <template #default="{ row }">{{ planName(row.planId) }}</template>
            </el-table-column>
            <el-table-column prop="amount" label="金额">
              <template #default="{ row }">¥{{ Number(row.amount).toFixed(2) }}</template>
            </el-table-column>
            <el-table-column label="支付时间">
              <template #default="{ row }">{{ formatTime(row.paidAt) }}</template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="12">
        <el-card shadow="never" class="dash-card">
          <template #header><span>最近注册用户</span></template>
          <el-table :data="recentUsers" size="small" empty-text="暂无数据">
            <el-table-column prop="id" label="ID" width="60" />
            <el-table-column prop="username" label="用户名" />
            <el-table-column prop="nickname" label="昵称" />
            <el-table-column label="注册时间">
              <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="12" style="margin-top: 12px">
      <el-col :xs="24" :md="12">
        <el-card shadow="never" class="dash-card">
          <template #header><span>最近更新导图</span></template>
          <el-table :data="recentMindmaps" size="small" empty-text="暂无数据">
            <el-table-column prop="id" label="ID" width="60" />
            <el-table-column prop="name" label="导图名" show-overflow-tooltip />
            <el-table-column prop="layout" label="布局" width="90" />
            <el-table-column label="更新时间">
              <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="12">
        <el-card shadow="never" class="dash-card">
          <template #header><span>存储使用情况</span></template>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="存储类型">{{ storage.type || '—' }}</el-descriptions-item>
            <el-descriptions-item label="已用空间">{{ storage.used || '—' }}</el-descriptions-item>
            <el-descriptions-item label="Bucket">{{ storage.bucket || '—' }}</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount, nextTick } from 'vue'
import * as echarts from 'echarts'
import { User, Medal, TrendCharts, Wallet, Coin, AlarmClock, Money, UserFilled, Refresh } from '@element-plus/icons-vue'
import { getUserList } from '@/api/users'
import { getMindmapList } from '@/api/mindmaps'
import { getDashboardStats, getBusinessStats } from '@/api/settings'

const loading = ref(false)
const range = ref([])
const rangeText = ref('')
const trendRef = ref(null)
const levelRef = ref(null)
const planRef = ref(null)
const sourceRef = ref(null)
let trendChart = null, levelChart = null, planChart = null, sourceChart = null

const stats = reactive({ totalUsers: 0, newUsers: 0, totalVips: 0, newVips: 0, vipRate: 0, totalIncome: 0, todayIncome: 0, paidOrders: 0, expireSoonVips: 0 })
const storage = reactive({ type: '', used: '', bucket: '' })
const recentUsers = ref([])
const recentMindmaps = ref([])
const recentOrders = ref([])
const planNameMap = reactive({})

const statCards = ref([
  { label: '注册用户', value: 0, sub: '', icon: User, color: '#3a8ee6' },
  { label: 'VIP 会员', value: 0, sub: '', icon: Medal, color: '#f0b429' },
  { label: '付费转化率', value: 0, sub: '', icon: TrendCharts, color: '#67c23a' },
  { label: '区间收入', value: 0, sub: '', icon: Wallet, color: '#e6a23c' },
  { label: '今日收入', value: 0, sub: '', icon: Money, color: '#07c160' },
  { label: '区间新增注册', value: 0, sub: '', icon: UserFilled, color: '#0e7c86' },
  { label: '区间新增付费', value: 0, sub: '', icon: Coin, color: '#8b5cf6' },
  { label: '30天内到期', value: 0, sub: '', icon: AlarmClock, color: '#f56c6c' }
])

function planName(id) {
  return planNameMap[id] || ('套餐#' + id)
}

function formatTime(t) {
  if (!t) return '—'
  return String(t).replace('T', ' ').slice(0, 19)
}

function fmtMoney(v) {
  return '¥' + Number(v || 0).toFixed(2)
}

function quickRange(days) {
  const end = new Date()
  const start = new Date()
  start.setDate(start.getDate() - days + 1)
  range.value = [fmtDate(start), fmtDate(end)]
  loadBusiness()
}

function fmtDate(d) {
  const y = d.getFullYear(), m = String(d.getMonth() + 1).padStart(2, '0'), dd = String(d.getDate()).padStart(2, '0')
  return y + '-' + m + '-' + dd
}

async function loadBusiness() {
  const [s, e] = range.value || []
  rangeText.value = (s && e) ? (s + ' ~ ' + e) : '全部时间'
  try {
    const d = await getBusinessStats(s || '', e || '')
    stats.totalUsers = d.totalUsers ?? 0
    stats.newUsers = d.newUsers ?? 0
    stats.totalVips = d.totalVips ?? 0
    stats.newVips = d.newVips ?? 0
    stats.vipRate = d.vipRate ?? 0
    stats.totalIncome = d.totalIncome ?? 0
    stats.todayIncome = d.todayIncome ?? 0
    stats.paidOrders = d.paidOrders ?? 0
    stats.expireSoonVips = d.expireSoonVips ?? 0
    recentOrders.value = d.recentOrders || []
    // 套餐名
    d.planDist && d.planDist.forEach(p => { planNameMap[p.name] = p.name })
    // 指标卡
    statCards.value[0].value = stats.totalUsers
    statCards.value[0].sub = '总注册用户'
    statCards.value[1].value = stats.totalVips
    statCards.value[1].sub = '当前有效会员'
    statCards.value[2].value = stats.vipRate + '%'
    statCards.value[2].sub = 'VIP / 注册'
    statCards.value[3].value = fmtMoney(stats.totalIncome)
    statCards.value[3].sub = '区间已支付 ' + stats.paidOrders + ' 单'
    statCards.value[4].value = fmtMoney(stats.todayIncome)
    statCards.value[4].sub = '今日已支付'
    statCards.value[5].value = stats.newUsers
    statCards.value[5].sub = '区间新增注册'
    statCards.value[6].value = stats.newVips
    statCards.value[6].sub = '区间新增付费用户'
    statCards.value[7].value = stats.expireSoonVips
    statCards.value[7].sub = '30天内到期提醒'
    await nextTick()
    renderTrend(d.trend || [])
    renderPie(levelRef.value, levelChart, d.levelDist || [], ['#f0b429', '#8b5cf6', '#3a8ee6', '#67c23a', '#e6a23c'])
    renderPie(planRef.value, planChart, d.planDist || [], ['#3a8ee6', '#07c160', '#e6a23c', '#8b5cf6', '#f0b429', '#f56c6c'])
    renderPie(sourceRef.value, sourceChart, d.sourceDist || [], ['#3a8ee6', '#07c160', '#8b5cf6'])
  } catch (e) { /* 忽略 */ }
}

function renderTrend(trend) {
  if (!trendChart) {
    if (!trendRef.value) return
    trendChart = echarts.init(trendRef.value)
  }
  const dates = trend.map(t => t.date)
  const users = trend.map(t => t.users)
  const vips = trend.map(t => t.vips)
  const income = trend.map(t => t.income)
  trendChart.setOption({
    tooltip: { trigger: 'axis', confine: true },
    legend: { data: ['注册数', '付费用户', '收入(元)'], top: 0 },
    grid: { left: 50, right: 50, top: 36, bottom: 8, containLabel: true },
    xAxis: { type: 'category', data: dates, axisLabel: { fontSize: 11 } },
    yAxis: [
      { type: 'value', name: '人数', minInterval: 1 },
      { type: 'value', name: '收入(元)', splitLine: { show: false } }
    ],
    series: [
      { name: '注册数', type: 'line', smooth: true, data: users, areaStyle: { opacity: 0.08 }, itemStyle: { color: '#3a8ee6' } },
      { name: '付费用户', type: 'line', smooth: true, data: vips, itemStyle: { color: '#f0b429' } },
      { name: '收入(元)', type: 'line', smooth: true, yAxisIndex: 1, data: income, itemStyle: { color: '#07c160' } }
    ]
  }, true)
}

function renderPie(el, chart, data, colors) {
  if (!el) return
  const c = chart || echarts.init(el)
  if (el === levelRef.value) levelChart = c
  if (el === planRef.value) planChart = c
  if (el === sourceRef.value) sourceChart = c
  c.setOption({
    tooltip: { trigger: 'item', confine: true },
    legend: { bottom: 0, type: 'scroll', fontSize: 11 },
    color: colors,
    series: [{
      type: 'pie', radius: ['40%', '66%'], center: ['50%', '44%'],
      label: { show: true, formatter: '{b}\n{c}' , fontSize: 11 },
      data: data.length ? data : [{ name: '暂无', value: 0 }]
    }]
  }, true)
}

async function loadStats() {
  try {
    const d = await getDashboardStats()
    storage.type = d.storage?.type || ''
    const ts = d.storage?.totalSize || 0
    storage.used = ts > 1024 * 1024 * 1024 ? (ts / 1024 / 1024 / 1024).toFixed(2) + ' GB'
      : ts > 1024 * 1024 ? (ts / 1024 / 1024).toFixed(2) + ' MB' : (ts / 1024).toFixed(1) + ' KB'
    storage.bucket = d.storage?.bucket || ''
  } catch (e) { /* 忽略 */ }
}

async function loadRecent() {
  try { const u = await getUserList({ page: 1, size: 5 }); recentUsers.value = u.list || [] } catch (e) { recentUsers.value = [] }
  try { const m = await getMindmapList({ page: 1, size: 5 }); recentMindmaps.value = m.list || [] } catch (e) { recentMindmaps.value = [] }
}

function onResize() {
  trendChart && trendChart.resize()
  levelChart && levelChart.resize()
  planChart && planChart.resize()
  sourceChart && sourceChart.resize()
}

onMounted(async () => {
  loading.value = true
  quickRange(7)
  await Promise.all([loadStats(), loadRecent()])
  window.addEventListener('resize', onResize)
  loading.value = false
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  trendChart && trendChart.dispose()
  levelChart && levelChart.dispose()
  planChart && planChart.dispose()
  sourceChart && sourceChart.dispose()
})
</script>

<style scoped>
.dash-toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 14px; flex-wrap: wrap; gap: 8px; }
.range-box { display: flex; align-items: center; gap: 4px; flex-wrap: wrap; }
.range-label { font-size: 13px; color: #909399; }
.stat-card { margin-bottom: 12px; display: flex; justify-content: space-between; align-items: center; }
.stat-left { flex: 1; min-width: 0; }
.stat-label { font-size: 12px; color: #909399; margin-bottom: 4px; }
.stat-value { font-size: 24px; font-weight: 800; line-height: 1.2; }
.stat-sub { font-size: 11px; color: #909399; margin-top: 4px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.stat-icon { width: 46px; height: 46px; border-radius: 12px; color: #fff; display: flex; align-items: center; justify-content: center; font-size: 22px; flex-shrink: 0; }
.dash-card { margin-bottom: 0; }
.chart { width: 100%; height: 300px; }
.chart-trend { height: 320px; }
</style>
