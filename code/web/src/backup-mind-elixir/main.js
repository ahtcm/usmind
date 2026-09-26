// USMIND 思维导图编辑器 - 入口 (mind-elixir 2.1.0)
import MindElixir from 'mind-elixir'
import './style.css'
import { AIAssistant } from './ai.js'
import { THEMES, applyTheme, renderThemeList } from './theme.js'

const STORAGE_PREFIX = 'usmind.doc.'
const STATUS_SAVED = '已保存'
const STATUS_DIRTY = '未保存'

let currentCanvas = 0 // 当前画布索引
const canvasTabs = [] // 画布文档数据

// 默认示例数据
function defaultData() {
  return {
    nodeData: {
      id: 'root',
      topic: '中心主题',
      root: true,
      children: [
        {
          id: 'c1',
          topic: '分支一',
          children: [
            { id: 'c1a', topic: '子主题 A' },
            { id: 'c1b', topic: '子主题 B' }
          ]
        },
        {
          id: 'c2',
          topic: '分支二',
          children: [{ id: 'c2a', topic: '子主题 C' }]
        }
      ]
    },
    linkData: {}
  }
}

const mind = new MindElixir({
  el: '#map',
  editable: true,
  contextMenu: false,       // 自定义右键菜单
  draggable: true,
  keypress: false,          // 自定义快捷键体系
  allowUndo: false,         // 自实现撤销栈
  toolBar: false,
  direction: MindElixir.RIGHT, // 默认单右侧布局(对标 XMind 逻辑结构图)
  theme: {
    name: THEMES[0].name,
    palette: THEMES[0].palette.slice(),
    cssVar: Object.assign({}, THEMES[0].cssVar)
  }
})

let dirty = false
const docStatusEl = document.getElementById('doc-status')
const savedTimeEl = document.getElementById('doc-saved-time')
const docNameEl = document.getElementById('doc-name')

function setStatus(text) {
  docStatusEl.textContent = text
  docStatusEl.classList.toggle('warn', text === STATUS_DIRTY)
}

function updateSavedTime() {
  const d = new Date()
  const hh = String(d.getHours()).padStart(2, '0')
  const mm = String(d.getMinutes()).padStart(2, '0')
  savedTimeEl.textContent = '最近保存 ' + hh + ':' + mm
}

function markDirty() {
  dirty = true
  setStatus(STATUS_DIRTY)
}

// 读取/写入当前画布文档(localStorage)
function readCanvasData(index) {
  // 兼容旧版单画布数据 usmind.doc: 若存在(旧数据含徽章等), 优先迁移到画布1
  if (index === 0) {
    try {
      const legacy = localStorage.getItem('usmind.doc')
      if (legacy) {
        const data = JSON.parse(legacy)
        if (data && data.nodeData) {
          localStorage.setItem(STORAGE_PREFIX + '0', legacy)
          localStorage.removeItem('usmind.doc')
          return data
        }
      }
    } catch (e) { /* ignore */ }
  }
  try {
    const raw = localStorage.getItem(STORAGE_PREFIX + index)
    if (raw) {
      const data = JSON.parse(raw)
      if (data && data.nodeData) return data
    }
  } catch (e) { /* ignore */ }
  return null
}
function writeCanvasData(index, data) {
  localStorage.setItem(STORAGE_PREFIX + index, JSON.stringify(data))
}

function loadDoc() {
  const data = readCanvasData(currentCanvas) || defaultData()
  mind.refresh(data)
  refreshDecor()
  paintLinks()
}

function saveDoc() {
  try {
    const data = mind.getData()
    writeCanvasData(currentCanvas, data)
    dirty = false
    setStatus(STATUS_SAVED)
    updateSavedTime()
  } catch (e) {
    alert('保存失败: ' + e.message)
  }
}

function exportJson() {
  const data = mind.getData()
  const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = 'usmind-' + Date.now() + '.json'
  a.click()
  URL.revokeObjectURL(a.href)
}

// 全部折叠/展开: 遍历数据设置 expanded 字段后刷新
function setAllExpanded(expanded) {
  const walk = (node) => {
    if (node.children && node.children.length) {
      node.expanded = expanded
      node.children.forEach(walk)
    }
  }
  const data = mind.getData()
  walk(data.nodeData)
  mind.refresh(data)
  refreshDecor()
  paintLinks()
}

