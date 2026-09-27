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
import MindMap from 'simple-mind-map'
import Drag from 'simple-mind-map/src/plugins/Drag'
import Select from 'simple-mind-map/src/plugins/Select'
import RainbowLines from 'simple-mind-map/src/plugins/RainbowLines'
import Scrollbar from 'simple-mind-map/src/plugins/Scrollbar'
import { THEMES } from '../utils/theme.js'
import { fetchThemes } from '../api/themes'
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
      openNodeMove: false
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
    mind.value.on('node_tree_render_end', () => { pageLoading.value = false })
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
function openAttachmentPreview(node) {
  const url = node.getData('attachmentUrl')
  const name = node.getData('attachmentName') || '附件'
  if (!url) return
  const ext = (name.match(/\.([a-zA-Z0-9]+)$/) || [,''])[1].toLowerCase()
  const isImg = ['png','jpg','jpeg','gif','webp','bmp','ico','avif','svg'].includes(ext)
  const isPdf = ext === 'pdf'
  const isAudio = ['mp3','wav','ogg','m4a','aac','flac'].includes(ext)
  const isVideo = ['mp4','webm','ogv','mov','mkv'].includes(ext)
  const isText = ['txt','md','json','csv','log','xml','yaml','yml','html','htm','js','css','ini','conf'].includes(ext)
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
  if (isImg) {
    const im = document.createElement('img')
    im.src = url; im.style.cssText = 'max-width:100%;max-height:62vh;border-radius:10px;display:block;margin:0 auto'
    body.appendChild(im)
  } else if (isPdf) {
    const ifr = document.createElement('iframe')
    ifr.src = url; ifr.style.cssText = 'width:100%;height:62vh;border:0;border-radius:10px;background:#fff'
    body.appendChild(ifr)
  } else if (isAudio) {
    body.innerHTML = '<audio controls autoplay style="width:100%"></audio>'
    body.querySelector('audio').src = url
  } else if (isVideo) {
    body.innerHTML = '<video controls autoplay style="width:100%;border-radius:10px;background:#000"></video>'
    body.querySelector('video').src = url
  } else if (isText) {
    fetch(url).then(r => r.text()).then(t => {
      const pre = document.createElement('pre')
      pre.textContent = t
      pre.style.cssText = 'max-width:100%;max-height:62vh;overflow:auto;background:#1e1e1e;color:#e6e6e6;padding:14px;border-radius:10px;font-size:13px;white-space:pre-wrap;word-break:break-all;line-height:1.6;margin:0'
      body.appendChild(pre)
    }).catch(() => {
      body.innerHTML = '<div style="color:#909399;font-size:14px;padding:40px 0 16px;text-align:center">无法读取内容</div><a href="' + url + '" target="_blank" rel="noopener" style="display:inline-block;padding:8px 24px;background:#0e7c86;color:#fff;border-radius:6px;text-decoration:none;font-size:14px">下载文件</a>'
    })
  } else {
    body.innerHTML = '<div style="color:#909399;font-size:14px;padding:40px 0 16px;text-align:center">该类型暂不支持在线预览，请下载后查看</div><a href="' + url + '" target="_blank" rel="noopener" style="display:inline-block;padding:8px 24px;background:#0e7c86;color:#fff;border-radius:6px;text-decoration:none;font-size:14px">下载文件</a>'
  }
}

function zoomIn() { mind.value && mind.value.view.enlarge() }
function zoomOut() { mind.value && mind.value.view.narrow() }
function zoomFit() { mind.value && mind.value.view.fit() }

onMounted(async () => {
  needPwd.value = false
  pwd.value = ''
  window.addEventListener('resize', updateBgSize)
  await loadShare()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', updateBgSize)
  if (mind.value) { try { mind.value.dispose && mind.value.dispose() } catch(e){} }
})
</script>

<style scoped>
.share-root { position: relative; height: 100%; width: 100%; }
.share-bg { position: absolute; inset: 0; overflow: hidden; }
.page-loading { position: absolute; inset: 0; z-index: 100; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 14px; background: rgba(255,255,255,.7); backdrop-filter: blur(2px); }
.pl-spinner { width: 44px; height: 44px; border: 4px solid rgba(74,92,247,.18); border-top-color: #4A5CF7; border-radius: 50%; animation: pl-rot .8s linear infinite; }
.pl-text { font-size: 14px; color: #667; }
@keyframes pl-rot { to { transform: rotate(360deg) } }
.share-bg-color { position: absolute; inset: 0; }
.share-bg-image { position: absolute; inset: 0; background-size: cover; background-position: center; background-repeat: no-repeat; }
.share-bg-video { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; object-position: center center; pointer-events: none; z-index: 0; }
#map { position: relative; height: 100%; width: 100%; background: transparent !important; }
</style>
