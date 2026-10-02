<template>
  <div class="share-root">
    <!-- 密码弹窗 -->
    <div v-if="needPwd" class="pwd-mask">
      <div class="pwd-box">
        <h3>此分享需要密码</h3>
        <input type="password" v-model="pwd" placeholder="输入访问密码" @keyup.enter="loadShare" />
        <input type="text" v-model="nick" maxlength="32" placeholder="输入你的昵称（必填，用于评论）" @keyup.enter="loadShare" />
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
        <div id="cmt-badge-layer" class="cmt-badge-layer"></div>
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

      <!-- 节点评论面板 -->
      <div v-if="cmtOpen" class="cmt-panel">
        <div class="cmt-header">
          <span class="cmt-title" title="当前节点的评论">💬 {{ cmtNodeName || '节点' }}</span>
          <button class="cmt-close" @click="closeCmt">×</button>
        </div>
        <div class="cmt-list">
          <div v-if="!cmtList.length" class="cmt-empty">还没有评论，来抢沙发~</div>
          <div v-for="c in cmtList" :key="c.id" class="cmt-item" :class="{ 'cmt-author': c.isAuthor === 1 }">
            <div class="cmt-head">
              <span class="cmt-src" :class="c.source === 'org' ? 'cmt-src-org' : 'cmt-src-share'">{{ c.source === 'org' ? '组织' : '分享' }}</span>
              <b>{{ c.userName }}</b>
              <span v-if="c.isAuthor === 1" class="cmt-author-tag">作者</span>
              <span class="cmt-time">{{ fmtTime(c.createdAt) }}</span>
            </div>
            <div class="cmt-body">{{ c.content }}</div>
            <div class="cmt-foot">
              <button class="cmt-reply" @click="setReply(c)">回复</button>
            </div>
            <div v-if="replyTo && replyTo.id === c.id" class="cmt-quick">
              <input v-model="cmtInput" maxlength="128" :placeholder="'回复 ' + c.userName + '…'" @keyup.enter="submitCmt(true)" />
              <button @click="submitCmt(true)">发送</button>
            </div>
          </div>
        </div>
        <div class="cmt-input">
          <input v-model="cmtInput" maxlength="128" :placeholder="replyTo ? '回复 ' + replyTo.userName + '…' : '写评论（128字内）…'" @keyup.enter="submitCmt(false)" />
          <button @click="submitCmt(false)">发送</button>
        </div>
        <div class="cmt-tip">昵称：{{ nick || '未设置' }} ｜ 评论 {{ cmtList.length }} 条<template v-if="cmtErr"> ｜ <span class="cmt-err">{{ cmtErr }}</span></template></div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount, nextTick } from 'vue'
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
import { commentApi } from '../api/comment'

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
const nick = ref('')
const pwdErr = ref('')
const shareName = ref('')
const zoomPct = ref('100%')
const bgColorVal = ref('#fafbff')
const bgImageVal = ref('')
const bgVideoVal = ref('')
const bgOpacityVal = ref(1)
let remoteThemes = []
const pageLoading = ref(true)

