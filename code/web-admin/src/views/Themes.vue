<template>
  <div class="page-container">
    <el-card shadow="never">
      <div class="toolbar">
        <span style="color: #606266">共 {{ list.length }} 个主题模板</span>
        <el-button type="primary" :icon="Plus" @click="openCreate">新增主题</el-button>
        <el-button v-if="selectedIds.length" type="danger" @click="handleBatchDelete">批量删除 ({{ selectedIds.length }})</el-button>
      </div>

      <el-table v-loading="loading" :data="list" border @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="42" :selectable="row => row.builtin !== 1" />
        <el-table-column prop="name" label="名称" width="140">
          <template #default="{ row }">
            <el-tag v-if="row.builtin === 1" size="small" type="info" effect="plain" style="margin-right:6px">内置</el-tag>
            {{ row.name }}
          </template>
        </el-table-column>
        <el-table-column prop="themeKey" label="标识 Key" width="180" show-overflow-tooltip />
        <el-table-column label="分支配色">
          <template #default="{ row }">
            <div class="swatch-row">
              <span v-for="(c, i) in parseColors(row.branchColors)" :key="i" class="swatch" :style="{ background: c }"></span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="sort" label="排序" width="80" />
        <el-table-column label="启用" width="90">
          <template #default="{ row }">
            <el-switch :model-value="row.enabled === 1" :disabled="row._switching" @change="toggle(row)" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openEdit(row)">编辑</el-button>
            <el-button size="small" type="danger" :disabled="row.builtin === 1" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑弹窗（可视化） -->
    <el-dialog v-model="dialog.visible" :title="dialog.mode === 'create' ? '新增主题模板' : '编辑主题模板'" width="820px" top="4vh">
      <el-form label-width="96px" @submit.prevent>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="名称" required>
              <el-input v-model="form.name" placeholder="例如：晨曦" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="标识 Key">
              <el-input v-model="form.themeKey" placeholder="如 usmind-dawn" :disabled="dialog.mode === 'edit'" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="排序">
              <el-input-number v-model="form.sort" :min="1" :max="999" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="启用">
              <el-switch v-model="form.enabled" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="分支配色">
          <div class="color-edit">
            <div class="color-items">
              <div v-for="(c, i) in form.colors" :key="i" class="color-item">
                <el-color-picker :model-value="c" size="small" :predefine="PRESET_COLORS" @update:model-value="v => form.colors[i] = v" />
                <span class="color-hex">{{ c }}</span>
                <span class="color-del" @click="removeColor(i)">×</span>
              </div>
              <button type="button" class="color-add" @click="addColor">+ 添加</button>
            </div>
          </div>
        </el-form-item>

        <el-form-item label="实时预览">
          <div class="theme-preview" :style="{ background: form.bgColor }">
            <div class="preview-bg-img" :style="{ backgroundImage: form.bgImage ? 'url(' + form.bgImage + ')' : 'none', opacity: form.bgOpacity / 100, transform: 'scale(' + form.bgZoom + ')', transformOrigin: 'center center' }"></div>
            <svg :viewBox="previewBox" class="preview-svg">
              <!-- 连线：中心→分支、分支→子项 -->
              <path v-for="(ln, i) in previewLines" :key="'l' + i" :d="ln.d" :stroke="ln.stroke" :stroke-width="ln.width" fill="none" />
              <!-- 中心节点 -->
              <g :transform="'translate(' + previewCenter.x + ',' + previewCenter.y + ')'">
                <rect :x="-previewCenter.w/2" :y="-previewCenter.h/2" :width="previewCenter.w" :height="previewCenter.h" :rx="form.root.borderRadius"
                  :fill="form.root.fillColor" :stroke="form.root.borderColor" :stroke-width="form.root.borderWidth" />
                <text :x="0" :y="previewCenter.h * 0.32" text-anchor="middle" :fill="form.root.color" :font-size="form.root.fontSize" :font-family="form.root.fontFamily || undefined" font-weight="bold">中心主题</text>
              </g>
              <!-- 分支节点 -->
              <g v-for="(b, i) in previewBranches" :key="i">
                <rect :x="b.x - b.w/2" :y="b.y - b.h/2" :width="b.w" :height="b.h" :rx="form.second.borderRadius"
                  :fill="b.fill" :stroke="form.second.borderColor" :stroke-width="form.second.borderWidth" />
                <text :x="b.x" :y="b.y + b.h * 0.32" text-anchor="middle" :fill="form.second.color" :font-size="form.second.fontSize" :font-family="form.second.fontFamily || undefined">{{ b.text }}</text>
              </g>
              <!-- 子节点 -->
              <g v-for="(c, i) in previewChildren" :key="'c' + i">
                <rect :x="c.x - c.w/2" :y="c.y - c.h/2" :width="c.w" :height="c.h" :rx="form.node.borderRadius"
                  :fill="c.fill" :stroke="form.node.borderColor" :stroke-width="form.node.borderWidth" />
                <text :x="c.x" :y="c.y + c.h * 0.32" text-anchor="middle" :fill="form.node.color" :font-size="form.node.fontSize" :font-family="form.node.fontFamily || undefined">{{ c.text }}</text>
              </g>
            </svg>
          </div>
        </el-form-item>

        <el-divider content-position="left">全局样式</el-divider>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="画布背景">
              <div class="inline-color"><el-color-picker v-model="form.bgColor" size="small" :predefine="PRESET_COLORS" /><span>{{ form.bgColor }}</span></div>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="背景图片">
              <div class="bg-upload">
                <el-upload :show-file-list="false" :http-request="uploadBgImage" accept="image/*">
                  <el-button size="small" :loading="uploading">上传图片</el-button>
                </el-upload>
                <div v-if="form.bgImage" class="bg-thumb">
                  <img :src="form.bgImage" />
                  <span class="bg-thumb-x" @click="form.bgImage = ''" title="清除">×</span>
                </div>
                <el-input v-model="form.bgImage" placeholder="或粘贴图片URL" clearable size="small" />
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="图片透明度">
              <el-slider v-model="form.bgOpacity" :min="0" :max="100" :show-input="true" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="背景缩放">
              <el-slider v-model="form.bgZoom" :min="1" :max="2.2" :step="0.05" :show-input="true" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="连线颜色">
              <div class="inline-color"><el-color-picker v-model="form.lineColor" size="small" :predefine="PRESET_COLORS" /><span>{{ form.lineColor }}</span></div>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="连线宽度">
              <el-slider v-model="form.lineWidth" :min="1" :max="8" :show-input="true" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">中心主题（第一层）</el-divider>
        <el-row :gutter="16">
          <el-col :span="8"><el-form-item label="填充色"><el-color-picker v-model="form.root.fillColor" size="small" :predefine="PRESET_COLORS" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="文字颜色"><el-color-picker v-model="form.root.color" size="small" :predefine="PRESET_COLORS" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="边框颜色"><el-color-picker :model-value="toCP(form.root.borderColor)" size="small" :predefine="PRESET_COLORS" @update:model-value="form.root.borderColor = fromCP($event)" /></el-form-item></el-col>
          <el-col :span="6"><el-form-item label="圆角"><el-slider v-model="form.root.borderRadius" :min="0" :max="40" /></el-form-item></el-col>
          <el-col :span="6"><el-form-item label="字号"><el-slider v-model="form.root.fontSize" :min="14" :max="60" /></el-form-item></el-col>
          <el-col :span="6"><el-form-item label="字体"><el-select v-model="form.root.fontFamily" clearable placeholder="默认" style="width:100%"><el-option v-for="f in FONTS" :key="f.value" :label="f.label" :value="f.value" /></el-select></el-form-item></el-col>
          <el-col :span="6"><el-form-item label="边框宽度"><el-slider v-model="form.root.borderWidth" :min="0" :max="6" /></el-form-item></el-col>
          <el-col :span="6"><el-form-item label="内边距"><el-slider v-model="form.root.paddingX" :min="8" :max="80" /></el-form-item></el-col>
        </el-row>

        <el-divider content-position="left">分支主题（第二层）</el-divider>
        <el-row :gutter="16">
          <el-col :span="8"><el-form-item label="填充色"><el-color-picker v-model="form.second.fillColor" size="small" :predefine="PRESET_COLORS" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="文字颜色"><el-color-picker v-model="form.second.color" size="small" :predefine="PRESET_COLORS" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="边框颜色"><el-color-picker :model-value="toCP(form.second.borderColor)" size="small" :predefine="PRESET_COLORS" @update:model-value="form.second.borderColor = fromCP($event)" /></el-form-item></el-col>
          <el-col :span="6"><el-form-item label="圆角"><el-slider v-model="form.second.borderRadius" :min="0" :max="40" /></el-form-item></el-col>
          <el-col :span="6"><el-form-item label="字号"><el-slider v-model="form.second.fontSize" :min="12" :max="40" /></el-form-item></el-col>
          <el-col :span="6"><el-form-item label="字体"><el-select v-model="form.second.fontFamily" clearable placeholder="默认" style="width:100%"><el-option v-for="f in FONTS" :key="f.value" :label="f.label" :value="f.value" /></el-select></el-form-item></el-col>
          <el-col :span="6"><el-form-item label="边框宽度"><el-slider v-model="form.second.borderWidth" :min="0" :max="6" /></el-form-item></el-col>
          <el-col :span="6"><el-form-item label="内边距"><el-slider v-model="form.second.paddingX" :min="6" :max="60" /></el-form-item></el-col>
        </el-row>

        <el-divider content-position="left">其它节点（第三层及以下）</el-divider>
        <el-row :gutter="16">
          <el-col :span="8"><el-form-item label="填充色"><el-color-picker v-model="form.node.fillColor" size="small" :predefine="PRESET_COLORS" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="文字颜色"><el-color-picker v-model="form.node.color" size="small" :predefine="PRESET_COLORS" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="边框颜色"><el-color-picker :model-value="toCP(form.node.borderColor)" size="small" :predefine="PRESET_COLORS" @update:model-value="form.node.borderColor = fromCP($event)" /></el-form-item></el-col>
          <el-col :span="6"><el-form-item label="圆角"><el-slider v-model="form.node.borderRadius" :min="0" :max="40" /></el-form-item></el-col>
          <el-col :span="6"><el-form-item label="字号"><el-slider v-model="form.node.fontSize" :min="10" :max="36" /></el-form-item></el-col>
          <el-col :span="6"><el-form-item label="字体"><el-select v-model="form.node.fontFamily" clearable placeholder="默认" style="width:100%"><el-option v-for="f in FONTS" :key="f.value" :label="f.label" :value="f.value" /></el-select></el-form-item></el-col>
          <el-col :span="6"><el-form-item label="边框宽度"><el-slider v-model="form.node.borderWidth" :min="0" :max="6" /></el-form-item></el-col>
          <el-col :span="6"><el-form-item label="内边距"><el-slider v-model="form.node.paddingX" :min="4" :max="50" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="dialog.loading" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { themeApi } from '../api/themes'
