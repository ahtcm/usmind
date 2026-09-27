<template>
  <div class="share-root">
    <!-- 密码弹窗 -->
    <div v-if="needPwd" class="pwd-mask">
      <div class="pwd-box">
        <h3>此分享需要密码</h3>
        <input type="password" v-model="pwd" placeholder="输入访问密码" @keyup.enter="loadShare" />
        <button @click="loadShare">查看导图</button>
        <div v-if="pwdErr" class="err">{{ pwdErr }}</div>
      </div>
    </div>

    <template v-else>
      <!-- 顶部只读工具栏 -->
      <div id="toolbar" class="toolbar" style="justify-content: center">
        <div class="toolbar-left">
          <span class="doc-name" style="width:auto;cursor:default">{{ shareName }}</span>
          <span class="status">只读</span>
        </div>
      </div>
      <div class="share-bg">
        <div class="share-bg-color" :style="{ background: bgColorVal }"></div>
        <div v-show="!bgVideoVal" class="share-bg-image" :style="{ backgroundImage: bgImageVal ? 'url(' + bgImageVal + ')' : 'none', opacity: bgOpacityVal }"></div>
        <video v-if="bgVideoVal" class="share-bg-video" :src="bgVideoVal" autoplay loop muted playsinline :style="{ opacity: bgOpacityVal }"></video>
        <div id="map" ref="mapEl"></div>
      </div>
      <div v-if="pageLoading" class="page-loading">
        <div class="pl-spinner"></div>
        <div class="pl-text">加载中...</div>
      </div>
      <div class="bottom-bar">
        <div class="view-bar" style="margin-left:auto; margin-right:14px">
          <span class="view-zoom">
            <button @click="zoomOut">−</button>
            <span>{{ zoomPct }}</span>
            <button @click="zoomIn">+</button>
          </span>
          <button @click="zoomFit">⤢</button>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { loadMathJax, migrateKatexToSvg } from '../utils/mathRenderer'
import { useRoute } from 'vue-router'
import 'katex/dist/katex.min.css'
import CodeMirror from 'codemirror'
import 'codemirror/lib/codemirror.css'
import 'codemirror/mode/javascript/javascript'
import 'codemirror/mode/xml/xml'
import 'codemirror/mode/css/css'
import 'codemirror/mode/htmlmixed/htmlmixed'
import 'codemirror/mode/markdown/markdown'
import 'codemirror/mode/python/python'
import 'codemirror/mode/clike/clike'
import 'codemirror/mode/go/go'
import 'codemirror/mode/rust/rust'
import 'codemirror/mode/php/php'
import 'codemirror/mode/shell/shell'
import 'codemirror/mode/sql/sql'
import 'codemirror/mode/ruby/ruby'
import 'codemirror/mode/yaml/yaml'
import 'codemirror/mode/swift/swift'
import MindMap from 'simple-mind-map'
import Drag from 'simple-mind-map/src/plugins/Drag'
import Select from 'simple-mind-map/src/plugins/Select'
import RainbowLines from 'simple-mind-map/src/plugins/RainbowLines'
import Scrollbar from 'simple-mind-map/src/plugins/Scrollbar'
import { THEMES } from '../utils/theme.js'
import { fetchThemes } from '../api/themes'
import { fetchPreviewConfig } from '../api/previewConfig'
import { mindmapApi } from '../api/mindmap'

MindMap.usePlugin(Drag)
MindMap.usePlugin(Select)
MindMap.usePlugin(RainbowLines)
MindMap.usePlugin(Scrollbar)
THEMES.forEach(t => { try { MindMap.defineTheme(t.name, t.config) } catch(e){} })

const route = useRoute()
const mapEl = ref(null)
const mind = ref(null)
const needPwd = ref(false)
const pwd = ref('')
const pwdErr = ref('')
const shareName = ref('')
const zoomPct = ref('100%')
const bgColorVal = ref('#fafbff')
const bgImageVal = ref('')
const bgVideoVal = ref('')
const bgOpacityVal = ref(1)
let remoteThemes = []
const pageLoading = ref(true)

