# -*- coding: utf-8 -*-
import io

def patch(path, pairs):
    with io.open(path, 'r', encoding='utf-8') as f:
        s = f.read()
    for old, new, tag in pairs:
        assert s.count(old) == 1, tag + ' count=' + str(s.count(old))
        s = s.replace(old, new)
    with io.open(path, 'w', encoding='utf-8', newline='') as f:
        f.write(s)
    print('OK', path.split('\\')[-1])

# 1. api/mindmaps.js 加协作配置接口
patch(r'C:\Users\25750\Desktop\usmind\code\web-admin\src\api\mindmaps.js', [
    ('''export function cancelShareMindmap(id) {
  return request.delete(`/mindmaps/${id}/share`)
}''',
     '''export function cancelShareMindmap(id) {
  return request.delete(`/mindmaps/${id}/share`)
}

export function getCollabConfig(id) {
  return request.get(`/mindmaps/${id}/collab-config`)
}

export function updateCollabConfig(id, cfg) {
  return request.put(`/mindmaps/${id}/collab-config`, cfg)
}''',
     'api-collab'),
])

# 2. Settings.vue：加节点所有权全局开关
patch(r'C:\Users\25750\Desktop\usmind\code\web-admin\src\views\Settings.vue', [
    ('''        <el-card shadow="never" style="margin-top: 16px">
          <template #header><span>文件上传限制</span></template>''',
     '''        <el-card shadow="never" style="margin-top: 16px">
          <template #header><span>节点所有权（机构协作）</span></template>
          <el-form label-width="140px">
            <el-form-item label="默认开启节点所有权">
              <el-switch v-model="settings.ownerLockDefault" active-value="1" inactive-value="0" />
              <div class="opt-tip">作用于新建导图：开启后非节点创建者编辑需申请审批；历史节点归导图创建者所有</div>
            </el-form-item>
          </el-form>
        </el-card>

        <el-card shadow="never" style="margin-top: 16px">
          <template #header><span>文件上传限制</span></template>''',
     'settings-ownerlock'),
    ('''  allowTypes: 'jpg,jpeg,png,gif,pdf,doc,docx,xlsx,pptx,mp4',
  aiApiKey: '',''',
     '''  allowTypes: 'jpg,jpeg,png,gif,pdf,doc,docx,xlsx,pptx,mp4',
  ownerLockDefault: '0',
  aiApiKey: '',''',
     'settings-model'),
])

# 3. MindMaps.vue：操作列加协作设置 + 弹窗
patch(r'C:\Users\25750\Desktop\usmind\code\web-admin\src\views\MindMaps.vue', [
    ('''        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openDetail(row)">详情</el-button>''',
     '''        <el-table-column label="操作" width="300" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openDetail(row)">详情</el-button>
            <el-button size="small" type="primary" @click="openCollab(row)">协作设置</el-button>''',
     'mm-action'),
    ('''    </el-dialog>
  </div>
</template>''',
     '''    </el-dialog>

    <!-- 协作设置弹窗 -->
    <el-dialog v-model="collab.visible" title="协作设置" width="520px">
      <el-form label-width="150px" v-if="collab.data">
        <el-form-item label="节点所有权">
          <el-switch v-model="collab.data.enableOwnerLock" />
          <div class="opt-tip">开启后非节点创建者编辑需申请审批；历史节点归导图创建者所有</div>
        </el-form-item>
        <el-form-item label="审批人超时转派(秒)">
          <el-input-number v-model="collab.data.approveTimeout1" :min="10" :max="3600" />
          <div class="opt-tip">节点创建者 2 分钟未审批则转派导图创建者（默认 120 秒）</div>
        </el-form-item>
        <el-form-item label="超时自动拒绝(秒)">
          <el-input-number v-model="collab.data.approveTimeout2" :min="10" :max="3600" />
          <div class="opt-tip">超过该时长未审批自动拒绝（默认 180 秒）</div>
        </el-form-item>
        <el-form-item label="授权编辑时长(分钟)">
          <el-input-number v-model="collab.data.grantMinutes" :min="1" :max="600" />
          <div class="opt-tip">审批通过后申请人可编辑该节点的时长</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="collab.visible = false">取消</el-button>
        <el-button type="primary" :loading="collab.saving" @click="saveCollab">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>''',
     'mm-dialog'),
    ('''import { getMindmapList, getMindmapDetail, deleteMindmap, cancelShareMindmap, batchDeleteMindmaps } from '@/api/mindmaps' ''',
     '''import { getMindmapList, getMindmapDetail, deleteMindmap, cancelShareMindmap, batchDeleteMindmaps, getCollabConfig, updateCollabConfig } from '@/api/mindmaps' ''',
     'mm-import'),
    ('''const detail = reactive({ visible: false, data: null, json: '' })''',
     '''const detail = reactive({ visible: false, data: null, json: '' })
const collab = reactive({ visible: false, data: null, saving: false })''',
     'mm-state'),
    ('''async function handleBatchDelete() {''',
     '''async function openCollab(row) {
  collab.visible = true
  collab.data = { enableOwnerLock: false, approveTimeout1: 120, approveTimeout2: 180, grantMinutes: 10, id: row.id, name: row.name }
  try {
    const cfg = await getCollabConfig(row.id)
    if (cfg && typeof cfg === 'object') {
      collab.data.enableOwnerLock = !!cfg.enableOwnerLock
      if (cfg.approveTimeout1) collab.data.approveTimeout1 = cfg.approveTimeout1
      if (cfg.approveTimeout2) collab.data.approveTimeout2 = cfg.approveTimeout2
      if (cfg.grantMinutes) collab.data.grantMinutes = cfg.grantMinutes
    }
  } catch (e) {}
}

async function saveCollab() {
  const d = collab.data
  if (!d) return
  collab.saving = true
  try {
    await updateCollabConfig(d.id, {
      enableOwnerLock: d.enableOwnerLock,
      approveTimeout1: d.approveTimeout1,
      approveTimeout2: d.approveTimeout2,
      grantMinutes: d.grantMinutes
    })
    ElMessage.success('协作设置已保存')
    collab.visible = false
  } finally {
    collab.saving = false
  }
}

async function handleBatchDelete() {''',
     'mm-funcs'),
])

# 4. MindMaps.vue 加 opt-tip 样式
patch(r'C:\Users\25750\Desktop\usmind\code\web-admin\src\views\MindMaps.vue', [
    ('''.json-box :deep(.el-textarea__inner) {
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
}''',
     '''.json-box :deep(.el-textarea__inner) {
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
}
.opt-tip {
  font-size: 12px;
  color: #909399;
  line-height: 1.5;
  margin-top: 4px;
}''',
     'mm-style'),
])

# 5. Settings.vue 加 opt-tip 样式
patch(r'C:\Users\25750\Desktop\usmind\code\web-admin\src\views\Settings.vue', [
    ('''onMounted(loadData)
</script>''',
     '''.opt-tip {
  font-size: 12px;
  color: #909399;
  line-height: 1.5;
  margin-top: 4px;
}

onMounted(loadData)
</script>''',
     'settings-style'),
])
print('ALL DONE')