import request from '../api/request'
const uploading = ref(false)
const PRESET_COLORS = [
  '#FF9F43', '#FECB33', '#FF6B6B', '#F08BB4', '#C084FC', '#60A5FA', '#34D399', '#22D3EE',
  '#ffffff', '#f8fafc', '#e2e8f0', '#cbd5e1', '#94a3b8', '#64748b', '#334155', '#1e293b',
  '#f43f5e', '#ef4444', '#f97316', '#eab308', '#22c55e', '#06b6d4', '#3b82f6', '#8b5cf6'
]
const toCP = (v) => (v === 'transparent' || !v) ? '#00000000' : v
const fromCP = (v) => (v === '#00000000' || v === null || v === undefined) ? 'transparent' : v
async function uploadBgImage(opt) {
  uploading.value = true
  try {
    const fd = new FormData()
    fd.append('file', opt.file)
    const res = await request.post('/file/upload', fd)
    if (res && res.url) form.bgImage = res.url
    else ElMessage.error('上传返回异常')
  } catch (e) { ElMessage.error('上传失败: ' + (e.message || '网络错误')) }
  uploading.value = false
}

const list = ref([])
const loading = ref(false)
const selectedIds = ref([])

function parseColors(text) {
  if (!text) return []
  let arr = text
  try { arr = JSON.parse(text) } catch (e) {}
  if (!Array.isArray(arr)) arr = String(text).split(',').map(s => s.trim()).filter(Boolean)
  return arr.slice(0, 12)
}