function themeConfigOf(name) {
  const all = [...THEMES, ...remoteThemes]
  const t = all.find(x => x.name === name) || THEMES[0]
  return (t && t.config) || {}
}
function applyShareBg(name) {
  const cfg = themeConfigOf(name)
  bgColorVal.value = cfg.backgroundColor || '#fafbff'
  bgImageVal.value = (cfg.backgroundImage && cfg.backgroundImage !== 'none') ? cfg.backgroundImage : ''
  bgVideoVal.value = (cfg.backgroundVideo && cfg.backgroundVideo !== 'none') ? cfg.backgroundVideo : ''
  bgOpacityVal.value = (typeof cfg.backgroundOpacity === 'number') ? cfg.backgroundOpacity : 1
  if (mapEl.value) mapEl.value.style.background = 'transparent'
  updateBgSize()
}
let bgImgLoader = null
let shareBgZoom = 1.35
function updateBgSize() {
  const img = document.querySelector('.share-bg-image')
  const url = bgImageVal.value
  if (!img || !url) return
  if (bgImgLoader) { bgImgLoader.onload = null; bgImgLoader = null }
  bgImgLoader = new Image()
  bgImgLoader.src = url
  bgImgLoader.onload = () => {
    const vw = img.clientWidth || window.innerWidth
    const vh = img.clientHeight || window.innerHeight
    if (!vw || !vh || !bgImgLoader) return
    const scale = Math.max(vw / bgImgLoader.naturalWidth, vh / bgImgLoader.naturalHeight) * shareBgZoom
    img.style.backgroundSize = Math.round(bgImgLoader.naturalWidth * scale) + 'px ' + Math.round(bgImgLoader.naturalHeight * scale) + 'px'
    img.style.backgroundPosition = 'center center'
  }
}
// 预览/高亮类型配置：后台可配，内置默认兜底
const CODE_MODE_DEFAULT = { js:'text/javascript', mjs:'text/javascript', cjs:'text/javascript', ts:'text/javascript', jsx:'text/javascript', tsx:'text/javascript', json:'application/json', map:'application/json', html:'text/html', htm:'text/html', vue:'text/html', xml:'application/xml', svg:'application/xml', css:'text/css', scss:'text/x-scss', less:'text/x-less', md:'text/x-markdown', markdown:'text/x-markdown', py:'text/x-python', python:'text/x-python', java:'text/x-java', kt:'text/x-kotlin', c:'text/x-csrc', h:'text/x-csrc', cpp:'text/x-c++src', cc:'text/x-c++src', cxx:'text/x-c++src', hpp:'text/x-c++src', hh:'text/x-c++src', cs:'text/x-csharp', go:'text/x-go', rs:'text/x-rust', php:'text/x-php', sh:'text/x-sh', bash:'text/x-sh', zsh:'text/x-sh', sql:'text/x-sql', rb:'text/x-ruby', yml:'text/x-yaml', yaml:'text/x-yaml', swift:'text/x-swift' }
const TEXT_EXT_DEFAULT = ['txt','log','ini','conf','cfg','csv','env','properties','tsv','gitignore','dockerfile','sass','plist']
const previewModes = ref({ ...CODE_MODE_DEFAULT })
const textPreviewExts = ref([...TEXT_EXT_DEFAULT])
async function loadShare() {
  pageLoading.value = true
  pwdErr.value = ''
  try { const r = await fetchThemes(); if (Array.isArray(r)) { remoteThemes = r.map(t => ({ name: t.name, config: t.config || {} })); remoteThemes.forEach(t => { try { MindMap.defineTheme(t.name, t.config) } catch(e){} }) } } catch (e) {}
  try {
    const data = await mindmapApi.getShare(route.params.token, pwd.value || undefined)
    shareName.value = data.name || '分享导图'
    needPwd.value = false // 密码通过：先切到导图视图，让 map 容器渲染
    // 后端 data/viewData 为 JSON 字符串，需解析
    let docData = data.data
    if (typeof docData === 'string') { try { docData = JSON.parse(docData) } catch (e) { docData = null } }
    docData = docData || {}
    shareBgZoom = (docData.tabs && docData.tabs[0] && docData.tabs[0].bgZoom) || docData.bgZoom || 1.35
    let root = docData.root || (docData.tabs && docData.tabs[0] && docData.tabs[0].data && docData.tabs[0].data.root) || docData
    let vd = data.viewData
    if (typeof vd === 'string') { try { vd = JSON.parse(vd) } catch (e) { vd = null } }
    await nextTick()
    mind.value = new MindMap({
      el: mapEl.value,
      data: root,
      layout: data.layout || 'logicalStructure',
      theme: data.theme || THEMES[0].name,
      viewData: vd || null,
      fit: true,
      readOnly: true,
      textAutoWrapWidth: 99999, // 不自动换行，公式完整显示
      mousewheelAction: 'zoom',
      isEnableEmptyNodeEdit: false,
      openNodeMove: false,
      // 禁用库内置附件图标（内容区右侧），统一使用注入的节点上方图标
      attachmentIcon: { style: { size: 0 }, icon: '' }
    })
    applyShareBg(data.theme || THEMES[0].name)
    // 旧 KaTeX 公式迁移为 MathJax SVG（只读展示，不保存）
    if (root) {
      const katexNodes = []
      const walk = (n) => {
        if (!n || !n.data) return
        const t = n.data.text || ''
        if (n.data.richText === true && t.indexOf('katex') >= 0) katexNodes.push(n)
        if (n.children) n.children.forEach(walk)
      }
      walk(root)
      if (katexNodes.length) {
        loadMathJax().then(async () => {
          let changed = false
          for (const n of katexNodes) {
            try {
              const migrated = await migrateKatexToSvg(n.data.text || '')
              if (migrated !== n.data.text) { n.data.text = migrated; changed = true }
            } catch (e) {}
          }
          if (changed && mind.value) { try { mind.value.setData(root) } catch (e) {} }
        }).catch(() => {})
      }
    }
    // KaTeX 字体异步加载：就绪后重渲染，修复公式塌陷
    if (document.fonts && document.fonts.ready) {
      document.fonts.ready.then(() => {
        setTimeout(() => { try { if (mind.value) mind.value.render() } catch (e) {} }, 300)
      })
    }
    mind.value.on('scale', s => { zoomPct.value = Math.round(s*100) + '%' })
    mind.value.on('node_tree_render_end', () => { pageLoading.value = false; try { fixAttachNodeTexts() } catch (e) {}; try { injectAttachIcons() } catch (e) {} })
    if (!window.__attIconTimer) {
      window.__attIconTimer = setInterval(() => { try { fixAttachNodeTexts() } catch (e) {}; try { injectAttachIcons() } catch (e) {} }, 700)
    }
    mind.value.on('node_attachmentClick', (node) => openAttachmentPreview(node))
  } catch (e) {
    if (e.message && e.message.indexOf('密码') >= 0) {
      needPwd.value = true
      pwdErr.value = '密码错误'
    } else {
      pwdErr.value = e.message || '加载失败'
      needPwd.value = true
    }
  }
}

