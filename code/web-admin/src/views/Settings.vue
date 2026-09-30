<template>
  <div class="page-container">
    <el-row :gutter="16">
      <el-col :xs="24" :md="12">
        <el-card shadow="never">
          <template #header><span>JWT 配置</span></template>
          <el-form label-width="140px">
            <el-form-item label="过期时间">
              <el-input v-model="settings.jwtExpire" />
            </el-form-item>
            <el-form-item label="签发者">
              <el-input v-model="settings.jwtIssuer" />
            </el-form-item>
          </el-form>
        </el-card>

        <el-card shadow="never" style="margin-top: 16px">
          <template #header><span>节点所有权（机构协作）</span></template>
          <el-form label-width="140px">
            <el-form-item label="默认开启节点所有权">
              <el-switch v-model="settings.ownerLockDefault" active-value="1" inactive-value="0" />
              <div class="opt-tip">作用于新建导图：开启后非节点创建者编辑需申请审批；历史节点归导图创建者所有</div>
            </el-form-item>
          </el-form>
        </el-card>

        <el-card shadow="never" style="margin-top: 16px">
          <template #header><span>文件上传限制</span></template>
          <el-form label-width="140px">
            <el-form-item label="最大上传大小(MB)">
              <el-input v-model.number="settings.maxUploadSize" type="number" />
            </el-form-item>
            <el-form-item label="允许类型">
              <el-input v-model="settings.allowTypes" placeholder="如: jpg,png,pdf,docx" />
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>

      <el-col :xs="24" :md="12">
        <el-card shadow="never">
          <template #header><span>AI API 配置</span></template>
          <el-form ref="aiFormRef" :model="settings" label-width="140px">
            <el-form-item label="API 密钥">
              <el-input v-model="settings.aiApiKey" type="password" show-password placeholder="sk-..." />
            </el-form-item>
            <el-form-item label="模型">
              <el-input v-model="settings.aiModel" placeholder="如 gpt-4o-mini" />
            </el-form-item>
            <el-form-item label="端点">
              <el-input v-model="settings.aiEndpoint" placeholder="https://api.openai.com/v1" />
            </el-form-item>
          </el-form>
        </el-card>

        <el-card shadow="never" style="margin-top: 16px">
          <el-button type="primary" :loading="saving" @click="handleSave">保存设置</el-button>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getSettings, saveSettings } from '@/api/settings'

const saving = ref(false)
const aiFormRef = ref(null)
const settings = reactive({
  jwtExpire: '7d',
  jwtIssuer: 'usmind',
  maxUploadSize: 50,
  allowTypes: 'jpg,jpeg,png,gif,pdf,doc,docx,xlsx,pptx,mp4',
  ownerLockDefault: '0',
  aiApiKey: '',
  aiModel: '',
  aiEndpoint: ''
})

async function loadData() {
  try {
    const data = await getSettings()
    Object.assign(settings, data)
  } catch (e) {
    // 接口未就绪时保留默认值
  }
}

async function handleSave() {
  saving.value = true
  try {
    await saveSettings({ ...settings })
    ElMessage.success('设置已保存')
  } finally {
    saving.value = false
  }
}

onMounted(loadData)
</script>
