<!-- icons-batch-delete-v2 -->
<template>
  <div class="page-container">
    <el-card shadow="never">
      <div class="toolbar">
        <el-input v-model="query.keyword" placeholder="搜索 emoji" clearable style="width: 180px" @input="applyFilter" />
        <el-select v-model="query.category" placeholder="全部分类" clearable style="width: 160px" @change="applyFilter">
          <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
        </el-select>
        <div class="spacer" />
        <el-button type="primary" @click="openAdd">新增</el-button>
        <el-button type="success" plain @click="openBatch">批量导入</el-button>
        <el-button v-if="selected.size" type="danger" @click="handleBatchDelete">批量删除 ({{ selected.size }})</el-button>
      </div>

      <el-alert type="info" :closable="false" show-icon style="margin-bottom: 12px">
        共 {{ total }} 个图标（9 大分类，来源 emoji8.com）。编辑器图标面板自动读取启用图标，按分类分组展示。
      </el-alert>

      <div v-loading="loading" class="icon-grid">
        <div v-for="item in paged" :key="item.id" class="icon-cell" :title="item.name" :class="{ checked: selected.has(item.id) }" @click="toggleSelect(item.id)">
          <el-checkbox :model-value="selected.has(item.id)" size="large" class="icon-check" @click.stop="toggleSelect(item.id)" />
          <span class="icon-emoji">{{ item.name }}</span>
          <span class="icon-cat">{{ item.category }}</span>
          <span class="icon-del" @click.stop="handleDelete(item)">×</span>
        </div>
        <el-empty v-if="!loading && paged.length === 0" description="暂无图标" :image-size="70" />
      </div>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="page"
          :page-size="pageSize"
          :total="filtered.length"
          layout="total, prev, pager, next"
          background
          small
        />
      </div>
    </el-card>

    <!-- 新增单个 -->
    <el-dialog v-model="addDialog" title="新增图标" width="420px">
      <el-form label-width="90px" @submit.prevent>
        <el-form-item label="emoji">
          <el-input v-model="addForm.name" placeholder="输入或粘贴一个 emoji" maxlength="8" />
        </el-form-item>
        <el-form-item label="分类">
          <el-input v-model="addForm.category" placeholder="如：我的收藏" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitAdd">确定</el-button>
      </template>
    </el-dialog>

    <!-- 批量导入 -->
    <el-dialog v-model="batchDialog" title="批量导入 emoji" width="520px">
      <el-form label-width="90px" @submit.prevent>
        <el-form-item label="分类">
          <el-input v-model="batchForm.category" placeholder="如：我的收藏（默认：默认）" />
        </el-form-item>
        <el-form-item label="emoji 内容">
          <el-input v-model="batchForm.emojiText" type="textarea" :rows="6" placeholder="粘贴多个 emoji，可用空格 / 逗号 / 换行分隔" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="batchDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitBatch">导入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getIconList, createIcon, batchImport, deleteIcon, batchDeleteIcons } from '../api/icons'

const list = ref([])
const loading = ref(false)
const page = ref(1)
const pageSize = 96
const query = ref({ keyword: '', category: '' })
const selected = ref(new Set())
const addDialog = ref(false)
const batchDialog = ref(false)
const saving = ref(false)
const addForm = ref({ name: '', category: '' })
const batchForm = ref({ category: '', emojiText: '' })

const categories = computed(() => {
  const set = new Set(list.value.map(i => i.category || '默认'))
  return [...set]
})
const filtered = computed(() => {
  const kw = query.value.keyword.trim()
  const cat = query.value.category
  return list.value.filter(i => {
    if (cat && i.category !== cat) return false
    if (kw && !i.name.includes(kw)) return false
    return true
  })
})
const total = computed(() => filtered.value.length)
const paged = computed(() => filtered.value.slice((page.value - 1) * pageSize, page.value * pageSize))

async function load() {
  loading.value = true
  try { list.value = await getIconList() || [] } catch (e) { ElMessage.error('加载失败') }
  loading.value = false
}
function applyFilter() { page.value = 1 }

function openAdd() { addForm.value = { name: '', category: '' }; addDialog.value = true }
async function submitAdd() {
  if (!addForm.value.name) { ElMessage.warning('请输入 emoji'); return }
  saving.value = true
  try {
    await createIcon({ name: addForm.value.name, category: addForm.value.category, sort: 99, enabled: 1, builtin: 0 })
    ElMessage.success('已添加')
    addDialog.value = false
    load()
  } catch (e) { ElMessage.error(e.message || '添加失败') }
  saving.value = false
}

function openBatch() { batchForm.value = { category: '', emojiText: '' }; batchDialog.value = true }
async function submitBatch() {
  if (!batchForm.value.emojiText) { ElMessage.warning('请粘贴 emoji 内容'); return }
  saving.value = true
  try {
    const n = await batchImport({ category: batchForm.value.category, emojiText: batchForm.value.emojiText })
    ElMessage.success(`成功导入 ${n} 个（重复已跳过）`)
    batchDialog.value = false
    load()
  } catch (e) { ElMessage.error(e.message || '导入失败') }
  saving.value = false
}

async function handleDelete(item) {
  try {
    await ElMessageBox.confirm(`删除 ${item.name} ？`, '提示', { type: 'warning' })
  } catch (e) { return }
  try {
    await deleteIcon(item.id)
    selected.value.delete(item.id)
    ElMessage.success('已删除')
    load()
  } catch (e) { ElMessage.error(e.message || '删除失败') }
}

function toggleSelect(id) {
  const s = new Set(selected.value)
  if (s.has(id)) s.delete(id); else s.add(id)
  selected.value = s
}

async function handleBatchDelete() {
  const ids = [...selected.value]
  await ElMessageBox.confirm(`确定删除选中的 ${ids.length} 个图标吗？`, '批量删除', { type: 'warning' })
  await batchDeleteIcons(ids)
  ElMessage.success('批量删除成功')
  selected.value = new Set()
  load()
}

onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; gap: 10px; margin-bottom: 12px; align-items: center; }
.spacer { flex: 1; }
.icon-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(72px, 1fr)); gap: 8px; min-height: 160px; }
.icon-cell { position: relative; display: flex; flex-direction: column; align-items: center; gap: 4px; padding: 10px 4px 8px; border: 1px solid rgba(0,0,0,.07); border-radius: 10px; background: #fff; transition: box-shadow .2s; cursor: pointer; }
.icon-cell:hover { box-shadow: 0 4px 14px rgba(0,0,0,.1); }
.icon-cell.checked { border-color: #0e7c86; box-shadow: 0 0 0 1px #0e7c86; }
.icon-check { position: absolute; top: 2px; left: 4px; opacity: 0; transition: opacity .15s; }
.icon-cell:hover .icon-check, .icon-cell.checked .icon-check { opacity: 1; }
.icon-emoji { font-size: 26px; line-height: 1.2; }
.icon-cat { font-size: 11px; color: #909399; max-width: 100%; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.icon-del { position: absolute; top: 2px; right: 4px; width: 16px; height: 16px; line-height: 14px; text-align: center; background: rgba(0,0,0,.45); color: #fff; border-radius: 50%; cursor: pointer; font-size: 11px; opacity: 0; transition: opacity .2s; }
.icon-cell:hover .icon-del { opacity: 1; }
.pagination-wrap { margin-top: 12px; display: flex; justify-content: flex-end; }
</style>
