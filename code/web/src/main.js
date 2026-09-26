// USMIND 思维导图编辑器 · simple-mind-map 底层版
// 保留原 UI 壳(顶栏/底栏/侧栏/右键菜单/AI), 渲染与交互全部由 simple-mind-map 提供
import MindMap from 'simple-mind-map'
import Drag from 'simple-mind-map/src/plugins/Drag'
import Select from 'simple-mind-map/src/plugins/Select'
import RainbowLines from 'simple-mind-map/src/plugins/RainbowLines'
import KeyboardNavigation from 'simple-mind-map/src/plugins/KeyboardNavigation'
import Scrollbar from 'simple-mind-map/src/plugins/Scrollbar'
import { THEMES } from './theme.js'
import { AIAssistant } from './ai.js'
import { saveMedia, mediaToUrl, isMediaRef, imageSizeFor, materializeNodeData, dematerializeNodeData, replaceMedia, blobUrlToMediaRef } from './mediastore.js'

// ---------- 自定义图标列表(序号 / 表情包) ----------
const numSvg = (n) => '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32" width="32" height="32"><rect x="1" y="1" width="30" height="30" rx="7" fill="#eef2ff"/><text x="16" y="23" font-size="17" font-weight="bold" text-anchor="middle" fill="#4A5CF7">' + n + '</text></svg>'
const emojiSvg = (e) => '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32" width="32" height="32"><text x="16" y="26" font-size="26" text-anchor="middle">' + e + '</text></svg>'
const EMOJI_LIST = ['😀','😄','😂','😍','🤔','😎','🤩','😭','😡','🤗','👍','👏','🔥','⭐','❤️','💡','🚀','🎯','🎉','✅','⚠️','📌','💰','📚','☕','🎵','🏆','💪']
const ICON_LIST = [
  { type: 'number', list: Array.from({ length: 10 }, (_, i) => ({ name: String(i + 1), icon: numSvg(i + 1) })) },
  { type: 'emoji', list: EMOJI_LIST.map((e) => ({ name: e, icon: emojiSvg(e) })) }
]

// ---------- 注册插件与主题(实例化前) ----------
MindMap.usePlugin(Drag)            // 节点拖拽
MindMap.usePlugin(Select)          // 右键框选多选
MindMap.usePlugin(RainbowLines)    // 分支彩色连线
MindMap.usePlugin(KeyboardNavigation) // 方向键导航
MindMap.usePlugin(Scrollbar)       // 画布滚动条
THEMES.forEach((t) => {
  try { MindMap.defineTheme(t.name, t.config) } catch (e) { /* 已存在则跳过 */ }
})

// ---------- 文档存储(多画布, 沿用原 localStorage 键) ----------
const DOC_KEY = 'usmind.doc'
const canvasTabs = []
let docIndex = 0
let dirty = false
let docName = '未命名'
let autoSaveTimer = null
let lastSaveTime = 0