// ---------- 多画布标签 ----------
function renderCanvasTabs() {
  const bar = document.getElementById('canvas-bar')
  bar.querySelectorAll('.canvas-tab').forEach((t) => t.remove())
  let n = 0
  while (readCanvasData(n) || n <= currentCanvas) {
    const tab = document.createElement('span')
    tab.className = 'canvas-tab' + (n === currentCanvas ? ' active' : '')
    tab.dataset.canvas = String(n)
    tab.textContent = '画布' + (n + 1)
    bar.insertBefore(tab, bar.querySelector('#canvas-add'))
    n++
    if (n > 20) break
  }
}
function switchCanvas(index) {
  saveDoc()
  currentCanvas = index
  loadDoc()
  dirty = false
  setStatus(STATUS_SAVED)
  renderCanvasTabs()
  requestAnimationFrame(() => setTimeout(() => mind.toCenter(), 80))
}
document.getElementById('canvas-bar').addEventListener('click', (e) => {
  const tab = e.target.closest('.canvas-tab')
  if (tab && parseInt(tab.dataset.canvas, 10) !== currentCanvas) {
    switchCanvas(parseInt(tab.dataset.canvas, 10))
  }
})
document.getElementById('canvas-add').addEventListener('click', () => {
  saveDoc()
  let n = 0
  while (readCanvasData(n)) n++
  currentCanvas = n
  writeCanvasData(currentCanvas, defaultData())
  mind.refresh(defaultData())
  dirty = false
  setStatus(STATUS_SAVED)
  renderCanvasTabs()
  requestAnimationFrame(() => setTimeout(() => { mind.toCenter(); paintLinks() }, 80))
})

// ---------- 缩放与视图控制 ----------
function refreshZoomLabel() {
  document.getElementById('zoom-val').textContent = Math.round(mind.scaleVal * 100) + '%'
}
function relayoutAndPaint(fn) {
  fn()
  requestAnimationFrame(() => paintLinks())
}
document.getElementById('zoom-in').addEventListener('click', () => {
  if (mind.scaleVal < 2) mind.scale(mind.scaleVal + 0.1)
  refreshZoomLabel()
})
document.getElementById('zoom-out').addEventListener('click', () => {
  if (mind.scaleVal > 0.5) mind.scale(mind.scaleVal - 0.1)
  refreshZoomLabel()
})
document.getElementById('view-fit').addEventListener('click', () => {
  mind.scale(1)
  refreshZoomLabel()
  mind.toCenter()
})
document.getElementById('view-layout').addEventListener('change', (e) => {
  const fn = layoutMap[e.target.value]
  if (fn) { fn(); markDirty() }
  syncLayoutActive(e.target.value)
  requestAnimationFrame(() => paintLinks())
})
function syncLayoutActive(layout) {
  document.querySelectorAll('#sp-page-layout .layout-item').forEach((i) => {
    i.classList.toggle('active', i.dataset.layout === layout)
  })
}
function syncViewLayoutSelect() {
  const sel = document.getElementById('view-layout')
  const d = mind.direction
  sel.value = d === 0 ? 'l' : (d === 1 ? 'r' : 'side')
}

// ---------- 文件名与分享 ----------
let docName = localStorage.getItem('usmind.docname') || '未命名'
docNameEl.value = docName
document.title = docName + ' - USMIND 思维导图'
docNameEl.addEventListener('change', () => {
  const name = docNameEl.value.trim() || '未命名'
  docNameEl.value = name
  localStorage.setItem('usmind.docname', name)
  document.title = name + ' - USMIND 思维导图'
})
document.getElementById('btn-share').addEventListener('click', () => {
  alert('加密分享功能即将上线：部署后可设置访问密码，未输入正确密码的访客无法查看导图内容。')
})

