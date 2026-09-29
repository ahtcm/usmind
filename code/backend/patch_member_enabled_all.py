# -*- coding: utf-8 -*-
# 会员总开关 memberEnabled：后端键 + 下发 + 前端隐藏会员中心/不拦截 + 后台多选框
import io, sys

def patch(path, pairs, base):
    p = base + path
    s = io.open(p, encoding='utf-8').read()
    for old, new in pairs:
        if old not in s:
            print('NOT FOUND in', path, '::', old[:80]); sys.exit(1)
        s = s.replace(old, new, 1)
    io.open(p, 'w', encoding='utf-8', newline='\n').write(s)
    print('OK', path)

# ===== 后端 =====
B = r'C:\Users\25750\Desktop\usmind\code\backend\src\main\java\com\usmind'
patch(r'\service\impl\SettingsServiceImpl.java', [
("""        m.put("attachTextColor", get("attachTextColor", "#e53935"));
        m.put("wechatLoginEnabled", getInt("wechatLoginEnabled", 0));""",
"""        m.put("attachTextColor", get("attachTextColor", "#e53935"));
        m.put("memberEnabled", getInt("memberEnabled", 1));
        m.put("wechatLoginEnabled", getInt("wechatLoginEnabled", 0));"""),
("""            "wechatPayEnabled", "authCaptchaEnabled", "authSmsEnabled",
            "wechatLoginEnabled", "wechatLoginAppId", "wechatLoginSecret", "wechatLoginRedirectUri",""",
"""            "wechatPayEnabled", "authCaptchaEnabled", "authSmsEnabled",
            "memberEnabled",
            "wechatLoginEnabled", "wechatLoginAppId", "wechatLoginSecret", "wechatLoginRedirectUri",""")
], B)
patch(r'\controller\ClientConfigController.java', [
("""        m.put("wechatLoginEnabled", settingsService.getInt("wechatLoginEnabled", 0) == 1);""",
"""        m.put("wechatLoginEnabled", settingsService.getInt("wechatLoginEnabled", 0) == 1);
        m.put("memberEnabled", settingsService.getInt("memberEnabled", 1) == 1);""")
], B)

# ===== web-user =====
U = r'C:\Users\25750\Desktop\usmind\code\web-user\src'
patch(r'\views\Editor.vue', [
("""let memberFeature = { memberThemeIds: '', memberExportFormats: '', memberLevel: 0 }
function checkMemberFeature(kind, value) {
  const cfg = memberFeature
  if (kind === 'theme') {""",
"""let memberFeature = { memberThemeIds: '', memberExportFormats: '', memberLevel: 0, enabled: true }
function checkMemberFeature(kind, value) {
  const cfg = memberFeature
  if (!cfg.enabled) return true // 会员功能总开关关闭：全部开放
  if (kind === 'theme') {"""),
("""    const c = await configApi.client()
    const f = c.feature || {}
    memberFeature.memberThemeIds = f.memberThemeIds || ''
    memberFeature.memberExportFormats = f.memberExportFormats || ''""",
"""    const c = await configApi.client()
    const f = c.feature || {}
    memberFeature.enabled = c.memberEnabled !== false
    memberFeature.memberThemeIds = f.memberThemeIds || ''
    memberFeature.memberExportFormats = f.memberExportFormats || ''""")
], U)
patch(r'\views\MindMapList.vue', [
("""import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { mindmapApi } from '../api/mindmap'
import { toast, showConfirm, showPrompt } from '../utils/modal'
import { useUserStore } from '../stores/user'

const router = useRouter()
const userStore = useUserStore()
const list = ref([])
const menuOpen = ref(false)

onMounted(loadList)""",
"""import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { mindmapApi } from '../api/mindmap'
import { configApi } from '../api/member'
import { toast, showConfirm, showPrompt } from '../utils/modal'
import { useUserStore } from '../stores/user'

const router = useRouter()
const userStore = useUserStore()
const list = ref([])
const menuOpen = ref(false)
const memberEnabled = ref(true)

onMounted(() => { loadList(); loadMemberEnabled() })

async function loadMemberEnabled() {
  try {
    const c = await configApi.client()
    memberEnabled.value = c.memberEnabled !== false
  } catch (e) { /* 忽略 */ }
}"""),
("""            <div class="drop-item" @click="goMember">
              <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 2l3 6 6 .8-4.5 4.3 1 6.4-5.5-3-5.5 3 1-6.4L3 8.8 9 8z"/></svg>
              会员中心
            </div>""",
"""            <div class="drop-item" @click="goMember" v-if="memberEnabled">
              <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 2l3 6 6 .8-4.5 4.3 1 6.4-5.5-3-5.5 3 1-6.4L3 8.8 9 8z"/></svg>
              会员中心
            </div>""")
], U)

io.open(r'C:\Users\25750\Desktop\usmind\code\backend\patch_member_enabled.py', 'w', encoding='utf-8', newline='\n').write('# memberEnabled 总开关补丁（见 patch_member_enabled_all.py）')
print('DONE')