// ===== 评论 =====
const cmtOpen = ref(false)
const cmtNodeId = ref('')
const cmtNodeName = ref('')
const cmtList = ref([])
let shareMindMapId = null   // 当前分享的导图 id（评论 WS 订阅用）
let cmtWs = null            // 评论实时推送通道
let cmtWsTimer = null       // 重连定时器
const cmtInput = ref('')
const replyTo = ref(null)
const sharePwd = ref('') // 当前分享访问密码（提交评论/拉评论时携带）

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
const attachTextColor = ref('#e53935')
const textPreviewExts = ref([...TEXT_EXT_DEFAULT])
async function loadShare() {
  // 密码模式：昵称强制必填（用于评论）
  if (needPwd.value && !(nick.value || '').trim()) {
    pwdErr.value = '请先填写昵称（评论需要）'
    return
  }
  pageLoading.value = true
  pwdErr.value = ''
  try { const r = await fetchThemes(); if (Array.isArray(r)) { remoteThemes = r.map(t => ({ name: t.name, config: t.config || {} })); remoteThemes.forEach(t => { try { MindMap.defineTheme(t.name, t.config) } catch(e){} }) } } catch (e) {}
  try {
    const data = await mindmapApi.getShare(route.params.token, pwd.value || undefined)
    shareName.value = data.name || '分享导图'
    sharePwd.value = pwd.value || ''
    // 密码会话：本机浏览器缓存，刷新不再要求输入密码
    if (pwd.value) { try { localStorage.setItem('usmind_share_pwd_' + route.params.token, pwd.value) } catch (e) {} }
    const savedNick = (nick.value || '').trim()
    if (savedNick) { nick.value = savedNick; try { localStorage.setItem('usmind_share_nick', savedNick) } catch (e) {} }
    needPwd.value = false // 密码通过：先切到导图视图，让 map 容器渲染
    shareMindMapId = data.id || null
    connectShareCmtWs()
    // 后端 data/viewData 为 JSON 字符串，需解析
    let docData = data.data
    if (typeof docData === 'string') { try { docData = JSON.parse(docData) } catch (e) { docData = null } }
    docData = docData || {}
    shareBgZoom = (docData.tabs && docData.tabs[0] && docData.tabs[0].bgZoom) || docData.bgZoom || 1.35
    let root = docData.root || (docData.tabs && docData.tabs[0] && docData.tabs[0].data && docData.tabs[0].data.root) || docData
    applyAttachDefaultColor(root)
    // 双图修复：富文本图片节点（text 含 <img）同时存在 data.image（粘贴产生的 base64 大图）时删除 image，
    // 避免渲染层同时渲染大图(SVG)与小图(foreignObject)
    const walkFixDup = (n) => {
      if (!n || !n.data) return
      if (n.data.richText && typeof n.data.text === 'string' && n.data.text.indexOf('<img') !== -1 && typeof n.data.image === 'string') {
        delete n.data.image
        delete n.data.imageSize
      }
      if (n.children) n.children.forEach(walkFixDup)
    }
    walkFixDup(root)
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
      attachmentIcon: { style: { size: 0 }, icon: '' },
      // 只读页禁止文本编辑：双击公式/文本节点不再弹出 SVG 源码或编辑框（公式节点点击只预览，不进入编辑）
      beforeTextEdit: () => false
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
    // 与编辑页一致：不注入节点右上角附件图标（simple-mind-map 附件节点自带标签可点击打开）
    mind.value.on('node_tree_render_end', () => { pageLoading.value = false; try { fixAttachNodeTexts() } catch (e) {} })
    if (!window.__attIconTimer) {
      window.__attIconTimer = setInterval(() => { try { fixAttachNodeTexts() } catch (e) {} }, 700)
    }
    mind.value.on('node_attachmentClick', (node) => openAttachmentPreview(node))
    // 点击节点 → 打开评论面板（readOnly 下节点仍可点击）
    mind.value.on('node_click', (node) => { if (node && node.getData) openCmt(node) })
    // 只读画布平移/缩放（readOnly 下库禁用 Drag 插件，自实现：鼠标空白/节点按住拖动、触摸单指平移+双指缩放）
    mapEl.value.addEventListener('mousedown', onMapMouseDown)
    mapEl.value.addEventListener('touchstart', onMapTouchStart, { passive: false })
    mapEl.value.addEventListener('touchmove', onMapTouchMove, { passive: false })
    mapEl.value.addEventListener('touchend', onMapTouchEnd)
    mapEl.value.addEventListener('touchcancel', onMapTouchEnd)
  } catch (e) {
    if (e.message && e.message.indexOf('密码') >= 0) {
      try { localStorage.removeItem('usmind_share_pwd_' + route.params.token) } catch (e2) {}
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
    // pdf.js 渲染（浏览器内置 viewer 对部分 pdf 打不开，pdf.js 兼容性更稳）
    body.innerHTML = '<div class="attp-loading" style="padding:30px;text-align:center;color:#8a93a6">正在加载 PDF…</div>'
    new Promise((res, rej) => {
      if (window.pdfjsLib) return res()
      const s = document.createElement('script')
      s.src = 'https://cdn.jsdelivr.net/npm/pdfjs-dist@3.11.174/build/pdf.min.js'
      s.onload = () => res(); s.onerror = () => rej(new Error('pdf.js load fail'))
      document.head.appendChild(s)
    }).then(() => {
      window.pdfjsLib.GlobalWorkerOptions.workerSrc = 'https://cdn.jsdelivr.net/npm/pdfjs-dist@3.11.174/build/pdf.worker.min.js'
      return window.pdfjsLib.getDocument(freshUrl).promise
    }).then(pdf => {
      body.innerHTML = ''
      const wrap = document.createElement('div')
      wrap.style.cssText = 'width:100%;height:62vh;overflow:auto;background:#525659;border-radius:10px;padding:16px'
      body.appendChild(wrap)
      const renderAll = async () => {
        for (let i = 1; i <= pdf.numPages; i++) {
          const page = await pdf.getPage(i)
          const vp = page.getViewport({ scale: 1.3 })
          const canvas = document.createElement('canvas')
          canvas.width = vp.width; canvas.height = vp.height
          canvas.style.cssText = 'max-width:100%;height:auto;margin:0 auto 12px;display:block;box-shadow:0 2px 10px rgba(0,0,0,.4)'
          wrap.appendChild(canvas)
          await page.render({ canvasContext: canvas.getContext('2d'), viewport: vp }).promise
        }
      }
      renderAll().catch(() => { wrap.innerHTML = '<div style="color:#fff;padding:30px;text-align:center">PDF 渲染失败</div>' })
    }).catch(() => {
      body.innerHTML = '<div class="attp-dl">PDF 加载失败，请下载查看</div><a class="attp-dl-btn" href="' + freshUrl + '" target="_blank" rel="noopener">下载文件</a>'
    })
  } else if (isAudio) {
    body.innerHTML = '<audio controls autoplay style="width:100%"></audio>'
    body.querySelector('audio').src = freshUrl
  } else if (isVideo) {
    body.innerHTML = '<video controls autoplay style="width:100%;border-radius:10px;background:#000"></video>'
    body.querySelector('video').src = freshUrl
  } else if (ext === 'docx' || ext === 'doc') {
    body.innerHTML = '<div style="color:#8a93a6;font-size:14px;padding:30px;text-align:center">正在加载文档预览…</div>'
    new Promise((res, rej) => {
      if (window.docx && window.JSZip) return res()
      let pending = 0, done = 0
      const check = () => { done += 1; if (done >= pending) res() }
      if (!window.JSZip) {
        pending += 1
        const s1 = document.createElement('script')
        s1.src = 'https://cdn.jsdelivr.net/npm/jszip@3.10.1/dist/jszip.min.js'
        s1.onload = check; s1.onerror = () => rej(new Error('jszip load fail'))
        document.head.appendChild(s1)
      }
      if (!window.docx) {
        pending += 1
        const s2 = document.createElement('script')
        s2.src = 'https://cdn.jsdelivr.net/npm/docx-preview@0.3.1/dist/docx-preview.min.js'
        s2.onload = check; s2.onerror = () => rej(new Error('docx load fail'))
        document.head.appendChild(s2)
      }
      if (pending === 0) res()
    }).then(() => {
      fetch(freshUrl, { cache: 'no-store' }).then(r => r.arrayBuffer()).then(buf => {
        body.innerHTML = ''
        const box = document.createElement('div')
        box.style.cssText = 'width:100%;height:62vh;overflow:auto;background:#fff;border-radius:10px;padding:18px;box-shadow:inset 0 0 0 1px #e5e7ef'
        body.appendChild(box)
        window.docx.renderAsync(buf, box).catch(() => {
          body.innerHTML = '<div style="color:#909399;font-size:14px;padding:40px 0 16px;text-align:center">文档渲染失败</div><a href="' + freshUrl + '" target="_blank" rel="noopener" style="display:inline-block;padding:8px 24px;background:#0e7c86;color:#fff;border-radius:6px;text-decoration:none;font-size:14px">下载文件</a>'
        })
      }).catch(() => {
        body.innerHTML = '<div style="color:#909399;font-size:14px;padding:40px 0 16px;text-align:center">文档读取失败</div><a href="' + freshUrl + '" target="_blank" rel="noopener" style="display:inline-block;padding:8px 24px;background:#0e7c86;color:#fff;border-radius:6px;text-decoration:none;font-size:14px">下载文件</a>'
      })
    }).catch(() => {
      body.innerHTML = '<div style="color:#909399;font-size:14px;padding:40px 0 16px;text-align:center">预览组件加载失败</div><a href="' + freshUrl + '" target="_blank" rel="noopener" style="display:inline-block;padding:8px 24px;background:#0e7c86;color:#fff;border-radius:6px;text-decoration:none;font-size:14px">下载文件</a>'
    })
  } else if (ext === 'xlsx' || ext === 'xls' || ext === 'csv') {
    body.innerHTML = '<div style="color:#8a93a6;font-size:14px;padding:30px;text-align:center">正在加载表格预览…</div>'
    new Promise((res, rej) => {
      if (window.XLSX) return res()
      const s = document.createElement('script')
      s.src = 'https://cdn.jsdelivr.net/npm/xlsx@0.18.5/dist/xlsx.full.min.js'
      s.onload = () => res(); s.onerror = () => rej(new Error('load fail'))
      document.head.appendChild(s)
    }).then(() => {
      fetch(freshUrl, { cache: 'no-store' }).then(r => r.arrayBuffer()).then(buf => {
        try {
          const wb = window.XLSX.read(buf)
          const sheet = wb.Sheets[wb.SheetNames[0]]
          const html = window.XLSX.utils.sheet_to_html(sheet)
          body.innerHTML = '<div style="width:100%;height:62vh;overflow:auto;background:#fff;border-radius:10px;padding:12px;box-shadow:inset 0 0 0 1px #e5e7ef"><style>.attp-table-wrap table{border-collapse:collapse;font-size:13px;font-family:Consolas,monospace}.attp-table-wrap td,.attp-table-wrap th{border:1px solid #dfe3ef;padding:5px 9px;white-space:nowrap}.attp-table-wrap th{background:#f4f6fb;font-weight:600}</style><div class="attp-table-wrap">' + html + '</div></div>'
        } catch (e) {
          body.innerHTML = '<div style="color:#909399;font-size:14px;padding:40px 0 16px;text-align:center">表格解析失败</div><a href="' + freshUrl + '" target="_blank" rel="noopener" style="display:inline-block;padding:8px 24px;background:#0e7c86;color:#fff;border-radius:6px;text-decoration:none;font-size:14px">下载文件</a>'
        }
      }).catch(() => {
        body.innerHTML = '<div style="color:#909399;font-size:14px;padding:40px 0 16px;text-align:center">表格读取失败</div><a href="' + freshUrl + '" target="_blank" rel="noopener" style="display:inline-block;padding:8px 24px;background:#0e7c86;color:#fff;border-radius:6px;text-decoration:none;font-size:14px">下载文件</a>'
      })
    }).catch(() => {
      body.innerHTML = '<div style="color:#909399;font-size:14px;padding:40px 0 16px;text-align:center">预览组件加载失败</div><a href="' + freshUrl + '" target="_blank" rel="noopener" style="display:inline-block;padding:8px 24px;background:#0e7c86;color:#fff;border-radius:6px;text-decoration:none;font-size:14px">下载文件</a>'
    })
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
// 渲染前：附件节点文件名默认色（后台 attachTextColor 可配）
function applyAttachDefaultColor(d) {
  if (!d || typeof d !== 'object') return
  const col = attachTextColor.value || '#e53935'
  const apply = (obj) => {
    if (!obj || typeof obj !== 'object') return
    const nd = obj.data || {}
    if ((nd.attachmentUrl || nd.attachmentName) && !nd.textColor) nd.textColor = col
    if (Array.isArray(obj.children)) obj.children.forEach(apply)
  }
  const target = d.root || d
  apply(target)
  if (Array.isArray(d._tabs)) d._tabs.forEach(t => { if (t && t.data && t.data.root) apply(t.data.root) })
}

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


function zoomIn() { if (mind.value) { mind.value.view.enlarge(); repositionCommentBadges() } }
function zoomOut() { if (mind.value) { mind.value.view.narrow(); repositionCommentBadges() } }
function zoomFit() { if (mind.value) { mind.value.view.fit(); repositionCommentBadges() } }

// ===== 评论 =====
// 分享访客会话 ID（匿名定向评论特效：回复我的评论才触发角标/呼吸灯/电流/音效）
const mySid = (() => {
  try {
    let s = localStorage.getItem('usmind_share_sid')
    if (!s) { s = 'sid_' + Date.now().toString(36) + '_' + Math.random().toString(36).slice(2, 10); localStorage.setItem('usmind_share_sid', s) }
    return s
  } catch (e) { return 'sid_' + Math.random().toString(36).slice(2, 10) }
})()
const unreadNodes = reactive({})
const cmtSoundOn = ref(localStorage.getItem('usmind.cmtSound') !== '0')
const cmtErr = ref('')
function openCmt(node) {
  const uid = node.getData('uid')
  if (!uid) return
  cmtNodeId.value = uid
  let t = node.getData('text') || node.getData('attachmentName') || ''
  // 富文本/公式节点剥离 HTML 标签，避免面板标题露出 SVG 源码
  if (/<[a-z][\s\S]*>/i.test(String(t))) {
    try { t = new DOMParser().parseFromString(String(t), 'text/html').body.textContent || t } catch (e) {}
  }
  cmtNodeName.value = String(t).slice(0, 24) || '节点'
  cmtOpen.value = true
  replyTo.value = null
  cmtInput.value = ''
  // 打开面板即视为已读：清除该节点未读标记与特效（刷新后不再显示）
  if (unreadNodes[uid]) { delete unreadNodes[uid]; applyCommentFlash() }
  refreshCmt()
}
function closeCmt() { cmtOpen.value = false; replyTo.value = null; cmtInput.value = '' }

// ===== 评论特效（与编辑页同款：呼吸灯/根路径/流动电子线/右上角未读角标/提示音）=====
function cmtAnchorOf(uid) {
  try {
    const cache = mind.value && mind.value.renderer ? (mind.value.renderer.nodeCache || {}) : {}
    const vis = (c2) => {
      if (!c2 || !c2.group) return null
      const g = c2.group.node || c2.group
      try {
        const rc = g.getBoundingClientRect()
        const win = g.ownerDocument && g.ownerDocument.defaultView ? g.ownerDocument.defaultView : null
        const cs = win ? win.getComputedStyle(g) : null
        if (rc.width > 1 && rc.height > 1 && (!cs || (cs.display !== 'none' && cs.visibility !== 'hidden'))) return g
      } catch (e) {}
      return null
    }
    let t = cache[uid] || null
    if (t) { const g = vis(t); if (g) return { node: t, el: g } }
    try {
      const data = mind.value && mind.value.getData ? mind.value.getData() : null
      const rootNode = data && data.data ? data.data : null
      if (rootNode) {
        if (!rootNode.children && data.children) rootNode.children = data.children
        const find = (n, path) => {
          if (!n) return null
          const nid = (n.data || {}).uid
          if (nid === uid) return path.concat(nid)
          for (const c2 of (n.children || [])) { const f = find(c2, path.concat(nid)); if (f) return f }
          return null
        }
        const path = find(rootNode, [])
        if (path && path.length) {
          for (let i = path.length - 1; i >= 0; i--) { const c2 = cache[path[i]]; const g = vis(c2); if (g) return { node: c2, el: g } }
        }
      }
    } catch (e) {}
    let guard = 0
    while (t && guard < 30) { const g = vis(t); if (g) return { node: t, el: g }; t = t.parent; guard++ }
  } catch (e) {}
  return null
}
function applyCommentFlash() {
  if (!mind.value || !mind.value.renderer) return
  try {
    document.querySelectorAll('.cmt-flash, .cmt-breath-path, .cmt-breath-line, .cmt-flow-line').forEach(el => {
      if (el.classList.contains('cmt-flow-line')) { try { el.remove() } catch (e) {} return }
      el.classList.remove('cmt-flash', 'cmt-breath-path', 'cmt-breath-line')
    })
    const layer = document.getElementById('cmt-badge-layer')
    if (layer) layer.innerHTML = ''
    const nodeCache = mind.value.renderer.nodeCache || {}
    const mapRect = mapEl.value ? mapEl.value.getBoundingClientRect() : null
    const groups = new Map()
    Object.keys(unreadNodes).forEach(uid => {
      const cnt = unreadNodes[uid]
      if (!cnt) return
      const anchor = cmtAnchorOf(uid)
      if (!anchor) return
      const key = anchor.el
      const g0 = groups.get(key)
      if (g0) { g0.cnt += cnt; return }
      groups.set(key, { node: anchor.node, el: key, cnt })
    })
    groups.forEach((g0) => {
      const n = g0.node, gEl = g0.el, cnt = g0.cnt
      const rect = gEl.querySelector('rect')
      if (rect) rect.classList.add('cmt-flash')
      let p = n.parent, child = n, guard = 0
      while (p && guard < 30) {
        const pg = p.group ? (p.group.node || p.group) : null
        if (pg) { const pr = pg.querySelector('rect'); if (pr) pr.classList.add('cmt-breath-path') }
        if (p._lines && p._lines.length && p.children && p.children.length) {
          const idx = p.children.indexOf(child)
          if (idx >= 0 && idx < p._lines.length) {
            const l = p._lines[idx]
            try {
              const el = l && l.node ? l.node : l
              if (el && el.classList) el.classList.add('cmt-breath-line')
              try {
                if (el && el.tagName === 'path' && el.parentNode) {
                  const flow = el.cloneNode(false)
                  flow.removeAttribute('class')
                  flow.removeAttribute('style')
                  flow.classList.add('cmt-flow-line')
                  el.parentNode.appendChild(flow)
                }
              } catch (e) {}
            } catch (e) {}
          }
        }
        child = p; p = p.parent; guard++
      }
      if (layer && mapRect) {
        const r = gEl.getBoundingClientRect()
        if (r.width > 1 && r.height > 1) {
          const b = document.createElement('div')
          b.textContent = cnt > 99 ? '99+' : cnt
          const st = b.style
          st.position = 'absolute'
          st.left = (r.right - mapRect.left) + 'px'
          st.top = (r.top - mapRect.top) + 'px'
          st.transform = 'translate(-50%, -50%)'
          st.minWidth = '18px'; st.height = '18px'; st.lineHeight = '18px'; st.textAlign = 'center'
          st.fontSize = '11px'; st.fontWeight = '700'; st.color = '#fff'
          st.background = 'linear-gradient(135deg, #ff6b6b, #e53935)'
          st.borderRadius = '9px'; st.padding = '0 5px'
          st.boxShadow = '0 2px 8px rgba(229,57,53,.6)'
          st.pointerEvents = 'none'
          layer.appendChild(b)
        }
      }
    })
  } catch (e) {}
}
function syncCommentFlash() {
  nextTick(() => { try { applyCommentFlash() } catch (e) {} })
  setTimeout(() => { try { applyCommentFlash(); repositionCommentBadges() } catch (e) {} }, 200)
  setTimeout(() => { try { applyCommentFlash(); repositionCommentBadges() } catch (e) {} }, 500)
}
function repositionCommentBadges() {
  try {
    const layer = document.getElementById('cmt-badge-layer')
    if (!layer || !layer.children.length) return
    const mapRect = mapEl.value ? mapEl.value.getBoundingClientRect() : null
    if (!mapRect) return
    const uids = Object.keys(unreadNodes).filter(uid => unreadNodes[uid])
    const badges = layer.children
    for (let i = 0; i < badges.length && i < uids.length; i++) {
      const uid = uids[i]
      if (!uid) continue
      const anchor = cmtAnchorOf(uid)
      if (!anchor) continue
      const gEl = anchor.el
      const r = gEl.getBoundingClientRect()
      if (r.width <= 1 || r.height <= 1) continue
      const b = badges[i]
      b.style.left = (r.right - mapRect.left) + 'px'
      b.style.top = (r.top - mapRect.top) + 'px'
    }
  } catch (e) {}
}
function playCmtSound() {
  if (!cmtSoundOn.value) return
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
}
function setReply(c) {
  replyTo.value = (replyTo.value && replyTo.value.id === c.id) ? null : c
  cmtInput.value = ''
}
function connectShareCmtWs() {
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
  // 定向特效：只有被回复的人（replySid=我的会话ID 且不是自己发的）收到角标/呼吸灯/电流/音效；其他人仅收信息
  const replySid = c.replySid || ''
  if (replySid && replySid === mySid && (c.sid || '') !== mySid) {
    unreadNodes[c.nodeId] = (unreadNodes[c.nodeId] || 0) + 1
    syncCommentFlash()
    playCmtSound()
  }
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

async function refreshCmt() {
  cmtErr.value = ''
  try {
    const list = await commentApi.listShare(route.params.token, sharePwd.value)
    cmtList.value = (Array.isArray(list) ? list : []).filter(c => c.nodeId === cmtNodeId.value)
  } catch (e) {
    cmtErr.value = e.message || '加载评论失败'
  }
}
async function submitCmt(isReply) {
  const content = (cmtInput.value || '').trim()
  if (!content) return
  if (content.length > 128) { cmtErr.value = '评论最多 128 字'; return }
  const nickName = (nick.value || '').trim() || (localStorage.getItem('usmind_share_nick') || '匿名访客')
  cmtErr.value = ''
  try {
    await commentApi.addShare(route.params.token, {
      nodeId: cmtNodeId.value,
      parentId: isReply && replyTo.value ? replyTo.value.id : null,
      nick: nickName,
      content,
      password: sharePwd.value || undefined,
      clientSid: mySid
    })
    cmtInput.value = ''
    if (isReply) replyTo.value = null
    await refreshCmt()
  } catch (e) {
    cmtErr.value = e.message || '发送失败（可能评论过于频繁，稍后再试）'
  }
}
function fmtTime(s) {
  if (!s) return ''
  const d = new Date(s)
  if (isNaN(d.getTime())) return String(s).slice(0, 16).replace('T', ' ')
  const p = n => (n < 10 ? '0' + n : '' + n)
  return d.getFullYear() + '-' + p(d.getMonth() + 1) + '-' + p(d.getDate()) + ' ' + p(d.getHours()) + ':' + p(d.getMinutes())
}

// ===== 只读画布平移/缩放 =====
let panState = null
let touchPan = null
let pinch = null
function getViewState() {
  const vd = mind.value.view.getTransformData()
  return (vd && vd.state) ? vd.state : vd
}
function setViewPan(nx, ny) {
  const view = mind.value.view
  const vd = view.getTransformData()
  if (!vd || !vd.state) return
  view.translateXY(nx - vd.state.x, ny - vd.state.y)
}
function isUiTarget(t) {
  return !!(t && t.closest && (t.closest('.toolbar') || t.closest('.view-bar') || t.closest('.cmt-panel') || t.closest('.page-loading') || t.closest('.pwd-mask') || t.closest('#att-preview')))
}
function onMapMouseDown(e) {
  if (e.button !== 0 || !mind.value || !mapEl.value) return
  if (isUiTarget(e.target)) return
  panState = { sx: e.clientX, sy: e.clientY, st: getViewState(), moved: false }
  document.addEventListener('mousemove', onMapMouseMove)
  document.addEventListener('mouseup', onMapMouseUp)
}
function onMapMouseMove(e) {
  if (!panState || !mind.value) return
  const dx = e.clientX - panState.sx, dy = e.clientY - panState.sy
  if (!panState.moved && Math.abs(dx) + Math.abs(dy) > 6) panState.moved = true
  if (panState.moved) { e.preventDefault(); try { setViewPan(panState.st.x + dx, panState.st.y + dy); repositionCommentBadges() } catch (e2) {} }
}
function onMapMouseUp() {
  panState = null
  document.removeEventListener('mousemove', onMapMouseMove)
  document.removeEventListener('mouseup', onMapMouseUp)
}
function onMapTouchStart(e) {
  if (e.touches.length >= 2) {
    if (!mind.value) return
    const t1 = e.touches[0], t2 = e.touches[1]
    pinch = { dist: Math.hypot(t1.clientX - t2.clientX, t1.clientY - t2.clientY), scale: mind.value.view.scale }
    touchPan = null
    return
  }
  if (e.touches.length !== 1) return
  if (isUiTarget(e.target)) return
  touchPan = { sx: e.touches[0].clientX, sy: e.touches[0].clientY, st: getViewState(), moved: false }
}
function onMapTouchMove(e) {
  if (pinch && mind.value && e.touches.length >= 2) {
    e.preventDefault(); e.stopPropagation()
    const t1 = e.touches[0], t2 = e.touches[1]
    const dist = Math.hypot(t1.clientX - t2.clientX, t1.clientY - t2.clientY)
    let ns = pinch.scale * (dist / pinch.dist)
    const opt = mind.value.opt || {}
    const min = (opt.minZoomRatio || 20) / 100
    const max = opt.maxZoomRatio === -1 ? 5 : ((opt.maxZoomRatio || 300) / 100)
    ns = Math.max(min, Math.min(max, ns))
    const rect = mapEl.value ? mapEl.value.getBoundingClientRect() : null
    const cx = (t1.clientX + t2.clientX) / 2 - (rect ? rect.left : 0)
    const cy = (t1.clientY + t2.clientY) / 2 - (rect ? rect.top : 0)
    try { mind.value.view.setScale(ns, cx, cy); repositionCommentBadges() } catch (e2) {}
    return
  }
  if (!touchPan || !mind.value) return
  if (e.touches.length !== 1) { touchPan = null; return }
  const t = e.touches[0]
  const dx = t.clientX - touchPan.sx, dy = t.clientY - touchPan.sy
  if (!touchPan.moved && Math.abs(dx) + Math.abs(dy) > 6) touchPan.moved = true
  if (touchPan.moved) { e.preventDefault(); e.stopPropagation(); try { setViewPan(touchPan.st.x + dx, touchPan.st.y + dy) } catch (e2) {} }
}
function onMapTouchEnd() { touchPan = null; pinch = null }

onMounted(async () => {
  needPwd.value = false
  pwd.value = ''
  try { const savedPwd = localStorage.getItem('usmind_share_pwd_' + route.params.token); if (savedPwd) pwd.value = savedPwd } catch (e) {}
  try { const saved = localStorage.getItem('usmind_share_nick'); if (saved) nick.value = saved } catch (e) {}
  window.addEventListener('resize', updateBgSize)
  // 拉取后台预览/高亮类型配置
  try {
    const pc = await fetchPreviewConfig()
    if (pc && pc.previewModes && Object.keys(pc.previewModes).length) previewModes.value = pc.previewModes
    if (pc && pc.attachTextColor) attachTextColor.value = pc.attachTextColor
    if (pc && Array.isArray(pc.textPreviewExts) && pc.textPreviewExts.length) textPreviewExts.value = pc.textPreviewExts
  } catch (e) { /* 后端不可用则用内置默认 */ }
  await loadShare()
})

onBeforeUnmount(() => {
  disconnectShareCmtWs()
  window.removeEventListener('resize', updateBgSize)
  if (mapEl.value) {
    mapEl.value.removeEventListener('mousedown', onMapMouseDown)
    mapEl.value.removeEventListener('touchstart', onMapTouchStart)
    mapEl.value.removeEventListener('touchmove', onMapTouchMove)
    mapEl.value.removeEventListener('touchend', onMapTouchEnd)
    mapEl.value.removeEventListener('touchcancel', onMapTouchEnd)
  }
  document.removeEventListener('mousemove', onMapMouseMove)
  document.removeEventListener('mouseup', onMapMouseUp)
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
#map { position: relative; height: 100%; width: 100%; background: transparent !important; touch-action: none; -ms-touch-action: none; }

/* ===== 节点评论面板 ===== */
.cmt-panel { position: fixed; top: 74px; right: 18px; bottom: 86px; width: 320px; max-width: calc(100vw - 36px); background: rgba(23, 26, 32, .88); backdrop-filter: blur(14px); -webkit-backdrop-filter: blur(14px); border: 1px solid rgba(255,255,255,.09); border-radius: 14px; box-shadow: 0 14px 44px rgba(0,0,0,.4); display: flex; flex-direction: column; overflow: hidden; z-index: 300; color: #e8eaf2; font-size: 13px; }
.cmt-header { display: flex; align-items: center; justify-content: space-between; padding: 12px 14px; border-bottom: 1px solid rgba(255,255,255,.08); }
.cmt-title { font-weight: 600; font-size: 14px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.cmt-close { border: 0; background: none; color: #aab; font-size: 18px; cursor: pointer; line-height: 1; padding: 2px 6px; border-radius: 6px; }
.cmt-close:hover { background: rgba(255,255,255,.12); color: #fff; }
.cmt-list { flex: 1; overflow-y: auto; padding: 10px 12px; display: flex; flex-direction: column; gap: 8px; }
.cmt-empty { text-align: center; color: #8890a3; padding: 26px 0; }
.cmt-item { background: rgba(255,255,255,.05); border: 1px solid rgba(255,255,255,.06); border-radius: 10px; padding: 8px 10px; }
.cmt-item.cmt-author { border-color: rgba(245, 158, 11, .35); background: rgba(245, 158, 11, .07); }
.cmt-head { display: flex; align-items: center; gap: 6px; margin-bottom: 4px; }
.cmt-head b { color: #f5f7ff; font-size: 13px; }
.cmt-author-tag { font-size: 11px; color: #fff; background: #f59e0b; border-radius: 4px; padding: 1px 5px; }
.cmt-src { display: inline-block; font-size: 10px; line-height: 16px; height: 16px; padding: 0 6px; border-radius: 4px; font-weight: 600; }
.cmt-src-share { background: rgba(74,92,247,.18); color: #9fb0ff; }
.cmt-src-org { background: rgba(14,124,134,.18); color: #3ec5cf; }
.cmt-time { margin-left: auto; color: #7c8399; font-size: 11px; white-space: nowrap; }
.cmt-body { color: #cdd3e3; line-height: 1.55; word-break: break-word; }
.cmt-foot { margin-top: 4px; }
.cmt-reply { border: 0; background: none; color: #7aa2f7; cursor: pointer; font-size: 12px; padding: 0; }
.cmt-reply:hover { text-decoration: underline; }
.cmt-quick { display: flex; gap: 6px; margin-top: 6px; }
.cmt-quick input, .cmt-input input { flex: 1; min-width: 0; border: 1px solid rgba(255,255,255,.14); background: rgba(255,255,255,.06); color: #eef; border-radius: 8px; padding: 7px 10px; font-size: 13px; outline: none; }
.cmt-quick input:focus, .cmt-input input:focus { border-color: rgba(122, 162, 247, .6); }
.cmt-quick button, .cmt-input button { border: 0; background: #4A5CF7; color: #fff; border-radius: 8px; padding: 7px 14px; font-size: 13px; cursor: pointer; white-space: nowrap; }
.cmt-quick button:hover, .cmt-input button:hover { background: #3a4ae0; }
.cmt-input { display: flex; gap: 8px; padding: 10px 12px; border-top: 1px solid rgba(255,255,255,.08); }
.cmt-tip { padding: 0 12px 10px; color: #7c8399; font-size: 11px; text-align: center; }
.cmt-err { color: #f87171; }
.cmt-list::-webkit-scrollbar { width: 5px; }
.cmt-list::-webkit-scrollbar-thumb { background: rgba(255,255,255,.16); border-radius: 3px; }
.cmt-list::-webkit-scrollbar-track { background: transparent; }
/* 评论特效：未读角标层 + 呼吸灯 + 根路径 + 流动电子线（与编辑页同款） */
.cmt-badge-layer { position: absolute; inset: 0; pointer-events: none; z-index: 2; overflow: hidden; }
:deep(.cmt-flash) { animation: cmtBreath 1.6s ease-in-out infinite; }
:deep(.cmt-breath-path) { animation: cmtBreathPath 1.6s ease-in-out infinite; }
:deep(.cmt-breath-line) { animation: cmtBreathLine 1.6s ease-in-out infinite; }
@keyframes cmtBreath {
  0%, 100% { filter: drop-shadow(0 0 0 rgba(255,77,79,0)); opacity: 1; }
  50% { filter: drop-shadow(0 0 18px rgba(255,77,79,1)); opacity: .62; }
}
@keyframes cmtBreathPath {
  0%, 100% { filter: drop-shadow(0 0 0 rgba(255,77,79,0)); opacity: 1; }
  50% { filter: drop-shadow(0 0 12px rgba(255,77,79,.9)); opacity: .5; }
}
@keyframes cmtBreathLine {
  0%, 100% { stroke-opacity: 1; }
  50% { stroke-opacity: .15; }
}
:deep(.cmt-flow-line) {
  stroke: #ff4d4f !important;
  stroke-width: 3.2 !important;
  stroke-linecap: round !important;
  fill: none !important;
  stroke-dasharray: 2.5 22 !important;
  animation: cmtFlow 0.9s linear infinite !important;
  opacity: .98 !important;
  pointer-events: none !important;
}
@keyframes cmtFlow {
  to { stroke-dashoffset: -24.5; }
}
</style>

<style>
/* 全局：隐藏 MathJax 辅助数学标记，防止部分浏览器下与主公式叠加显示成两层 */
mjx-assistive-mml { display: none !important; }
</style>