async function load() {
  loading.value = true
  try { list.value = await themeApi.list() || [] } catch (e) { ElMessage.error('加载失败') }
  loading.value = false
}

// ---------- 可视化表单 ----------
const FONTS = [
  { label: '微软雅黑', value: 'Microsoft YaHei, "PingFang SC", sans-serif' },
  { label: '苹方', value: '"PingFang SC", "Microsoft YaHei", sans-serif' },
  { label: '黑体', value: 'SimHei, "Microsoft YaHei", sans-serif' },
  { label: '宋体', value: 'SimSun, serif' },
  { label: '楷体', value: 'KaiTi, "STKaiti", serif' },
  { label: '仿宋', value: 'FangSong, "STFangsong", serif' },
  { label: '思源黑体', value: '"Noto Sans SC", "Source Han Sans SC", "Microsoft YaHei", sans-serif' },
  { label: '等线', value: 'DengXian, "Microsoft YaHei", sans-serif' }
]
const defaultLevel = () => ({ fillColor: '#ffffff', color: '#334155', borderColor: '#cbd5e1', borderWidth: 1, borderRadius: 16, fontSize: 22, paddingX: 18, fontFamily: '' })
const defaultForm = () => ({
  id: null, name: '', themeKey: '', sort: 99, enabled: 1,
  colors: ['#FF9F43', '#FECB33', '#FF6B6B', '#F08BB4', '#C084FC', '#60A5FA', '#34D399', '#22D3EE'],
  bgColor: '#fafbff', bgImage: '', bgOpacity: 100, bgZoom: 1, lineColor: '#94a3b8', lineWidth: 4,
  root: { ...defaultLevel(), fillColor: '#4A5CF7', color: '#ffffff', fontSize: 36, fontWeight: 'bold', paddingX: 30 },
  second: { ...defaultLevel(), fontSize: 25, paddingX: 26 },
  node: { ...defaultLevel(), fontSize: 22, paddingX: 22 }
})
const dialog = reactive({ visible: false, mode: 'create', loading: false })
const form = reactive(defaultForm())

