<!-- 会员功能开关 + 套餐价格管理 + 兑换码管理 -->
<template>
  <div class="page-container">
    <el-card shadow="never" class="cfg-card">
      <template #header><b>会员总开关</b><span class="card-tip">关闭后客户端不显示会员中心，全部功能开放，用户感知不到会员</span></template>
      <el-form label-width="180px" label-position="left">
        <el-form-item label="启用会员功能">
          <el-switch v-model="form.memberEnabled" />
          <span class="field-hint">关闭 = 全部功能对所有用户开放</span>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="cfg-card">
      <template #header><b>会员功能开关</b><span class="card-tip">留空表示全部开放（不限制）；勾选后仅会员可用，客户端会显示会员标识并拦截非会员</span></template>
      <el-form label-width="180px" label-position="left">
        <el-form-item label="会员专属主题">
          <el-select v-model="memberThemeIds" multiple filterable collapse-tags placeholder="选择会员专属主题（留空=全部开放）" style="width: 100%">
            <el-option v-for="t in themes" :key="t.id" :label="t.name" :value="String(t.id)" />
          </el-select>
          <span class="field-hint">勾选的主题仅会员可使用</span>
        </el-form-item>
        <el-form-item label="会员专属导出格式">
          <el-select v-model="memberExportFormats" multiple filterable collapse-tags placeholder="选择会员专属导出格式（留空=全部开放）" style="width: 100%">
            <el-option v-for="f in exportOptions" :key="f" :label="f.toUpperCase()" :value="f" />
          </el-select>
          <span class="field-hint">勾选的导出格式仅会员可使用</span>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="cfg-card">
      <template #header><b>机构协作</b><span class="card-tip">创建机构 / 发起导图协作的会员门槛；关闭后普通用户也可创建机构和发起协作，会员到期仅停止发起新协作，已有协作不受影响</span></template>
      <el-form label-width="180px" label-position="left">
        <el-form-item label="创建机构需要会员">
          <el-switch v-model="form.orgCreateNeedMember" />
          <span class="field-hint">开启时，非会员无法创建机构</span>
        </el-form-item>
        <el-form-item label="发起协作需要会员">
          <el-switch v-model="form.orgInviteNeedMember" />
          <span class="field-hint">开启时，非会员/会员到期后无法把导图加入机构协作（已有协作不受影响，续费即可恢复）</span>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="cfg-card">
      <template #header><b>上传限制</b><span class="card-tip">普通用户 / 会员的资源数量与单文件大小上限</span></template>
      <el-form label-width="180px" label-position="left">
        <el-form-item label="普通用户数量上限">
          <el-input-number v-model="form.uploadFreeCount" :min="1" :max="100000" />
        </el-form-item>
        <el-form-item label="普通用户单文件(MB)">
          <el-input-number v-model="form.uploadFreeMaxMb" :min="1" :max="10240" />
        </el-form-item>
        <el-form-item label="会员数量上限">
          <el-input-number v-model="form.uploadVipCount" :min="1" :max="100000" />
        </el-form-item>
        <el-form-item label="会员单文件(MB)">
          <el-input-number v-model="form.uploadVipMaxMb" :min="1" :max="10240" />
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="cfg-card">
      <template #header><b>画布个数限制</b><span class="card-tip">每位用户可创建的导图数量上限</span></template>
      <el-form label-width="180px" label-position="left">
        <el-form-item label="普通用户画布上限">
          <el-input-number v-model="form.mindmapFreeCount" :min="1" :max="100000" />
        </el-form-item>
        <el-form-item label="会员画布上限">
          <el-input-number v-model="form.mindmapVipCount" :min="1" :max="100000" />
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 会员价格管理 -->
    <el-card shadow="never" class="cfg-card">
      <template #header>
        <b>会员价格管理</b><span class="card-tip">修改现价 / 原价 / 折扣后，客户端会员中心实时生效</span>
      </template>
      <div class="card-actions">
        <el-button size="small" type="primary" @click="openPlanDialog()">新增套餐</el-button>
      </div>
      <el-table :data="plans" border size="small" v-loading="planLoading">
        <el-table-column prop="name" label="名称" width="150" />
        <el-table-column prop="level" label="级别" width="70" />
        <el-table-column label="现价" width="110">
          <template #default="{ row }">¥{{ Number(row.price || 0).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column label="原价" width="110">
          <template #default="{ row }">¥{{ Number(row.originalPrice || 0).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column label="折扣" width="90">
          <template #default="{ row }">{{ row.discountPercent ? row.discountPercent + '%' : '—' }}</template>
        </el-table-column>
        <el-table-column prop="days" label="天数" width="80" />
        <el-table-column prop="sort" label="排序" width="70" />
        <el-table-column label="启用" width="80">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ row.enabled ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140">
          <template #default="{ row }">
            <el-button size="small" link type="primary" @click="openPlanDialog(row)">编辑</el-button>
            <el-button size="small" link type="danger" @click="removePlan(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 兑换码管理 -->
    <el-card shadow="never" class="cfg-card">
      <template #header>
        <b>会员兑换码</b><span class="card-tip">一次性兑换码；用户端输入兑换码即可开通会员（可设定兑换时长），已兑换码标注使用者</span>
      </template>
      <div class="redeem-toolbar">
        <el-select v-model="gen.planId" placeholder="选择套餐（可空=自定义时长）" clearable filterable style="width: 200px">
          <el-option v-for="p in plans" :key="p.id" :label="p.name" :value="p.id" />
        </el-select>
        <el-input-number v-model="gen.days" :min="1" :max="3650" placeholder="自定义天数" />
        <span class="field-hint">天</span>
        <el-input-number v-model="gen.count" :min="1" :max="100" />
        <span class="field-hint">个</span>
        <el-input v-model="gen.remark" placeholder="备注（如：活动赠送）" style="width: 180px" clearable />
        <el-button type="primary" :loading="genLoading" @click="createCodes">生成兑换码</el-button>
      </div>
      <div class="redeem-toolbar">
        <el-select v-model="codeQuery.status" placeholder="全部状态" clearable style="width: 140px" @change="loadCodes">
          <el-option label="可用" value="active" />
          <el-option label="已兑换" value="used" />
          <el-option label="已停用" value="disabled" />
        </el-select>
        <span class="field-hint">共 <b>{{ codeTotal }}</b> 个</span>
      </div>
      <el-table :data="codes" border size="small" v-loading="codeLoading">
        <el-table-column prop="code" label="兑换码" width="185" />
        <el-table-column prop="planName" label="套餐" width="100" />
        <el-table-column prop="days" label="时长(天)" width="80" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'used' ? 'success' : row.status === 'disabled' ? 'info' : 'warning'" size="small">
              {{ row.status === 'used' ? '已兑换' : row.status === 'disabled' ? '已停用' : '可用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="usedByName" label="使用者" width="120" show-overflow-tooltip />
        <el-table-column prop="usedAt" label="兑换时间" width="165">
          <template #default="{ row }">{{ row.usedAt ? String(row.usedAt).replace('T', ' ').slice(0, 19) : '—' }}</template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="100" show-overflow-tooltip />
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-button size="small" link type="danger" @click="removeCode(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="codeQuery.page"
          v-model:page-size="codeQuery.size"
          :total="codeTotal"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @size-change="loadCodes"
          @current-change="loadCodes"
        />
      </div>
    </el-card>

    <div class="save-bar">
      <el-button type="primary" :loading="saving" @click="save">保存配置</el-button>
    </div>

    <!-- 套餐编辑弹窗 -->
    <el-dialog v-model="planDialog.visible" :title="planDialog.isEdit ? '编辑套餐' : '新增套餐'" width="460px">
      <el-form :model="planForm" label-width="100px" label-position="left">
        <el-form-item label="套餐名称" required>
          <el-input v-model="planForm.name" placeholder="如：月度会员" />
        </el-form-item>
        <el-form-item label="级别">
          <el-input-number v-model="planForm.level" :min="1" :max="10" />
        </el-form-item>
        <el-form-item label="现价(元)" required>
          <el-input-number v-model="planForm.price" :min="0" :max="100000" :precision="2" />
        </el-form-item>
        <el-form-item label="原价(元)">
          <el-input-number v-model="planForm.originalPrice" :min="0" :max="100000" :precision="2" />
          <span class="field-hint">留空/0 不显示划线价</span>
        </el-form-item>
        <el-form-item label="折扣(%)">
          <el-input-number v-model="planForm.discountPercent" :min="0" :max="100" />
          <span class="field-hint">如 80 = 8折；0 = 无折扣</span>
        </el-form-item>
        <el-form-item label="时长(天)" required>
          <el-input-number v-model="planForm.days" :min="1" :max="3650" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="planForm.sort" :min="0" :max="999" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="planForm.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="planDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="planSaving" @click="savePlan">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getSettings, saveSettings } from '@/api/settings'
import { themeApi } from '@/api/themes'
import { getMemberPlans, createMemberPlan, updateMemberPlan, deleteMemberPlan, getRedeemCodes, createRedeemCodes, deleteRedeemCode } from '@/api/members'

const saving = ref(false)
const memberThemeIds = ref([])
const memberExportFormats = ref([])
const themes = ref([])
const exportOptions = ['json', 'usmind', 'png', 'jpg', 'jpeg', 'svg', 'pdf', 'markdown']

const form = reactive({
  memberEnabled: true,
  orgCreateNeedMember: true, orgInviteNeedMember: true,
  uploadFreeCount: 20, uploadFreeMaxMb: 20, uploadVipCount: 200, uploadVipMaxMb: 200,
  mindmapFreeCount: 5, mindmapVipCount: 200
})

// ===== 套餐价格管理 =====
const plans = ref([])
const planLoading = ref(false)
const planDialog = reactive({ visible: false, isEdit: false, editId: null })
const planForm = reactive({ name: '', level: 1, price: 0, originalPrice: 0, discountPercent: 0, days: 30, sort: 0, enabled: true })
const planSaving = ref(false)

// ===== 兑换码管理 =====
const gen = reactive({ planId: null, days: 30, count: 1, remark: '' })
const genLoading = ref(false)
const codes = ref([])
const codeLoading = ref(false)
const codeTotal = ref(0)
const codeQuery = reactive({ page: 1, size: 10, status: '' })

async function loadPlans() {
  planLoading.value = true
  try {
    plans.value = (await getMemberPlans()) || []
  } catch (e) { plans.value = [] } finally { planLoading.value = false }
}

function openPlanDialog(row) {
  if (row) {
    planDialog.isEdit = true
    planDialog.editId = row.id
    planForm.name = row.name || ''
    planForm.level = row.level || 1
    planForm.price = Number(row.price || 0)
    planForm.originalPrice = Number(row.originalPrice || 0)
    planForm.discountPercent = Number(row.discountPercent || 0)
    planForm.days = row.days || 30
    planForm.sort = row.sort || 0
    planForm.enabled = !!row.enabled
  } else {
    planDialog.isEdit = false
    planDialog.editId = null
    planForm.name = ''
    planForm.level = 1
    planForm.price = 0
    planForm.originalPrice = 0
    planForm.discountPercent = 0
    planForm.days = 30
    planForm.sort = 0
    planForm.enabled = true
  }
  planDialog.visible = true
}

async function savePlan() {
  if (!planForm.name) { ElMessage.warning('请填写套餐名称'); return }
  planSaving.value = true
  try {
    const data = { ...planForm, enabled: planForm.enabled ? 1 : 0 }
    if (planDialog.isEdit) await updateMemberPlan(planDialog.editId, data)
    else await createMemberPlan(data)
    ElMessage.success('保存成功，客户端价格已实时生效')
    planDialog.visible = false
    loadPlans()
  } finally { planSaving.value = false }
}

async function removePlan(row) {
  await ElMessageBox.confirm(`确定删除套餐「${row.name}」吗？`, '删除确认', { type: 'warning' })
  await deleteMemberPlan(row.id)
  ElMessage.success('删除成功')
  loadPlans()
}

async function createCodes() {
  genLoading.value = true
  try {
    const data = {
      planId: gen.planId,
      days: gen.planId ? undefined : gen.days,
      count: gen.count,
      remark: gen.remark
    }
    const r = await createRedeemCodes(data)
    ElMessage.success(`已生成 ${r.created} 个兑换码`)
    gen.remark = ''
    loadCodes()
  } finally { genLoading.value = false }
}

async function loadCodes() {
  codeLoading.value = true
  try {
    const params = { page: codeQuery.page, size: codeQuery.size }
    if (codeQuery.status) params.status = codeQuery.status
    const d = await getRedeemCodes(params)
    codes.value = d.list || []
    codeTotal.value = d.total || 0
  } finally { codeLoading.value = false }
}

async function removeCode(row) {
  await ElMessageBox.confirm(`确定删除兑换码「${row.code}」吗？`, '删除确认', { type: 'warning' })
  await deleteRedeemCode(row.id)
  ElMessage.success('删除成功')
  loadCodes()
}

onMounted(async () => {
  try {
    const d = await getSettings()
    form.memberEnabled = d.memberEnabled !== undefined ? !!d.memberEnabled : true
    form.orgCreateNeedMember = d.orgCreateNeedMember !== undefined ? !!Number(d.orgCreateNeedMember) : true
    form.orgInviteNeedMember = d.orgInviteNeedMember !== undefined ? !!Number(d.orgInviteNeedMember) : true
    memberThemeIds.value = d.memberThemeIds ? String(d.memberThemeIds).split(',').map(x => x.trim()).filter(Boolean) : []
    memberExportFormats.value = d.memberExportFormats ? String(d.memberExportFormats).split(',').map(x => x.trim()).filter(Boolean) : []
    form.uploadFreeCount = Number(d.uploadFreeCount || 20)
    form.uploadFreeMaxMb = Number(d.uploadFreeMaxMb || 20)
    form.uploadVipCount = Number(d.uploadVipCount || 200)
    form.uploadVipMaxMb = Number(d.uploadVipMaxMb || 200)
    form.mindmapFreeCount = Number(d.mindmapFreeCount || 5)
    form.mindmapVipCount = Number(d.mindmapVipCount || 200)
  } catch (e) { /* 忽略 */ }
  try {
    themes.value = await themeApi.list() || []
  } catch (e) { /* 忽略 */ }
  loadPlans()
  loadCodes()
})

async function save() {
  saving.value = true
  try {
    await saveSettings({
      memberEnabled: form.memberEnabled ? 1 : 0,
      orgCreateNeedMember: form.orgCreateNeedMember ? 1 : 0,
      orgInviteNeedMember: form.orgInviteNeedMember ? 1 : 0,
      memberThemeIds: memberThemeIds.value.join(','),
      memberExportFormats: memberExportFormats.value.join(','),
      uploadFreeCount: String(form.uploadFreeCount),
      uploadFreeMaxMb: String(form.uploadFreeMaxMb),
      uploadVipCount: String(form.uploadVipCount),
      uploadVipMaxMb: String(form.uploadVipMaxMb),
      mindmapFreeCount: String(form.mindmapFreeCount),
      mindmapVipCount: String(form.mindmapVipCount)
    })
    ElMessage.success('保存成功')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.cfg-card { margin-bottom: 16px; }
.card-tip { font-weight: normal; font-size: 12px; color: #909399; margin-left: 12px; }
.field-hint { font-size: 12px; color: #909399; margin-left: 10px; }
.save-bar { margin-top: 8px; }
.card-actions { margin-bottom: 10px; }
.redeem-toolbar { display: flex; align-items: center; gap: 10px; margin-bottom: 10px; flex-wrap: wrap; }
.pagination-wrap { display: flex; justify-content: flex-end; margin-top: 10px; }
</style>