function defaultData() {
  return { root: { data: { text: '中心主题' }, children: [] } }
}
function newDoc() {
  return { name: '未命名', layout: 'logicalStructure', theme: THEMES[0].name, data: defaultData(), viewData: null }
}
// 旧 mind-elixir 数据 -> mind-map 数据
function migrateNode(elixirNode) {
  const d = { text: String(elixirNode.topic || '').trim() || '主题' }
  if (elixirNode.icons && elixirNode.icons.length) d.icon = elixirNode.icons.slice()
  if (elixirNode.expanded === false) d.expand = false
  const node = { data: d }
  if (elixirNode.children && elixirNode.children.length) {
    node.children = elixirNode.children.map(migrateNode)
  }
  return node
}
function migrateIfNeeded(doc) {
  // mind-elixir 格式特征: doc.nodeData 存在(topic/children), 无 root
  if (doc && doc.nodeData && doc.nodeData.topic !== undefined && !doc.root) {
    return { name: doc.name || '未命名', layout: doc.layout || 'logicalStructure', theme: doc.theme || THEMES[0].name, data: { root: migrateNode(doc.nodeData) }, viewData: doc.viewData || null }
  }
  // 数据清洗: doc.data 必须是 { root: 根节点数据 } 形态
  if (doc && doc.data) {
    const d = doc.data
    if (d.root && d.root.data) {
      // 已是标准形态; 但保留冗余顶层键时剔除
      const clean = { root: d.root }
      if (d.root.data && d.root.data.smmVersion) { /* keep */ }
      doc.data = clean
    } else if (d.data && d.data.text !== undefined) {
      // 根节点数据(无 root 包裹) -> 补 root
      doc.data = { root: d }
    } else if (d.nodeData && d.nodeData.topic !== undefined) {
      // 旧 mind-elixir: data 里嵌 nodeData
      doc.data = { root: migrateNode(d.nodeData) }
    } else {
      doc.data = defaultData()
    }
  }
  return doc
}
function getThemeName(doc) {
  const t = doc && doc.theme
  if (typeof t === 'string' && t) {
    return THEMES.some((x) => x.name === t) ? t : THEMES[0].name
  }
  if (t && typeof t === 'object' && t.name) {
    return THEMES.some((x) => x.name === t.name) ? t.name : THEMES[0].name
  }
  return THEMES[0].name
}
// ---------- 彩虹分支着色(节点按分支色, 对齐 XMind) ----------
function hexRgb(hex) {
  if (!hex || typeof hex !== 'string') {
    if (hex === null || hex === undefined) console.warn('[USMIND] hexRgb 收到空颜色:', hex)
    return [0, 0, 0]
  }
  const h = hex.replace('#', '')
  return [parseInt(h.slice(0, 2), 16), parseInt(h.slice(2, 4), 16), parseInt(h.slice(4, 6), 16)]
}
function mix(hex1, hex2, ratio) {
  const a = hexRgb(hex1), b = hexRgb(hex2)
  const c = a.map((v, i) => Math.round(v * ratio + b[i] * (1 - ratio)))
  return '#' + c.map((v) => v.toString(16).padStart(2, '0')).join('')
}
function isDarkColor(hex) {
  const [r, g, b] = hexRgb(hex)
  if (!hex || typeof hex !== 'string') return false
  return (r * 299 + g * 587 + b * 114) / 1000 < 150
}
let rainbowIdx = 0
// 手动设置过样式(颜色/背景)的节点: 彩虹分支着色不再覆盖它们
let manualStyled = new Set()
function applyBranchTo(node, depth, branchColor) {
  if (!node || !node.data) return
  if (depth === 0) {
    // 根节点恢复主题默认色
    delete node.data.fillColor
    delete node.data.color
    delete node.data.borderColor
    delete node.data.lineColor
    delete node.data.borderWidth
    ;(node.children || []).forEach((c) => applyBranchTo(c, 1, null))
  } else if (manualStyled.has(node.data.uid)) {
    // 手动锁定样式的节点: 保留自定义颜色/背景, 仅递归子节点
    ;(node.children || []).forEach((cn) => applyBranchTo(cn, depth + 1, branchColor))
  } else if (depth === 1) {
    const colors = getBranchColors()
    const c = colors[rainbowIdx++ % colors.length]
    node.data.fillColor = c
    node.data.color = isDarkColor(c) ? '#ffffff' : '#1a1a1a'
    node.data.borderColor = c
    node.data.borderWidth = 1
    node.data.lineColor = c
    ;(node.children || []).forEach((cn) => applyBranchTo(cn, 2, c))
  } else {
    node.data.fillColor = mix(branchColor, '#ffffff', 0.14)
    node.data.color = isDarkColor(branchColor) ? '#475569' : '#334155'
    node.data.borderColor = mix(branchColor, '#ffffff', 0.38)
    node.data.borderWidth = 1
    node.data.lineColor = branchColor
    ;(node.children || []).forEach((cn) => applyBranchTo(cn, depth + 1, branchColor))
  }
}
const BRANCH_FALLBACK_COLORS = ['#5B8FF9', '#2FB36B', '#F6A609', '#9270CA', '#E8684A', '#22A7C9', '#F08BB4', '#8C9B6F']
function getBranchColors() {
  const doc = canvasTabs[docIndex]
  const t = THEMES.find((x) => x.name === getThemeName(doc)) || THEMES[0]
  const colors = (t && t.branchColors && t.branchColors.length ? t.branchColors : THEMES[0].branchColors)
  const clean = (colors || []).filter(Boolean)
  return clean.length ? clean : BRANCH_FALLBACK_COLORS
}
function applyBranchRainbow() {
  if (!mind || !mind.renderer || !mind.renderer.renderTree) return
  rainbowIdx = 0
  applyBranchTo(mind.renderer.renderTree, 0, null)
  mind.render(() => {})
}
let rainbowTimer = null
function scheduleBranchRainbow() {
  if (rainbowTimer) clearTimeout(rainbowTimer)
  rainbowTimer = setTimeout(() => applyBranchRainbow(), 350)
}
function persistAll() {
  canvasTabs.forEach((d, i) => {
    try { localStorage.setItem(DOC_KEY + '.' + i, JSON.stringify(d)) } catch (e) { /* ignore */ }
  })
  localStorage.setItem('usmind.docname', String(docIndex))
}
function loadFromStorage() {
  canvasTabs.length = 0
  // 1) 本地编辑数据优先(localStorage): 编辑中的用户刷新页面不丢数据
  const name = localStorage.getItem('usmind.docname')
  let idx = parseInt(name, 10) || 0
  for (let i = 0; i < 20; i++) {
    try {
      const raw = localStorage.getItem(DOC_KEY + '.' + i)
      if (!raw) break
      const doc = migrateIfNeeded(JSON.parse(raw))
      if (!doc || !doc.data || !doc.data.root) continue
      canvasTabs.push(doc)
    } catch (e) { break }
  }
  if (canvasTabs.length) {
    docIndex = idx
    if (docIndex >= canvasTabs.length) docIndex = 0
    // 支持 ?canvas=N 定位画布(复制链接分享)
    try {
      const cv = parseInt(new URLSearchParams(location.search).get('canvas'), 10)
      if (!isNaN(cv) && cv >= 0 && cv < canvasTabs.length) docIndex = cv
    } catch (e) { /* ignore */ }
    return
  }
  // 2) 无本地数据时用 EXE 编译注入的分享数据(线上首页/分享页直接渲染已保存导图)
  if (window.__USMIND_INIT__ && Array.isArray(window.__USMIND_INIT__.tabs) && window.__USMIND_INIT__.tabs.length) {
    window.__USMIND_INIT__.tabs.forEach((t) => {
      const doc = migrateIfNeeded(t)
      if (doc && doc.data && doc.data.root) canvasTabs.push(doc)
    })
    docIndex = window.__USMIND_INIT__.docIndex || 0
    if (docIndex >= canvasTabs.length) docIndex = 0
    // 分享版仍支持 ?canvas=N 定位
    try {
      const cv = parseInt(new URLSearchParams(location.search).get('canvas'), 10)
      if (!isNaN(cv) && cv >= 0 && cv < canvasTabs.length) docIndex = cv
    } catch (e) { /* ignore */ }
    return
  }
  if (!canvasTabs.length) canvasTabs.push(newDoc())
  if (docIndex >= canvasTabs.length) docIndex = 0
}

// ---------- 状态 UI ----------
const statusEl = document.getElementById('doc-status')
const nameEl = document.getElementById('doc-name')
const savedTimeEl = document.getElementById('doc-saved-time')
function markDirty() {
  dirty = true
  statusEl.textContent = '未保存'
  statusEl.classList.add('dirty')
}
function markSaved() {
  dirty = false
  statusEl.textContent = '已保存'
  statusEl.classList.remove('dirty')
  lastSaveTime = Date.now()
  if (savedTimeEl) {
    const d = new Date(lastSaveTime)
    savedTimeEl.textContent = '最近保存 ' + d.getHours() + ':' + String(d.getMinutes()).padStart(2, '0')
  }
}
function refreshTabBar() {
  const bar = document.getElementById('canvas-bar')
  bar.querySelectorAll('.canvas-tab').forEach((el) => el.remove())
  canvasTabs.forEach((doc, i) => {
    const span = document.createElement('span')
    span.className = 'canvas-tab' + (i === docIndex ? ' active' : '')
    const label = document.createElement('span')
    label.className = 'canvas-tab-label'
    label.textContent = doc.name || ('画布' + (i + 1))
    label.addEventListener('click', () => switchCanvas(i))
    // 双击名字 -> 行内编辑
    label.addEventListener('dblclick', (e) => {
      e.stopPropagation()
      const input = document.createElement('input')
      input.className = 'canvas-tab-input'
      input.value = doc.name || ('画布' + (i + 1))
      label.replaceWith(input)
      input.focus()
      input.select()
      const commit = () => {
        const nm = input.value.trim()
        if (nm) {
          doc.name = nm
          persistAll()
          if (i === docIndex) {
            docName = nm
            nameEl.value = nm
          }
        }
        refreshTabBar()
      }
      input.addEventListener('keydown', (e2) => {
        e2.stopPropagation()
        if (e2.key === 'Enter') commit()
        else if (e2.key === 'Escape') refreshTabBar()
      })
      input.addEventListener('blur', commit)
    })
    span.appendChild(label)
    // 圆×: 删除画布
    const del = document.createElement('span')
    del.className = 'canvas-tab-del'
    del.textContent = '×'
    del.title = '删除画布'
    del.addEventListener('click', (e) => {
      e.stopPropagation()
      if (canvasTabs.length <= 1) {
        alert('至少保留一个画布')
        return
      }
      if (!confirm('删除画布「' + (doc.name || ('画布' + (i + 1))) + '」？此操作不可恢复。')) return
      canvasTabs.splice(i, 1)
      if (i === docIndex) {
        docIndex = Math.min(docIndex, canvasTabs.length - 1)
        loadDoc()
      } else if (i < docIndex) {
        docIndex--
      }
      persistAll()
      refreshTabBar()
    })
    span.appendChild(del)
    bar.insertBefore(span, document.getElementById('canvas-add'))
  })
}