// ---------- 工具栏绑定 ----------
document.getElementById('btn-new').addEventListener('click', () => {
  if (!confirm('新建将清空当前画布，确定？')) return
  mind.refresh(defaultData())
  markDirty()
  requestAnimationFrame(() => paintLinks())
})
document.getElementById('btn-open').addEventListener('click', () => {
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = '.json'
  input.onchange = () => {
    const file = input.files[0]
    if (!file) return
    const reader = new FileReader()
    reader.onload = () => {
      try {
        const data = JSON.parse(reader.result)
        if (!data || !data.nodeData) throw new Error('缺少 nodeData')
        mind.refresh(data)
        markDirty()
        requestAnimationFrame(() => paintLinks())
      } catch (e) {
        alert('文件格式错误: ' + e.message)
      }
    }
    reader.readAsText(file)
  }
  input.click()
})
document.getElementById('btn-save').addEventListener('click', saveDoc)
document.getElementById('btn-export').addEventListener('click', exportJson)
document.getElementById('btn-undo').addEventListener('click', undo)
// 2.1.0 无内置 redo, 禁用按钮
document.getElementById('btn-redo').disabled = true
document.getElementById('btn-collapse-all').addEventListener('click', () => setAllExpanded(false))
document.getElementById('btn-expand-all').addEventListener('click', () => setAllExpanded(true))

// ---------- 自实现撤销栈(最大步数可配置) ----------
const undoStack = []
let undoLimit = 50 // 可在 EXE 面板/此值自定义
// 手动克隆节点树: mind 的 nodeObj 带 parent 循环引用, 不能 JSON.stringify
function cloneTree(node) {
  const c = { id: node.id, topic: node.topic }
  if (node.icons && node.icons.length) c.icons = node.icons.slice()
  if (node.expanded !== undefined) c.expanded = node.expanded
  if (node.children && node.children.length) c.children = node.children.map(cloneTree)
  return c
}
function pushUndo() {
  const d = mind.getData()
  undoStack.push(JSON.stringify({ nodeData: cloneTree(d.nodeData), linkData: d.linkData || {} }))
  if (undoStack.length > undoLimit) undoStack.shift()
}
function undo() {
  if (!undoStack.length) return
  const data = JSON.parse(undoStack.pop())
  mind.refresh(data)
  refreshDecor()
  paintLinks()
  markDirty()
}

// ---------- 节点增删改(操作数据 + refresh) ----------
let clipSeq = 0
function genId() {
  return 'n' + Date.now().toString(36) + (++clipSeq).toString(36)
}
function cloneNode(node) {
  const c = { id: genId(), topic: node.topic }
  if (node.icons && node.icons.length) c.icons = node.icons.slice()
  if (node.expanded !== undefined) c.expanded = node.expanded
  if (node.children && node.children.length) c.children = node.children.map(cloneNode)
  return c
}
function findNodeById(node, id) {
  if (!node) return null
  if (node.id === id) return node
  for (const c of node.children || []) {
    const r = findNodeById(c, id)
    if (r) return r
  }
  return null
}
function findParent(data, id, parent) {
  const children = parent.children || []
  for (const c of children) {
    if (c.id === id) return parent
    const r = findParent(data, id, c)
    if (r) return r
  }
  return null
}
function refreshAll(data) {
  // refresh(data) 会把传入树替换为内部树, 必须传修改过的同一个对象
  mind.refresh(data)
  refreshDecor()
  paintLinks()
}
function insertChildNode(nodeObj) {
  pushUndo()
  const data = mind.getData()
  const target = findNodeById(data.nodeData, nodeObj.id)
  if (!target) { console.warn('[insertChild] node not found', nodeObj.id); return }
  target.children = target.children || []
  target.children.push({ id: genId(), topic: '新主题' })
  refreshAll(data)
  markDirty()
}
function insertSiblingNode(nodeObj) {
  if (nodeObj.root) { alert('中心主题无法插入同级'); return }
  pushUndo()
  const data = mind.getData()
  const parent = findParent(data, nodeObj.id, data.nodeData)
  if (!parent) { console.warn('[insertSibling] parent not found, id=', nodeObj.id); return }
  const list = parent.children || []
  const i = list.findIndex((c) => c.id === nodeObj.id)
  list.splice(i + 1, 0, { id: genId(), topic: '新主题' })
  refreshAll(data)
  markDirty()
}
function removeNodeByObj(nodeObj) {
  if (nodeObj.root) { alert('中心主题无法删除'); return }
  pushUndo()
  const data = mind.getData()
  const parent = findParent(data, nodeObj.id, data.nodeData)
  if (!parent) { console.warn('[removeNode] parent not found, id=', nodeObj.id); return }
  const list = parent.children || []
  const i = list.findIndex((c) => c.id === nodeObj.id)
  if (i >= 0) list.splice(i, 1)
  refreshAll(data)
  markDirty()
}
// 编辑节点: mind.beginEdit 接收 me-tpc 元素(不是 nodeObj)
function beginEditNode(nodeObj) {
  const el = findTopicEl(nodeObj.id)
  if (el) mind.beginEdit(el)
}
// 复制/剪切/粘贴(含全部子节点)
let clipboard = null
let clipMode = 'copy'
function copyNodeToClip(nodeObj) {
  const data = mind.getData()
  const target = findNodeById(data.nodeData, nodeObj.id)
  if (!target) return
  clipboard = cloneTree(target) // 含全部子节点, 无循环引用
  clipMode = 'copy'
  document.getElementById('ctx-paste').disabled = false
}
function cutNodeToClip(nodeObj) {
  copyNodeToClip(nodeObj)
  clipMode = 'cut'
  if (!nodeObj.root) removeNodeByObj(nodeObj)
}
function pasteInto(nodeObj) {
  if (!clipboard) return
  pushUndo()
  const data = mind.getData()
  const target = findNodeById(data.nodeData, nodeObj.id)
  if (!target) { console.warn('[pasteInto] node not found', nodeObj.id); return }
  target.children = target.children || []
  target.children.push(cloneNode(clipboard)) // 统一克隆, 避免 id 冲突
  refreshAll(data)
  markDirty()
  if (clipMode === 'cut') clipboard = null // 剪切只可粘一次
  document.getElementById('ctx-paste').disabled = !clipboard
}