// 附件预览（只读）
function openAttachmentPreview(nodeOrUrl, nameArg) {
  let url, name
  if (typeof nodeOrUrl === 'string') {
    url = nodeOrUrl
    name = nameArg || '附件'
  } else {
    url = nodeOrUrl.getData('attachmentUrl')
    name = nodeOrUrl.getData('attachmentName') || '附件'
  }
  if (!url) return
  const ext = (name.match(/\.([a-zA-Z0-9]+)$/) || [,''])[1].toLowerCase()
  const isImg = ['png','jpg','jpeg','gif','webp','bmp','ico','avif','svg'].includes(ext)
  const isPdf = ext === 'pdf'
  const isAudio = ['mp3','wav','ogg','m4a','aac','flac'].includes(ext)
  const isVideo = ['mp4','webm','ogv','mov','mkv'].includes(ext)
  const CODE_MODE = Object.keys(previewModes.value).length ? previewModes.value : CODE_MODE_DEFAULT
  const TEXT_EXT = textPreviewExts.value.length ? textPreviewExts.value : TEXT_EXT_DEFAULT
  const isText = CODE_MODE[ext] || TEXT_EXT.includes(ext)
  let wrap = document.getElementById('att-preview')
  if (wrap) wrap.remove()
  wrap = document.createElement('div')
  wrap.id = 'att-preview'
  wrap.style.cssText = 'position:fixed;inset:0;z-index:9999;display:flex;align-items:center;justify-content:center'
  wrap.innerHTML = '<div style="position:absolute;inset:0;background:rgba(0,0,0,.5)"></div><div style="position:relative;max-width:860px;width:92%;background:#fff;border-radius:14px;padding:14px;box-shadow:0 12px 40px rgba(0,0,0,.35);max-height:90vh;overflow:auto"><div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:10px"><span style="font-size:14px;font-weight:600;color:#333;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;max-width:80%"></span><button style="border:0;background:none;font-size:20px;color:#666;cursor:pointer;line-height:1">×</button></div><div class="attp-body"></div></div>'
  document.body.appendChild(wrap)
  wrap.querySelector('span').textContent = name
  const close = () => wrap.remove()
  wrap.querySelector('button').addEventListener('click', close)
  wrap.querySelector('div[style*=rgba]').addEventListener('click', close)
  const body = wrap.querySelector('.attp-body')
  // 加时间戳参数绕过 /uploads 强缓存，保证看到最新附件内容
  const freshUrl = url + (url.indexOf('?') > -1 ? '&' : '?') + 'v=' + Date.now()
  if (isImg) {
    const im = document.createElement('img')
    im.src = freshUrl; im.style.cssText = 'max-width:100%;max-height:62vh;border-radius:10px;display:block;margin:0 auto'
    body.appendChild(im)
  } else if (isPdf) {
    const ifr = document.createElement('iframe')
    ifr.src = freshUrl; ifr.style.cssText = 'width:100%;height:62vh;border:0;border-radius:10px;background:#fff'
    body.appendChild(ifr)
  } else if (isAudio) {
    body.innerHTML = '<audio controls autoplay style="width:100%"></audio>'
    body.querySelector('audio').src = freshUrl
  } else if (isVideo) {
    body.innerHTML = '<video controls autoplay style="width:100%;border-radius:10px;background:#000"></video>'
    body.querySelector('video').src = freshUrl
  } else if (isText) {
    fetch(freshUrl, { cache: 'no-store' }).then(r => r.text()).then(t => {
      if (CODE_MODE[ext] || TEXT_EXT.includes(ext)) {
        const ta = document.createElement('textarea')
        ta.value = t
        ta.style.cssText = 'width:100%;height:62vh;display:none'
        body.appendChild(ta)
        const cm = CodeMirror.fromTextArea(ta, {
          mode: CODE_MODE[ext] || null,
          readOnly: true,
          lineNumbers: true,
          lineWrapping: false,
          scrollbarStyle: 'null'
        })
        cm.setSize('100%', '62vh')
      } else {
        const pre = document.createElement('pre')
        pre.textContent = t
        pre.style.cssText = 'max-width:100%;max-height:62vh;overflow:auto;background:#1e1e1e;color:#e6e6e6;padding:14px;border-radius:10px;font-size:13px;white-space:pre-wrap;word-break:break-all;line-height:1.6;margin:0'
        body.appendChild(pre)
      }
    }).catch(() => {
      body.innerHTML = '<div style="color:#909399;font-size:14px;padding:40px 0 16px;text-align:center">无法读取内容</div><a href="' + freshUrl + '" target="_blank" rel="noopener" style="display:inline-block;padding:8px 24px;background:#0e7c86;color:#fff;border-radius:6px;text-decoration:none;font-size:14px">下载文件</a>'
    })
  } else {
    body.innerHTML = '<div style="color:#909399;font-size:14px;padding:40px 0 16px;text-align:center">该类型暂不支持在线预览，请下载后查看</div><a href="' + freshUrl + '" target="_blank" rel="noopener" style="display:inline-block;padding:8px 24px;background:#0e7c86;color:#fff;border-radius:6px;text-decoration:none;font-size:14px">下载文件</a>'
  }
}