// 分支配色编辑
function addColor() { if (form.colors.length < 12) form.colors.push('#5B8FF9') }
function removeColor(i) { if (form.colors.length > 2) form.colors.splice(i, 1) }

// 预览计算（贴近实际渲染：字号/圆角/线宽/子项连线跟随表单）
const previewW = 700, previewH = 340
const previewBox = computed(() => `0 0 ${previewW} ${previewH}`)
const textW = (s, f) => s.length * f * 1.02 + 8
const previewCenter = computed(() => {
  const f = form.root.fontSize
  return { x: 150, y: previewH / 2, w: textW('中心主题', f) + form.root.paddingX * 2, h: f * 1.9 }
})
const previewBranches = computed(() => {
  const f1 = form.second.fontSize
  const texts = ['分支1', '分支2', '分支3', '分支4']
  const ys = [80, 155, 230, 305]
  return texts.map((t, i) => ({
    x: 390, y: ys[i], w: textW(t, f1) + form.second.paddingX * 2, h: f1 * 1.9,
    fill: form.colors[i % Math.max(form.colors.length, 1)] || '#ccc',
    text: t
  }))
})
const previewChildren = computed(() => {
  const bs = previewBranches.value
  const f2 = form.node.fontSize
  const out = []
  bs.forEach((b, i) => {
    if (i % 2 === 1) return
    out.push({ x: 590, y: b.y + 45, w: textW('子项', f2) + form.node.paddingX * 2, h: f2 * 1.9, fill: mixColor(b.fill, '#ffffff', 0.55), text: '子项' })
  })
  return out
})
const curvePath = (x0, y0, x1, y1) => {
  const dx = Math.max(28, (x1 - x0) * 0.45)
  return `M ${x0} ${y0} C ${x0 + dx} ${y0}, ${x1 - dx} ${y1}, ${x1} ${y1}`
}
const previewLines = computed(() => {
  const c = previewCenter.value
  const bs = previewBranches.value
  const cs = previewChildren.value
  const lines = []
  bs.forEach(b => {
    lines.push({ d: curvePath(c.x + c.w / 2, c.y, b.x - b.w / 2, b.y), stroke: form.lineColor, width: form.lineWidth })
  })
  bs.forEach((b, i) => {
    if (i % 2 === 1) return
    const ch = cs[i / 2]
    if (ch) lines.push({ d: curvePath(b.x + b.w / 2, b.y, ch.x - ch.w / 2, ch.y), stroke: form.lineColor, width: form.lineWidth })
  })
  return lines
})
function mixColor(a, b, ratio) {
  const hex = (c) => { c = c.replace('#', ''); return c.length === 3 ? c.split('').map(x => x + x).join('') : c }
  const pa = parseInt(hex(a), 16), pb = parseInt(hex(b), 16)
  const ra = (pa >> 16) & 255, ga = (pa >> 8) & 255, ba = pa & 255
  const rb = (pb >> 16) & 255, gb = (pb >> 8) & 255, bb = pb & 255
  const r = Math.round(ra + (rb - ra) * ratio), g = Math.round(ga + (gb - ga) * ratio), bl = Math.round(ba + (bb - ba) * ratio)
  return '#' + ((1 << 24) + (r << 16) + (g << 8) + bl).toString(16).slice(1)
}

