<template>
  <div class="page-container">
    <!-- 上传类型与预览类型配置 -->
    <el-card shadow="never">
      <template #header>
        <div class="card-head">
          <span>上传与预览配置</span>
          <el-tag type="info" size="small">保存到数据库 sys_config，前端与上传接口实时生效</el-tag>
        </div>
      </template>
      <el-form label-width="160px">
        <el-form-item label="上传类型白名单">
          <el-input
            v-model="form.allowedFileTypes"
            type="textarea"
            :rows="4"
            placeholder="逗号分隔的后缀，如: jpg,png,js,py,tsx"
          />
          <div class="tip">允许上传的文件后缀（不含点）。填写 * 表示全部允许。前端粘贴附件、管理后台上传资源均按此校验。</div>
        </el-form-item>
        <el-form-item label="代码/文本预览高亮">
          <el-input
            v-model="form.previewModes"
            type="textarea"
            :rows="7"
            placeholder='JSON：扩展名 -> CodeMirror mode，如 {"js":"text/javascript"}'
          />
          <div class="tip">这类文件可在附件预览中用代码高亮浏览/编辑。JSON 格式：扩展名（不含点）为键，CodeMirror 语言 mode 为值。</div>
        </el-form-item>
        <el-form-item label="纯文本预览类型">
          <el-input
            v-model="form.textPreviewExts"
            type="textarea"
            :rows="3"
            placeholder="逗号分隔，如: txt,log,ini,conf,csv"
          />
          <div class="tip">这类文件可在附件预览中按纯文本查看（无高亮）。</div>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="saving" @click="saveUploadCfg">保存上传与预览配置</el-button>
          <el-button @click="resetForm">恢复默认</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 部署配置文件（Java / Nginx） -->
    <el-card shadow="never" style="margin-top: 16px">
      <template #header>
        <div class="card-head">
          <span>部署配置文件（可视化编辑 + 导出迁移）</span>
          <div>
            <el-button size="small" :loading="cfgSaving" @click="saveFiles">保存修改（自动备份原文件）</el-button>
            <el-button size="small" @click="exportCfg('java')">导出 Java 配置</el-button>
            <el-button size="small" @click="exportCfg('nginx')">导出 Nginx 配置</el-button>
          </div>
        </div>
      </template>
      <el-alert type="warning" :closable="false" show-icon style="margin-bottom: 12px">
        修改 Java 配置需重启后端服务生效；修改 Nginx 配置需执行 nginx -s reload 生效。保存时自动备份带时间戳的旧文件。
      </el-alert>

      <el-tabs v-model="cfgTab">
        <el-tab-pane label="Java 后端配置 (application-dev.yml)" name="java">
          <div class="cfg-meta">
            <span class="cfg-label">{{ javaItem.label }}</span>
            <el-tag size="small" type="info">{{ javaItem.path }}</el-tag>
          </div>
          <div class="cfg-tip">{{ javaItem.tip }}</div>
          <el-input
            v-model="javaContent"
            type="textarea"
            :rows="22"
            spellcheck="false"
            style="font-family: Consolas, 'Courier New', monospace; font-size: 12px"
          />
        </el-tab-pane>
        <el-tab-pane label="Nginx 配置 (nginx.conf)" name="nginx">
          <div class="cfg-meta">
            <span class="cfg-label">{{ nginxItem.label }}</span>
            <el-tag size="small" type="info">{{ nginxItem.path }}</el-tag>
          </div>
          <div class="cfg-tip">{{ nginxItem.tip }}</div>
          <el-input
            v-model="nginxContent"
            type="textarea"
            :rows="22"
            spellcheck="false"
            style="font-family: Consolas, 'Courier New', monospace; font-size: 12px"
          />
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getSettings, saveSettings } from '@/api/settings'
import { getProjectConfig, saveProjectConfig } from '@/api/projectConfig'

const saving = ref(false)
const cfgSaving = ref(false)
const cfgTab = ref('java')
const javaItem = ref({ label: 'Java 后端配置', tip: '', path: '' })
const nginxItem = ref({ label: 'Nginx 配置', tip: '', path: '' })
const javaContent = ref('')
const nginxContent = ref('')