// ---------- 自定义右键菜单 ----------
const ctxMenu = document.getElementById('ctx-menu')
// ---------- 右键按住拖拽框选 ----------
const boxSel = { active: false, moved: false, x0: 0, y0: 0, x1: 0, y1: 0, el: null, nodes: [] }
function clearBoxSelection() {
  document.querySelectorAll('#map me-tpc.selected').forEach((t) => t.classList.remove('selected'))
  boxSel.nodes = []
}
// 右键按下(捕获阶段): 阻止 mind 的画布平移(H.mousedown), 记录起点
document.getElementById('map').addEventListener('mousedown', (e) => {
  if (e.button !== 2) return
  e.stopPropagation() // 阻止 mind z.map mousedown -> H 平移
  boxSel.active = true
  boxSel.moved = false
  boxSel.x0 = e.clientX
  boxSel.y0 = e.clientY
  const d = document.createElement('div')
  d.className = 'usmind-boxsel'
  document.body.appendChild(d)
  boxSel.el = d
}, true) // 捕获阶段
document.getElementById('map').addEventListener('mousemove', (e) => {
  if (!boxSel.active) return
  if (!boxSel.moved) {
    const dx = e.clientX - boxSel.x0
    const dy = e.clientY - boxSel.y0
    if (dx * dx + dy * dy < 49) return // 7px 阈值
    boxSel.moved = true
  }
  boxSel.x1 = e.clientX
  boxSel.y1 = e.clientY
  const x = Math.min(boxSel.x0, boxSel.x1)
  const y = Math.min(boxSel.y0, boxSel.y1)
  const el = boxSel.el
  el.style.left = x + 'px'
  el.style.top = y + 'px'
  el.style.width = Math.abs(boxSel.x1 - boxSel.x0) + 'px'
  el.style.height = Math.abs(boxSel.y1 - boxSel.y0) + 'px'
})
document.addEventListener('mouseup', (e) => {
  if (e.button !== 2 || !boxSel.active) return
  boxSel.active = false
  if (boxSel.el) {
    boxSel.el.remove()
    boxSel.el = null
  }
  if (!boxSel.moved) return // 右键单击: 交给 contextmenu 正常弹菜单
  const x = Math.min(boxSel.x0, boxSel.x1)
  const y = Math.min(boxSel.y0, boxSel.y1)
  const w = Math.abs(boxSel.x1 - boxSel.x0)
  const h = Math.abs(boxSel.y1 - boxSel.y0)
  const hit = []
  document.querySelectorAll('#map me-tpc').forEach((t) => {
    const r = t.getBoundingClientRect()
    if (r.left < x + w && r.right > x && r.top < y + h && r.bottom > y) hit.push(t)
  })
  if (hit.length) {
    clearBoxSelection()
    hit.forEach((t) => t.classList.add('selected'))
    boxSel.nodes = hit.map((t) => t.nodeObj)
    try { mind.currentNode = hit[hit.length - 1] } catch (err) { /* ignore */ }
  }
})

