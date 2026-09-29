<!-- 会员功能开关 -->
<template>
  <div class="page-container">
    <el-card shadow="never" class="cfg-card">
      <template #header><b>会员功能开关</b><span class="card-tip">留空表示全部开放（不限制）；填写后仅会员可用，客户端会显示会员图标并拦截非会员</span></template>
      <el-form label-width="180px" label-position="left">
        <el-form-item label="会员专属主题">
          <el-input v-model="form.memberThemeIds" placeholder="主题ID，多个用逗号分隔，如 3,5；留空=全部开放" />
        </el-form-item>
        <el-form-item label="会员专属导出格式">
          <el-input v-model="form.memberExportFormats" placeholder="格式名，如 png,pdf,svg；留空=全部开放" />
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

const saving = ref(false)
const form = reactive({
  memberThemeIds: '', memberExportFormats: '',
  uploadFreeCount: 20, uploadFreeMaxMb: 20, uploadVipCount: 200, uploadVipMaxMb: 200,
  mindmapFreeCount: 5, mindmapVipCount: 200
})

onMounted(async () => {
  try {
    const d = await getSettings()
    form.memberThemeIds = d.memberThemeIds || ''
    form.memberExportFormats = d.memberExportFormats || ''
    form.uploadFreeCount = Number(d.uploadFreeCount || 20)
    form.uploadFreeMaxMb = Number(d.uploadFreeMaxMb || 20)
    form.uploadVipCount = Number(d.uploadVipCount || 200)
    form.uploadVipMaxMb = Number(d.uploadVipMaxMb || 200)
    form.mindmapFreeCount = Number(d.mindmapFreeCount || 5)
    form.mindmapVipCount = Number(d.mindmapVipCount || 200)
  } catch (e) { /* 忽略 */ }
})

async function save() {
  saving.value = true
  try {
    await saveSettings({
      memberThemeIds: form.memberThemeIds,
      memberExportFormats: form.memberExportFormats,
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
.save-bar { margin-top: 8px; }
</style>
