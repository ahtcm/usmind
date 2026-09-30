# -*- coding: utf-8 -*-
import io
p = r'C:\Users\25750\Desktop\usmind\code\web-user\src\views\ShareView.vue'
s = io.open(p, encoding='utf-8').read()

# 1) WS 状态定义（cmtList 定义后）
old_state = '''const cmtList = ref([])'''
new_state = '''const cmtList = ref([])
let shareMindMapId = null   // 当前分享的导图 id（评论 WS 订阅用）
let cmtWs = null            // 评论实时推送通道
let cmtWsTimer = null       // 重连定时器'''
assert s.count(old_state) == 1, 'state=' + str(s.count(old_state))
s = s.replace(old_state, new_state)

# 2) loadShare 密码通过后连接评论 WS（needPwd.value = false 后）
old_load = '''    needPwd.value = false // 密码通过：先切到导图视图，让 map 容器渲染'''
new_load = '''    needPwd.value = false // 密码通过：先切到导图视图，让 map 容器渲染
    shareMindMapId = data.id || null
    connectShareCmtWs()'''
assert s.count(old_load) == 1, 'load=' + str(s.count(old_load))
s = s.replace(old_load, new_load)

# 3) 评论 WS 函数：插在 refreshCmt 函数前
anchor = '''async function refreshCmt() {'''
add_fn = '''function connectShareCmtWs() {
  if (cmtWs) { try { cmtWs.close() } catch (e) {} cmtWs = null }
  if (!shareMindMapId) return
  const proto = location.protocol === 'https:' ? 'wss://' : 'ws://'
  const q = '?token=' + encodeURIComponent(route.params.token) + '&password=' + encodeURIComponent(sharePwd.value || '')
  try {
    cmtWs = new WebSocket(proto + location.host + '/ws/comment' + q)
  } catch (e) { scheduleShareCmtReconnect(); return }
  cmtWs.onmessage = (ev) => {
    try {
      const msg = JSON.parse(ev.data)
      if (msg.event !== 'comment_new') return
      if (String(msg.mindMapId) !== String(shareMindMapId)) return
      handleShareCommentNew(msg.comment)
    } catch (e) {}
  }
  cmtWs.onclose = () => { cmtWs = null; scheduleShareCmtReconnect() }
  cmtWs.onerror = () => { try { cmtWs.close() } catch (e) {} }
}

function scheduleShareCmtReconnect() {
  if (cmtWsTimer) clearTimeout(cmtWsTimer)
  if (!shareMindMapId) return
  cmtWsTimer = setTimeout(() => connectShareCmtWs(), 5000)
}

function disconnectShareCmtWs() {
  if (cmtWsTimer) { clearTimeout(cmtWsTimer); cmtWsTimer = null }
  if (cmtWs) { try { cmtWs.close() } catch (e) {} cmtWs = null }
}

function handleShareCommentNew(c) {
  if (!c || !c.id) return
  // 仅当评论面板打开且是当前节点时实时追加（按 id 去重保序）
  if (cmtOpen.value && c.nodeId === cmtNodeId.value) {
    const list = cmtList.value
    if (!list.some(x => Number(x.id) === Number(c.id))) {
      list.push(c)
      list.sort((a, b) => Number(a.id) - Number(b.id))
      cmtList.value = [...list]
    }
  }
}

async function refreshCmt() {'''
assert s.count(anchor) == 1, 'anchor=' + str(s.count(anchor))
s = s.replace(anchor, add_fn)

# 4) onBeforeUnmount：断开评论 WS
old_unmount = '''onBeforeUnmount(() => {
  window.removeEventListener('resize', updateBgSize)'''
new_unmount = '''onBeforeUnmount(() => {
  disconnectShareCmtWs()
  window.removeEventListener('resize', updateBgSize)'''
assert s.count(old_unmount) == 1, 'unmount=' + str(s.count(old_unmount))
s = s.replace(old_unmount, new_unmount)

io.open(p, 'w', encoding='utf-8', newline='').write(s)
print('SHARE OK')