// 给有附件的节点注入可点击的附件图标（右上角），单击打开附件预览
// 渲染期修正：附件节点文字统一为文件名（纯文本），避免文字飘出框外
function fixAttachNodeTexts() {
  if (!mind.value || !mind.value.renderer) return
  try {
    const tree = mind.value.getData()
    const walk = (t) => {
      if (t && t.data) {
        const d = t.data
        if (d.attachmentName && (d.text !== d.attachmentName || d.richText)) {
          const inst = d.uid ? mind.value.renderer.findNodeByUid(d.uid) : null
          if (inst) mind.value.renderer.setNodeDataRender(inst, { text: d.attachmentName, richText: false })
        }
      }
      if (t && t.children) t.children.forEach(walk)
    }
    walk(tree)
  } catch (e) {}
  try {
    // DOM 对齐兜底：附件节点的文字可能因样式重建而与节点框分离（飘出框外），强制将文字居中到框内
    const c = document.querySelector('.smm-container')
    if (c) {
      c.querySelectorAll('text.smm-text-node-wrap').forEach(tx => {
        const tr = tx.getBoundingClientRect()
        if (!tr || !tr.width) return
        let g = tx.parentElement
        let shape = null, ng = g
        for (let i = 0; i < 8 && ng; i++) {
          const s2 = ng.querySelector(':scope > rect, :scope > [class*=shape]')
          if (s2 && s2 !== tx) { shape = s2; break }
          ng = ng.parentElement
        }
        if (!shape) return
        const sr = shape.getBoundingClientRect()
        if (!sr || !sr.width) return
        const tc = tr.x + tr.width / 2
        const sc = sr.x + sr.width / 2
        if (Math.abs(tc - sc) > 2) {
          const ctm = tx.getScreenCTM && tx.getScreenCTM()
          const k = ctm && ctm.a ? ctm.a : 1
          const dx = (sc - tc) / k
          tx.setAttribute('transform', 'translate(' + dx.toFixed(2) + ' 0)')
        }
      })
    }
  } catch (e) {}
}

