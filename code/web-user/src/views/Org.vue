<template>
  <div class="orgs-page">
    <div class="orgs-header">
      <h1>我的机构</h1>
      <div class="orgs-actions">
        <input v-model="createName" class="orgs-input" placeholder="机构名称（创建需会员，可后台关闭）" maxlength="50" @keyup.enter="onCreate" />
        <button class="btn" @click="onCreate">创建机构</button>
        <input v-model="joinCode" class="orgs-input" placeholder="输入邀请码加入机构" maxlength="8" @keyup.enter="onJoin" />
        <button class="btn" @click="onJoin">加入机构</button>
        <button class="btn btn-ghost" @click="router.push('/maps')">返回导图</button>
      </div>
    </div>

    <div class="orgs-grid" v-if="orgs.length">
      <div class="org-card" v-for="o in orgs" :key="o.id">
        <div class="org-card-head">
          <span class="org-name">{{ o.name }}</span>
          <span class="org-role" :class="o.role === 'owner' ? 'role-owner' : ''">{{ o.role === 'owner' ? '创建者' : '成员' }}</span>
        </div>
        <div class="org-meta">成员 {{ o.memberCount }} 人 · 创建于 {{ fmt(o.createdAt) }}</div>
        <div class="org-invite">
          <span>邀请码</span>
          <code class="invite-code">{{ o.inviteCode }}</code>
          <button class="link-btn" @click="copyCode(o.inviteCode)">复制</button>
        </div>
        <div class="org-card-actions">
          <button class="btn" @click="openDetail(o)">管理 / 协作</button>
          <button class="btn danger" @click="onExit(o)">{{ o.role === 'owner' ? '解散机构' : '退出机构' }}</button>
        </div>
      </div>
    </div>
    <div class="orgs-empty" v-else>
      还没有加入机构。创建机构后可邀请同事协作编辑脑图；或输入邀请码加入已有机构。
    </div>

    <!-- 机构详情弹窗 -->
    <div v-if="detailOpen" class="org-mask" @click.self="detailOpen = false">
      <div class="org-box">
        <div class="org-box-head">
          <span>{{ curOrg.name }}</span>
          <span class="org-box-sub">邀请码 {{ curOrg.inviteCode }} <button class="link-btn" @click="copyCode(curOrg.inviteCode)">复制</button></span>
          <button class="box-close" @click="detailOpen = false">×</button>
        </div>

        <div class="org-sec-title">协作导图（{{ collabs.length }}）</div>
        <div class="collab-add">
          <select v-model="collabPick" class="orgs-input">
            <option value="" disabled>选择我的导图加入协作</option>
            <option v-for="m in myMaps" :key="m.id" :value="m.id">{{ m.name || '未命名' }}（#{{ m.id }}）</option>
          </select>
          <button class="btn" @click="onAddCollab">加入协作</button>
        </div>
        <div class="collab-list" v-if="collabs.length">
          <div class="collab-item" v-for="c in collabs" :key="c.mindMapId">
            <div class="collab-info">
              <span class="collab-name">{{ c.name }}</span>
              <span class="collab-meta">属主 {{ c.ownerNick }} · 更新 {{ fmt(c.updatedAt) }}</span>
            </div>
            <div class="collab-ops">
              <button class="link-btn" @click="openCollab(c)">进入编辑</button>
              <button v-if="isOwner || c.ownerId === myId" class="link-btn danger" @click="onRemoveCollab(c)">移出协作</button>
            </div>
          </div>
        </div>
        <div v-else class="org-sec-empty">暂无协作导图，选择上方「我的导图」加入协作</div>

        <div class="org-sec-title">成员（{{ curOrg.members ? curOrg.members.length : 0 }}）</div>
        <div class="member-list">
          <div class="member-item" v-for="m in (curOrg.members || [])" :key="m.userId">
            <div class="member-avatar">{{ (m.nickname || '?').slice(0, 1).toUpperCase() }}</div>
            <span class="member-name">{{ m.nickname }}</span>
            <span class="org-role" :class="m.role === 'owner' ? 'role-owner' : ''">{{ m.role === 'owner' ? '创建者' : '成员' }}</span>
            <button v-if="isOwner && m.userId !== myId" class="link-btn danger" @click="onKick(m)">踢出</button>
          </div>
        </div>

        <div class="org-box-foot">
          <button class="btn" @click="detailOpen = false">关闭</button>
          <button v-if="isOwner" class="btn danger" @click="onExit(curOrg)">解散机构</button>
          <button v-else class="btn danger" @click="onExit(curOrg)">退出机构</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { orgApi } from '../api/org'
