# -*- coding: utf-8 -*-
"""前端 Editor.vue：评论定向提醒 + 音效开关"""
import io

P = r'C:\Users\25750\Desktop\usmind\code\web-user\src\views\Editor.vue'
s = io.open(P, encoding='utf-8').read()

# 1) 评论面板 header 加音效开关按钮
old_h = """      <div class="cmt-header">
        <span class="cmt-title">💬 {{ cmtPanel.nodeName || '节点' }}</span>
        <button class="cmt-close" @click="closeCmtPanel">×</button>
      </div>"""
new_h = """      <div class="cmt-header">
        <span class="cmt-title">💬 {{ cmtPanel.nodeName || '节点' }}</span>
        <span class="cmt-hd-r">
          <button class="cmt-sound" :title="cmtSoundOn ? '关闭评论提示音' : '开启评论提示音'" @click="toggleCmtSound">{{ cmtSoundOn ? '🔊' : '🔇' }}</button>
          <button class="cmt-close" @click="closeCmtPanel">×</button>
        </span>
      </div>"""
assert s.count(old_h) == 1, 'header anchor'
s = s.replace(old_h, new_h)

# 2) handleCommentNew 定向改造 + 音效函数
old_f = """function handleCommentNew(c) {
  if (!c || !c.id) return
  if (c.id > cmtAfterId) cmtAfterId = c.id
  // 排除自己发的评论（自己回复/自己组织评论不触发自己的未读）；访客/他人新评论 → 未读+1
  if (Number(c.isAuthor) === 1) {
    try {
      const me = JSON.parse(localStorage.getItem('usmind.user') || '{}')
      if (me && c.userId != null && Number(c.userId) === Number(me.id)) return
    } catch (e) { return }
  }
  unreadNodes[c.nodeId] = (unreadNodes[c.nodeId] || 0) + 1
  syncCommentFlash()
  if (cmtPanel.open && c.nodeId === cmtPanel.nodeId) refreshCmtList()
}"""
new_f = """function handleCommentNew(c) {
  if (!c || !c.id) return
  if (c.id > cmtAfterId) cmtAfterId = c.id
  let me = {}
  try { me = JSON.parse(localStorage.getItem('usmind.user') || '{}') } catch (e) {}
  // 自己发的评论不提醒自己（仅打开面板时刷新列表）
  if (Number(c.isAuthor) === 1 && me.id != null && c.userId != null && Number(c.userId) === Number(me.id)) {
    if (cmtPanel.open && c.nodeId === cmtPanel.nodeId) refreshCmtList()
    return
  }
  // 定向提醒：后端已算好 targetUserIds，特效（电流+音效+角标）只发给提醒目标，其他人仅收到信息
  const targets = c.targetUserIds
  if (Array.isArray(targets) && targets.length > 0) {
    const isTarget = me.id != null && targets.some(t => Number(t) === Number(me.id))
    if (!isTarget) {
      if (cmtPanel.open && c.nodeId === cmtPanel.nodeId) refreshCmtList()
      return
    }
  }
  unreadNodes[c.nodeId] = (unreadNodes[c.nodeId] || 0) + 1
  syncCommentFlash()
  playCmtSound()
  if (cmtPanel.open && c.nodeId === cmtPanel.nodeId) refreshCmtList()
}

/** 评论提示音：Web Audio 合成短提示音（无需资源文件），开关持久化到 localStorage */
let cmtSoundOn = localStorage.getItem('usmind.cmtSound') !== '0'
function toggleCmtSound() {
  cmtSoundOn = !cmtSoundOn
  localStorage.setItem('usmind.cmtSound', cmtSoundOn ? '1' : '0')
}
function playCmtSound() {
  if (!cmtSoundOn) return
  try {
    const Ctx = window.AudioContext || window.webkitAudioContext
    if (!Ctx) return
    const ctx = new Ctx()
    const o = ctx.createOscillator(), g = ctx.createGain()
    o.type = 'sine'
    o.frequency.setValueAtTime(660, ctx.currentTime)
    o.frequency.exponentialRampToValueAtTime(990, ctx.currentTime + 0.18)
    g.gain.setValueAtTime(0.12, ctx.currentTime)
    g.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.45)
    o.connect(g); g.connect(ctx.destination)
    o.start(); o.stop(ctx.currentTime + 0.45)
    setTimeout(() => { try { ctx.close() } catch (e) {} }, 700)
  } catch (e) {}
}"""
assert s.count(old_f) == 1, 'handleCommentNew anchor'
s = s.replace(old_f, new_f)

# 3) 样式：.cmt-hd-r / .cmt-sound
old_c = """.cmt-close:hover { background: rgba(255,255,255,.12); color: #fff; }"""
new_c = """.cmt-close:hover { background: rgba(255,255,255,.12); color: #fff; }
.cmt-hd-r { display: flex; align-items: center; gap: 4px; }
.cmt-sound { border: 0; background: none; color: #aab; font-size: 15px; cursor: pointer; line-height: 1; padding: 2px 6px; border-radius: 6px; }
.cmt-sound:hover { background: rgba(255,255,255,.12); color: #fff; }"""
assert s.count(old_c) == 1, 'style anchor'
s = s.replace(old_c, new_c)

io.open(P, 'w', encoding='utf-8', newline='\n').write(s)
print('Editor.vue OK')