function injectAttachIcons() {
  if (!mind.value || !mind.value.renderer) return
  const NS = 'http://www.w3.org/2000/svg'
  let uidMap = null
  const nameMap = {}
  try {
    const tree = mind.value.getData()
    uidMap = {}
    const walk = (t) => {
      if (t && t.data) {
        if (t.data.uid) uidMap[t.data.uid] = t.data
        if (t.data.attachmentUrl && t.data.attachmentName) nameMap[t.data.attachmentName] = t.data
      }
      if (t && t.children) t.children.forEach(walk)
    }
    walk(tree)
  } catch (e) {}
  const injectIntoGroup = (gEl, d) => {
    if (!gEl || gEl.querySelector('.smm-att-icon')) return
    // 优先取文字框边界（节点文字），图标放其右上角；退回节点组整体边界
    let shape = gEl.querySelector('.smm-text-node-wrap, [class*=text]')
    let bb = null
    if (shape) { try { bb = shape.getBBox() } catch (e) {} }
    if (!bb && gEl.getBBox) { try { bb = gEl.getBBox() } catch (e) {} }
    const fo = document.createElementNS(NS, 'foreignObject')
    fo.setAttribute('class', 'smm-att-icon')
    const x = bb ? Math.max(0, bb.x + bb.width - 26) : 0
    const y = bb ? Math.max(0, bb.y + 2) : 0
    fo.setAttribute('x', x)
    fo.setAttribute('y', y)
    fo.setAttribute('width', '24')
    fo.setAttribute('height', '24')
    const div = document.createElement('div')
    div.style.cssText = 'width:22px;height:22px;display:flex;align-items:center;justify-content:center;cursor:pointer;font-size:12px;background:rgba(20,22,26,.55);backdrop-filter:blur(4px);-webkit-backdrop-filter:blur(4px);border-radius:7px;color:#fff;line-height:1;transition:transform .15s ease,background .15s ease'
    div.title = d.attachmentName || '附件'
    div.innerHTML = '<svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21.44 11.05l-9.19 9.19a6 6 0 0 1-8.49-8.49l9.19-9.19a4 4 0 0 1 5.66 5.66l-9.2 9.19a2 2 0 0 1-2.83-2.83l8.49-8.48"/></svg>'
    div.addEventListener('mouseenter', () => { div.style.transform = 'scale(1.15)'; div.style.background = 'rgba(14,124,134,.85)' })
    div.addEventListener('mouseleave', () => { div.style.transform = 'scale(1)'; div.style.background = 'rgba(20,22,26,.55)' })
    div.addEventListener('click', (e) => {
      e.preventDefault(); e.stopPropagation()
      openAttachmentPreview(d.attachmentUrl, d.attachmentName || '附件')
    })
    fo.appendChild(div)
    gEl.appendChild(fo)
  }
  try {
    const cache = mind.value.renderer.nodeCache || {}
    Object.keys(cache).forEach(uid => {
      const n = cache[uid]
      const gEl = (n && n.group && n.group.node) ? n.group.node : (n && n.group ? n.group : null)
      if (!gEl || gEl.querySelector('.smm-att-icon')) return
      const d = (uidMap && uidMap[uid]) || (n && n.data) || {}
      if (!d.attachmentUrl) return
      injectIntoGroup(gEl, d)
    })
  } catch (e) {}
  try {
    const svg = document.querySelector('.smm-container svg')
    if (!svg) return
    svg.querySelectorAll('title').forEach(t => {
      const name = (t.textContent || '').trim()
      if (!name || !nameMap[name]) return
      const gEl = t.closest('g')
      if (!gEl || gEl.querySelector('.smm-att-icon')) return
      injectIntoGroup(gEl, nameMap[name])
    })
  } catch (e) {}
}