let ctxNode = null // 右键命中的节点对象
function showCtxMenu(x, y, nodeObj) {
  ctxNode = nodeObj
  ctxMenu.classList.remove('hidden')
  ctxMenu.style.left = Math.min(x, window.innerWidth - 220) + 'px'
  ctxMenu.style.top = Math.min(y, window.innerHeight - 320) + 'px'
}
function hideCtxMenu() {
  ctxMenu.classList.add('hidden')
  ctxNode = null
}
document.getElementById('map').addEventListener('contextmenu', (e) => {
  e.preventDefault()
  if (boxSel.moved) { boxSel.moved = false; return } // 框选拖拽后不弹菜单
  const topicEl = e.target.closest('me-tpc')
  if (!topicEl) { hideCtxMenu(); return }
  // mind 渲染时把节点对象挂在 me-tpc 元素上(getObjById 在该版本有 bug, 不用)
  const nodeObj = topicEl.nodeObj || null
  if (!nodeObj) { hideCtxMenu(); return }
  try { mind.selectNode(nodeObj) } catch (err) { /* ignore */ }
  document.getElementById('ctx-paste').disabled = !clipboard
  showCtxMenu(e.clientX, e.clientY, nodeObj)
})
document.addEventListener('click', (e) => {
  if (!e.target.closest('#ctx-menu')) hideCtxMenu()
})
// 递归设置整棵子树的展开状态(一键收起/展开多级子内容)
function setSubtreeExpanded(nodeObj, expanded) {
  if (!nodeObj) return
  nodeObj.expanded = expanded
  ;(nodeObj.children || []).forEach((c) => setSubtreeExpanded(c, expanded))
}

ctxMenu.addEventListener('click', (e) => {
  const btn = e.target.closest('button')
  if (!btn || !ctxNode) return
  const act = btn.dataset.act
  if (act === 'insert-child') insertChildNode(ctxNode)
  else if (act === 'insert-sibling') insertSiblingNode(ctxNode)
  else if (act === 'edit') beginEditNode(ctxNode)
  else if (act === 'copy') copyNodeToClip(ctxNode)
  else if (act === 'cut') cutNodeToClip(ctxNode)
  else if (act === 'paste') pasteInto(ctxNode)
  else if (act === 'collapse') { const el = findTopicEl(ctxNode.id); if (el) { setSubtreeExpanded(el.nodeObj, false); mind.expandNode(el, false) } }
  else if (act === 'expand') { const el = findTopicEl(ctxNode.id); if (el) { setSubtreeExpanded(el.nodeObj, true); mind.expandNode(el, true) } }
  else if (act === 'del') {
    if (boxSel.nodes.length > 1) {
      const list = boxSel.nodes.slice()
      clearBoxSelection()
      list.forEach((n) => { if (n && n.id !== 'root') removeNodeByObj(n) })
    } else removeNodeByObj(ctxNode)
  }
  hideCtxMenu()
})

// ---------- 自定义快捷键体系 ----------
document.addEventListener('keydown', (e) => {
  const tag = (e.target.tagName || '').toLowerCase()
  if (tag === 'input' || tag === 'textarea') return // 输入框内不触发
  const ctrl = e.ctrlKey || e.metaKey
  const key = e.key.toLowerCase()
  if (ctrl && key === 's') { e.preventDefault(); saveDoc(); return }
  if (ctrl && key === 'z') { e.preventDefault(); undo(); return }
  const node = mind.currentNode
  const nodeObj = node ? node.nodeObj : null
  if (ctrl && key === 'c' && nodeObj) { copyNodeToClip(nodeObj); return }
  if (ctrl && key === 'x' && nodeObj) { cutNodeToClip(nodeObj); return }
  if (ctrl && key === 'v' && nodeObj && clipboard) { pasteInto(nodeObj); return }
  if (key === 'tab') { e.preventDefault(); if (nodeObj) insertChildNode(nodeObj); return }
  if (key === 'enter') { e.preventDefault(); if (nodeObj) insertSiblingNode(nodeObj); return }
  if (key === 'f2') { e.preventDefault(); if (nodeObj) beginEditNode(nodeObj); return }
  if (key === 'delete' || key === 'backspace') { e.preventDefault(); if (nodeObj) removeNodeByObj(nodeObj); return }
})