// ---------- MindMap 实例 ----------
let mind = null
const mapEl = document.getElementById('map')
const LAYOUT_MAP = {
  side: 'mindMap',                 // 思维导图(两侧)
  l: 'logicalStructureLeft',       // 逻辑结构图(左)
  r: 'logicalStructure',           // 逻辑结构图(右)
  org: 'organizationStructure',    // 组织结构图
  catalog: 'catalogOrganization',  // 目录组织图
  tl: 'timeline',                  // 时间轴
  vtl: 'verticalTimeline',         // 竖向时间轴
  fish: 'fishbone'                 // 鱼骨头图
}
const LAYOUT_REV = {}
Object.keys(LAYOUT_MAP).forEach((k) => { LAYOUT_REV[LAYOUT_MAP[k]] = k })

async function createMind() {
  const doc = canvasTabs[docIndex]
  manualStyled = new Set(doc.manualStyles || []) // 初始化即恢复手动样式锁定, 防止彩虹覆盖并避免保存时清空
  try { await materializeNodeData(doc.data.root) } catch (e) { console.warn('[USMIND] 媒体物化失败:', e) }
  migrateLegacyMedia(doc.data.root)
  applyMediaCardStyle(doc.data.root, getThemeName(doc))
  mind = new MindMap({
    el: mapEl,
    data: doc.data.root,
    layout: doc.layout || 'logicalStructure',
    theme: getThemeName(doc),
    viewData: doc.viewData || null,
    fit: true,
    maxHistoryCount: 60,
    mousewheelAction: 'zoom',          // 滚轮缩放(Ctrl+滚轮也缩放)
    defaultInsertSecondLevelNodeText: '新主题',
    defaultInsertBelowSecondLevelNodeText: '新主题',
    useLeftKeySelectionRightKeyDrag: false, // 左键平移画布 + 右键框选
    enableShortcutOnlyWhenMouseInSvg: false,
    openRealtimeRenderOnNodeTextEdit: true, // 输入时节点框实时伸长(所见即所得)
    rainbowLinesConfig: {
      open: true,
      colorsList: ['#5B8FF9', '#2FB36B', '#F6A609', '#9270CA', '#E8684A', '#22A7C9', '#F08BB4', '#8C9B6F']
    },
    iconList: ICON_LIST
  })
  bindMindEvents()
  registerShortcuts()
  syncLayoutUI()
  updateZoomVal()
  applyBranchRainbow()
  markSaved()
}

function bindMindEvents() {
  // 数据变化 -> 未保存 + 自动保存计时
  mind.on('data_change', () => {
    markDirty()
    scheduleAutoSave()
    scheduleBranchRainbow()
  })
  // 刷新/关闭前强制保存
  window.addEventListener('beforeunload', () => {
    if (dirty) saveDoc()
  })
  // 视图变化(平移/缩放) -> 存 viewData
  mind.on('view_data_change', (viewData) => {
    if (canvasTabs[docIndex]) canvasTabs[docIndex].viewData = viewData
    persistAll()
  })
  mind.on('scale', (s) => {
    const el = document.getElementById('zoom-val')
    if (el) el.textContent = Math.round(s * 100) + '%'
  })
  // 撤销/重做按钮状态
  mind.on('back_forward', (cur, total) => {
    document.getElementById('btn-undo').disabled = cur <= 0
    document.getElementById('btn-redo').disabled = cur >= total - 1
  })
  // 右键节点 -> 自定义菜单
  mind.on('node_contextmenu', (e, node) => {
    e.preventDefault()
    if (node && node.nodeData) showCtxMenu(e, node)
  })
  mind.on('node_click', () => hideCtxMenu())
  mind.on('draw_click', () => hideCtxMenu())
  // 丝滑: 拖拽/编辑文字时禁用节点过渡(保持跟手/打字流畅), 其余场景保留平滑动画
  mind.on('node_dragging', () => mapEl.classList.add('no-anim'))
  mind.on('node_dragend', () => mapEl.classList.remove('no-anim'))
  mind.on('node_text_edit_change', () => mapEl.classList.add('no-anim'))
  mind.on('render_after', () => {
    if (!mind.editNodeClassList || mind.editNodeClassList.length === 0) mapEl.classList.remove('no-anim')
  })
  // 点击附件图标 -> 预览弹窗(文本类可在线编辑保存)
  mind.on('node_attachmentClick', (node) => {
    openAttachmentPreview(node)
  })
}

