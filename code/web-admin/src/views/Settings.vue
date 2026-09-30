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
              <div class="opt-tip">作用于新建导图：开启后节点仅节点创建者和导图创建者可编辑；历史节点归导图创建者所有</div>
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

        <el-card shadow="never" style="margin-top: 16px">
          <template #header><span>请求日志</span></template>
          <el-form label-width="140px">
            <el-form-item label="记录哪些日志">
              <el-checkbox-group v-model="levelsArr">
                <el-checkbox value="info">正常</el-checkbox>
                <el-checkbox value="warn">警告</el-checkbox>
                <el-checkbox value="error">错误</el-checkbox>
              </el-checkbox-group>
              <div class="opt-tip">多选生效：后端请求日志与前端上报日志都按此过滤；不勾选任何级别则完全不记录</div>
            </el-form-item>
            <el-form-item label="保留天数">
              <el-input-number v-model="settings.logRetainDays" :min="1" :max="3650" />
              <div class="opt-tip">超过该天数的日志会被定时任务自动删除（如 90 = 保留近 90 天）</div>
            </el-form-item>
            <el-form-item label="清理时间">
              <el-time-picker v-model="settings.logCleanTime" value-format="HH:mm" format="HH:mm" placeholder="每天执行删除的时间" style="width: 140px" />
              <div class="opt-tip">每天到点自动删除超过保留天数的日志（默认 03:00）</div>
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
const levelsArr = ref(['info', 'warn', 'error'])
const settings = reactive({
  jwtExpire: '7d',
  jwtIssuer: 'usmind',
  maxUploadSize: 50,
  allowTypes: 'jpg,jpeg,png,gif,pdf,doc,docx,xlsx,pptx,mp4',
  ownerLockDefault: '0',
  aiApiKey: '',
  aiModel: '',
  aiEndpoint: '',
  logRecordLevels: 'info,warn,error',
  logRetainDays: 90,
  logCleanTime: '03:00'
})

async function loadData() {
  try {
    const data = await getSettings()
    Object.assign(settings, data)
    levelsArr.value = (data.logRecordLevels || 'info,warn,error').split(',').filter(Boolean)
  } catch (e) {
    // 接口未就绪时保留默认值
  }
}

async function handleSave() {
  saving.value = true
  try {
    settings.logRecordLevels = levelsArr.value.join(',')
    await saveSettings({ ...settings })
    ElMessage.success('设置已保存')
  } finally {
    saving.value = false
  }
}

onMounted(loadData)
</script>

<style scoped>
.opt-tip {
  font-size: 12px;
  color: #909399;
  line-height: 1.5;
  margin-top: 4px;
}
</style>