// ---------- 综合样式侧栏(样式/主题/布局/图标) ----------
const sp = document.getElementById('style-panel')
function setPanel(show) {
  sp.classList.toggle('hidden', !show)
}
document.getElementById('btn-style').addEventListener('click', () => {
  setPanel(sp.classList.contains('hidden'))
})
document.getElementById('sp-close').addEventListener('click', () => setPanel(false))

// 标签切换
document.querySelectorAll('.sp-tab').forEach((tab) => {
  tab.addEventListener('click', () => {
    document.querySelectorAll('.sp-tab').forEach((t) => t.classList.remove('active'))
    tab.classList.add('active')
    document.querySelectorAll('.sp-page').forEach((p) => p.classList.add('hidden'))
    document.getElementById('sp-page-' + tab.dataset.tab).classList.remove('hidden')
  })
})

// 布局方向映射
const layoutMap = { l: () => mind.initLeft(), side: () => mind.initSide(), r: () => mind.initRight() }

// --- 样式页: 节点圆角/字号/连线粗细/边框 ---
function applyStyleVars() {
  const mapEl = document.getElementById('map')
  const radius = parseInt(document.getElementById('st-radius').value, 10)
  const font = parseInt(document.getElementById('st-font').value, 10)
  const link = parseInt(document.getElementById('st-link').value, 10)
  const border = parseInt(document.getElementById('st-border').value, 10)
  mapEl.style.setProperty('--usmind-radius', radius + 'px')
  mapEl.style.setProperty('--usmind-root-radius', (radius + 12) + 'px')
  mapEl.style.setProperty('--usmind-font', font + 'px')
  mapEl.style.setProperty('--usmind-link-w', link + 'px')
  mapEl.style.setProperty('--usmind-border-w', border + 'px')
  document.getElementById('st-radius-val').textContent = radius + 'px'
  document.getElementById('st-font-val').textContent = font + 'px'
  document.getElementById('st-link-val').textContent = link + 'px'
  document.getElementById('st-border-val').textContent = border + 'px'
  // CSS 变量实时生效, 无需 refresh(避免 fillParent 崩溃)
}
document.querySelectorAll('#sp-page-style input[type=range]').forEach((el) => {
  el.addEventListener('input', applyStyleVars)
})

// --- 主题页 ---
function renderThemes() {
  const current = document.getElementById('map').getAttribute('data-theme') || THEMES[0].name
  renderThemeList((themeName) => {
    applyTheme(mind, themeName)
    document.querySelector('.map-canvas').style.background = ''
    const t = THEMES.find((x) => x.name === themeName)
    if (t) document.getElementById('st-bgcolor').value = t.cssVar['--bgcolor']
    markDirty()
    refreshDecor()
    paintLinks()
    renderThemes()
  }, current)
}
document.getElementById('st-bgcolor').addEventListener('input', (e) => {
  document.querySelector('.map-canvas').style.background = e.target.value
})
document.getElementById('st-mix').addEventListener('click', () => {
  // 一键搭配: 随机主题 + 随机布局
  const t = THEMES[Math.floor(Math.random() * THEMES.length)]
  applyTheme(mind, t.name)
  document.querySelector('.map-canvas').style.background = ''
  document.getElementById('st-bgcolor').value = t.cssVar['--bgcolor']
  const layouts = ['l', 'side', 'r']
  const fn = layoutMap[layouts[Math.floor(Math.random() * layouts.length)]]
  if (fn) { fn(); markDirty() }
  refreshDecor()
  requestAnimationFrame(() => paintLinks())
  renderThemes()
})