import { mindmapApi } from '../api/mindmap'
import { toast, showConfirm } from '../utils/modal'
import { useUserStore } from '../stores/user'

const router = useRouter()
const userStore = useUserStore()
const orgs = ref([])
const createName = ref('')
const joinCode = ref('')
const detailOpen = ref(false)
const curOrg = ref({})
const collabs = ref([])
const collabPick = ref('')
const myMaps = ref([])
const myId = computed(() => (userStore.user && userStore.user.id) || 0)
const isOwner = computed(() => curOrg.value.ownerId === myId.value)

onMounted(load)

async function load() {
  try { orgs.value = await orgApi.my() } catch (e) { toast('加载机构失败: ' + e.message, 'error') }
}

async function onCreate() {
  if (!createName.value.trim()) { toast('请输入机构名称', 'error'); return }
  try {
    await orgApi.create(createName.value.trim())
    createName.value = ''
    toast('机构创建成功，邀请码已生成')
    load()
  } catch (e) { toast((e && e.message) || '创建失败', 'error') }
}

async function onJoin() {
  if (!joinCode.value.trim()) { toast('请输入邀请码', 'error'); return }
  try {
    const r = await orgApi.join(joinCode.value.trim())
    joinCode.value = ''
    toast('已加入机构「' + (r.name || '') + '」')
    load()
  } catch (e) { toast((e && e.message) || '加入失败', 'error') }
}

function copyCode(code) {
  try { navigator.clipboard.writeText(code); toast('邀请码已复制') } catch (e) { toast('复制失败', 'error') }
}

async function openDetail(o) {
  try {
    const d = await orgApi.detail(o.id)
    curOrg.value = d
    detailOpen.value = true
    collabPick.value = ''
    await Promise.all([loadCollabs(), loadMyMaps()])
  } catch (e) { toast('加载详情失败: ' + e.message, 'error') }
}

async function loadCollabs() {
  try { collabs.value = await orgApi.collabList(curOrg.value.id) } catch (e) { collabs.value = [] }
}

async function loadMyMaps() {
  try {
    const data = await mindmapApi.list({ page: 1, size: 200 })
    myMaps.value = (data.list || []).filter(m => !collabs.value.some(c => c.mindMapId === m.id))
  } catch (e) { myMaps.value = [] }
}

async function onAddCollab() {
  if (!collabPick.value) { toast('请选择要协作的导图', 'error'); return }
  try {
    await orgApi.addCollab(curOrg.value.id, Number(collabPick.value))
    toast('已加入协作，机构成员均可编辑')
    collabPick.value = ''
    await Promise.all([loadCollabs(), loadMyMaps()])
  } catch (e) { toast((e && e.message) || '操作失败', 'error') }
}

async function onRemoveCollab(c) {
  if (!(await showConfirm({ title: '移出协作', message: '将「' + (c.name || '未命名') + '」移出协作？成员将无法再编辑。' }))) return
  try {
    await orgApi.removeCollab(curOrg.value.id, c.mindMapId)
    toast('已移出协作')
    await Promise.all([loadCollabs(), loadMyMaps()])
  } catch (e) { toast((e && e.message) || '操作失败', 'error') }
}

function openCollab(c) {
  detailOpen.value = false
  router.push('/editor/' + c.mindMapId)
}

async function onKick(m) {
  if (!(await showConfirm({ title: '踢出成员', message: '将「' + m.nickname + '」踢出机构？' }))) return
  try {
    await orgApi.kick(curOrg.value.id, m.userId)
    toast('已踢出')
    openDetail(curOrg.value)
  } catch (e) { toast((e && e.message) || '操作失败', 'error') }
}

async function onExit(o) {
  const isOwner = o.role === 'owner' || curOrg.value.ownerId === myId.value
  if (!(await showConfirm({ title: isOwner ? '解散机构' : '退出机构', message: isOwner ? '解散「' + o.name + '」？将删除机构、成员与协作关系。' : '退出机构「' + o.name + '」？' }))) return
  try {
    await orgApi.exit(o.id)
    detailOpen.value = false
    toast(isOwner ? '机构已解散' : '已退出机构')
    load()
  } catch (e) { toast((e && e.message) || '操作失败', 'error') }
}