const DEFAULT_TYPES = 'jpg,jpeg,png,gif,webp,bmp,ico,avif,svg,pdf,doc,docx,xls,xlsx,ppt,pptx,txt,md,zip,rar,7z,mp3,wav,ogg,m4a,aac,flac,mp4,webm,ogv,mov,mkv,json,js,mjs,cjs,ts,tsx,jsx,vue,html,htm,css,scss,less,xml,yaml,yml,py,python,java,kt,c,h,cpp,cc,cxx,hpp,hh,cs,go,rs,php,sh,bash,zsh,sql,rb,ini,conf,cfg,env,properties,tsv,gitignore,dockerfile,sass,plist'
const DEFAULT_MODES = JSON.stringify({
  js:'text/javascript', mjs:'text/javascript', cjs:'text/javascript', ts:'text/javascript', jsx:'text/javascript', tsx:'text/javascript',
  json:'application/json', map:'application/json', html:'text/html', htm:'text/html', vue:'text/html', xml:'application/xml', svg:'application/xml',
  css:'text/css', scss:'text/x-scss', less:'text/x-less', md:'text/x-markdown', markdown:'text/x-markdown', py:'text/x-python', python:'text/x-python',
  java:'text/x-java', kt:'text/x-kotlin', c:'text/x-csrc', h:'text/x-csrc', cpp:'text/x-c++src', cc:'text/x-c++src', cxx:'text/x-c++src', hpp:'text/x-c++src', hh:'text/x-c++src',
  cs:'text/x-csharp', go:'text/x-go', rs:'text/x-rust', php:'text/x-php', sh:'text/x-sh', bash:'text/x-sh', zsh:'text/x-sh',
  sql:'text/x-sql', rb:'text/x-ruby', yml:'text/x-yaml', yaml:'text/x-yaml', swift:'text/x-swift'
}, null, 2)
const DEFAULT_TEXT = 'txt,log,ini,conf,cfg,csv,env,properties,tsv,gitignore,dockerfile,sass,plist'

const form = reactive({
  allowedFileTypes: DEFAULT_TYPES,
  previewModes: DEFAULT_MODES,
  textPreviewExts: DEFAULT_TEXT
})

async function loadData() {
  try {
    const data = await getSettings()
    if (data.allowedFileTypes != null) form.allowedFileTypes = data.allowedFileTypes
    if (data.previewModes) {
      try { form.previewModes = JSON.stringify(JSON.parse(data.previewModes), null, 2) } catch (e) { form.previewModes = data.previewModes }
    }
    if (data.textPreviewExts) {
      try { form.textPreviewExts = JSON.parse(data.textPreviewExts).join(',') } catch (e) { form.textPreviewExts = data.textPreviewExts }
    }
  } catch (e) { /* 保留默认 */ }
  try {
    const cfg = await getProjectConfig()
    javaItem.value = cfg.java
    nginxItem.value = cfg.nginx
    javaContent.value = cfg.java && cfg.java.content || ''
    nginxContent.value = cfg.nginx && cfg.nginx.content || ''
  } catch (e) { /* 文件读取失败则留空 */ }
}

function resetForm() {
  form.allowedFileTypes = DEFAULT_TYPES
  form.previewModes = DEFAULT_MODES
  form.textPreviewExts = DEFAULT_TEXT
}

async function saveUploadCfg() {
  saving.value = true
  try {
    let modes = form.previewModes
    try { modes = JSON.stringify(JSON.parse(form.previewModes)) } catch (e) { ElMessage.warning('预览高亮配置不是合法 JSON，已按原文本保存'); }
    const extText = form.textPreviewExts.split(',').map(s => s.trim()).filter(Boolean)
    await saveSettings({
      allowedFileTypes: form.allowedFileTypes,
      previewModes: modes,
      textPreviewExts: JSON.stringify(extText)
    })
    ElMessage.success('上传与预览配置已保存')
  } finally {
    saving.value = false
  }
}

async function saveFiles() {
  cfgSaving.value = true
  try {
    await saveProjectConfig({
      java: javaContent.value,
      nginx: nginxContent.value
    })
    ElMessage.success('配置文件已保存（原文件已自动备份）')
  } finally {
    cfgSaving.value = false
  }
}

function exportCfg(kind) {
  const map = {
    java: { name: 'application-dev.yml', content: javaContent.value },
    nginx: { name: 'nginx.conf', content: nginxContent.value }
  }
  const item = map[kind]
  const blob = new Blob([item.content], { type: 'text/plain;charset=utf-8' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = item.name
  a.click()
  URL.revokeObjectURL(a.href)
}

onMounted(loadData)
</script>

<style scoped>
.card-head { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.tip { font-size: 12px; color: #909399; line-height: 1.6; margin-top: 4px; }
.cfg-meta { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; flex-wrap: wrap; }
.cfg-label { font-weight: 600; color: #333; }
.cfg-tip { font-size: 12px; color: #909399; margin-bottom: 10px; }
</style>