// --- 布局页 ---
document.querySelectorAll('#sp-page-layout .layout-item').forEach((item) => {
  item.addEventListener('click', () => {
    document.querySelectorAll('#sp-page-layout .layout-item').forEach((i) => i.classList.remove('active'))
    item.classList.add('active')
    const fn = layoutMap[item.dataset.layout]
    if (fn) { fn(); markDirty() }
    syncViewLayoutSelect()
    requestAnimationFrame(() => paintLinks())
  })
})

// --- 图标页: 选中节点添加/移除优先级、序号标记 ---
function renderIconsOnNode(topicEl) {
  const obj = topicEl.nodeObj
  const parentEl = topicEl.parentElement
  parentEl.querySelectorAll('.usmind-badge').forEach((b) => b.remove())
  if (!obj.icons || !obj.icons.length) return
  obj.icons.forEach((icon) => {
    const span = document.createElement('span')
    const isEmoji = /[^\x00-\x7F]/.test(icon) && !/^P\d$/.test(icon)
    span.className = 'usmind-badge ' + (icon.startsWith('P') ? 'prio-' + icon : (isEmoji ? 'emoji' : 'num'))
    span.textContent = icon
    parentEl.insertBefore(span, topicEl)
  })
}
function toggleNodeIcon(icon) {
  const node = mind.currentNode
  if (!node) { alert('请先选中一个节点，再点击图标'); return }
  const obj = node.nodeObj
  obj.icons = obj.icons || []
  const idx = obj.icons.indexOf(icon)
  if (idx >= 0) obj.icons.splice(idx, 1)
  else obj.icons.push(icon)
  renderIconsOnNode(node)
  markDirty()
}
document.querySelectorAll('.icon-grid button').forEach((btn) => {
  btn.addEventListener('click', () => toggleNodeIcon(btn.dataset.icon))
})
// 图标徽章 + 分支配色边框: 任何 refresh 后调用
// 注意: 不用 MindElixir.E(打包后模块作用域不可靠), 改用 DOM 查询
function findTopicEl(id) {
  return document.querySelector('me-tpc[data-nodeid="me' + id + '"]')
}
function restoreIcons() {
  const walk = (node) => {
    if (node.icons && node.icons.length) {
      const el = findTopicEl(node.id)
      if (el) renderIconsOnNode(el)
    }
    ;(node.children || []).forEach(walk)
  }
  try { walk(mind.getData().nodeData) } catch (e) { /* ignore */ }
}
function decorateNodes() {
  const themeName = document.getElementById('map').getAttribute('data-theme') || THEMES[0].name
  const t = THEMES.find((x) => x.name === themeName)
  if (!t) return
  const colors = t.palette
  const walk = (node, parentColor) => {
    const children = node.children || []
    children.forEach((c, i) => {
      const color = parentColor || colors[i % colors.length]
      const el = findTopicEl(c.id)
      if (el && el.parentElement) {
        el.parentElement.style.setProperty('--node-color', color)
        el.parentElement.style.borderColor = color + '99'
      }
      walk(c, color)
    })
  }
  try { walk(mind.getData().nodeData, '') } catch (e) { /* ignore */ }
}
function refreshDecor() {
  restoreIcons()
  decorateNodes()
}