// ---------- 附件预览 + 在线编辑 ----------
const TEXT_EXT = ['txt','md','markdown','json','js','mjs','cjs','ts','jsx','tsx','css','html','htm','xml','svg','csv','log','ini','yaml','yml','py','java','c','cpp','h','go','rs','sh','bat','sql','vue','conf','toml','env']
function attExt(name) {
  const m = /\.([a-zA-Z0-9]+)$/.exec(name || '')
  return m ? m[1].toLowerCase() : ''
}
function isAttText(name) {
  const ext = attExt(name)
  if (!ext) return false
  if (['png','jpg','jpeg','gif','webp','bmp','ico','avif'].includes(ext)) return false
  if (['pdf'].includes(ext)) return false
  if (['mp3','wav','ogg','m4a','aac','flac'].includes(ext)) return false
  if (['mp4','webm','ogv','mov','mkv'].includes(ext)) return false
  return TEXT_EXT.includes(ext)
}
function openAttachmentPreview(node) {
  const url = node.getData('attachmentUrl')
  const name = node.getData('attachmentName') || '附件'
  if (!url) return
  const ext = attExt(name)
  const isImg = ['png','jpg','jpeg','gif','webp','bmp','ico','avif','svg'].includes(ext)
  const isPdf = ext === 'pdf'
  const isAudio = ['mp3','wav','ogg','m4a','aac','flac'].includes(ext)
  const isVideo = ['mp4','webm','ogv','mov','mkv'].includes(ext)
  const isText = isAttText(name)
  // 容器
  let wrap = document.getElementById('att-preview')
  if (wrap) wrap.remove()
  wrap = document.createElement('div')
  wrap.id = 'att-preview'
  wrap.innerHTML = '<div class="attp-mask"></div><div class="attp-box"><div class="attp-head"><span class="attp-title"></span><button class="attp-close" title="关闭">×</button></div><div class="attp-body"></div><div class="attp-foot"></div></div>'
  document.body.appendChild(wrap)
  const title = wrap.querySelector('.attp-title')
  const body = wrap.querySelector('.attp-body')
  const foot = wrap.querySelector('.attp-foot')
  title.textContent = name
  wrap.querySelector('.attp-close').addEventListener('click', () => wrap.remove())
  wrap.querySelector('.attp-mask').addEventListener('click', () => wrap.remove())
  const closeOnEsc = (e) => { if (e.key === 'Escape') { wrap.remove(); document.removeEventListener('keydown', closeOnEsc) } }
  document.addEventListener('keydown', closeOnEsc)
  // 内容
  if (isImg) {
    const img = document.createElement('img')
    img.src = url
    img.style.maxWidth = '100%'
    img.style.maxHeight = '62vh'
    img.style.borderRadius = '10px'
    body.appendChild(img)
  } else if (isPdf) {
    const ifr = document.createElement('iframe')
    ifr.src = url
    ifr.style.cssText = 'width:100%;height:62vh;border:0;border-radius:10px;background:#fff'
    body.appendChild(ifr)
  } else if (isAudio) {
    body.innerHTML = '<audio controls autoplay style="width:100%"></audio>'
    body.querySelector('audio').src = url
  } else if (isVideo) {
    body.innerHTML = '<video controls autoplay style="width:100%;border-radius:10px;background:#000"></video>'
    body.querySelector('video').src = url
  } else if (isText) {
    const ta = document.createElement('textarea')
    ta.spellcheck = false
    ta.style.cssText = 'width:100%;height:52vh;resize:vertical;border-radius:10px;border:1px solid rgba(255,255,255,.12);background:#14161a;color:#e6e6e6;padding:12px;font:13px/1.6 Consolas,monospace;outline:none'
    body.appendChild(ta)
    // 读取内容
    const blob = url.indexOf('blob:') === 0 ? null : null
    fetch(url).then((r) => r.text()).then((txt) => { ta.value = txt }).catch(() => { ta.value = '' })
    const saveBtn = document.createElement('button')
    saveBtn.textContent = '保存修改'
    saveBtn.className = 'attp-save'
    saveBtn.disabled = true
    foot.appendChild(saveBtn)
    ta.addEventListener('input', () => { saveBtn.disabled = false })
    saveBtn.addEventListener('click', async () => {
      try {
        const ref = blobUrlToMediaRef(url)
        if (!ref) { alert('附件引用无效，无法保存'); return }
        const mime = isAttText(name) && (attExt(name) === 'json' ? 'application/json' : 'text/plain') || 'text/plain'
        const nb = new Blob([ta.value], { type: mime })
        const newUrl = await replaceMedia(ref, nb)
        mind.renderer.setNodeDataRender(node, { attachmentUrl: newUrl })
        saveDoc()
        saveBtn.textContent = '已保存 ✓'
        saveBtn.disabled = true
        setTimeout(() => { if (wrap.isConnected) wrap.remove() }, 1200)
      } catch (e) { alert('保存失败: ' + (e && e.message ? e.message : e)) }
    })
  } else {
    // 不支持预览的类型: 提示下载
    const tip = document.createElement('div')
    tip.className = 'attp-tip'
    tip.innerHTML = '该类型暂不支持在线预览，<a href="' + url + '" download="' + name.replace(/"/g, '') + '">点击下载</a>'
    body.appendChild(tip)
  }
}

function registerShortcuts() {
  // Tab/Enter/复制/剪切/粘贴/F2 等已由 simple-mind-map 内置注册, 避免重复注册导致一次创建两个节点
  // 仅补充内置未注册的撤销/重做
  mind.keyCommand.addShortcut('Control+z', () => mind.execCommand('BACK'))
  mind.keyCommand.addShortcut('Control+y', () => mind.execCommand('FORWARD'))
}

// ---------- 画布切换 ----------
function switchCanvas(index) {
  if (index === docIndex) return
  saveDoc() // 先保存当前
  docIndex = index
  mapEl.style.transition = 'opacity .22s ease'
  mapEl.style.opacity = '0'
  loadDoc().then(() => {
    requestAnimationFrame(() => { mapEl.style.opacity = '1' })
  })
  refreshTabBar()
}
async function loadDoc() {
  const doc = canvasTabs[docIndex]
  manualStyled = new Set(doc.manualStyles || [])
  docName = doc.name || '未命名'
  nameEl.value = docName
  try { await materializeNodeData(doc.data.root) } catch (e) { console.warn('[USMIND] 媒体物化失败:', e) }
  migrateLegacyMedia(doc.data.root)
  applyMediaCardStyle(doc.data.root, getThemeName(doc))
  mind.setData(doc.data.root)
  if (doc.viewData) mind.view.setTransformData(doc.viewData)
  else mind.view.fit()
  if (doc.layout) {
    mind.setLayout(doc.layout)
    syncLayoutUI()
  }
  mind.setTheme(getThemeName(doc))
  applyBranchRainbow()
  markSaved()
}
async function saveDoc() {
  const doc = canvasTabs[docIndex]
  // getData 返回深拷贝(当前渲染态含 blob URL); 还原为 media:// 引用后再存储
  const data = mind.getData()
  try { dematerializeNodeData(data) } catch (e) { console.warn('[USMIND] 媒体还原失败:', e) }
  doc.data = { root: data }
  doc.viewData = mind.view.getTransformData()
  doc.manualStyles = Array.from(manualStyled)
  doc.name = docName
  doc.layout = mind.opt.layout || 'logicalStructure'
  doc.theme = mind.opt.theme || THEMES[0].name
  persistAll()
  markSaved()
  cloudSync()
}
let cloudSyncTimer = null
function cloudSync() {
  // 检测到 EXE 本地服务(/api/health)后, 把整份画布推送给 EXE 保存+编译+同步
  if (cloudSyncTimer) clearTimeout(cloudSyncTimer)
  cloudSyncTimer = setTimeout(() => {
    try {
      // 保存必须走本机 EXE(127.0.0.1:8899), 线上页面(Netlify)相对路径会 404
      fetch('http://127.0.0.1:8899/api/health', { cache: 'no-store' }).then((r) => {
        if (r.ok) {
          fetch('http://127.0.0.1:8899/api/save', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ tabs: canvasTabs, docIndex: docIndex })
          }).catch(() => {})
        }
      }).catch(() => {})
    } catch (e) { /* 无 EXE 服务时静默, 仅本地保存 */ }
  }, 1200)
}
function scheduleAutoSave() {
  if (autoSaveTimer) clearTimeout(autoSaveTimer)
  autoSaveTimer = setTimeout(() => { if (dirty) saveDoc() }, 5000)
}