function fmt(t) {
  if (!t) return ''
  const d = new Date(t)
  return (d.getMonth() + 1) + '/' + d.getDate() + ' ' + d.getHours() + ':' + String(d.getMinutes()).padStart(2, '0')
}
</script>

<style scoped>
.orgs-page { min-height: 100vh; padding: 26px 32px; }
.orgs-header { display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; margin-bottom: 22px; }
.orgs-header h1 { font-size: 22px; }
.orgs-actions { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.orgs-input { background: rgba(255,255,255,.07); border: 1px solid rgba(255,255,255,.13); color: var(--text); border-radius: 9px; padding: 8px 12px; font-size: 13px; outline: none; width: 210px; }
.orgs-input:focus { border-color: var(--accent); }
.btn { background: var(--accent); color: #fff; border: 0; border-radius: 9px; padding: 8px 14px; font-size: 13px; cursor: pointer; }
.btn-ghost { background: rgba(255,255,255,.08); color: var(--text); }
.btn.danger { background: #e5484d; }
.btn:hover { opacity: .9; }
.link-btn { background: none; border: 0; color: var(--accent); cursor: pointer; font-size: 12px; padding: 2px 4px; }
.link-btn.danger { color: #e5484d; }
.orgs-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(320px, 1fr)); gap: 16px; }
.org-card { background: rgba(255,255,255,.05); border: 1px solid rgba(255,255,255,.09); border-radius: 14px; padding: 16px; }
.org-card-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 6px; }
.org-name { font-size: 16px; font-weight: 700; }
.org-role { font-size: 11px; color: var(--text-dim); background: rgba(255,255,255,.09); border-radius: 6px; padding: 2px 8px; }
.org-role.role-owner { color: #f5c96b; background: rgba(245,201,107,.14); }
.org-meta { font-size: 12px; color: var(--text-dim); margin-bottom: 10px; }
.org-invite { display: flex; align-items: center; gap: 8px; font-size: 12px; color: var(--text-dim); margin-bottom: 12px; }
.invite-code { font-family: Consolas, monospace; font-size: 15px; letter-spacing: 1px; color: #f5c96b; }
.org-card-actions { display: flex; gap: 8px; }
.orgs-empty { color: var(--text-dim); padding: 60px 0; text-align: center; }
.org-mask { position: fixed; inset: 0; background: rgba(0,0,0,.55); display: flex; align-items: center; justify-content: center; z-index: 400; }
.org-box { width: 560px; max-width: 94vw; max-height: 84vh; overflow-y: auto; background: #1b1e25; border: 1px solid rgba(255,255,255,.12); border-radius: 14px; padding: 18px; }
.org-box-head { display: flex; align-items: center; gap: 10px; margin-bottom: 14px; font-size: 16px; font-weight: 700; }
.org-box-sub { font-size: 12px; color: var(--text-dim); font-weight: 400; }
.box-close { margin-left: auto; background: none; border: 0; color: var(--text-dim); font-size: 20px; cursor: pointer; }
.org-sec-title { font-size: 13px; color: var(--text-dim); margin: 12px 0 8px; }
.org-sec-empty { color: var(--text-dim); font-size: 12px; padding: 8px 0; }
.collab-add { display: flex; gap: 8px; margin-bottom: 8px; }
.collab-add .orgs-input { width: 100%; flex: 1; }
.collab-list { display: flex; flex-direction: column; gap: 6px; }
.collab-item { display: flex; align-items: center; justify-content: space-between; background: rgba(255,255,255,.05); border-radius: 9px; padding: 8px 10px; }
.collab-name { font-size: 13px; }
.collab-meta { font-size: 11px; color: var(--text-dim); margin-left: 8px; }
.collab-ops { display: flex; gap: 6px; }
.member-list { display: flex; flex-direction: column; gap: 6px; }
.member-item { display: flex; align-items: center; gap: 10px; background: rgba(255,255,255,.05); border-radius: 9px; padding: 8px 10px; }
.member-avatar { width: 26px; height: 26px; border-radius: 50%; background: linear-gradient(135deg, #4f6ef7, #6a5cf6); display: flex; align-items: center; justify-content: center; font-size: 12px; color: #fff; flex-shrink: 0; }
.member-name { font-size: 13px; flex: 1; }
.org-box-foot { display: flex; justify-content: flex-end; gap: 8px; margin-top: 16px; }
</style>