// ---------- 打开 ----------
function openCreate() {
  Object.assign(form, defaultForm())
  dialog.mode = 'create'
  dialog.visible = true
}
function openEdit(row) {
  Object.assign(form, defaultForm())
  Object.assign(form, { id: row.id, name: row.name, themeKey: row.themeKey, sort: row.sort, enabled: row.enabled })
  form.colors = parseColors(row.branchColors)
  try {
    const cfg = JSON.parse(row.configJson || '{}')
    form.bgColor = cfg.backgroundColor || '#fafbff'
    form.bgImage = cfg.backgroundImage || ''
    form.bgOpacity = (typeof cfg.backgroundOpacity === 'number') ? Math.round(cfg.backgroundOpacity * 100) : 100
    form.bgZoom = (typeof cfg.backgroundZoom === 'number' && cfg.backgroundZoom >= 1) ? cfg.backgroundZoom : 1
    form.lineColor = cfg.lineColor || '#94a3b8'
    form.lineWidth = cfg.lineWidth || 4
    ;['root', 'second', 'node'].forEach(k => {
      const lv = cfg[k]
      if (lv) Object.keys(form[k]).forEach(f => { if (lv[f] !== undefined) form[k][f] = lv[f] })
      if (lv) form[k].fontFamily = lv.fontFamily || ''
    })
  } catch (e) {}
  dialog.mode = 'edit'
  dialog.visible = true
}

// ---------- 提交 ----------
function buildConfig() {
  const lv = (k) => {
    const v = form[k]
    const style = { shape: 'roundedRectangle', fillColor: v.fillColor, color: v.color, borderColor: v.borderColor, borderWidth: v.borderWidth, borderRadius: v.borderRadius, fontSize: v.fontSize, fontWeight: k === 'root' ? 'bold' : '600', paddingX: v.paddingX, paddingY: Math.round(v.paddingX * 0.6), marginX: k === 'root' ? undefined : 60, marginY: k === 'root' ? undefined : 30 }
    if (v.fontFamily) style.fontFamily = v.fontFamily
    return style
  }
  const cfg = {
    paddingX: 18, paddingY: 8, lineWidth: form.lineWidth, lineColor: form.lineColor,
    lineStyle: 'curve', rootLineKeepSameInCurve: true, rootLineStartPositionKeepSameInCurve: true,
    backgroundColor: form.bgColor,
    backgroundImage: form.bgImage || 'none',
    backgroundOpacity: form.bgOpacity / 100,
    backgroundZoom: form.bgZoom,
    backgroundRepeat: 'no-repeat', backgroundPosition: 'center center', backgroundSize: 'cover',
    root: lv('root'), second: lv('second'), node: lv('node')
  }
  delete cfg.root.marginX; delete cfg.root.marginY
  return JSON.stringify(cfg, null, 2)
}