// ---------- 旧结构媒体迁移(无容器 audio/video -> 固定尺寸 div, 防渲染高度溢出节点框) ----------
function migrateLegacyMedia(root) {
  if (!root || !root.data) return
  const walk = (node) => {
    const d = node.data
    if (d && d.richText === true && typeof d.text === 'string') {
      const t = d.text
      if (t.indexOf('<div') === -1 && (t.indexOf('<audio') > -1 || t.indexOf('<video') > -1)) {
        const m = t.match(/src="([^"]+)"/)
        const src = m ? m[1] : ''
        if (t.indexOf('<audio') > -1) {
          d.text = '<div style="position:relative;width:280px;height:40px;overflow:hidden;border-radius:12px;line-height:0;background:#000"><audio controls style="width:100%;height:100%;display:block" src="' + src + '"></audio></div>'
        } else {
          d.text = '<div style="position:relative;width:300px;height:170px;overflow:hidden;border-radius:12px;background:#000;line-height:0"><video controls style="width:100%;height:100%;object-fit:cover;display:block;background:#000" src="' + src + '"></video></div>'
        }
      }
    }
    ;(node.children || []).forEach(walk)
  }
  walk(root)
}

// ---------- 媒体节点卡片样式(加载时统一应用, 幂等) ----------
function mediaCardColors(themeName) {
  try {
    let bg = ''
    const t = (window.THEMES || []).find((x) => x.name === themeName)
    if (t && t.config) bg = t.config.backgroundColor || ''
    if (!bg) bg = '#16181d'
    const m = bg.match(/#([0-9a-fA-F]{6})/)
    let isDark = true
    if (m) {
      const r = parseInt(m[1].slice(0, 2), 16)
      const g = parseInt(m[1].slice(2, 4), 16)
      const b = parseInt(m[1].slice(4, 6), 16)
      isDark = (r * 299 + g * 587 + b * 114) / 1000 < 128
    }
    // 媒体节点框融入画布背景(视觉上节点=媒体本身, 无框)
    return { fill: bg, border: bg }
  } catch (e) { return { fill: '#16181d', border: '#16181d' } }
}

function applyMediaCardStyle(root, themeName) {
  if (!root || !root.data) return
  const card = mediaCardColors(themeName)
  const walk = (node) => {
    const d = node.data
    const isMedia = !!(d && (d.image || d.richText === true))
    const isAtt = !!(d && d.attachmentUrl)
    if (isMedia) {
      // 图片/音视频: 无框融入画布
      d.shape = 'roundedRectangle'
      d.borderRadius = 12
      d.borderWidth = 0
      d.fillColor = card.fill
      d.borderColor = card.border
      const isFill = d.richText === true && typeof d.text === 'string' && d.text.indexOf('overflow:hidden') > -1
      d.paddingX = isFill ? 0 : 6
      d.paddingY = isFill ? 0 : 6
      if (d.uid) manualStyled.add(d.uid)
    } else if (isAtt) {
      // 附件节点: 白色磨砂(白底 + 点状虚线边框 + 圆角)
      d.shape = 'roundedRectangle'
      d.borderRadius = 12
      d.borderWidth = 1
      d.borderColor = '#C9CDD6'
      d.borderDasharray = '2 4'
      d.fillColor = '#F4F5F7'
      d.paddingX = 6
      d.paddingY = 6
      if (d.uid) manualStyled.add(d.uid)
    }
    ;(node.children || []).forEach(walk)
  }
  walk(root)
}

// ---------- 节点插入媒体(图片/音频/视频/文件) ----------
function fileToDataUrl(file) {
  return new Promise((resolve, reject) => {
    const r = new FileReader()
    r.onload = () => resolve(r.result)
    r.onerror = reject
    r.readAsDataURL(file)
  })
}
// 静态图片压缩到最长边 1280; GIF/WebP/SVG 保留原样(动图不丢动画)
async function compressImage(file) {
  const type = file.type || ''
  const dataUrl = await fileToDataUrl(file)
  if (type === 'image/gif' || type === 'image/webp' || type === 'image/svg+xml' || type === 'image/x-icon') return dataUrl
  const img = new Image()
  await new Promise((res, rej) => { img.onload = res; img.onerror = rej; img.src = dataUrl })
  const MAX = 1280
  let w = img.naturalWidth, h = img.naturalHeight
  if (!w || !h) return dataUrl
  if (Math.max(w, h) > MAX) {
    const scale = MAX / Math.max(w, h)
    w = Math.round(w * scale); h = Math.round(h * scale)
    const cv = document.createElement('canvas')
    cv.width = w; cv.height = h
    cv.getContext('2d').drawImage(img, 0, 0, w, h)
    return cv.toDataURL('image/jpeg', 0.85)
  }
  return dataUrl
}
async function uploadToExe(file) {
  try {
    const fd = new FormData()
    fd.append('file', file)
    const r = await fetch('http://127.0.0.1:8899/api/upload', { method: 'POST', body: fd })
    if (!r.ok) return null
    const j = await r.json()
    return (j && j.path) ? j.path : null
  } catch (e) { return null }
}
async function insertFileToNode(node, file) {
  if (!node || !file) return
  const type = file.type || ''
  try {
    if (type.startsWith('image/')) {
      const dataUrl = await compressImage(file)
      const blob = await (await fetch(dataUrl)).blob()
      const upFile = new File([blob], file.name || 'image', { type: blob.type || type })
      let src = await uploadToExe(upFile)
      if (!src) { const ref = await saveMedia(upFile); src = await mediaToUrl(ref) }
      const size = await imageSizeFor(dataUrl)
      const w = Math.round(size.width) || 210
      const h = Math.round(size.height) || 157
      const rawName = node.getData ? node.getData('text') : ''
      const name = typeof rawName === 'string' && !/<[a-z][\s\S]*>/i.test(rawName) ? rawName : ''
      const esc = name.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      const label = esc
        ? '<span style="position:absolute;left:8px;bottom:6px;color:#fff;font-size:12px;line-height:1.2;text-shadow:0 1px 3px rgba(0,0,0,.85);z-index:2">' + esc + '</span>'
        : ''
      mind.renderer.setNodeDataRender(node, { richText: true, text:
        '<div style="position:relative;width:' + w + 'px;height:' + h + 'px;overflow:hidden;border-radius:12px;line-height:0">' +
        '<img src="' + src + '" style="position:absolute;top:-10px;left:-10px;width:' + (w + 20) + 'px;height:' + (h + 20) + 'px;object-fit:cover;display:block;margin:0">' +
        label +
        '</div>' })
    } else if (type.startsWith('audio/')) {
      let src = await uploadToExe(file)
      if (!src) { const ref = await saveMedia(file); src = await mediaToUrl(ref) }
      mind.renderer.setNodeDataRender(node, { richText: true, text:
        '<div style="position:relative;width:280px;height:40px;overflow:hidden;border-radius:12px;line-height:0;background:#000">' +
        '<audio controls style="width:100%;height:100%;display:block" src="' + src + '"></audio>' +
        '</div>' })
    } else if (type.startsWith('video/')) {
      let src = await uploadToExe(file)
      if (!src) { const ref = await saveMedia(file); src = await mediaToUrl(ref) }
      mind.renderer.setNodeDataRender(node, { richText: true, text:
        '<div style="position:relative;width:300px;height:170px;overflow:hidden;border-radius:12px;background:#000;line-height:0">' +
        '<video controls style="width:100%;height:100%;object-fit:cover;display:block;background:#000" src="' + src + '"></video>' +
        '</div>' })
    } else {
      let src = await uploadToExe(file)
      if (!src) { const ref = await saveMedia(file); src = await mediaToUrl(ref) }
      mind.execCommand('SET_NODE_ATTACHMENT', node, src, file.name || '附件')
    }
    try {
      const isFile = !(type.startsWith('image/') || type.startsWith('audio/') || type.startsWith('video/'))
      if (isFile) {
        mind.execCommand('SET_NODE_STYLES', node, { shape: 'roundedRectangle', borderRadius: 12, borderWidth: 1, borderColor: '#C9CDD6', borderDasharray: '2 4', fillColor: '#F4F5F7', paddingX: 6, paddingY: 6 })
      } else {
        const card = mediaCardColors(getThemeName(canvasTabs[docIndex]))
        mind.execCommand('SET_NODE_STYLES', node, { shape: 'roundedRectangle', borderRadius: 12, borderWidth: 0, fillColor: card.fill, borderColor: card.border, paddingX: 6, paddingY: 6 })
      }
      manualStyled.add(node.getData('uid'))
    } catch (e) { /* 样式设置失败不阻塞插入 */ }
    scheduleBranchRainbow()
    saveDoc()
  } catch (e) {
    console.warn('[USMIND] 插入媒体失败:', e)
    alert('插入媒体失败: ' + (e && e.message ? e.message : e))
  }
}
// Ctrl+V 粘贴文件/图片到当前选中节点
document.addEventListener('paste', (e) => {
  if (!mind || !mind.renderer) return
  const t = e.target
  if (t && (t.tagName === 'INPUT' || t.tagName === 'TEXTAREA' || t.isContentEditable)) return
  const node = mind.renderer.activeNodeList[0]
  if (!node) return
  const items = e.clipboardData ? e.clipboardData.items : []
  const files = []
  for (let i = 0; i < items.length; i++) {
    const it = items[i]
    if (it.kind === 'file') {
      const f = it.getAsFile()
      if (f) files.push(f)
    }
  }
  if (!files.length) return
  e.preventDefault()
  files.forEach((f) => insertFileToNode(node, f))
})

// ---------- 右键菜单 ----------
const ctxMenu = document.getElementById('ctx-menu')
let ctxNode = null
let ctxHideTimer = null
function showCtxMenu(e, node) {
  if (ctxHideTimer) clearTimeout(ctxHideTimer)
  ctxMenu.classList.remove('closing', 'hidden')
  ctxNode = node
  const delAtt = ctxMenu.querySelector('[data-act="del-attachment"]')
  if (delAtt) delAtt.style.display = node && node.getData && node.getData('attachmentUrl') ? '' : 'none'
  ctxMenu.classList.remove('hidden')
  const x = e.clientX != null ? e.clientX : e.pageX
  const y = e.clientY != null ? e.clientY : e.pageY
  ctxMenu.style.left = Math.min(x, window.innerWidth - 320) + 'px'
  ctxMenu.style.top = Math.min(y, window.innerHeight - 520) + 'px'
}
function hideCtxMenu() {
  if (ctxMenu.classList.contains('hidden')) return
  ctxMenu.classList.add('closing')
  if (ctxHideTimer) clearTimeout(ctxHideTimer)
  ctxHideTimer = setTimeout(() => {
    ctxMenu.classList.add('hidden')
    ctxMenu.classList.remove('closing', 'no-anim')
    ctxNode = null
    ctxCanvasIndex = -1
  }, 100)
}

// ---------- 画布标签右键菜单 ----------
let ctxCanvasIndex = -1
function showCanvasCtxMenuAt(e, i, anchorEl) {
  if (ctxHideTimer) clearTimeout(ctxHideTimer)
  ctxMenu.classList.remove('closing', 'hidden')
  ctxCanvasIndex = i
  ctxMenu.dataset.mode = 'canvas'
  ctxMenu.innerHTML = ''
  const items = [
    { act: 'canvas-copy-link', text: '复制链接' },
    { act: 'canvas-rename', text: '重命名' },
    { act: 'canvas-dup', text: '复制' },
    { act: 'canvas-del', text: '删除', danger: true },
    { act: 'canvas-export', text: '将画布保存为思维导图' }
  ]
  items.forEach((it) => {
    const b = document.createElement('button')
    b.dataset.act = it.act
    b.textContent = it.text
    if (it.danger) b.classList.add('ctx-danger')
    ctxMenu.appendChild(b)
  })
  ctxNode = null
  ctxMenu.classList.add('no-anim') // 画布菜单禁用入场动画, 保证锚定位置精确
  ctxMenu.classList.remove('hidden')
  // 锚定: 菜单在标签正上方, 与标签紧贴(强制 reflow 后测量真实尺寸)
  void ctxMenu.offsetHeight
  const r = anchorEl.getBoundingClientRect()
  const mw = ctxMenu.offsetWidth || 190
  const mh = ctxMenu.offsetHeight || 240
  const left = Math.max(8, Math.min(r.left + r.width / 2 - mw / 2, window.innerWidth - mw - 8))
  const top = Math.max(8, r.top - mh - 3)
  ctxMenu.style.left = left + 'px'
  ctxMenu.style.top = top + 'px'
}
function canvasName(i) {
  const c = canvasTabs[i]
  return (c && c.name) || ('画布' + (i + 1))
}
function copyCanvasLink(i) {
  const url = location.origin + location.pathname + '?canvas=' + i
  if (navigator.clipboard && navigator.clipboard.writeText) {
    navigator.clipboard.writeText(url).then(() => {})
  }
}
function exportCanvas(i) {
  const c = canvasTabs[i]
  if (!c) return
  const blob = new Blob([JSON.stringify(c, null, 2)], { type: 'application/json' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = canvasName(i) + '.smm.json'
  a.click()
  URL.revokeObjectURL(a.href)
}
document.addEventListener('click', (e) => {
  if (!e.target.closest('#ctx-menu')) hideCtxMenu()
})
ctxMenu.addEventListener('click', (e) => {
  const btn = e.target.closest('button')
  if (!btn) return
  const act = btn.dataset.act
  // 画布菜单分发(ctxNode 为空)
  if (ctxMenu.dataset.mode === 'canvas') {
    if (act === 'canvas-copy-link') {
      copyCanvasLink(ctxCanvasIndex)
    } else if (act === 'canvas-rename') {
      const c = canvasTabs[ctxCanvasIndex]
      if (c) {
        const nm = prompt('重命名画布', canvasName(ctxCanvasIndex))
        if (nm && nm.trim()) {
          c.name = nm.trim()
          persistAll()
          refreshTabBar()
          if (ctxCanvasIndex === docIndex) {
            docName = c.name
            nameEl.value = c.name
          }
        }
      }
    } else if (act === 'canvas-dup') {
      const c = canvasTabs[ctxCanvasIndex]
      if (c) {
        const copy = JSON.parse(JSON.stringify(c))
        copy.name = canvasName(ctxCanvasIndex) + ' 副本'
        canvasTabs.push(copy)
        persistAll()
        refreshTabBar()
      }
    } else if (act === 'canvas-del') {
      if (canvasTabs.length <= 1) {
        alert('至少保留一个画布')
      } else if (confirm('删除画布「' + canvasName(ctxCanvasIndex) + '」？此操作不可恢复。')) {
        canvasTabs.splice(ctxCanvasIndex, 1)
        if (ctxCanvasIndex === docIndex) {
          docIndex = Math.min(docIndex, canvasTabs.length - 1)
          loadDoc()
        } else if (ctxCanvasIndex < docIndex) {
          docIndex--
        }
        persistAll()
        refreshTabBar()
      }
    } else if (act === 'canvas-export') {
      exportCanvas(ctxCanvasIndex)
    }
    hideCtxMenu()
    return
  }
  if (!ctxNode) return
  const activeNode = ctxNode
  if (btn.dataset.sub) {
    // 子菜单: 设置节点样式(字体/字号)
    mind.execCommand('SET_NODE_STYLE', activeNode, btn.dataset.sub, btn.dataset.value)
    manualStyled.add(activeNode.getData('uid'))
    hideCtxMenu()
    saveDoc() // 立即落盘, 避免刷新/切换丢失
    return
  }
  if (act === 'insert-child') {
    if (activeNode) mind.execCommand('INSERT_CHILD_NODE', activeNode)
    else mind.execCommand('INSERT_CHILD_NODE')
  } else if (act === 'insert-sibling') {
    mind.execCommand('INSERT_NODE')
  } else if (act === 'edit') {
    if (activeNode) mind.execCommand('SET_NODE_ACTIVE', [activeNode])
    mind.renderer.startTextEdit()
  } else if (act === 'copy') {
    if (activeNode) mind.execCommand('SET_NODE_ACTIVE', [activeNode])
    mind.renderer.copy()
  } else if (act === 'cut') {
    if (activeNode) mind.execCommand('SET_NODE_ACTIVE', [activeNode])
    mind.execCommand('CUT_NODE')
  } else if (act === 'paste') {
    mind.renderer.paste()
  } else if (act === 'insert-image' || act === 'insert-audio' || act === 'insert-video' || act === 'insert-file') {
    const accept = act === 'insert-image' ? 'image/*' : act === 'insert-audio' ? 'audio/*' : act === 'insert-video' ? 'video/*' : '*/*'
    const inp = document.createElement('input')
    inp.type = 'file'
    inp.accept = accept
    inp.style.cssText = 'position:fixed;left:-9999px;top:0'
    document.body.appendChild(inp)
    inp.addEventListener('change', () => {
      const f = inp.files && inp.files[0]
      if (f && activeNode) insertFileToNode(activeNode, f)
      inp.remove()
    })
    inp.click()
  } else if (act === 'insert-link') {
    const url = prompt('输入链接地址:', activeNode.getData('hyperlink') || '')
    if (url && url.trim()) mind.execCommand('SET_NODE_HYPERLINK', activeNode, url.trim())
  } else if (act === 'insert-note') {
    const note = prompt('输入备注内容:', activeNode.getData('note') || '')
    if (note && note.trim()) mind.execCommand('SET_NODE_NOTE', activeNode, note.trim())
  } else if (act === 'font-color' || act === 'bg-color') {
    const prop = act === 'font-color' ? 'color' : 'fillColor'
    const cur = activeNode.getStyle(prop) || (prop === 'color' ? '#333333' : '#ffffff')
    const c = document.createElement('input')
    c.type = 'color'
    c.value = cur
    c.style.cssText = 'position:fixed;left:-9999px;top:0'
    document.body.appendChild(c)
    const apply = () => {
      mind.execCommand('SET_NODE_STYLE', activeNode, prop, c.value)
      manualStyled.add(activeNode.getData('uid'))
      scheduleBranchRainbow() // 立即重新着色, 但锁定节点保持手动样式
    }
    c.addEventListener('input', apply)
    c.addEventListener('change', () => {
      apply()
      hideCtxMenu()
      c.remove()
      saveDoc() // 立即落盘
    })
    c.click()
  } else if (act === 'collapse') {
    if (activeNode) mind.execCommand('SET_NODE_EXPAND', activeNode, false)
  } else if (act === 'expand') {
    if (activeNode) mind.execCommand('SET_NODE_EXPAND', activeNode, true)
  } else if (act === 'del-attachment') {
    if (activeNode) {
      // 删除附件 + 清除白色磨砂样式 + 解锁 -> 回归彩虹分支色
      mind.execCommand('SET_NODE_DATA', activeNode, { attachmentUrl: undefined, attachmentName: undefined, fillColor: undefined, borderColor: undefined, borderWidth: undefined, borderDasharray: undefined, paddingX: undefined, paddingY: undefined })
      manualStyled.delete(activeNode.getData('uid'))
      scheduleBranchRainbow()
      saveDoc()
    }
  } else if (act === 'del') {
    if (activeNode) {
      mind.execCommand('SET_NODE_ACTIVE', [activeNode])
      mind.execCommand('REMOVE_NODE')
    } else {
      mind.execCommand('REMOVE_CURRENT_NODE')
    }
  }
  hideCtxMenu()
})

// ---------- 顶栏按钮 ----------
document.getElementById('btn-undo').addEventListener('click', () => mind.execCommand('BACK'))
document.getElementById('btn-redo').addEventListener('click', () => mind.execCommand('FORWARD'))
document.getElementById('btn-save').addEventListener('click', () => saveDoc())
document.getElementById('btn-new').addEventListener('click', () => {
  saveDoc()
  canvasTabs.push(newDoc())
  docIndex = canvasTabs.length - 1
  loadDoc()
  refreshTabBar()
  markDirty()
})
document.getElementById('canvas-add').addEventListener('click', () => {
  saveDoc()
  canvasTabs.push(newDoc())
  docIndex = canvasTabs.length - 1
  loadDoc()
  refreshTabBar()
  markDirty()
})
document.getElementById('btn-open').addEventListener('click', () => {
  const raw = prompt('粘贴思维导图 JSON 数据(反向导入):')
  if (!raw) return
  try {
    const data = JSON.parse(raw)
    if (!data.root) throw new Error('缺少 root 字段')
    saveDoc()
    canvasTabs[docIndex].data = data
    loadDoc()
    markDirty()
  } catch (err) {
    alert('JSON 解析失败: ' + err.message)
  }
})
document.getElementById('btn-export').addEventListener('click', () => {
  const data = mind.getData()
  const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = (docName || '思维导图') + '.json'
  a.click()
  URL.revokeObjectURL(a.href)
})
document.getElementById('btn-collapse-all').addEventListener('click', () => {
  mind.execCommand('UNEXPAND_ALL')
})
document.getElementById('btn-expand-all').addEventListener('click', () => {
  mind.execCommand('EXPAND_ALL')
})
document.getElementById('btn-share').addEventListener('click', () => {
  alert('加密分享：构建部署时设置访问密码后生效(当前演示版为占位)')
})

// 文档名
nameEl.addEventListener('change', () => { docName = nameEl.value.trim() || '未命名'; markDirty() })
nameEl.addEventListener('dblclick', () => nameEl.focus())

// ---------- 底栏: 布局/缩放 ----------
const layoutSel = document.getElementById('view-layout')
function syncLayoutUI() {
  const layout = canvasTabs[docIndex].layout || 'logicalStructure'
  const v = LAYOUT_REV[layout] || 'r'
  layoutSel.value = v
  document.querySelectorAll('.layout-item').forEach((el) => {
    el.classList.toggle('active', el.dataset.layout === v)
  })
}
function applyLayout(layoutKey) {
  canvasTabs[docIndex].layout = layoutKey
  mind.setLayout(layoutKey)
  persistAll()
  syncLayoutUI()
}
layoutSel.addEventListener('change', () => {
  applyLayout(LAYOUT_MAP[layoutSel.value] || 'logicalStructure')
})
document.querySelectorAll('.layout-item').forEach((item) => {
  item.addEventListener('click', () => {
    applyLayout(LAYOUT_MAP[item.dataset.layout] || 'logicalStructure')
  })
})
document.getElementById('zoom-in').addEventListener('click', () => mind.view.enlarge())
document.getElementById('zoom-out').addEventListener('click', () => mind.view.narrow())
document.getElementById('view-fit').addEventListener('click', () => mind.view.fit())
function updateZoomVal() {
  const el = document.getElementById('zoom-val')
  if (el && mind) el.textContent = Math.round(mind.view.scale * 100) + '%'
}

// ---------- 侧栏: 主题/样式 ----------
const sp = document.getElementById('style-panel')
document.getElementById('btn-style').addEventListener('click', () => {
  sp.classList.toggle('hidden')
})
document.getElementById('sp-close').addEventListener('click', () => sp.classList.add('hidden'))
document.querySelectorAll('.sp-tab').forEach((tab) => {
  tab.addEventListener('click', () => {
    document.querySelectorAll('.sp-tab').forEach((t) => t.classList.remove('active'))
    tab.classList.add('active')
    document.querySelectorAll('.sp-page').forEach((p) => p.classList.add('hidden'))
    document.getElementById('sp-page-' + tab.dataset.tab).classList.remove('hidden')
  })
})

// 主题列表
function renderThemeList() {
  const wrap = document.getElementById('theme-list')
  wrap.innerHTML = ''
  THEMES.forEach((t) => {
    const d = document.createElement('div')
    d.className = 'theme-card'
    const c = t.config
    // 彩虹色块条: 8 段分支色
    const sw = document.createElement('div')
    sw.className = 'theme-swatches'
    ;(t.branchColors || THEMES[0].branchColors).forEach((bc) => {
      const seg = document.createElement('span')
      seg.className = 'theme-swatches-seg'
      seg.style.background = bc
      sw.appendChild(seg)
    })
    const label = document.createElement('div')
    label.className = 'theme-card-name'
    label.textContent = t.name
    d.appendChild(sw)
    d.appendChild(label)
    d.addEventListener('click', () => {
      canvasTabs[docIndex].theme = t.name
      mind.setTheme(t.name)
      if (mind.opt) {
        mind.opt.rainbowLinesConfig = { open: true, colorsList: t.branchColors || THEMES[0].branchColors }
      }
      persistAll()
      applyBranchRainbow()
      document.querySelectorAll('.theme-card').forEach((x) => x.classList.remove('active'))
      d.classList.add('active')
    })
    wrap.appendChild(d)
  })
}
renderThemeList()

// 背景色
document.getElementById('st-bgcolor').addEventListener('input', (e) => {
  mind.setThemeConfig({ backgroundColor: e.target.value })
  persistAll()
})

// 样式滑块(节点圆角/字号/连线粗细/边框)
function applyStylePatch() {
  const radius = document.getElementById('st-radius').value
  const font = document.getElementById('st-font').value
  const link = document.getElementById('st-link').value
  const border = document.getElementById('st-border').value
  document.getElementById('st-radius-val').textContent = radius + 'px'
  document.getElementById('st-font-val').textContent = font + 'px'
  document.getElementById('st-link-val').textContent = link + 'px'
  document.getElementById('st-border-val').textContent = border + 'px'
  mind.setThemeConfig({
    lineWidth: parseInt(link, 10),
    root: { borderRadius: parseInt(radius, 10) },
    second: { borderRadius: parseInt(radius, 10), fontSize: parseInt(font, 10), borderWidth: parseInt(border, 10) },
    node: { borderRadius: parseInt(radius, 10), fontSize: parseInt(font, 10), borderWidth: parseInt(border, 10) }
  })
  persistAll()
}
document.getElementById('st-radius').addEventListener('input', applyStylePatch)
document.getElementById('st-font').addEventListener('input', applyStylePatch)
document.getElementById('st-link').addEventListener('input', applyStylePatch)
document.getElementById('st-border').addEventListener('input', applyStylePatch)

// ---------- 图标(优先级/序号/表情) ----------
function setNodeIcon(icon) {
  const active = mind.renderer.activeNodeList
  if (!active || !active.length) {
    alert('请先选中一个节点')
    return
  }
  active.forEach((node) => {
    const cur = node.nodeData.icon || []
    const idx = cur.indexOf(icon)
    const next = idx >= 0 ? cur.filter((x) => x !== icon) : cur.concat([icon])
    mind.execCommand('SET_NODE_ICON', node, next)
  })
}
document.querySelectorAll('#icon-priority button, #icon-number button, #icon-emoji button').forEach((btn) => {
  btn.addEventListener('click', () => setNodeIcon(btn.dataset.icon))
})

// ---------- AI 助手 ----------
const ai = new AIAssistant(mind, {
  getSelectedNode: () => (mind.renderer.activeNodeList[0] || null)
})
document.getElementById('btn-ai').addEventListener('click', () => {
  document.getElementById('ai-panel').classList.remove('hidden')
})
ai.mount(document.getElementById('ai-panel'))

// ---------- 启动 ----------
loadFromStorage()
refreshTabBar()
createMind()

// ---------- 对外暴露(诊断) ----------
window.usmind = {
  get mind() { return mind },
  saveDoc,
  switchCanvas,
  paint: () => {},
  insertFileToNode
}
