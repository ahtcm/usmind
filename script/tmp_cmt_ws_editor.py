# -*- coding: utf-8 -*-
import io
p = r'C:\Users\25750\Desktop\usmind\code\web-user\src\views\Editor.vue'
s = io.open(p, encoding='utf-8').read()

# 1) 评论 WS 状态定义（cmtTimer 附近）
old_state = '''let cmtAfterId = 0
let cmtTimer = null
let cmtLoading = false'''
new_state = '''let cmtAfterId = 0
let cmtTimer = null
let cmtLoading = false
let cmtWs = null          // 评论实时推送通道（/ws/comment）
let cmtWsTimer = null     // 评论通道重连定时器
let cmtSubscribed = false'''
assert s.count(old_state) == 1, 'state=' + str(s.count(old_state))
s = s.replace(old_state, new_state)

# 2) 新增评论 WS 函数：插在 startCommentPoll 函数前
anchor = '''function startCommentPoll() {'''
add_fn = '''function connectCmtWs() {
  if (cmtWs) { try { cmtWs.close() } catch (e) {} cmtWs = null }
  if (!mindMapId.value) return
  const token = localStorage.getItem('usmind.token') || ''
  if (!token) return
  const proto = location.protocol === 'https:' ? 'wss://' : 'ws://'
  try {
    cmtWs = new WebSocket(proto + location.host + '/ws/comment?token=' + encodeURIComponent(token) + '&mindMapId=' + mindMapId.value)
  } catch (e) { scheduleCmtWsReconnect(); return }
  cmtWs.onopen = () => {
    cmtSubscribed = true
    // 重连成功：增量拉取一次兜底（WS 断线期间可能漏掉的评论）
    pullCommentsOnce()
  }
  cmtWs.onmessage = (ev) => {
    try {
      const msg = JSON.parse(ev.data)
      if (msg.event !== 'comment_new') return
      if (String(msg.mindMapId) !== String(mindMapId.value)) return
      handleCommentNew(msg.comment)
    } catch (e) {}
  }
  cmtWs.onclose = () => { cmtWs = null; cmtSubscribed = false; scheduleCmtWsReconnect() }
  cmtWs.onerror = () => { try { cmtWs.close() } catch (e) {} }
}

function scheduleCmtWsReconnect() {
  if (cmtWsTimer) clearTimeout(cmtWsTimer)
  if (!mindMapId.value) return
  cmtWsTimer = setTimeout(() => { if (mindMapId.value) connectCmtWs() }, 5000)
}

function disconnectCmtWs() {
  if (cmtWsTimer) { clearTimeout(cmtWsTimer); cmtWsTimer = null }
  if (cmtWs) { try { cmtWs.close() } catch (e) {} cmtWs = null; cmtSubscribed = false }
}

async function pullCommentsOnce() {
  if (!mindMapId.value) return
  try {
    const r = await commentApi.listByMindMap(mindMapId.value, cmtAfterId)
    const news = Array.isArray(r.comments) ? r.comments : []
    if (news.length) news.forEach(c => { if (c.id > cmtAfterId) cmtAfterId = c.id })
    if (r.unread && typeof r.unread === 'object') {
      Object.keys(r.unread).forEach(k => { if (!r.unread[k]) delete unreadNodes[k]; else unreadNodes[k] = r.unread[k] })
      syncCommentFlash()
    }
    if (cmtPanel.open && news.some(c => c.nodeId === cmtPanel.nodeId)) refreshCmtList()
  } catch (e) {}
}

function handleCommentNew(c) {
  if (!c || !c.id) return
  if (c.id > cmtAfterId) cmtAfterId = c.id
  // 作者自己的回复（isAuthor=1）不触发闪烁；访客新评论 → 节点未读+1
  if (Number(c.isAuthor) === 1) return
  unreadNodes[c.nodeId] = (unreadNodes[c.nodeId] || 0) + 1
  syncCommentFlash()
  if (cmtPanel.open && c.nodeId === cmtPanel.nodeId) refreshCmtList()
}

function startCommentPoll() {'''
assert s.count(anchor) == 1, 'anchor=' + str(s.count(anchor))
s = s.replace(anchor, add_fn)

# 3) onMounted：轮询替换为评论 WS 连接
old_mount = '''  startCommentPoll()
  connectWs()'''
new_mount = '''  connectCmtWs()
  connectWs()'''
assert s.count(old_mount) == 1, 'mount=' + str(s.count(old_mount))
s = s.replace(old_mount, new_mount)

# 4) onBeforeUnmount：断开评论 WS
old_unmount = '''onBeforeUnmount(() => {
  disconnectWs()'''
new_unmount = '''onBeforeUnmount(() => {
  disconnectWs()
  disconnectCmtWs()'''
assert s.count(old_unmount) == 1, 'unmount=' + str(s.count(old_unmount))
s = s.replace(old_unmount, new_unmount)

io.open(p, 'w', encoding='utf-8', newline='').write(s)
print('EDITOR OK')