async function submitForm() {
  if (!form.name) { ElMessage.warning('请输入名称'); return }
  dialog.loading = true
  const payload = {
    name: form.name, themeKey: form.themeKey, sort: form.sort, enabled: form.enabled,
    branchColors: JSON.stringify(form.colors), configJson: buildConfig()
  }
  try {
    if (dialog.mode === 'create') await themeApi.create(payload)
    else await themeApi.update(form.id, payload)
    ElMessage.success('已保存')
    dialog.visible = false
    await load()
  } catch (e) { ElMessage.error('保存失败') }
  dialog.loading = false
}

async function toggle(row) {
  row._switching = true
  try { await themeApi.update(row.id, { enabled: row.enabled === 1 ? 0 : 1 }); row.enabled = row.enabled === 1 ? 0 : 1 } catch (e) { ElMessage.error('操作失败') }
  row._switching = false
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除主题「${row.name}」？`, '提示', { type: 'warning' })
    await themeApi.remove(row.id)
    ElMessage.success('已删除')
    await load()
  } catch (e) { /* 取消 */ }
}

function handleSelectionChange(rows) {
  selectedIds.value = rows.map(r => r.id)
}

async function handleBatchDelete() {
  await ElMessageBox.confirm(`确定删除选中的 ${selectedIds.value.length} 个主题吗？（内置主题已自动排除）`, '批量删除', { type: 'warning' })
  await themeApi.batchRemove(selectedIds.value)
  ElMessage.success('批量删除成功')
  selectedIds.value = []
  await load()
}

onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 14px; }
.swatch-row { display: flex; gap: 4px; flex-wrap: wrap; align-items: center; }
.swatch { width: 22px; height: 14px; border-radius: 3px; border: 1px solid rgba(0,0,0,.08); }

.color-edit { width: 100%; }
.color-items { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }
.color-item { display: flex; align-items: center; gap: 6px; padding: 3px 8px 3px 4px; border: 1px solid #e4e7ed; border-radius: 6px; background: #fff; }
.color-pick { width: 26px; height: 26px; border: none; padding: 0; background: none; cursor: pointer; }
.color-hex { font-size: 12px; color: #606266; font-family: monospace; }
.color-del { color: #909399; cursor: pointer; font-size: 14px; line-height: 1; padding: 0 2px; }
.color-del:hover { color: #f56c6c; }
.color-add { border: 1px dashed #c0c4cc; background: transparent; color: #606266; border-radius: 6px; padding: 6px 12px; cursor: pointer; font-size: 13px; }
.color-add:hover { color: #0e7c86; border-color: #0e7c86; }

.bg-upload { display: flex; flex-direction: column; gap: 6px; width: 100%; }
.bg-thumb { position: relative; width: 100%; height: 56px; border-radius: 8px; overflow: hidden; border: 1px solid rgba(0,0,0,.1); }
.bg-thumb img { width: 100%; height: 100%; object-fit: cover; display: block; }
.bg-thumb-x { position: absolute; top: 2px; right: 4px; width: 18px; height: 18px; line-height: 16px; text-align: center; background: rgba(0,0,0,.55); color: #fff; border-radius: 50%; cursor: pointer; font-size: 12px; }
.theme-preview { position: relative; width: 100%; height: 220px; border-radius: 10px; border: 1px solid rgba(0,0,0,.08); overflow: hidden; }
.preview-bg-img { position: absolute; inset: 0; background-size: cover; background-position: center; background-repeat: no-repeat; }
.preview-svg { position: relative; z-index: 1; }
.preview-svg { width: 100%; height: 100%; }
.inline-color { display: flex; align-items: center; gap: 6px; }
.inline-color input { width: 30px; height: 30px; border: none; padding: 0; background: none; cursor: pointer; }
.inline-color span { font-size: 12px; color: #606266; font-family: monospace; }
</style>