// ---------- 自定义连线(XMind 风格, 单侧布局下不交叉) ----------
// 起始点 = 父框子侧边中点, 终点 = 子框子侧边中点, 两端水平切线圆滑, 不穿进框子
function paintLinks() {
  const lines = mind.lines
  const canvas = document.querySelector('#map .map-canvas')
  if (!lines || !canvas) return
  lines.innerHTML = ''
  // 清除 mind 原生子线(每个 me-parent 内的 subLines svg), 避免与自定义线叠加
  // subLines 挂在 me-wrapper 下(mind 原生), 需按类名全清
  document.querySelectorAll('#map svg.subLines').forEach((s) => s.remove())
  const cr = canvas.getBoundingClientRect()
  const pos = (el) => {
    const r = el.getBoundingClientRect()
    return { x: r.left - cr.left, y: r.top - cr.top, w: r.width, h: r.height }
  }
  const NS = 'http://www.w3.org/2000/svg'
  const themeName = document.getElementById('map').getAttribute('data-theme') || THEMES[0].name
  const t = THEMES.find((x) => x.name === themeName) || THEMES[0]
  const colors = t.palette
  const linkW = parseFloat(getComputedStyle(document.getElementById('map')).getPropertyValue('--usmind-link-w')) || 2
  const allTpcs = document.querySelectorAll('#map me-tpc')
  const linkColorMap = new Map() // nodeId -> 分支色(不污染数据)
  let branchIdx = 0
  allTpcs.forEach((tpc) => {
    const node = tpc.nodeObj
    if (!node) return
    const childEl = tpc.parentElement // me-parent
    if (!childEl) return
    const parentNode = node.root ? null : (node.parent || null)
    if (!parentNode) return
    const parentTpc = parentNode ? findTopicEl(parentNode.id) : null
    const parentEl = parentTpc ? parentTpc.parentElement : document.querySelector('me-root')
    if (!parentEl) return
    // 颜色: 一级分支按 palette 分配, 子孙继承
    let color = linkColorMap.get(node.id)
    if (!color) {
      if (parentNode.root) {
        color = colors[branchIdx++ % colors.length]
        linkColorMap.set(node.id, color)
      } else {
        color = linkColorMap.get(parentNode.id)
        if (color) linkColorMap.set(node.id, color)
      }
    }
    const p = pos(parentEl)
    const c = pos(childEl)
    // 折叠/隐藏的节点 rect 全为 0 -> 跳过, 避免线画到左上角或消失
    if (c.w === 0 || c.h === 0 || p.w === 0 || p.h === 0) return
    // 纯几何判断方向: 子节点在父左侧则为左向
    const childOnLeft = c.x + c.w / 2 < p.x + p.w / 2
    const pY = p.y + p.h / 2 // 起始点: 父框子侧边中点
    const cY = c.y + c.h / 2 // 终点: 子框子侧边中点
    const x1 = childOnLeft ? p.x : p.x + p.w // 父节点朝向子的侧边
    const x2 = childOnLeft ? c.x + c.w : c.x // 子节点朝向父的侧边
    const dist = Math.abs(x2 - x1)
    const bend = Math.max(24, Math.min(dist * 0.42, 130))
    const dir = childOnLeft ? -1 : 1
    const d = 'M ' + x1 + ' ' + pY + ' C ' + (x1 + dir * bend) + ' ' + pY + ' ' + (x2 - dir * bend) + ' ' + cY + ' ' + x2 + ' ' + cY
    const path = document.createElementNS(NS, 'path')
    path.setAttribute('d', d)
    path.setAttribute('fill', 'none')
    path.setAttribute('stroke', color + 'cc')
    path.setAttribute('stroke-width', String(linkW))
    lines.appendChild(path)
  })
}

// Ctrl+S 保存
document.addEventListener('keydown', (e) => {
  if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 's') {
    e.preventDefault()
    saveDoc()
  }
})

// 编辑器变更事件 -> 标记未保存 + 重绘连线(拖拽移动节点等)
mind.bus.addListener('operation', () => {
  markDirty()
  requestAnimationFrame(() => paintLinks())
})

// ---------- AI 助手 ----------
const ai = new AIAssistant(mind, {
  getSelectedNode: () => mind.currentNode
})
document.getElementById('btn-ai').addEventListener('click', () => {
  document.getElementById('ai-panel').classList.remove('hidden')
})
ai.mount(document.getElementById('ai-panel'))

// ---------- 初始化 ----------
window.usmind = { mind, refreshDecor, insertChildNode, insertSiblingNode, pasteInto, copyNodeToClip, paintLinks } // 提前暴露(供 AI 模块与诊断使用)
mind.init()
applyTheme(mind, THEMES[0].name) // 应用默认主题(深空黑), 同时设置 data-theme
loadDoc()
// init 时布局未完成导致 toCenter 失效, 延迟补一次确保画布居中
requestAnimationFrame(() => setTimeout(() => { mind.toCenter(); paintLinks() }, 100))
renderThemes()
applyStyleVars()
requestAnimationFrame(() => { refreshDecor(); paintLinks() })
renderCanvasTabs()
syncViewLayoutSelect()
setStatus(STATUS_SAVED)