function zoomIn() { mind.value && mind.value.view.enlarge() }
function zoomOut() { mind.value && mind.value.view.narrow() }
function zoomFit() { mind.value && mind.value.view.fit() }

onMounted(async () => {
  needPwd.value = false
  pwd.value = ''
  window.addEventListener('resize', updateBgSize)
  // 拉取后台预览/高亮类型配置
  try {
    const pc = await fetchPreviewConfig()
    if (pc && pc.previewModes && Object.keys(pc.previewModes).length) previewModes.value = pc.previewModes
    if (pc && Array.isArray(pc.textPreviewExts) && pc.textPreviewExts.length) textPreviewExts.value = pc.textPreviewExts
  } catch (e) { /* 后端不可用则用内置默认 */ }
  await loadShare()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', updateBgSize)
  if (window.__attIconTimer) { clearInterval(window.__attIconTimer); window.__attIconTimer = null }
  if (mind.value) { try { mind.value.dispose && mind.value.dispose() } catch(e){} }
})
</script>

<style scoped>
.share-root { position: relative; height: 100%; width: 100%; }
.share-bg { position: absolute; inset: 0; overflow: hidden; }
.page-loading { position: absolute; inset: 0; z-index: 100; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 14px; background: rgba(255,255,255,.7); backdrop-filter: blur(2px); }
/* CodeMirror VS Code Dark */
#att-preview .CodeMirror { background: #1e1e1e !important; color: #d4d4d4; font-family: Consolas, 'Courier New', monospace; font-size: 13px; border-radius: 10px; height: 62vh; }
#att-preview .CodeMirror-gutters { background: #252526 !important; border-right: 1px solid #333 !important; color: #858585; }
#att-preview .CodeMirror-linenumber { color: #858585; }
#att-preview .CodeMirror-cursor { border-left: 1px solid #fff; }
#att-preview .CodeMirror-selected { background: rgba(38,79,120,.6) !important; }
#att-preview .cm-keyword { color: #569cd6; }
#att-preview .cm-string, #att-preview .cm-string-2 { color: #ce9178; }
#att-preview .cm-comment { color: #6a9955; }
#att-preview .cm-number, #att-preview .cm-atom { color: #b5cea8; }
#att-preview .cm-variable, #att-preview .cm-variable-2, #att-preview .cm-variable-3 { color: #9cdcfe; }
#att-preview .cm-def { color: #dcdcaa; }
#att-preview .cm-operator, #att-preview .cm-property { color: #d4d4d4; }
#att-preview .cm-tag { color: #569cd6; }
#att-preview .cm-attribute { color: #9cdcfe; }
#att-preview .cm-meta { color: #dcdcaa; }
#att-preview .cm-builtin { color: #4ec9b0; }
#att-preview .cm-qualifier { color: #4ec9b0; }
#att-preview .cm-type { color: #4ec9b0; }
#att-preview .cm-bracket { color: #808080; }
#att-preview .cm-header { color: #569cd6; }
#att-preview .cm-link { color: #ce9178; }
#att-preview .cm-error { color: #f4470e; background: none; }
.pl-spinner { width: 44px; height: 44px; border: 4px solid rgba(74,92,247,.18); border-top-color: #4A5CF7; border-radius: 50%; animation: pl-rot .8s linear infinite; }
.pl-text { font-size: 14px; color: #667; }
@keyframes pl-rot { to { transform: rotate(360deg) } }
.share-bg-color { position: absolute; inset: 0; }
.share-bg-image { position: absolute; inset: 0; background-size: cover; background-position: center; background-repeat: no-repeat; }
.share-bg-video { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; object-position: center center; pointer-events: none; z-index: 0; }
#map { position: relative; height: 100%; width: 100%; background: transparent !important; }
</style>
