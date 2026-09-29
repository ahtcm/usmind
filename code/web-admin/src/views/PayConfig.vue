<!-- 支付 / 验证码 / 防护配置 -->
<template>
  <div class="page-container">
    <el-card shadow="never" class="cfg-card">
      <template #header><b>微信支付</b><span class="card-tip">默认关闭；接入商户号并开启后，用户端会员充值即可扫码支付</span></template>
      <el-form label-width="150px" label-position="left">
        <el-form-item label="启用微信支付">
          <el-switch v-model="form.wechatPayEnabled" />
        </el-form-item>
        <el-form-item label="商户号 mchid">
          <el-input v-model="form.wechatMchId" placeholder="微信支付商户号" />
        </el-form-item>
        <el-form-item label="AppID">
          <el-input v-model="form.wechatAppId" placeholder="小程序/公众号 AppID" />
        </el-form-item>
        <el-form-item label="APIv3 密钥">
          <el-input v-model="form.wechatApiKey" placeholder="32位密钥" show-password />
        </el-form-item>
        <el-form-item label="商户私钥">
          <el-input v-model="form.wechatPrivateKey" type="textarea" :rows="4" placeholder="-----BEGIN PRIVATE KEY----- ..." />
        </el-form-item>
        <el-form-item label="回调地址">
          <el-input v-model="form.wechatNotifyUrl" placeholder="http://域名/api/pay/wechat/notify" />
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="cfg-card">
      <template #header><b>注册验证码</b><span class="card-tip">图形验证码默认开启；短信验证码默认关闭，接入服务商后开启</span></template>
      <el-form label-width="150px" label-position="left">
        <el-form-item label="图形验证码">
          <el-switch v-model="form.authCaptchaEnabled" />
        </el-form-item>
        <el-form-item label="短信验证码">
          <el-switch v-model="form.authSmsEnabled" />
        </el-form-item>
        <template v-if="form.authSmsEnabled">
          <el-form-item label="服务商">
            <el-select v-model="form.smsProvider" style="width: 100%">
              <el-option label="阿里云短信" value="aliyun" />
              <el-option label="腾讯云短信" value="tencent" />
            </el-select>
          </el-form-item>
          <el-form-item label="AccessKeyId">
            <el-input v-model="form.smsAccessKeyId" />
          </el-form-item>
          <el-form-item label="AccessKeySecret">
            <el-input v-model="form.smsAccessKeySecret" show-password />
          </el-form-item>
          <el-form-item label="签名">
            <el-input v-model="form.smsSignName" placeholder="短信签名" />
          </el-form-item>
          <el-form-item label="模板编号">
            <el-input v-model="form.smsTemplateCode" placeholder="短信模板ID" />
          </el-form-item>
        </template>
      </el-form>
    </el-card>

    <el-card shadow="never" class="cfg-card">
      <template #header><b>IP 访问防护</b><span class="card-tip">滑动窗口限频：超过阈值自动拉入黑名单（可到「IP 黑白名单」页查看/解除）</span></template>
      <el-form label-width="150px" label-position="left">
        <el-form-item label="启用防护">
          <el-switch v-model="form.ipGuardEnabled" />
        </el-form-item>
        <el-form-item label="每分钟请求上限">
          <el-input-number v-model="form.ipRateLimit" :min="1" :max="10000" />
        </el-form-item>
        <el-form-item label="统计窗口(秒)">
          <el-input-number v-model="form.ipRateWindow" :min="5" :max="3600" />
        </el-form-item>
        <el-form-item label="自动拉黑时长(秒)">
          <el-input-number v-model="form.ipAutoBlackSeconds" :min="60" :max="86400 * 30" />
          <span class="field-hint">拉黑时长内该 IP 直接拒绝访问</span>
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
import { getPayConfig, savePayConfig } from '@/api/members'

const saving = ref(false)
const form = reactive({
  wechatPayEnabled: false,
  wechatMchId: '', wechatAppId: '', wechatApiKey: '', wechatPrivateKey: '', wechatNotifyUrl: '',
  authCaptchaEnabled: true,
  authSmsEnabled: false,
  smsProvider: '', smsAccessKeyId: '', smsAccessKeySecret: '', smsSignName: '', smsTemplateCode: '',
  ipGuardEnabled: true,
  ipRateLimit: 60, ipRateWindow: 60, ipAutoBlackSeconds: 3600
})

onMounted(async () => {
  try {
    const d = await getPayConfig()
    Object.assign(form, {
      wechatPayEnabled: !!d.wechatPayEnabled,
      wechatMchId: d.wechatMchId || '',
      wechatAppId: d.wechatAppId || '',
      wechatApiKey: d.wechatApiKey || '',
      wechatPrivateKey: d.wechatPrivateKey || '',
      wechatNotifyUrl: d.wechatNotifyUrl || '',
      authCaptchaEnabled: !!d.authCaptchaEnabled,
      authSmsEnabled: !!d.authSmsEnabled,
      smsProvider: d.smsProvider || '',
      smsAccessKeyId: d.smsAccessKeyId || '',
      smsAccessKeySecret: d.smsAccessKeySecret || '',
      smsSignName: d.smsSignName || '',
      smsTemplateCode: d.smsTemplateCode || '',
      ipGuardEnabled: !!d.ipGuardEnabled,
      ipRateLimit: Number(d.ipRateLimit || 60),
      ipRateWindow: Number(d.ipRateWindow || 60),
      ipAutoBlackSeconds: Number(d.ipAutoBlackSeconds || 3600)
    })
  } catch (e) { /* 忽略 */ }
})

async function save() {
  saving.value = true
  try {
    await savePayConfig({
      wechatPayEnabled: form.wechatPayEnabled ? 1 : 0,
      wechatMchId: form.wechatMchId,
      wechatAppId: form.wechatAppId,
      wechatApiKey: form.wechatApiKey,
      wechatPrivateKey: form.wechatPrivateKey,
      wechatNotifyUrl: form.wechatNotifyUrl,
      authCaptchaEnabled: form.authCaptchaEnabled ? 1 : 0,
      authSmsEnabled: form.authSmsEnabled ? 1 : 0,
      smsProvider: form.smsProvider,
      smsAccessKeyId: form.smsAccessKeyId,
      smsAccessKeySecret: form.smsAccessKeySecret,
      smsSignName: form.smsSignName,
      smsTemplateCode: form.smsTemplateCode,
      ipGuardEnabled: form.ipGuardEnabled ? 1 : 0,
      ipRateLimit: form.ipRateLimit,
      ipRateWindow: form.ipRateWindow,
      ipAutoBlackSeconds: form.ipAutoBlackSeconds
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
