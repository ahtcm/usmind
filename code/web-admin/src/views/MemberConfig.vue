<!-- 会员功能开关 -->
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

    <div class="save-bar">
      <el-button type="primary" :loading="saving" @click="save">保存配置</el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getSettings, saveSettings } from '@/api/settings'
import { themeApi } from '@/api/themes'
const saving = ref(false)
const memberThemeIds = ref([])
const memberExportFormats = ref([])
const themes = ref([])
const exportOptions = ['json', 'usmind', 'png', 'jpg', 'jpeg', 'svg', 'pdf', 'markdown']

const form = reactive({
  memberEnabled: true,
  uploadFreeCount: 20, uploadFreeMaxMb: 20, uploadVipCount: 200, uploadVipMaxMb: 200,
  mindmapFreeCount: 5, mindmapVipCount: 200
})

onMounted(async () => {
  try {
    const d = await getSettings()
    form.memberEnabled = d.memberEnabled !== undefined ? !!d.memberEnabled : true
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
})

async function save() {
  saving.value = true
  try {
    await saveSettings({
      memberEnabled: form.memberEnabled ? 1 : 0,
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
</style>
