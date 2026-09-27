<template>
  <div class="editor-root">
    <!-- 顶部工具栏 -->
    <div id="toolbar" class="toolbar">
      <div class="toolbar-left">
        <input id="doc-name" class="doc-name" v-model="docName" title="双击重命名" spellcheck="false" />
        <span id="doc-status" class="status" :class="{ warn: dirty }">{{ dirty ? '未保存' : '已保存' }}</span>
      </div>
      <div class="toolbar-center">
        <button title="新建画布" @click="onNewCanvas"><svg viewBox="0 0 24 24"><path d="M5 4h9l5 5v11H5z" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"/><path d="M14 4v5h5" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"/><path d="M9 13h6M12 10v6" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg></button>
        <button title="返回列表" @click="goList"><svg viewBox="0 0 24 24"><path d="M4 7h5l2-2h9v14H4z" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"/><path d="M4 11h16" stroke="currentColor" stroke-width="1.8"/></svg></button>
        <button title="保存" @click="saveDoc"><svg viewBox="0 0 24 24"><path d="M5 4h13l2 2v14H4V6z" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"/><path d="M8 4v6h8V4M8 20v-6h8v6" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"/></svg></button>
        <div class="export-wrap" @click.stop="exportOpen = !exportOpen">
          <button title="导出" :class="{ active: exportOpen }"><svg viewBox="0 0 24 24"><path d="M12 3v12m0 0l-4-4m4 4l4-4" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/><path d="M5 19h14" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg></button>
          <div v-if="exportOpen" class="export-menu">
            <div class="export-menu-item" @click="runExport('json')"><span class="em-tag" style="background:#f7b731">J</span>JSON 数据</div>
            <div class="export-menu-item" @click="exportUsmind"><span class="em-tag" style="background:#2d98da">U</span>USMIND 压缩包</div>
            <div class="export-menu-item" @click="runExport('png')"><span class="em-tag" style="background:#45b7d1">P</span>PNG 图片</div>
            <div class="export-menu-item" @click="runExport('jpg')"><span class="em-tag" style="background:#e77f67">J</span>JPG 图片</div>
            <div class="export-menu-item" @click="runExport('jpeg')"><span class="em-tag" style="background:#ea8685">G</span>JPEG 图片</div>
            <div class="export-menu-item" @click="runExport('svg')"><span class="em-tag" style="background:#778beb">S</span>SVG 矢量图</div>
            <div class="export-menu-item" @click="runExport('pdf')"><span class="em-tag" style="background:#fc5c65">P</span>PDF 文档</div>
            <div class="export-menu-item" @click="runExport('md')"><span class="em-tag" style="background:#26de81">M</span>Markdown</div>
            <div class="export-menu-sep"></div>
            <div class="export-menu-item" @click="onImportClick"><span class="em-tag" style="background:#a55eea">I</span>导入 JSON</div>
            <div class="export-menu-item" @click="onImportUsmindClick"><span class="em-tag" style="background:#fa8231">U</span>导入 USMIND</div>
          </div>
        </div>
        <input ref="importFileInput" type="file" accept=".json,application/json" style="display:none" @change="onImportFile" />
        <input ref="usmindFileInput" type="file" accept=".usmind,application/octet-stream" style="display:none" @change="onImportUsmindFile" />
        <button id="btn-undo" title="撤销" @click="undo"><svg viewBox="0 0 24 24"><path d="M9 7L4 12l5 5" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/><path d="M4 12h9a7 7 0 0 1 7 7" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg></button>
        <button id="btn-redo" title="重做" @click="redo"><svg viewBox="0 0 24 24"><path d="M15 7l5 5-5 5" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/><path d="M20 12h-9a7 7 0 0 0-7 7" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg></button>
        <button title="样式设置" @click="stylePanelHidden = !stylePanelHidden"><svg viewBox="0 0 24 24"><path d="M4 6h10M18 6h2M4 12h6M14 12h6M4 18h12M20 18h0" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/><circle cx="16" cy="6" r="2.4" fill="none" stroke="currentColor" stroke-width="1.8"/><circle cx="12" cy="12" r="2.4" fill="none" stroke="currentColor" stroke-width="1.8"/><circle cx="18" cy="18" r="2.4" fill="none" stroke="currentColor" stroke-width="1.8"/></svg></button>
        <button title="AI 助手" @click="aiPanelHidden = false"><svg viewBox="0 0 24 24"><path d="M12 3l1.8 4.6L18 9.4l-4.2 1.8L12 16l-1.8-4.8L6 9.4l4.2-1.8z" fill="currentColor"/><path d="M19 15l.8 2 2 .8-2 .8-.8 2-.8-2-2-.8 2-.8z" fill="currentColor"/></svg></button>
      </div>
      <div class="toolbar-right">
        <span class="saved-time">{{ savedTime }}</span>
        <button title="分享" @click="onShare"><svg viewBox="0 0 24 24"><circle cx="6" cy="12" r="3" fill="none" stroke="currentColor" stroke-width="1.8"/><circle cx="18" cy="6" r="3" fill="none" stroke="currentColor" stroke-width="1.8"/><circle cx="18" cy="18" r="3" fill="none" stroke="currentColor" stroke-width="1.8"/><path d="M8.7 10.8l6.6-3.6M8.7 13.2l6.6 3.6" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg></button>
        <button title="全部折叠" @click="collapseAll"><svg viewBox="0 0 24 24"><rect x="4" y="4" width="16" height="16" rx="3" fill="none" stroke="currentColor" stroke-width="1.8"/><path d="M9 12h6" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg></button>
        <button title="全部展开" @click="expandAll"><svg viewBox="0 0 24 24"><rect x="4" y="4" width="16" height="16" rx="3" fill="none" stroke="currentColor" stroke-width="1.8"/><path d="M12 9v6M9 12h6" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg></button>
      </div>
    </div>

    <!-- 画布（三层背景：底色 → 背景图 → 脑图） -->
    <div class="canvas-bg">
      <div class="canvas-bg-color" :style="{ background: bgColorVal }"></div>
      <div v-show="!bgVideoVal" class="canvas-bg-image" :style="{ backgroundImage: bgImageVal ? 'url(' + bgImageVal + ')' : 'none', opacity: bgOpacityVal }"></div>
      <video v-if="bgVideoVal" class="canvas-bg-video" :src="bgVideoVal" autoplay loop muted playsinline :style="{ opacity: bgOpacityVal, transform: 'scale(' + bgZoom + ')' }"></video>
      <div id="map" ref="mapEl"></div>
    </div>

    <!-- 画布滚动条（水平+垂直，透明细条，双向同步平移） -->
    <div class="sb-h" @mousedown.prevent="sbDown('h', $event)" @touchstart.prevent="sbDown('h', $event)">
      <div class="sb-thumb sb-thumb-h" :style="{ left: sbH.pos + '%', width: sbH.size + '%' }"></div>
    </div>
    <div class="sb-v" @mousedown.prevent="sbDown('v', $event)" @touchstart.prevent="sbDown('v', $event)">
      <div class="sb-thumb sb-thumb-v" :style="{ top: sbV.pos + '%', height: sbV.size + '%' }"></div>
    </div>

    <!-- 加载遮罩 -->
    <div v-if="pageLoading" class="page-loading">
      <div class="pl-spinner"></div>
      <div class="pl-text">加载中...</div>
    </div>

    <!-- 底部栏 -->
    <div class="bottom-bar">
      <div class="canvas-bar" ref="canvasBarEl" @mousedown="onBarDragStart" @click.capture="onBarClick">
        <span v-for="(t, i) in canvasTabs" :key="i" class="canvas-tab" :class="{ active: i === docIndex, dragging: tabDragFrom === i, 'drag-over': tabDragOver === i }" @click="switchCanvas(i)" @dblclick="startRename(i)" :draggable="true" @dragstart="onTabDragStart(i, $event)" @dragenter.prevent="onTabDragEnter(i)" @dragover.prevent @drop.prevent="onTabDrop(i, $event)" @dragend="onTabDragEnd">
          <span class="canvas-tab-label">{{ t.name || ('画布' + (i + 1)) }}</span>
          <input v-if="renamingIndex === i" class="canvas-tab-input" v-model="renameInput" @keydown="onRenameKey" @blur="commitRename" @click.stop />
          <span class="canvas-tab-del" @click.stop="delCanvas(i)">×</span>
        </span>
        <button title="新建画布" @click="onNewCanvas">+</button>
      </div>
      <div class="bar-nav" v-if="barOverflow">
        <button title="向左移动" @click="barScroll(-1)">‹</button>
        <button title="向右移动" @click="barScroll(1)">›</button>
      </div>
      <div class="view-bar">
        <div class="layout-drop" :class="{ open: layoutOpen }" @click.stop="layoutOpen = !layoutOpen">
          <span>{{ layoutList.find(l => l.key === layoutKey)?.label || '布局' }}</span>
          <span class="drop-caret">▾</span>
          <div v-if="layoutOpen" class="layout-menu" @click.stop>
            <div v-for="l in layoutList" :key="l.key" class="layout-menu-item" :class="{ active: layoutKey === l.key }" @click="applyLayout(l.key); layoutOpen = false">
              <span class="layout-icon" v-html="l.icon"></span>
              <span>{{ l.label }}</span>
            </div>
          </div>
        </div>
        <span class="view-zoom">
          <button title="缩小" @click="zoomOut">−</button>
          <span id="zoom-val">{{ zoomPct }}</span>
          <button title="放大" @click="zoomIn">+</button>
        </span>
        <button title="居中" @click="zoomFit">⤢</button>
      </div>
    </div>

    <!-- 样式侧栏 -->
    <div id="style-panel" class="style-panel" :class="{ hidden: stylePanelHidden }">
      <div class="sp-header">
        <div class="sp-tabs">
          <button v-for="tab in tabs" :key="tab.key" class="sp-tab" :class="{ active: activeTab === tab.key }" @click="activeTab = tab.key">{{ tab.label }}</button>
        </div>
        <button class="ai-close" @click="stylePanelHidden = true">×</button>
      </div>
      <div class="sp-body">
        <!-- 样式页 -->
        <div v-show="activeTab === 'style'">
          <div class="sp-section">节点</div>
          <div class="sp-row">
            <label>圆角</label>
            <input type="range" min="0" max="30" v-model.number="styleRadius">
            <span class="sp-val">{{ styleRadius }}px</span>
          </div>
          <div class="sp-row">
            <label>字号</label>
            <input type="range" min="12" max="24" v-model.number="styleFont">
            <span class="sp-val">{{ styleFont }}px</span>
          </div>
          <div class="sp-section">连接线</div>
          <div class="sp-row">
            <label>粗细</label>
            <input type="range" min="1" max="6" v-model.number="styleLink">
            <span class="sp-val">{{ styleLink }}px</span>
          </div>
          <div class="sp-section">边框</div>
          <div class="sp-row">
            <label>边框粗细</label>
            <input type="range" min="0" max="4" v-model.number="styleBorder">
            <span class="sp-val">{{ styleBorder }}px</span>
          </div>
          <div class="sp-row">
            <label>边框颜色</label>
            <input type="color" v-model="styleBorderColor">
            <span class="sp-val" style="width:auto;text-transform:none">{{ styleBorderColor }}</span>
          </div>
        </div>
        <!-- 主题页 -->
        <div v-show="activeTab === 'theme'">
          <div class="sp-section">主题模板</div>
          <div class="theme-grid">
            <div v-for="t in themes" :key="t.name" class="theme-card" :class="{ active: currentTheme === t.name }" @click="applyTheme(t.name)">
              <div class="theme-swatches">
                <span v-for="(bc, bi) in (t.branchColors || themes[0].branchColors)" :key="bi" class="theme-swatches-seg" :style="{ background: bc }"></span>
              </div>
              <div class="theme-card-name">{{ t.name }}</div>
            </div>
          </div>
          <div class="sp-section">画布背景</div>
          <div class="sp-row sp-row-bgcolor">
            <div class="sp-color-pick"><input type="color" v-model="bgColor" @input="applyBgColor"></div>
          </div>
        </div>
        <!-- 布局页 -->
        <div v-show="activeTab === 'layout'">
          <div class="sp-section">选择布局</div>
          <div v-for="l in layoutList" :key="l.key" class="layout-item" :class="{ active: layoutKey === l.key }" @click="applyLayout(l.key)">
            <span class="layout-icon" v-html="l.icon"></span>
            <span>{{ l.label }}</span>
          </div>
        </div>
        <!-- 图标页 -->
        <div v-show="activeTab === 'icon'">
          <div v-if="emojiGroups.length > 1" class="sp-fold-toolbar">
            <button class="sp-fold-all" @click="toggleAllIcons">{{ allIconOpen ? '全部收起' : '全部展开' }}</button>
          </div>
          <template v-for="grp in emojiGroups" :key="grp.category">
            <div class="sp-section sp-fold-head" :class="{ open: iconOpen[grp.category] }" @click="toggleIconGroup(grp.category)">
              <span class="sp-fold-arrow">{{ iconOpen[grp.category] ? '▾' : '▸' }}</span>
              <span>{{ grp.category }}</span>
            </div>
            <div v-show="iconOpen[grp.category]" class="icon-grid">
              <button v-for="e in grp.emojis" :key="e" @click="insertEmoji(e)" :title="e">{{ e }}</button>
            </div>
          </template>
          <div class="sp-section">标记</div>
          <div class="icon-grid">
            <button v-for="p in ['P0','P1','P2','P3']" :key="p" @click="setIcon('priority_' + (parseInt(p.slice(1)) + 1))">{{ p }}</button>
          </div>
          <div class="sp-section">序号</div>
          <div class="icon-grid">
            <button v-for="n in 10" :key="n" @click="setIcon('number_' + n)">{{ String(n).padStart(2,'0') }}</button>
          </div>
          <div class="sp-hint">先选中节点，再点击表情：有编辑光标时插到光标处，否则自动进入编辑追加到文字末尾</div>
        </div>
      </div>
    </div>

    <!-- AI 面板 -->
    <div id="ai-panel" class="ai-panel" :class="{ hidden: aiPanelHidden }">
      <div class="ai-header">
        <span>AI 助手</span>
        <button class="ai-close" @click="aiPanelHidden = true">×</button>
      </div>
      <div class="ai-body">
        <div class="ai-mode">
          <button data-mode="create" class="ai-mode-btn active" @click="aiMode='create'; aiActiveMode='create'">创作</button>
          <button data-mode="summary" class="ai-mode-btn" @click="aiMode='summary'; aiActiveMode='summary'">总结</button>
          <button data-mode="chat" class="ai-mode-btn" @click="aiMode='chat'; aiActiveMode='chat'">交互</button>
        </div>
        <textarea id="ai-input" v-model="aiInput" placeholder="输入指令..."></textarea>
        <button id="ai-run" class="ai-run" @click="runAI">生成</button>
        <div id="ai-output" class="ai-output" v-html="aiOutput"></div>
      </div>
    </div>

    <!-- 右键菜单 -->
    <div id="ctx-menu" :class="{ hidden: ctxHidden, closing: ctxClosing, 'no-anim': ctxNoAnim }" :style="ctxStyle" @click.stop>
      <template v-if="ctxMode === 'node'">
        <button data-act="insert-child" @click="ctxAct('insert-child')">插入子主题</button>
        <button data-act="insert-sibling" @click="ctxAct('insert-sibling')">插入同级主题</button>
        <button data-act="edit" @click="ctxAct('edit')">编辑节点</button>
        <div class="ctx-sep"></div>
        <button data-act="insert-image" @click="ctxAct('insert-image')">插入图片</button>
        <button data-act="insert-audio" @click="ctxAct('insert-audio')">插入音频</button>
        <button data-act="insert-video" @click="ctxAct('insert-video')">插入视频</button>
        <button data-act="insert-file" @click="ctxAct('insert-file')">插入文件</button>
        <button data-act="insert-math" @click="ctxAct('insert-math')">插入数学公式</button>
        <div class="ctx-sep"></div>
        <button data-act="font-color" @click="ctxAct('font-color')">字体颜色</button>
        <button data-act="bg-color" @click="ctxAct('bg-color')">背景颜色</button>
        <div class="ctx-sep"></div>
        <button data-act="copy" @click="ctxAct('copy')">复制</button>
        <button data-act="cut" @click="ctxAct('cut')">剪切</button>
        <button data-act="paste" @click="ctxAct('paste')">粘贴</button>
        <div class="ctx-sep"></div>
        <button data-act="collapse" @click="ctxAct('collapse')">折叠</button>
        <button data-act="expand" @click="ctxAct('expand')">展开</button>
        <div class="ctx-sep"></div>
        <button data-act="del" class="ctx-danger" @click="ctxAct('del')">删除节点</button>
      </template>
    </div>
  </div>

  <!-- 编辑态快速插入公式入口 -->
  <button v-if="textEditActive && !mathDialog" class="math-quick" :style="mathQuickPos" title="插入数学公式" @click="openMathQuick">∑</button>

  <!-- 数学公式弹窗 -->
  <div v-if="mathDialog" class="math-mask" @click.self="mathDialog = false">
    <div class="math-dialog">
      <div class="math-title">{{ mathEditMode ? '编辑数学公式（LaTeX）' : '插入数学公式（LaTeX）' }}</div>
      <textarea v-model="mathInput" class="math-input" rows="3" placeholder="例如：E = mc^2  或  \frac{a}{b} + \sqrt{x}" @input="updateMathPreview"></textarea>
      <div class="math-preview">
        <span class="math-preview-label">预览</span>
        <span v-if="mathPreview" v-html="mathPreview"></span>
        <span v-else class="math-preview-empty">输入 LaTeX 后实时预览</span>
      </div>
      <div class="math-actions">
        <button class="math-btn" @click="mathDialog = false">取消</button>
        <button class="math-btn math-btn-primary" @click="insertMathToNode">插入</button>
      </div>
    </div>
  </div>

  <!-- 分享链接弹窗 -->
  <div v-if="shareDialog" class="share-mask" @click.self="shareDialog = false">
    <div class="share-box">
      <div class="share-title">分享导图</div>
      <template v-if="!shareReady">
        <div class="share-tip">开启后访客无需登录即可查看；可设置访问密码，设置后需输入密码才能打开。</div>
        <input v-model="sharePwd" type="password" class="share-link-input" placeholder="访问密码（可选）" @keyup.enter="confirmShare" />
        <div class="share-actions">
          <button class="math-btn math-btn-primary" @click="confirmShare">确认分享</button>
          <button class="math-btn" @click="shareDialog = false">取消</button>
        </div>
      </template>
      <template v-else>
        <div class="share-tip">已开启分享<template v-if="sharePwd">（需密码访问）</template>，链接：</div>
        <input readonly :value="shareLink" class="share-link-input" @focus="e => e.target.select()" />
        <div class="share-actions">
          <button class="math-btn math-btn-primary" @click="copyShare">复制链接</button>
          <button class="math-btn" @click="shareDialog = false">关闭</button>
        </div>
      </template>
    </div>
  </div>

  <!-- 富文本节点内容编辑弹窗 -->
  <div v-if="richEditDialog" class="rich-mask" @click.self="richEditCancel">
    <div class="rich-dialog">
      <div class="rich-title">编辑节点文字</div>
      <div class="rich-tip">节点内的公式、图片、视频等以 [富1] [富2] … 占位标记显示，保存后原样保留；删除标记即删除对应内容。</div>
      <textarea v-model="richPlainText" class="rich-textarea" rows="8" spellcheck="false"></textarea>
      <div class="rich-actions">
        <button class="math-btn" @click="richEditCancel">取消</button>
        <button class="math-btn math-btn-primary" @click="richEditConfirm">保存</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { toast, showConfirm, showPrompt } from '../utils/modal'
import { ref, watch, reactive, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import MindMap from 'simple-mind-map'
import Drag from 'simple-mind-map/src/plugins/Drag'
import Select from 'simple-mind-map/src/plugins/Select'
import RainbowLines from 'simple-mind-map/src/plugins/RainbowLines'
import KeyboardNavigation from 'simple-mind-map/src/plugins/KeyboardNavigation'
import Scrollbar from 'simple-mind-map/src/plugins/Scrollbar'
import Export from 'simple-mind-map/src/plugins/Export'
import ExportPDF from 'simple-mind-map/src/plugins/ExportPDF'
import JSZip from 'jszip'
import { THEMES } from '../utils/theme.js'
import { fetchThemes } from '../api/themes'
import { mindmapApi } from '../api/mindmap'
import { fetchIcons } from '../api/icons'
import { uploadMedia } from '../utils/mediastore'
import { aiApi } from '../api/ai'
import { loadMathJax, tex2svgHtml, extractLatexFromHtml as extractLatexUtil, migrateKatexToSvg } from '../utils/mathRenderer'

// 注册插件
MindMap.usePlugin(Drag)
MindMap.usePlugin(Select)
MindMap.usePlugin(RainbowLines)
MindMap.usePlugin(KeyboardNavigation)
MindMap.usePlugin(Scrollbar)
MindMap.usePlugin(Export)
MindMap.usePlugin(ExportPDF)
THEMES.forEach((t) => { try { MindMap.defineTheme(t.name, t.config) } catch (e) {} })
const themes = ref(THEMES)

const route = useRoute()
const router = useRouter()

const FALLBACK_EMOJI = ['😀','😄','😂','😍','🤔','😎','🤩','😭','😡','🤗','👍','👏','🔥','⭐','❤️','💡','🚀','🎯','🎉','✅','⚠️','📌','💰','📚','☕','🎵','🏆','💪']
const emojiGroups = ref([{ category: '表情包', emojis: FALLBACK_EMOJI }])
// emoji 分类折叠状态（默认收起，点击分类名展开）
const iconOpen = reactive({})
function toggleIconGroup(cat) { iconOpen[cat] = !iconOpen[cat] }
const allIconOpen = computed(() => emojiGroups.value.length > 0 && emojiGroups.value.every(g => iconOpen[g.category]))
function toggleAllIcons() {
  const allOpen = allIconOpen.value
  emojiGroups.value.forEach(g => { iconOpen[g.category] = !allOpen })
}

const LAYOUT_MAP = {
  side: 'mindMap', l: 'logicalStructureLeft', r: 'logicalStructure',
  org: 'organizationStructure', catalog: 'catalogOrganization',
  tl: 'timeline', vtl: 'verticalTimeline', fish: 'fishbone'
}
const LAYOUT_REV = {}
Object.keys(LAYOUT_MAP).forEach((k) => { LAYOUT_REV[LAYOUT_MAP[k]] = k })

const mapEl = ref(null)
const canvasBarEl = ref(null)
const mind = ref(null)
const mindMapId = ref(route.params.id || null)

// 状态
const docName = ref('未命名')
const dirty = ref(false)
const savedTime = ref('')
const canvasTabs = ref([])
const docIndex = ref(0)
const stylePanelHidden = ref(true)
const aiPanelHidden = ref(true)
const activeTab = ref('style')
const layoutKey = ref('r')
const zoomPct = ref('100%')
const currentTheme = ref(themes.value[0].name)
const styleRadius = ref(14)
const styleFont = ref(15)
const styleLink = ref(2)
const styleBorder = ref(0)
const styleBorderColor = ref('#ffffff')
const bgColor = ref('#16181d')
const pageLoading = ref(false)
let loadingToken = 0
let loadingTimer = null
let loadingStart = 0
function hideLoading(tk) {
  if (loadingToken !== tk) return
  const rest = 360 - (Date.now() - loadingStart)
  setTimeout(() => {
    if (loadingToken !== tk) return
    loadingToken = 0
    pageLoading.value = false
    if (mapEl.value) mapEl.value.classList.remove('map-loading')
  }, Math.max(0, rest))
}
const bgColorVal = ref('#16181d')
const bgImageVal = ref('')
const bgVideoVal = ref('')
const bgZoom = ref(1.35)
const sbH = reactive({ size: 100, pos: 0 })
const sbV = reactive({ size: 100, pos: 0 })
let sbDrag = null
let barDrag = null
let suppressBarClick = false
let tabDragFrom = -1
let tabDragOver = -1
const barOverflow = ref(false)
watch(() => canvasTabs.value.length, () => { nextTick(checkBarOverflow) })
let sbTimer = null
const bgOpacityVal = ref(1)
const renamingIndex = ref(-1)
const renameInput = ref('')

// AI
const aiMode = ref('create')
const aiActiveMode = ref('create')
const aiInput = ref('')
const aiOutput = ref('<div class="ai-output-empty">AI 输出将显示在这里</div>')

// 右键菜单
const mathQuickPos = ref({ left: '0px', top: '0px' })
const mathDialog = ref(false)
const mathEditMode = ref(false)
const richEditDialog = ref(false)
const richEditNode = ref(null)
const richEditBox = ref(null)
const richPlainText = ref('')
const richMarks = ref([])
const richMathBar = ref(false)
const richMathInput = ref('')
const richMathPreviewHtml = ref('')
const mathNode = ref(null)
const mathInput = ref('')
const mathPreview = ref('')
const textEditActive = ref(false)
const ctxHidden = ref(true)
const ctxClosing = ref(false)
const ctxNoAnim = ref(false)
const ctxStyle = ref({})
const ctxMode = ref('node')
let ctxNode = null

let manualStyled = new Set()
let autoSaveTimer = null
let beforeUnloadHandler = null

const tabs = [
  { key: 'style', label: '样式' },
  { key: 'theme', label: '主题' },
  { key: 'layout', label: '布局' },
  { key: 'icon', label: '图标' }
]

const layoutOpen = ref(false)
document.addEventListener('click', (e) => { if (!e.target.closest('.layout-drop')) layoutOpen.value = false })

const layoutList = [
  { key: 'side', label: '思维导图（两侧）', icon: '<svg viewBox="0 0 44 26"><circle cx="22" cy="13" r="3.5" fill="currentColor"/><line x1="22" y1="13" x2="5" y2="13" stroke="currentColor" stroke-width="2"/><line x1="5" y1="13" x2="5" y2="4" stroke="currentColor" stroke-width="2"/><line x1="5" y1="13" x2="5" y2="22" stroke="currentColor" stroke-width="2"/><line x1="22" y1="13" x2="39" y2="13" stroke="currentColor" stroke-width="2"/><line x1="39" y1="13" x2="39" y2="4" stroke="currentColor" stroke-width="2"/><line x1="39" y1="13" x2="39" y2="22" stroke="currentColor" stroke-width="2"/></svg>' },
  { key: 'r', label: '逻辑结构图（右）', icon: '<svg viewBox="0 0 44 26"><line x1="38" y1="13" x2="16" y2="13" stroke="currentColor" stroke-width="2"/><line x1="16" y1="13" x2="16" y2="4" stroke="currentColor" stroke-width="2"/><line x1="16" y1="13" x2="16" y2="22" stroke="currentColor" stroke-width="2"/><circle cx="8" cy="13" r="3.5" fill="currentColor"/></svg>' },
  { key: 'l', label: '逻辑结构图（左）', icon: '<svg viewBox="0 0 44 26"><line x1="6" y1="13" x2="28" y2="13" stroke="currentColor" stroke-width="2"/><line x1="28" y1="13" x2="28" y2="4" stroke="currentColor" stroke-width="2"/><line x1="28" y1="13" x2="28" y2="22" stroke="currentColor" stroke-width="2"/><circle cx="36" cy="13" r="3.5" fill="currentColor"/></svg>' },
  { key: 'org', label: '组织结构图', icon: '<svg viewBox="0 0 44 26"><circle cx="22" cy="4" r="3.5" fill="currentColor"/><line x1="22" y1="7.5" x2="22" y2="11" stroke="currentColor" stroke-width="2"/><line x1="22" y1="11" x2="8" y2="11" stroke="currentColor" stroke-width="2"/><line x1="22" y1="11" x2="36" y2="11" stroke="currentColor" stroke-width="2"/><line x1="8" y1="11" x2="8" y2="15" stroke="currentColor" stroke-width="2"/><line x1="36" y1="11" x2="36" y2="15" stroke="currentColor" stroke-width="2"/><line x1="22" y1="11" x2="22" y2="15" stroke="currentColor" stroke-width="2"/><circle cx="8" cy="19" r="3" fill="currentColor"/><circle cx="36" cy="19" r="3" fill="currentColor"/><circle cx="22" cy="19" r="3" fill="currentColor"/></svg>' },
  { key: 'catalog', label: '目录组织图', icon: '<svg viewBox="0 0 44 26"><circle cx="5" cy="13" r="3.5" fill="currentColor"/><line x1="8.5" y1="13" x2="15" y2="13" stroke="currentColor" stroke-width="2"/><line x1="15" y1="13" x2="15" y2="5" stroke="currentColor" stroke-width="2"/><line x1="15" y1="13" x2="15" y2="21" stroke="currentColor" stroke-width="2"/><line x1="15" y1="5" x2="22" y2="5" stroke="currentColor" stroke-width="2"/><line x1="15" y1="21" x2="22" y2="21" stroke="currentColor" stroke-width="2"/><circle cx="26" cy="5" r="2.5" fill="currentColor"/><circle cx="26" cy="21" r="2.5" fill="currentColor"/></svg>' },
  { key: 'tl', label: '时间轴', icon: '<svg viewBox="0 0 44 26"><line x1="4" y1="13" x2="40" y2="13" stroke="currentColor" stroke-width="2"/><circle cx="10" cy="13" r="3" fill="currentColor"/><circle cx="22" cy="13" r="3" fill="currentColor"/><circle cx="34" cy="13" r="3" fill="currentColor"/></svg>' },
  { key: 'vtl', label: '竖向时间轴', icon: '<svg viewBox="0 0 44 26"><line x1="22" y1="3" x2="22" y2="23" stroke="currentColor" stroke-width="2"/><circle cx="22" cy="7" r="3" fill="currentColor"/><circle cx="22" cy="16" r="3" fill="currentColor"/></svg>' },
  { key: 'fish', label: '鱼骨头图', icon: '<svg viewBox="0 0 44 26"><line x1="3" y1="20" x2="41" y2="6" stroke="currentColor" stroke-width="2"/></svg>' }
]

function defaultData() {
  return { root: { data: { text: '中心主题' }, children: [] } }
}
function newTab() {
  return { name: '画布' + (canvasTabs.value.length + 1), layout: 'logicalStructure', theme: themes.value[0].name, data: defaultData(), viewData: null }
}

// 彩虹分支
let rainbowIdx = 0
function hexRgb(hex) {
  if (!hex || typeof hex !== 'string') return [0,0,0]
  const h = hex.replace('#','')
  return [parseInt(h.slice(0,2),16), parseInt(h.slice(2,4),16), parseInt(h.slice(4,6),16)]
}
function mix(hex1, hex2, ratio) {
  const a = hexRgb(hex1), b = hexRgb(hex2)
  const c = a.map((v,i) => Math.round(v*ratio + b[i]*(1-ratio)))
  return '#' + c.map(v => v.toString(16).padStart(2,'0')).join('')
}
function isDarkColor(hex) {
  if (!hex || typeof hex !== 'string') return false
  const [r,g,b] = hexRgb(hex)
  return (r*299 + g*587 + b*114)/1000 < 150
}
function getBranchColors() {
  const doc = canvasTabs.value[docIndex.value]
  const t = themes.value.find(x => x.name === (doc && doc.theme)) || themes.value[0]
  const colors = (t && t.branchColors && t.branchColors.length ? t.branchColors : themes.value[0].branchColors)
  const clean = (colors || []).filter(Boolean)
  return clean.length ? clean : ['#5B8FF9','#2FB36B','#F6A609','#9270CA','#E8684A','#22A7C9','#F08BB4','#8C9B6F']
}
function applyBranchTo(node, depth, branchColor) {
  if (!node || !node.data) return
  if (depth === 0) {
    if (!manualStyled.has(node.data.uid)) {
      delete node.data.fillColor; delete node.data.color; delete node.data.borderColor; delete node.data.lineColor; delete node.data.borderWidth
    }
    ;(node.children||[]).forEach(c => applyBranchTo(c, 1, null))
  } else if (manualStyled.has(node.data.uid)) {
    ;(node.children||[]).forEach(cn => applyBranchTo(cn, depth+1, branchColor))
  } else if (depth === 1) {
    const colors = getBranchColors()
    const c = colors[rainbowIdx++ % colors.length]
    node.data.fillColor = c
    node.data.color = isDarkColor(c) ? '#ffffff' : '#1a1a1a'
    node.data.borderColor = c
    node.data.borderWidth = 1
    node.data.lineColor = c
    ;(node.children||[]).forEach(cn => applyBranchTo(cn, 2, c))
  } else {
    node.data.fillColor = mix(branchColor, '#ffffff', 0.14)
    node.data.color = isDarkColor(branchColor) ? '#475569' : '#334155'
    node.data.borderColor = mix(branchColor, '#ffffff', 0.38)
    node.data.borderWidth = 1
    node.data.lineColor = branchColor
    ;(node.children||[]).forEach(cn => applyBranchTo(cn, depth+1, branchColor))
  }
}
function applyBranchRainbow() {
  if (!mind.value || !mind.value.renderer || !mind.value.renderer.renderTree) return
  rainbowIdx = 0
  applyBranchTo(mind.value.renderer.renderTree, 0, null)
  mind.value.render(() => {})
}
let rainbowTimer = null
function scheduleBranchRainbow() {
  if (rainbowTimer) clearTimeout(rainbowTimer)
  rainbowTimer = setTimeout(() => applyBranchRainbow(), 350)
}

function mediaCardColors(themeName) {
  try {
    const t = themes.value.find(x => x.name === themeName)
    const bg = (t && t.config && t.config.backgroundColor) || '#16181d'
    return { fill: bg, border: bg }
  } catch (e) { return { fill: '#16181d', border: '#16181d' } }
}
function applyStyleState(doc) {
  if (!doc || !doc.styleState) return
  styleRadius.value = doc.styleState.radius
  styleFont.value = doc.styleState.font
  styleLink.value = doc.styleState.link
  styleBorder.value = doc.styleState.border
  styleBorderColor.value = doc.styleState.borderColor
}
function applyMediaCardStyle(root, themeName) {
  if (!root || !root.data) return
  const card = mediaCardColors(themeName)
  const walk = (node) => {
    const d = node.data
    // 只对真正的媒体/附件节点应用卡片样式；公式、富文本节点保留用户自定义样式（圆角/边框等）
    const isMedia = !!(d && d.image)
    const isAtt = !!(d && d.attachmentUrl)
    if (isMedia) {
      d.shape = 'roundedRectangle'; d.borderRadius = 12; d.borderWidth = 0
      d.fillColor = card.fill; d.borderColor = card.border
      const isFill = d.richText === true && typeof d.text === 'string' && d.text.indexOf('overflow:hidden') > -1
      d.paddingX = isFill ? 0 : 6; d.paddingY = isFill ? 0 : 6
      if (d.uid) manualStyled.add(d.uid)
    } else if (isAtt) {
      d.shape = 'roundedRectangle'; d.borderRadius = 12; d.borderWidth = 1
      d.borderColor = '#C9CDD6'; d.borderDasharray = '2 4'; d.fillColor = '#F4F5F7'
      d.paddingX = 6; d.paddingY = 6
      if (d.uid) manualStyled.add(d.uid)
    }
    ;(node.children||[]).forEach(walk)
  }
  walk(root)
}

async function createMind() {
  const tk = ++loadingToken
  pageLoading.value = true
  loadingStart = Date.now()
  clearTimeout(loadingTimer)
  loadingTimer = setTimeout(() => { if (loadingToken === tk) hideLoading(tk) }, 8000)
  if (mapEl.value) mapEl.value.classList.add('map-loading')
  const doc = canvasTabs.value[docIndex.value]
  manualStyled = new Set(doc.manualStyles || [])
  applyStyleState(doc)
  applyMediaCardStyle(doc.data.root, doc.theme)
  mind.value = new MindMap({
    el: mapEl.value,
    data: doc.data.root,
    layout: doc.layout || 'logicalStructure',
    theme: doc.theme || themes.value[0].name,
    viewData: doc.viewData || null,
    fit: true,
    maxHistoryCount: 60,
    textAutoWrapWidth: 99999, // 不自动换行：Ctrl+Enter 手动换行，节点横向延伸
    mousewheelAction: 'zoom',
    defaultInsertSecondLevelNodeText: '新主题',
    defaultInsertBelowSecondLevelNodeText: '新主题',
    useLeftKeySelectionRightKeyDrag: false,
    isLimitMindMapInCanvas: false,
    isLimitMindMapInCanvasWhenHasScrollbar: false,
    enableShortcutOnlyWhenMouseInSvg: false,
    openRealtimeRenderOnNodeTextEdit: true,
    rainbowLinesConfig: { open: true, colorsList: getBranchColors() },
    iconList: [
      { type: 'number', list: Array.from({length:10},(_,i)=>({name:String(i+1),icon:numSvg(i+1)})) },
      { type: 'emoji', list: FALLBACK_EMOJI.map(e=>({name:e,icon:emojiSvg(e)})) }
    ]
  })
  bindMindEvents()
  const t0name = doc.theme || themes.value[0].name
  ;[400, 1200].forEach((d) => setTimeout(() => { try { applyThemeFonts(t0name) } catch (e) {} }, d))
  mind.value.keyCommand.addShortcut('Control+z', () => mind.value.execCommand('BACK'))
  mind.value.keyCommand.addShortcut('Control+y', () => mind.value.execCommand('FORWARD'))
  layoutKey.value = LAYOUT_REV[doc.layout] || 'r'
  currentTheme.value = doc.theme || themes.value[0].name
  applyCanvasBackground(currentTheme.value)
  applyBranchRainbow()
  markSaved()
  mind.value.view.fit()
  ;[300, 900, 1800].forEach((d) => setTimeout(() => { try { centerRoot(); updateScrollbars() } catch (e) {} }, d))
  migrateDocMath(doc)
  try { mind.value.render() } catch (e) {}
}
function numSvg(n) { return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32" width="32" height="32"><rect x="1" y="1" width="30" height="30" rx="7" fill="#eef2ff"/><text x="16" y="23" font-size="17" font-weight="bold" text-anchor="middle" fill="#4A5CF7">'+n+'</text></svg>' }
function emojiSvg(e) { return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32" width="32" height="32"><text x="16" y="26" font-size="26" text-anchor="middle">'+e+'</text></svg>' }

function bindMindEvents() {
  mind.value.on('data_change', () => { markDirty(); scheduleAutoSave(); scheduleBranchRainbow(); clearTimeout(sbTimer); sbTimer = setTimeout(updateScrollbars, 400) })
  mind.value.on('view_data_change', (vd) => { try { updateScrollbars() } catch (e) {}; if (canvasTabs.value[docIndex.value]) canvasTabs.value[docIndex.value].viewData = vd; applyLazyView() })
  mind.value.on('scale', (s) => { zoomPct.value = Math.round(s*100) + '%'; try { updateScrollbars() } catch (e) {}; applyLazyView() })
  mind.value.on('back_forward', (cur, total) => {
    const bu = document.getElementById('btn-undo'); const br = document.getElementById('btn-redo')
    if (bu) bu.disabled = cur <= 0
    if (br) br.disabled = cur >= total-1
  })
  mind.value.on('node_contextmenu', (e, node) => { e.preventDefault(); if (node && node.nodeData) showCtxMenu(e, node) })
  mind.value.on('node_click', () => hideCtxMenu())
  // Ctrl+Enter：节点编辑框内手动换行（Enter 由库确认编辑）
  const onCtrlEnterKeydown = (e) => {
    if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) {
      const w = document.querySelector('.smm-node-edit-wrap')
      if (w && w.getBoundingClientRect().width > 0) {
        e.preventDefault(); e.stopPropagation()
        try { document.execCommand('insertLineBreak') } catch (err) {}
        w.dispatchEvent(new Event('input', { bubbles: true }))
      }
    }
  }
  document.addEventListener('keydown', onCtrlEnterKeydown, true)
  onBeforeUnmount(() => { document.removeEventListener('keydown', onCtrlEnterKeydown, true); window.removeEventListener('resize', updateCanvasBgSize) })
  onBeforeUnmount(() => { document.removeEventListener('mousemove', sbMove); document.removeEventListener('mouseup', sbUp); document.removeEventListener('touchmove', sbMove); document.removeEventListener('touchend', sbUp); document.removeEventListener('mousemove', rootPanMove); document.removeEventListener('mouseup', rootPanEnd); if (mapEl.value) { mapEl.value.removeEventListener('touchstart', onMapTouchStart); mapEl.value.removeEventListener('touchmove', onMapTouchMove); mapEl.value.removeEventListener('touchend', onMapTouchEnd); mapEl.value.removeEventListener('touchcancel', onMapTouchEnd) } })

  // KaTeX 字体异步加载：字体就绪后重渲染，修复公式行高按 fallback 字体测量导致的塌陷
  if (document.fonts && document.fonts.ready) {
    document.fonts.ready.then(() => {
      setTimeout(() => { try { if (mind.value) mind.value.render() } catch (e) {} }, 300)
    })
  }

  // 双击公式节点 → 打开公式编辑弹窗（库默认会开文本编辑框，这里接管）
  mind.value.on('node_dblclick', (node, e) => {
    try {
      const text = node.getData('text') || ''
      const rich = node.getData('richText') === true
      if (rich && (text.indexOf('katex') >= 0 || text.indexOf('mjx-container') >= 0 || text.indexOf('MathJax') >= 0)) {
        try { mind.value.renderer.textEdit.hideEditTextBox() } catch (err) {}
        openMathEdit(node)
      }
    } catch (err) { console.warn(err) }
  })
  mind.value.on('draw_click', () => hideCtxMenu())
  mind.value.on('node_dragging', () => mapEl.value.classList.add('no-anim'))
  mind.value.on('node_dragend', () => mapEl.value.classList.remove('no-anim'))
  mind.value.on('node_text_edit_change', () => mapEl.value.classList.add('no-anim'))
mind.value.on('hide_text_edit', () => textEditActive.value = false)
  // 编辑框聚焦/失焦切换快速入口
  const onEditFocusIn = (e) => {
    if (e.target && e.target.classList && e.target.classList.contains('smm-node-edit-wrap')) {
      textEditActive.value = true
      const r = e.target.getBoundingClientRect()
      mathQuickPos.value = { left: Math.min(r.right + 10, window.innerWidth - 60) + 'px', top: r.top + 'px' }
    }
  }
  const onEditFocusOut = (e) => {
    if (e.target && e.target.classList && e.target.classList.contains('smm-node-edit-wrap')) {
      setTimeout(() => { if (!mind.value.renderer.textEdit.showTextEdit) textEditActive.value = false }, 250)
    }
  }
  document.addEventListener('focusin', onEditFocusIn)
  document.addEventListener('focusout', onEditFocusOut)
  mind.value.on('node_tree_render_end', () => { try { updateScrollbars() } catch (e) {}; clearTimeout(loadingTimer); if (!mind.value.editNodeClassList || !mind.value.editNodeClassList.length) mapEl.value.classList.remove('no-anim'); if (loadingToken > 0) hideLoading(loadingToken); applyLazyView() })
  mind.value.on('node_attachmentClick', (node) => openAttachmentPreview(node))
}

function markDirty() { dirty.value = true }
function markSaved() {
  dirty.value = false
  const d = new Date()
  savedTime.value = '最近保存 ' + d.getHours() + ':' + String(d.getMinutes()).padStart(2,'0')
}

async function saveDoc() {
  if (!mind.value || !mindMapId.value) return
  const doc = canvasTabs.value[docIndex.value]
  const prevRoot = doc.data && doc.data.root && doc.data.root.data
  const data = mind.value.getData()
  // setData 重建可能丢失根节点边框/形状字段，保存前补写：用户本次设置的边框，或加载时的原边框
  if (data && data.data) {
    if (styleBorder.value > 0) {
      data.data.borderWidth = styleBorder.value
      data.data.borderColor = styleBorderColor.value
    } else if (prevRoot && prevRoot.borderWidth > 0 && !data.data.borderWidth) {
      data.data.borderWidth = prevRoot.borderWidth
      data.data.borderColor = prevRoot.borderColor
    }
  }
  if (data && data.data && styleRadius.value > 0 && !data.data.shape) {
    data.data.shape = 'rectangle'
  }
  doc.data = { root: data }
  doc.viewData = mind.value.view.getTransformData()
  doc.manualStyles = Array.from(manualStyled)
  doc.styleState = { radius: styleRadius.value, font: styleFont.value, link: styleLink.value, border: styleBorder.value, borderColor: styleBorderColor.value }
  doc.name = docName.value
  doc.layout = mind.value.opt.layout || 'logicalStructure'
  doc.theme = mind.value.opt.theme || themes.value[0].name
  try {
    await mindmapApi.update(mindMapId.value, {
      name: docName.value,
      data: { root: data, _tabs: canvasTabs.value, _activeTab: docIndex.value },
      layout: doc.layout,
      theme: doc.theme,
      viewData: doc.viewData,
      manualStyles: Array.from(manualStyled)
    })
    markSaved()
  } catch (e) {
    console.warn('保存失败:', e)
  }
}

function scheduleAutoSave() {
  if (autoSaveTimer) clearTimeout(autoSaveTimer)
  autoSaveTimer = setTimeout(() => { if (dirty.value) saveDoc() }, 3000)
}

// 旧 KaTeX 公式数据迁移为 MathJax SVG（foreignObject 内 KaTeX 多行矩阵会行重叠塌陷）
async function migrateDocMath(doc) {
  const root = doc && doc.data && doc.data.root
  if (!root) return
  const nodes = []
  const walk = (n) => {
    if (!n || !n.data) return
    const t = n.data.text || ''
    if (n.data.richText === true && t.indexOf('katex') >= 0) nodes.push(n)
    if (n.children) n.children.forEach(walk)
  }
  walk(root)
  if (!nodes.length) return
  await loadMathJax()
  let changed = false
  for (const n of nodes) {
    const t = n.data.text || ''
    if (t.indexOf('katex') >= 0) {
      try {
        const migrated = await migrateKatexToSvg(t)
        if (migrated !== t) { n.data.text = migrated; changed = true }
      } catch (e) { console.warn('公式迁移失败', e) }
    }
  }
  if (changed) {
    try { if (mind.value) mind.value.setData(root) } catch (e) {}
    if (doc.viewData) {
      let vd = doc.viewData
      if (typeof vd === 'string') { try { vd = JSON.parse(vd) } catch (e) { vd = null } }
      if (vd && mind.value) { try { mind.value.view.setTransformData(vd) } catch (e) {} }
    }
    markDirty()
    saveDoc()
  }
}

async function loadDoc() {
  const tk = ++loadingToken
  pageLoading.value = true
  loadingStart = Date.now()
  clearTimeout(loadingTimer)
  loadingTimer = setTimeout(() => { if (loadingToken === tk) hideLoading(tk) }, 8000)
  if (mapEl.value) mapEl.value.classList.add('map-loading')
  await nextTick()
  const doc = canvasTabs.value[docIndex.value]
  manualStyled = new Set(doc.manualStyles || [])
  docName.value = doc.name || '未命名'
  applyStyleState(doc)
  applyMediaCardStyle(doc.data.root, doc.theme)
  if (mind.value) {
    const docRoot = (doc.data && doc.data.root) ? doc.data.root : defaultData()
    try { mind.value.setData(docRoot) } catch (e) { console.error('setData失败', e) }
    mind.value.view.fit()
    ;[300, 900, 1800].forEach((d) => setTimeout(() => { try { centerRoot(); updateScrollbars() } catch (e) {} }, d))
    if (doc.layout) { mind.value.setLayout(doc.layout); layoutKey.value = LAYOUT_REV[doc.layout] || 'r' }
    mind.value.setTheme(doc.theme)
    ;[400, 1200].forEach((d) => setTimeout(() => { try { applyThemeFonts(doc.theme) } catch (e) {} }, d))
    applyCanvasBackground(doc.theme)
    currentTheme.value = doc.theme
  }
  applyBranchRainbow()
  markSaved()
}

async function switchCanvas(i) {
  if (i === docIndex.value) return
  await saveDoc()
  docIndex.value = i
  await loadDoc()
}

function onNewCanvas() {
  saveDoc()
  canvasTabs.value.push(newTab())
  docIndex.value = canvasTabs.value.length - 1
  loadDoc()
  markDirty()
  nextTick(checkBarOverflow)
}

async function delCanvas(i) {
  if (canvasTabs.value.length <= 1) { toast('至少保留一个画布'); return }
  if (!(await showConfirm({ title: '删除画布', message: '删除画布「' + (canvasTabs.value[i].name || ('画布'+(i+1))) + '」？' }))) return
  canvasTabs.value.splice(i, 1)
  if (i === docIndex.value) {
    docIndex.value = Math.min(docIndex.value, canvasTabs.value.length - 1)
    loadDoc()
  } else if (i < docIndex.value) docIndex.value--
  nextTick(checkBarOverflow)
  markDirty()
}

function startRename(i) {
  renamingIndex.value = i
  renameInput.value = canvasTabs.value[i].name || ''
}
function onRenameKey(e) {
  if (e.key === 'Enter') commitRename()
  else if (e.key === 'Escape') renamingIndex.value = -1
}
function commitRename() {
  const i = renamingIndex.value
  if (i >= 0 && renameInput.value.trim()) {
    canvasTabs.value[i].name = renameInput.value.trim()
    if (i === docIndex.value) docName.value = renameInput.value.trim()
  }
  renamingIndex.value = -1
  markDirty()
}

// 布局/缩放
function applyLayout(key) {
  canvasTabs.value[docIndex.value].layout = LAYOUT_MAP[key]
  mind.value.setLayout(LAYOUT_MAP[key])
  layoutKey.value = key
  markDirty()
}
function zoomIn() { mind.value.view.enlarge() }
function zoomOut() { mind.value.view.narrow() }
function zoomFit() { mind.value.view.fit(); setTimeout(() => { centerRoot(); updateScrollbars() }, 80) }

// ===== 画布滚动条（双向同步平移） =====
function centerRoot() {
  const svg = document.querySelector('#map svg')
  if (!svg || !mind.value || !mapEl.value) return
  const nodes = svg.querySelectorAll('.smm-node')
  let root = null, minX = Infinity
  nodes.forEach((n) => {
    const tf = n.getAttribute('transform')
    if (!tf || tf.indexOf('matrix(') < 0) return
    const nums = tf.replace('matrix(', '').replace(')', '').split(',').map(v => parseFloat(v))
    if (nums.length >= 6 && isFinite(nums[4]) && nums[4] < minX) { minX = nums[4]; root = n }
  })
  if (!root) return
  const r = root.getBoundingClientRect()
  const st = getViewState()
  const cx = r.x + r.width / 2, cy = r.y + r.height / 2
  const VW = mapEl.value.clientWidth, VH = mapEl.value.clientHeight
  setViewPan(st.x + (VW / 2 - cx), st.y + (VH / 2 - cy))
}

function getContentBounds() {
  return domContentBounds()
}

// DOM 兜底：从渲染后的 SVG 节点算包围盒（布局坐标 = 节点自身 transform，屏幕尺寸 / scale）
function domContentBounds() {
  const svg = document.querySelector('#map svg')
  if (!svg) return null
  const nodes = svg.querySelectorAll('.smm-node')
  let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity
  let sc = 1
  try { const st = mind.value ? getViewState() : null; if (st && st.scale) sc = st.scale } catch (e) {}
  nodes.forEach((n) => {
    const tf = n.getAttribute('transform')
    if (!tf || tf.indexOf('matrix(') < 0) return
    const nums = tf.replace('matrix(', '').replace(')', '').split(',').map(v => parseFloat(v))
    if (nums.length < 6 || !isFinite(nums[4]) || !isFinite(nums[5])) return
    const x = nums[4], y = nums[5]
    const r = n.getBoundingClientRect()
    const w = r.width / sc, h = r.height / sc
    if (x < minX) minX = x
    if (y < minY) minY = y
    if (x + w > maxX) maxX = x + w
    if (y + h > maxY) maxY = y + h
  })
  if (!isFinite(minX) || !isFinite(maxX)) return null
  return { left: minX, top: minY, width: maxX - minX, height: maxY - minY }
}

function getViewState() {
  const vd = mind.value.view.getTransformData()
  return (vd && vd.state) ? vd.state : vd
}

function onBarDragStart(e) {
  if (e.button !== 0) return
  if (e.target && e.target.closest && e.target.closest('.canvas-tab')) return
  const el = canvasBarEl.value
  if (!el) return
  barDrag = { startX: e.clientX, startLeft: el.scrollLeft, moved: false }
  document.addEventListener('mousemove', onBarDragMove)
  document.addEventListener('mouseup', onBarDragEnd)
  e.preventDefault()
}
function onBarDragMove(e) {
  if (!barDrag) return
  const el = canvasBarEl.value
  if (!el) return
  const dx = e.clientX - barDrag.startX
  if (Math.abs(dx) > 3) barDrag.moved = true
  el.scrollLeft = barDrag.startLeft - dx
}
function onBarDragEnd() {
  const wasMoved = barDrag && barDrag.moved
  barDrag = null
  document.removeEventListener('mousemove', onBarDragMove)
  document.removeEventListener('mouseup', onBarDragEnd)
  if (wasMoved) {
    suppressBarClick = true
    setTimeout(() => { suppressBarClick = false }, 80)
  }
}
function onBarClick(e) {
  if (suppressBarClick) { e.stopPropagation(); e.preventDefault(); suppressBarClick = false }
}
function checkBarOverflow() {
  const el = canvasBarEl.value
  barOverflow.value = !!(el && el.scrollWidth > el.clientWidth + 2)
}
function barScroll(dir) {
  const el = canvasBarEl.value
  if (!el) return
  el.scrollBy({ left: dir * Math.max(200, el.clientWidth * 0.6), behavior: 'smooth' })
}
function onTabDragStart(i, e) {
  tabDragFrom = i
  if (e.dataTransfer) { e.dataTransfer.effectAllowed = 'move'; e.dataTransfer.setData('text/plain', String(i)) }
}
function onTabDragEnter(i) {
  tabDragOver = i
}
function onTabDrop(i) {
  const from = tabDragFrom
  tabDragFrom = -1
  tabDragOver = -1
  if (from < 0 || from === i) return
  const arr = canvasTabs.value
  const [item] = arr.splice(from, 1)
  arr.splice(i, 0, item)
  if (docIndex.value === from) docIndex.value = i
  else if (docIndex.value > from && docIndex.value <= i) docIndex.value--
  else if (docIndex.value < from && docIndex.value >= i) docIndex.value++
  markDirty()
}
function onTabDragEnd() { tabDragFrom = -1; tabDragOver = -1 }

function setViewPan(nx, ny) {
  const view = mind.value.view
  const vd = view.getTransformData()
  if (!vd || !vd.state) return
  view.translateXY(nx - vd.state.x, ny - vd.state.y)
}

function updateScrollbars() {
  if (!mind.value || !mapEl.value) return
  const st = getViewState()
  const b = getContentBounds()
  const VW = mapEl.value.clientWidth, VH = mapEl.value.clientHeight
  if (!b) { sbH.size = 100; sbV.size = 100; sbH.pos = 0; sbV.pos = 0; return }
  const cw = b.width * st.scale, ch = b.height * st.scale
  const minX = VW - (b.left + b.width) * st.scale
  const maxX = -b.left * st.scale
  const rangeX = maxX - minX
  if (rangeX > 0.5) {
    sbH.size = Math.max(10, Math.min(100, VW / cw * 100))
    sbH.pos = Math.max(0, Math.min(100, (st.x - minX) / rangeX * 100))
  } else { sbH.size = 100; sbH.pos = 0 }
  const minY = VH - (b.top + b.height) * st.scale
  const maxY = -b.top * st.scale
  const rangeY = maxY - minY
  if (rangeY > 0.5) {
    sbV.size = Math.max(10, Math.min(100, VH / ch * 100))
    sbV.pos = Math.max(0, Math.min(100, (st.y - minY) / rangeY * 100))
  } else { sbV.size = 100; sbV.pos = 0 }
}

function sbDown(axis, e) {
  if (!mind.value || !mapEl.value) return
  const st = getViewState()
  const b = getContentBounds()
  if (!st || !b) return
  const el = axis === 'h' ? document.querySelector('.sb-h') : document.querySelector('.sb-v')
  if (!el) return
  const rect = el.getBoundingClientRect()
  sbDrag = { axis, st, b, rect, VW: mapEl.value.clientWidth, VH: mapEl.value.clientHeight }
  sbMove(e)
  document.addEventListener('mousemove', sbMove)
  document.addEventListener('mouseup', sbUp)
  document.addEventListener('touchmove', sbMove, { passive: false })
  document.addEventListener('touchend', sbUp)
}

function sbMove(e) {
  if (!sbDrag) return
  e.preventDefault && e.preventDefault()
  const t = e.touches ? e.touches[0] : e
  const isH = sbDrag.axis === 'h'
  const rect = sbDrag.rect
  const p = Math.max(0, Math.min(1, isH ? (t.clientX - rect.left) / rect.width : (t.clientY - rect.top) / rect.height))
  const b = sbDrag.b, st = sbDrag.st
  let nx = st.x, ny = st.y
  if (isH) {
    const cw = b.width * st.scale
    const minX = sbDrag.VW - (b.left + b.width) * st.scale
    const maxX = -b.left * st.scale
    nx = minX + p * (maxX - minX)
  } else {
    const ch = b.height * st.scale
    const minY = sbDrag.VH - (b.top + b.height) * st.scale
    const maxY = -b.top * st.scale
    ny = minY + p * (maxY - minY)
  }
  try { setViewPan(nx, ny) } catch (e) {}
}

function sbUp() {
  sbDrag = null
  document.removeEventListener('mousemove', sbMove)
  document.removeEventListener('mouseup', sbUp)
  document.removeEventListener('touchmove', sbMove)
  document.removeEventListener('touchend', sbUp)
}

// ===== 根节点按住拖动 = 平移画布 =====
let rootPan = null
function onMapMouseDown(e) {
  if (e.button !== 0) return
  const t = e.target
  const nodeEl = t && t.closest ? t.closest('.smm-node') : null
  if (!nodeEl || !mind.value || !mapEl.value) return
  // 根节点特征：库对中心主题禁用拖拽（cursor default）
  if (nodeEl.style.cursor !== 'default') return
  rootPan = { sx: e.clientX, sy: e.clientY, st: getViewState(), moved: false }
  document.addEventListener('mousemove', rootPanMove)
  document.addEventListener('mouseup', rootPanEnd)
}
let touchPan = null
function onMapTouchStart(e) {
  if (e.touches.length !== 1) return
  const t = e.target
  if (t && t.closest && (t.closest('.sb-h') || t.closest('.sb-v') || t.closest('.toolbar') || t.closest('.style-panel') || t.closest('.ai-panel') || t.closest('.bar-nav') || t.closest('.view-bar') || t.closest('#ctx-menu') || t.closest('.math-dialog') || t.closest('.rich-dialog') || t.closest('#att-preview'))) return
  touchPan = { sx: e.touches[0].clientX, sy: e.touches[0].clientY, st: getViewState(), moved: false }
}
function onMapTouchMove(e) {
  if (!touchPan || !mind.value) return
  if (e.touches.length !== 1) { touchPan = null; return }
  const t = e.touches[0]
  const dx = t.clientX - touchPan.sx, dy = t.clientY - touchPan.sy
  if (!touchPan.moved && Math.abs(dx) + Math.abs(dy) > 6) touchPan.moved = true
  if (touchPan.moved) {
    e.preventDefault()
    e.stopPropagation()
    try { setViewPan(touchPan.st.x + dx, touchPan.st.y + dy) } catch (e2) {}
  }
}
function onMapTouchEnd() { touchPan = null }

function rootPanMove(e) {
  if (!rootPan || !mind.value) return
  const dx = e.clientX - rootPan.sx, dy = e.clientY - rootPan.sy
  if (!rootPan.moved && Math.abs(dx) + Math.abs(dy) > 6) rootPan.moved = true
  if (rootPan.moved) {
    e.preventDefault()
    try { setViewPan(rootPan.st.x + dx, rootPan.st.y + dy) } catch (e2) {}
  }
}
function rootPanEnd() {
  rootPan = null
  document.removeEventListener('mousemove', rootPanMove)
  document.removeEventListener('mouseup', rootPanEnd)
}
function undo() { mind.value.execCommand('BACK') }
function redo() { mind.value.execCommand('FORWARD') }
function collapseAll() { mind.value.execCommand('UNEXPAND_ALL') }
function expandAll() { mind.value.execCommand('EXPAND_ALL') }

function applyThemeFonts(name) {
  const t = themes.value.find(x => x.name === name)
  if (!t || !t.config) return
  const cfg = t.config
  const r = mind.value ? mind.value.renderer : null
  if (!r || !r.root) return
  const walk = (node, depth) => {
    if (!node) return
    const d = node.getData ? node.getData() : null
    if (d) {
      const lv = depth === 0 ? (cfg.root || {}) : (depth === 1 ? (cfg.second || {}) : (cfg.node || {}))
      const style = {}
      if (lv.fontSize) style.fontSize = lv.fontSize
      if (lv.fontFamily) style.fontFamily = lv.fontFamily
      if (Object.keys(style).length) {
        try { mind.value.execCommand('SET_NODE_STYLES', node, style) } catch (e) {}
      }
    }
    const children = node.children || []
    children.forEach(c => walk(c, depth + 1))
  }
  walk(r.root, 0)
}

function applyTheme(name) {
  canvasTabs.value[docIndex.value].theme = name
  mind.value.setTheme(name)
  ;[400, 1200].forEach((d) => setTimeout(() => { try { applyThemeFonts(name) } catch (e) {} }, d))
  applyCanvasBackground(name)
  currentTheme.value = name
  if (mind.value.opt) {
    const t = themes.value.find(x => x.name === name)
    mind.value.opt.rainbowLinesConfig = { open: true, colorsList: (t && t.branchColors) || themes.value[0].branchColors }
  }
  applyBranchRainbow()
  markDirty()
}
let lazyViewT = null
function applyLazyView() {
  if (lazyViewT) return
  lazyViewT = requestAnimationFrame(() => {
    lazyViewT = null
    const container = mapEl.value
    if (!container) return
    const cw = container.clientWidth, ch = container.clientHeight
    const pad = 400
    const nodes = container.querySelectorAll('svg g:has(> rect)')
    nodes.forEach(el => {
      const r = el.getBoundingClientRect()
      const vis = r.right > -pad && r.left < cw + pad && r.bottom > -pad && r.top < ch + pad
      el.style.visibility = vis ? 'visible' : 'hidden'
    })
  })
}
function applyCanvasBackground(name) {
  const t = themes.value.find(x => x.name === name) || themes.value[0]
  const cfg = (t && t.config) || {}
  bgColorVal.value = cfg.backgroundColor || '#16181d'
  bgImageVal.value = (cfg.backgroundImage && cfg.backgroundImage !== 'none') ? cfg.backgroundImage : ''
  bgVideoVal.value = (cfg.backgroundVideo && cfg.backgroundVideo !== 'none') ? cfg.backgroundVideo : ''
  bgVideoVal.value = (cfg.backgroundVideo && cfg.backgroundVideo !== 'none') ? cfg.backgroundVideo : ''
  bgOpacityVal.value = (typeof cfg.backgroundOpacity === 'number') ? cfg.backgroundOpacity : 1
  bgZoom.value = (typeof cfg.backgroundZoom === 'number' && cfg.backgroundZoom >= 1) ? cfg.backgroundZoom : 1
  bgColor.value = bgColorVal.value
  if (mapEl.value) mapEl.value.style.background = 'transparent'
  updateCanvasBgSize()
}
let canvasBgLoader = null
function updateCanvasBgSize() {
  const img = document.querySelector('.canvas-bg-image')
  const url = bgImageVal.value
  if (!img || !url) return
  if (canvasBgLoader) { canvasBgLoader.onload = null; canvasBgLoader = null }
  canvasBgLoader = new Image()
  canvasBgLoader.src = url
  canvasBgLoader.onload = () => {
    const vw = img.clientWidth || window.innerWidth
    const vh = img.clientHeight || window.innerHeight
    if (!vw || !vh || !canvasBgLoader) return
    const scale = Math.max(vw / canvasBgLoader.naturalWidth, vh / canvasBgLoader.naturalHeight) * bgZoom.value
    img.style.backgroundSize = Math.round(canvasBgLoader.naturalWidth * scale) + 'px ' + Math.round(canvasBgLoader.naturalHeight * scale) + 'px'
    img.style.backgroundPosition = 'center center'
  }
}
function applyBgColor() {
  bgColorVal.value = bgColor.value
  mind.value.setThemeConfig({ backgroundColor: bgColor.value })
}
let styleTimer = null
function applyStylePatch() {
  // 主题配置（渲染回退值）+ 节点数据（保存值）双写
  mind.value.setThemeConfig({
    lineWidth: styleLink.value,
    root: { borderRadius: styleRadius.value, borderWidth: styleBorder.value, borderColor: styleBorderColor.value },
    second: { borderRadius: styleRadius.value, borderWidth: styleBorder.value, borderColor: styleBorderColor.value },
    node: { borderRadius: styleRadius.value, borderWidth: styleBorder.value, borderColor: styleBorderColor.value }
  })
  // 用官方命令 SET_NODE_STYLES 逐节点应用（直接更新 node.nodeData 并重绘，可靠生效且可保存）
  // 注意：必须遍历节点实例树 renderer.root（纯数据树 renderTree 的节点没有 nodeData 属性，命令会静默失败）
  const r = mind.value.renderer
  if (!r || !r.root) return
  const walk = (node) => {
    if (!node) return
    const d = node.getData ? node.getData() : null
    if (d) {
      // 手动样式豁免彩虹色覆盖
      if (d.uid) manualStyled.add(d.uid)
      // 媒体节点保持原形状，普通节点用 rectangle 使 borderRadius 生效（roundedRectangle 圆角=高度一半，与圆角值无关）
      const style = {
        shape: (d.attachmentUrl || d.image) ? (d.shape || 'roundedRectangle') : 'rectangle',
        borderRadius: styleRadius.value,
        fontSize: styleFont.value,
        borderWidth: styleBorder.value,
        borderColor: styleBorderColor.value
      }
      try { mind.value.execCommand('SET_NODE_STYLES', node, style) } catch (e) {}
    }
    const children = node.children || []
    children.forEach(walk)
  }
  walk(r.root)
}

// 样式参数变化时应用（watch 保证滑块拖动可靠触发）
watch([styleRadius, styleFont, styleLink, styleBorder, styleBorderColor], () => {
  if (styleTimer) clearTimeout(styleTimer)
  styleTimer = setTimeout(() => applyStylePatch(), 120)
})
function updateMathPreview() {
  const raw = mathInput.value.trim()
  if (!raw) { mathPreview.value = ''; return }
  loadMathJax().then(() => {
    mathPreview.value = tex2svgHtml(raw, true)
  }).catch(() => { mathPreview.value = '' })
}

let savedMathRange = null
let mathEditText = ''

function extractLatexFromHtml(html) {
  return extractLatexUtil(html)
}
function replaceKatexInText(text, newHtml) {
  try {
    const div = document.createElement('div')
    div.innerHTML = text
    const els = div.querySelectorAll('.katex, mjx-container')
    if (!els.length) return null
    els.forEach(el => {
      const tmp = document.createElement('span')
      tmp.innerHTML = newHtml
      el.replaceWith.apply(el, Array.from(tmp.childNodes))
    })
    return div.innerHTML
  } catch (err) { return null }
}
function richEscHtml(s) {
  return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}
function openRichEdit(node) {
  richEditNode.value = node
  const html = node.getData('text') || ''
  // 分离文字与富元素（公式/图片/视频/音频/附件/链接）：富元素替换为占位标记，保存时原样保留
  const div = document.createElement('div')
  div.innerHTML = html
  // 先把 br/p/div 换行转\n，便于纯文字编辑
  div.querySelectorAll('br').forEach(br => br.replaceWith(document.createTextNode('\n')))
  div.querySelectorAll('p, div').forEach(p => { p.appendChild(document.createTextNode('\n')) })
  const marks = []
  div.querySelectorAll('.katex, mjx-container, img, video, audio, a[href]').forEach((el, idx) => {
    const mark = '[富' + (idx + 1) + ']'
    marks.push({ mark, html: el.outerHTML })
    el.replaceWith(document.createTextNode(mark))
  })
  richMarks.value = marks
  richPlainText.value = div.textContent
  richEditDialog.value = true
  nextTick(() => { try { const el = document.querySelector('.rich-textarea'); if (el) { el.focus(); el.setSelectionRange(el.value.length, el.value.length) } } catch (e) {} })
}
function richEditConfirm() {
  const node = richEditNode.value
  if (node) {
    // 纯文字转义，占位标记替换回原富元素 HTML（标记无特殊 HTML 字符，转义后不变）
    let html = richEscHtml(richPlainText.value).replace(/\n/g, '<br>')
    richMarks.value.forEach(m => { html = html.split(m.mark).join(m.html) })
    try { mind.value.renderer.setNodeDataRender(node, { text: html, richText: true }) } catch (err) {}
    saveDoc()
  }
  richEditDialog.value = false
  richMathBar.value = false
}
function richEditCancel() { richEditDialog.value = false; richMathBar.value = false }
function richToggleMathBar() {
  richMathBar.value = !richMathBar.value
  if (richMathBar.value) {
    richMathInput.value = ''
    richMathPreviewHtml.value = ''
    nextTick(() => { try { const el = document.querySelector('.rich-math-input'); if (el) el.focus() } catch (e) {} })
  }
}
function updateRichMathPreview() {
  const raw = richMathInput.value.trim()
  if (!raw) { richMathPreviewHtml.value = ''; return }
  loadMathJax().then(() => {
    richMathPreviewHtml.value = tex2svgHtml(raw, false)
  }).catch(() => { richMathPreviewHtml.value = '' })
}
function richInsertFormulaAtCursor(html) {
  const box = richEditBox.value
  if (!box) return
  box.focus()
  const sel = window.getSelection()
  let range = sel.rangeCount ? sel.getRangeAt(0) : null
  if (!range || !box.contains(range.commonAncestorContainer)) {
    range = document.createRange()
    range.selectNodeContents(box)
    range.collapse(false)
    sel.removeAllRanges(); sel.addRange(range)
  }
  range.deleteContents()
  const frag = range.createContextualFragment(html)
  range.insertNode(frag)
  range.collapse(false)
  box.dispatchEvent(new Event('input', { bubbles: true }))
}
function richMathInsert() {
  const raw = richMathInput.value.trim()
  if (!raw) return
  let html = ''
  loadMathJax().then(() => {
    try { html = tex2svgHtml(raw, false) } catch (e) { html = '<span style="color:#e74c3c">[公式错误]</span>' }
    richInsertFormulaAtCursor(html)
  })
  richMathInput.value = ''
  richMathPreviewHtml.value = ''
  richMathBar.value = false
}
function richUploadMedia(type) {
  const accept = type === 'image' ? 'image/*' : type === 'video' ? 'video/*' : type === 'audio' ? 'audio/*' : '*/*'
  const inp = document.createElement('input')
  inp.type = 'file'; inp.accept = accept
  inp.style.cssText = 'position:fixed;left:-9999px;top:0'
  document.body.appendChild(inp)
  inp.addEventListener('change', () => {
    const f = inp.files && inp.files[0]
    if (f) { uploadMedia(f).then(url => { if (url) richInsertMediaAtCursor(url, f.type, f.name) }).catch(() => {}) }
    inp.remove()
  })
  inp.click()
}
function richInsertMediaAtCursor(url, type, name) {
  let tag = ''
  if ((type || '').startsWith('image/')) tag = '<div style="display:inline-block;max-width:200px;max-height:140px;overflow:hidden;border-radius:8px;vertical-align:middle"><img src="' + url + '" style="width:100%;height:100%;object-fit:cover;display:block"></div> '
  else if ((type || '').startsWith('video/')) tag = '<video src="' + url + '" controls style="max-width:200px;max-height:130px;vertical-align:middle"></video> '
  else if ((type || '').startsWith('audio/')) tag = '<audio controls src="' + url + '" style="max-width:220px;vertical-align:middle"></audio> '
  else tag = '<a href="' + url + '" target="_blank" rel="noopener">' + (name || '附件') + '</a> '
  richInsertFormulaAtCursor(tag)
}

function openMathEdit(node) {
  const text = node.getData('text') || ''
  let latex = extractLatexFromHtml(text)
  if (!latex) {
    // fallback：从渲染 DOM 的富文本容器提取（库可能清洗/重写 text，以 DOM 为准）
    try {
      const g = node.group || node.node
      if (g && g.querySelector) {
        const fo = g.querySelector('foreignObject')
        const wrap = fo ? fo.querySelector('.smm-richtext-node-wrap') : null
        if (wrap) latex = extractLatexFromHtml(wrap.innerHTML)
      }
    } catch (e) { console.warn('公式回填 fallback 失败', e) }
  }
  mathNode.value = node
  mathInput.value = latex
  mathPreview.value = ''
  mathEditMode.value = true
  mathDialog.value = true
  setTimeout(() => { try { const el = document.querySelector('.math-input'); if (el) { el.focus(); el.setSelectionRange(el.value.length, el.value.length) } } catch (err) {} }, 50)
}

function openMathQuick() {
  const renderer = mind.value.renderer
  let node = null
  try { node = renderer.getCurrentEditNode() } catch (e) { node = null }
  if (!node) { const act = renderer.activeNodeList; node = act && act.length ? act[0] : null }
  if (!node) { toast('请先选中一个节点'); return }
  mathNode.value = node
  mathInput.value = ''
  mathPreview.value = ''
  mathEditMode.value = false
  // 保存当前编辑框文本（点击按钮会失焦导致编辑框隐藏，插入时用记忆内容兜底）
  mathEditText = ''
  try {
    const teNode = renderer.textEdit.textEditNode
    if (teNode) mathEditText = teNode.innerText || ''
  } catch (e) { mathEditText = '' }
  // 保存当前编辑框选区（点击按钮会失焦，插入时恢复）
  try {
    const sel = window.getSelection()
    if (sel && sel.rangeCount) savedMathRange = sel.getRangeAt(0).cloneRange()
    else savedMathRange = null
  } catch (e) { savedMathRange = null }
  mathDialog.value = true
}

function insertMathToNode() {
  const node = mathNode.value
  const raw = mathInput.value.trim()
  if (!node || !raw) { mathDialog.value = false; return }
  // 编辑模式：替换节点内已有 KaTeX 公式
  if (mathEditMode.value) {
    let editHtml = ''
    try {
      editHtml = tex2svgHtml(raw, true)
    } catch (err) {
      editHtml = '<span style="color:#e74c3c">[公式错误]</span>'
    }
    const oldText = node.nodeData.text || ''
    if (oldText.indexOf('katex') >= 0 || oldText.indexOf('mjx-container') >= 0) {
      const replaced = replaceKatexInText(oldText, editHtml)
      if (replaced) {
        mind.value.renderer.setNodeDataRender(node, { text: replaced, richText: true })
        mathEditMode.value = false
        mathDialog.value = false
        saveDoc()
        return
      }
    }
    mathEditMode.value = false
  }
  let html = ''
  try {
    html = tex2svgHtml(raw, true)
  } catch (err) {
    html = '<span style="color:#e74c3c">[公式错误]</span>'
  }
  // 1. 正在编辑 → 插入光标处
  const te = mind.value.renderer.textEdit
  if (te && te.textEditNode && te.showTextEdit) {
    // 编辑态：恢复保存的选区后用 Range 插入（execCommand 会因失焦丢失选区而清空内容）
    const teNode = te.textEditNode
    teNode.focus()
    let range = null
    if (savedMathRange && savedMathRange.commonAncestorContainer && teNode.contains && teNode.contains(savedMathRange.commonAncestorContainer)) {
      range = savedMathRange
    } else {
      range = document.createRange()
      range.selectNodeContents(teNode)
      range.collapse(false)
    }
    const sel = window.getSelection()
    sel.removeAllRanges()
    sel.addRange(range)
    try {
      const frag = range.createContextualFragment(html)
      range.insertNode(frag)
      const after = document.createRange()
      after.selectNodeContents(teNode)
      after.collapse(false)
      sel.removeAllRanges()
      sel.addRange(after)
    } catch (err) { console.warn('公式插入失败', err) }
    teNode.dispatchEvent(new InputEvent('input', { bubbles: true }))
  } else {
    // 2. 未编辑 → 追加到节点文字末尾（转富文本）
    const oldText = mathEditText || node.nodeData.text || ''
    const isRich = node.nodeData.richText === true
    let newText
    if (isRich) {
      newText = oldText + html
    } else {
      const esc = oldText.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      newText = esc + html
    }
    mind.value.renderer.setNodeDataRender(node, { text: newText, richText: true })
  }
  mathDialog.value = false
  saveDoc()
}

async function insertEmoji(e) {
  const renderer = mind.value.renderer
  const te = renderer.textEdit
  // 1. 正在编辑 → 插入光标处
  if (te && te.textEditNode && te.showTextEdit) {
    te.textEditNode.focus()
    document.execCommand('insertText', false, e)
    te.textEditNode.dispatchEvent(new InputEvent('input', { bubbles: true }))
    return
  }
  // 2. 未编辑 → 进入选中节点编辑，追加到文字末尾
  const active = renderer.activeNodeList
  if (!active || !active.length) { toast('请先选中一个节点'); return }
  const node = active[0]
  try { await te.show({ node }) } catch (err) { return }
  requestAnimationFrame(() => {
    const w2 = te.textEditNode
    if (w2) {
      w2.focus()
      document.execCommand('insertText', false, e)
      w2.dispatchEvent(new InputEvent('input', { bubbles: true }))
    }
  })
}

function setIcon(icon) {
  const active = mind.value.renderer.activeNodeList
  if (!active || !active.length) { toast('请先选中一个节点'); return }
  active.forEach((node) => {
    const cur = node.nodeData.icon || []
    const idx = cur.indexOf(icon)
    const next = idx >= 0 ? cur.filter(x => x !== icon) : cur.concat([icon])
    mind.value.execCommand('SET_NODE_ICON', node, next)
  })
}

// 媒体插入
async function fileToDataUrl(file) {
  return new Promise((resolve, reject) => {
    const r = new FileReader()
    r.onload = () => resolve(r.result)
    r.onerror = reject
    r.readAsDataURL(file)
  })
}
async function compressImage(file) {
  const type = file.type || ''
  const dataUrl = await fileToDataUrl(file)
  if (type === 'image/gif' || type === 'image/webp' || type === 'image/svg+xml' || type === 'image/x-icon') return dataUrl
  const img = new Image()
  await new Promise((res, rej) => { img.onload = res; img.onerror = rej; img.src = dataUrl })
  const MAX = 1280
  let w = img.naturalWidth, h = img.naturalHeight
  if (!w || !h) return dataUrl
  if (Math.max(w,h) > MAX) {
    const scale = MAX / Math.max(w,h)
    w = Math.round(w*scale); h = Math.round(h*scale)
    const cv = document.createElement('canvas')
    cv.width = w; cv.height = h
    cv.getContext('2d').drawImage(img, 0, 0, w, h)
    return cv.toDataURL('image/jpeg', 0.85)
  }
  return dataUrl
}

async function insertFileToNode(node, file) {
  if (!node || !file) return
  const type = file.type || ''
  try {
    if (type.startsWith('image/')) {
      const dataUrl = await compressImage(file)
      const blob = await (await fetch(dataUrl)).blob()
      const upFile = new File([blob], file.name || 'image', { type: blob.type || type })
      const src = await uploadMedia(upFile)
      const size = await new Promise(res => {
        const im = new Image()
        im.onload = () => res({ width: im.naturalWidth, height: im.naturalHeight })
        im.onerror = () => res({ width: 210, height: 157 })
        im.src = dataUrl
      })
      // 限制为合理卡片尺寸（图片仍铺满节点）
      const MAX_W = 360, MAX_H = 240
      const scale = Math.min(1, MAX_W / (size.width || 1), MAX_H / (size.height || 1))
      const w = Math.max(100, Math.round((size.width || 210) * scale))
      const h = Math.max(75, Math.round((size.height || 157) * scale))
      mind.value.renderer.setNodeDataRender(node, { richText: true, text:
        '<div style="position:relative;width:'+w+'px;height:'+h+'px;overflow:hidden;border-radius:12px;line-height:0">'+
        '<img src="'+src+'" style="width:100%;height:100%;object-fit:cover;display:block;margin:0">'+
        '</div>' })
      // 节点本身即图片：去掉节点外框，图片作为节点主体
      mind.value.execCommand('SET_NODE_STYLES', node, { shape:'roundedRectangle', borderRadius:12, borderWidth:0, borderColor:'transparent', fillColor:'transparent', paddingX:0, paddingY:0 })
    } else if (type.startsWith('audio/')) {
      const src = await uploadMedia(file)
      mind.value.renderer.setNodeDataRender(node, { richText: true, text:
        '<div style="position:relative;width:280px;height:40px;overflow:hidden;border-radius:12px;line-height:0;background:#000">'+
        '<audio controls style="width:100%;height:100%;display:block" src="'+src+'"></audio></div>' })
    } else if (type.startsWith('video/')) {
      const src = await uploadMedia(file)
      mind.value.renderer.setNodeDataRender(node, { richText: true, text:
        '<div style="position:relative;width:300px;height:170px;overflow:hidden;border-radius:12px;background:#000;line-height:0">'+
        '<video controls style="width:100%;height:100%;object-fit:cover;display:block;background:#000" src="'+src+'"></video></div>' })
      // 视频本身即节点：去掉节点外框
      mind.value.execCommand('SET_NODE_STYLES', node, { shape:'roundedRectangle', borderRadius:12, borderWidth:0, borderColor:'transparent', fillColor:'transparent', paddingX:0, paddingY:0 })
    } else {
      const src = await uploadMedia(file)
      mind.value.execCommand('SET_NODE_ATTACHMENT', node, src, file.name || '附件')
      mind.value.execCommand('SET_NODE_STYLES', node, { shape:'roundedRectangle', borderRadius:12, borderWidth:1, borderColor:'#C9CDD6', borderDasharray:'2 4', fillColor:'#F4F5F7', paddingX:6, paddingY:6 })
    }
    if (node.getData('uid')) manualStyled.add(node.getData('uid'))
    scheduleBranchRainbow()
    saveDoc()
  } catch (e) {
    toast('插入媒体失败: ' + (e.message || e), 'error')
  }
}

// 右键菜单
function showCtxMenu(e, node) {
  ctxMode.value = 'node'
  ctxNode = node
  ctxNoAnim.value = false
  ctxClosing.value = false
  ctxHidden.value = false
  const x = e.clientX != null ? e.clientX : e.pageX
  const y = e.clientY != null ? e.clientY : e.pageY
  ctxStyle.value = { left: Math.min(x, window.innerWidth - 200) + 'px', top: Math.min(y, window.innerHeight - 400) + 'px' }
}
function hideCtxMenu() {
  if (ctxHidden.value) return
  ctxClosing.value = true
  setTimeout(() => { ctxHidden.value = true; ctxClosing.value = false; ctxNode = null }, 100)
}

function ctxAct(act) {
  const node = ctxNode
  if (!node) return
  if (act === 'insert-child') {
    mind.value.execCommand('INSERT_CHILD_NODE', node)
  } else if (act === 'insert-sibling') {
    mind.value.execCommand('INSERT_NODE')
  } else if (act === 'edit') {
    if (node.getData('richText') === true) { openRichEdit(node); return }
    mind.value.execCommand('SET_NODE_ACTIVE', [node])
    try { mind.value.renderer.textEdit.show({ node }) } catch (err) { mind.value.renderer.startTextEdit() }
  } else if (act === 'copy') {
    mind.value.execCommand('SET_NODE_ACTIVE', [node])
    mind.value.renderer.copy()
  } else if (act === 'cut') {
    mind.value.execCommand('SET_NODE_ACTIVE', [node])
    mind.value.execCommand('CUT_NODE')
  } else if (act === 'paste') {
    mind.value.renderer.paste()
  } else if (act === 'insert-image' || act === 'insert-audio' || act === 'insert-video' || act === 'insert-file') {
    const accept = act === 'insert-image' ? 'image/*' : act === 'insert-audio' ? 'audio/*' : act === 'insert-video' ? 'video/*' : '*/*'
    const inp = document.createElement('input')
    inp.type = 'file'; inp.accept = accept
    inp.style.cssText = 'position:fixed;left:-9999px;top:0'
    document.body.appendChild(inp)
    inp.addEventListener('change', () => {
      const f = inp.files && inp.files[0]
      if (f && node) insertFileToNode(node, f)
      inp.remove()
    })
    inp.click()
  } else if (act === 'insert-math') {
    mathNode.value = ctxNode
    mathInput.value = ''
    mathPreview.value = ''
    mathEditMode.value = false
    mathDialog.value = true
  } else if (act === 'font-color' || act === 'bg-color') {
    const prop = act === 'font-color' ? 'color' : 'fillColor'
    const c = document.createElement('input')
    c.type = 'color'
    c.style.cssText = 'position:fixed;left:-9999px;top:0'
    document.body.appendChild(c)
    c.addEventListener('change', () => {
      mind.value.execCommand('SET_NODE_STYLE', node, prop, c.value)
      if (node.getData('uid')) manualStyled.add(node.getData('uid'))
      scheduleBranchRainbow()
      saveDoc()
      c.remove()
    })
    c.click()
  } else if (act === 'collapse') {
    mind.value.execCommand('SET_NODE_EXPAND', node, false)
  } else if (act === 'expand') {
    mind.value.execCommand('SET_NODE_EXPAND', node, true)
  } else if (act === 'del') {
    mind.value.execCommand('SET_NODE_ACTIVE', [node])
    mind.value.execCommand('REMOVE_NODE')
  }
  hideCtxMenu()
}

// 附件预览
function openAttachmentPreview(node) {
  const url = node.getData('attachmentUrl')
  const name = node.getData('attachmentName') || '附件'
  if (!url) return
  const ext = (name.match(/\.([a-zA-Z0-9]+)$/) || [,''])[1].toLowerCase()
  const isImg = ['png','jpg','jpeg','gif','webp','bmp','ico','avif','svg'].includes(ext)
  const isPdf = ext === 'pdf'
  const isAudio = ['mp3','wav','ogg','m4a','aac','flac'].includes(ext)
  const isVideo = ['mp4','webm','ogv','mov','mkv'].includes(ext)
  let wrap = document.getElementById('att-preview')
  if (wrap) wrap.remove()
  wrap = document.createElement('div')
  wrap.id = 'att-preview'
  wrap.innerHTML = '<div class="attp-mask"></div><div class="attp-box"><div class="attp-head"><span class="attp-title"></span><button class="attp-close">×</button></div><div class="attp-body"></div></div>'
  document.body.appendChild(wrap)
  wrap.querySelector('.attp-title').textContent = name
  const close = () => wrap.remove()
  wrap.querySelector('.attp-close').addEventListener('click', close)
  wrap.querySelector('.attp-mask').addEventListener('click', close)
  const body = wrap.querySelector('.attp-body')
  if (isImg) {
    const im = document.createElement('img')
    im.src = url; im.style.cssText = 'max-width:100%;max-height:62vh;border-radius:10px'
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
  } else {
    // 文本类：直接读取显示
    const isText = ['txt','md','json','csv','log','xml','yaml','yml','html','htm','js','css','ini','conf'].includes(ext)
    if (isText) {
      fetch(url).then(r => r.text()).then(t => {
        // 文本：可浏览 + 可编辑
        const pre = document.createElement('pre')
        pre.textContent = t
        pre.style.cssText = 'max-width:100%;max-height:56vh;overflow:auto;background:#1e1e1e;color:#e6e6e6;padding:14px;border-radius:10px;font-size:13px;white-space:pre-wrap;word-break:break-all;line-height:1.6;margin:0 0 10px'
        body.appendChild(pre)
        const bar = document.createElement('div')
        bar.style.cssText = 'text-align:right'
        const editBtn = document.createElement('button')
        editBtn.textContent = '编辑'
        editBtn.className = 'attp-dl-btn'
        editBtn.style.cssText = 'padding:6px 18px;background:#0e7c86;color:#fff;border:0;border-radius:6px;cursor:pointer;font-size:13px'
        bar.appendChild(editBtn)
        body.appendChild(bar)
        editBtn.addEventListener('click', () => {
          pre.style.display = 'none'
          bar.style.display = 'none'
          const ta = document.createElement('textarea')
          ta.value = t
          ta.style.cssText = 'width:100%;height:50vh;background:#1e1e1e;color:#e6e6e6;border:1px solid #333;border-radius:10px;padding:12px;font-size:13px;font-family:Consolas,monospace;box-sizing:border-box;resize:vertical'
          body.appendChild(ta)
          const saveBar = document.createElement('div')
          saveBar.style.cssText = 'text-align:right;margin-top:10px'
          const saveBtn = document.createElement('button')
          saveBtn.textContent = '保存'
          saveBtn.style.cssText = 'padding:6px 18px;background:#0e7c86;color:#fff;border:0;border-radius:6px;cursor:pointer;font-size:13px'
          const cancelBtn = document.createElement('button')
          cancelBtn.textContent = '取消'
          cancelBtn.style.cssText = 'padding:6px 18px;background:#555;color:#fff;border:0;border-radius:6px;cursor:pointer;font-size:13px;margin-right:8px'
          saveBar.appendChild(cancelBtn)
          saveBar.appendChild(saveBtn)
          body.appendChild(saveBar)
          cancelBtn.addEventListener('click', () => { wrap.remove() })
          saveBtn.addEventListener('click', async () => {
            saveBtn.disabled = true; saveBtn.textContent = '保存中...'
            try {
              const res = await fetch('/api/file/content', {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + (localStorage.getItem('usmind.token') || '') },
                body: JSON.stringify({ url, content: ta.value })
              })
              const j = await res.json()
              if (j.code === 200) { toast('已保存'); wrap.remove() } else { toast('保存失败: ' + (j.message || ''), 'error') }
            } catch (e) { toast('保存失败: ' + e.message, 'error') }
            saveBtn.disabled = false; saveBtn.textContent = '保存'
          })
        })
      }).catch(() => {
        body.innerHTML = '<div class="attp-dl">无法读取内容，请下载查看</div><a class="attp-dl-btn" href="' + url + '" target="_blank" rel="noopener">下载文件</a>'
      })
    } else {
      // office/压缩包等：弹窗内下载
      body.innerHTML = '<div class="attp-dl">该类型暂不支持在线预览，请下载后查看</div><a class="attp-dl-btn" href="' + url + '" target="_blank" rel="noopener">下载文件</a>'
    }
  }
}

// AI
async function runAI() {
  if (!aiInput.value.trim()) { toast('请输入指令'); return }
  aiOutput.value = '<div class="ai-loading">AI 思考中...</div>'
  try {
    const res = await aiApi.generate({
      mode: aiMode.value,
      prompt: aiInput.value,
      mindMapData: aiMode.value === 'create' ? undefined : JSON.stringify(mind.value.getData())
    })
    const text = res.result || ''
    if (aiMode.value === 'create') {
      const m = text.match(/\{[\s\S]*\}/)
      if (m) {
        try {
          const tree = JSON.parse(m[0])
          const walk = (n) => {
            const o = { data: { text: String(n.topic || n.text || '').trim() || '未命名' } }
            if (Array.isArray(n.children) && n.children.length) o.children = n.children.map(walk)
            return o
          }
          mind.value.setData(walk(tree))
          mind.value.view.fit()
          setTimeout(centerRoot, 80)
        } catch (e) {}
      }
      aiOutput.value = '<div class="ai-result">已生成导图</div>'
    } else {
      aiOutput.value = '<div class="ai-result">' + escapeHtml(text) + '</div>'
    }
  } catch (e) {
    aiOutput.value = '<div class="ai-error">调用失败: ' + escapeHtml(e.message || String(e)) + '</div>'
  }
}
function escapeHtml(s) {
  return String(s).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]))
}

// 工具栏动作
function goList() { router.push('/maps') }
const exportOpen = ref(false)
const importFileInput = ref(null)
document.addEventListener('click', (e) => { if (!e.target.closest('.export-wrap')) exportOpen.value = false })

function downloadDataUrl(dataUrl, filename) {
  const a = document.createElement('a')
  a.href = dataUrl
  a.download = filename
  a.click()
}

async function runExport(type) {
  exportOpen.value = false
  if (!mind.value) { toast('请先进入画布', 'error'); return }
  const name = docName.value || '思维导图'
  try {
    toast('正在导出 ' + type.toUpperCase() + ' ...')
    if (type === 'jpg' || type === 'jpeg') {
      // 用 png 数据源转成 jpeg（白底）
      const dataUrl = await mind.value.doExport.export('png', false, name)
      const img = new Image()
      await new Promise((res, rej) => { img.onload = res; img.onerror = rej; img.src = dataUrl })
      const canvas = document.createElement('canvas')
      canvas.width = img.width
      canvas.height = img.height
      const ctx = canvas.getContext('2d')
      ctx.fillStyle = '#ffffff'
      ctx.fillRect(0, 0, canvas.width, canvas.height)
      ctx.drawImage(img, 0, 0)
      downloadDataUrl(canvas.toDataURL('image/jpeg', 0.92), name + '.' + type)
    } else {
      await mind.value.doExport.export(type, true, name)
    }
    toast('导出完成')
  } catch (e) {
    toast('导出失败: ' + e.message, 'error')
  }
}

async function exportUsmind() {
  exportOpen.value = false
  if (!mind.value) { toast('请先进入画布', 'error'); return }
  const name = docName.value || '思维导图'
  try {
    toast('正在收集资源并打包...')
    const data = mind.value.getData(true)
    const urls = collectResourceUrls(data.root)
    const files = new Map()
    for (const url of urls) {
      if (files.has(url)) continue
      try {
        const blob = await fetchBlob(url)
        const fname = nameFromUrl(url, files)
        files.set(url, { name: fname, blob })
      } catch (e) {
        console.warn('资源下载失败，已跳过: ' + url, e)
      }
    }
    replaceUrlsInExportData(data.root, files)
    const jsonStr = JSON.stringify(data)
    const zip = new JSZip()
    zip.file(name + '.json', jsonStr)
    const dataFolder = zip.folder('data')
    files.forEach((v) => { dataFolder.file(v.name, v.blob) })
    const blob = await zip.generateAsync({ type: 'blob' })
    downloadBlob(blob, name + '.usmind')
    toast('导出完成，共打包 ' + files.size + ' 个资源')
  } catch (e) {
    toast('导出失败: ' + e.message, 'error')
  }
}

function collectResourceUrls(root) {
  const urls = new Set()
  const RE = /^(https?:|blob:|data:|\/)/i
  function walk(n) {
    const d = (n && n.data) || {}
    if (typeof d.image === 'string' && RE.test(d.image)) urls.add(d.image)
    if (typeof d.attachmentUrl === 'string' && RE.test(d.attachmentUrl)) urls.add(d.attachmentUrl)
    if (typeof d.text === 'string' && (d.richText || d.text.indexOf('<') >= 0)) {
      const re = /<(?:img|video|audio|a)\b[^>]*(?:src|href)="([^"]+)"/g
      let m
      while ((m = re.exec(d.text))) {
        const u = m[1]
        if (RE.test(u)) urls.add(u)
      }
    }
    ;(n.children || []).forEach(walk)
  }
  walk(root)
  return [...urls]
}

function nameFromUrl(url, files) {
  let name = ''
  try {
    const u = new URL(url, location.href)
    const parts = u.pathname.split('/').filter(Boolean)
    if (parts.length) name = decodeURIComponent(parts[parts.length - 1])
  } catch (e) {}
  if (!name || name === '/') name = 'res_' + Math.random().toString(36).slice(2, 8)
  const used = new Set([...files.values()].map((v) => v.name))
  if (!used.has(name)) return name
  const dot = name.lastIndexOf('.')
  const base = dot > 0 ? name.slice(0, dot) : name
  const ext = dot > 0 ? name.slice(dot) : ''
  let i = 1
  while (used.has(base + '_' + i + ext)) i++
  return base + '_' + i + ext
}

async function fetchBlob(url) {
  const res = await fetch(url)
  if (!res.ok) throw new Error('下载失败 ' + res.status)
  return await res.blob()
}

function downloadBlob(blob, filename) {
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = filename
  a.click()
  setTimeout(() => URL.revokeObjectURL(a.href), 5000)
}

function onImportUsmindClick() {
  exportOpen.value = false
  if (usmindFileInput.value) usmindFileInput.value.click()
}

async function onImportUsmindFile(e) {
  const file = e.target.files && e.target.files[0]
  e.target.value = ''
  if (!file) return
  if (!mind.value) { toast('请先进入画布', 'error'); return }
  try {
    toast('正在解包并上传资源...')
    console.log('[usmind] start, size=' + file.size)
    const zip = await JSZip.loadAsync(file)
    console.log('[usmind] zip entries=' + Object.keys(zip.files).length + ' names=' + Object.keys(zip.files).slice(0,6).join(','))
    const jsonEntry = Object.values(zip.files).find((f) => !f.dir && /\.json$/i.test(f.name))
    if (!jsonEntry) { toast('压缩包内未找到导图 JSON', 'error'); return }
    const jsonStr = await jsonEntry.async('string')
    const dataEntries = Object.values(zip.files).filter((f) => !f.dir && f.name.indexOf('data/') === 0)
    const urlMap = new Map()
    for (const entry of dataEntries) {
      try {
        const blob = await entry.async('blob')
        const fname = entry.name.split('/').pop()
        const f = new File([blob], fname)
        const newUrl = await uploadMedia(f)
        urlMap.set(entry.name, newUrl)
      } catch (err) {
        console.warn('资源上传失败: ' + entry.name, err)
      }
    }
    const data = JSON.parse(jsonStr)
    if (data && data.root) {
      replaceUrlsInData(data.root, urlMap)
      await ensureImageSize(data.root)
      mind.value.setFullData(data)
      if (data.layout) {
        const key = LAYOUT_REV[data.layout] || 'r'
        layoutKey.value = key
        applyLayout(key)
      }
      if (data.theme && data.theme.template) {
        currentTheme.value = data.theme.template
        applyTheme(data.theme.template)
      }
      applyThemeFonts(data.theme && data.theme.template ? data.theme.template : currentTheme.value)
      applyCanvasBackground(data.theme && data.theme.template ? data.theme.template : currentTheme.value)
    } else if (data && data.data !== undefined) {
      mind.value.setData(data)
      applyThemeFonts(currentTheme.value)
    } else {
      toast('无法识别的导图数据', 'error')
      return
    }
    const doc = canvasTabs.value[docIndex.value]
    doc.data = { root: mind.value.getData() }
    applyBranchRainbow()
    markDirty()
    nextTick(() => { try { centerRoot(); updateScrollbars() } catch (e) {} })
    toast('导入成功，已入库 ' + urlMap.size + ' 个资源')
  } catch (err) {
    console.error('[usmind] import error:', err)
    toast('导入失败: ' + (err && err.message ? err.message : err), 'error')
  }
}

function replaceUrlsInData(node, urlMap) {
  if (!node || urlMap.size === 0) return
  const d = node.data || {}
  if (typeof d.image === 'string' && urlMap.has(d.image)) d.image = urlMap.get(d.image)
  if (typeof d.attachmentUrl === 'string' && urlMap.has(d.attachmentUrl)) d.attachmentUrl = urlMap.get(d.attachmentUrl)
  if (typeof d.text === 'string') {
    let t = d.text
    urlMap.forEach((v, k) => { t = t.split(k).join(v) })
    d.text = t
  }
  ;(node.children || []).forEach((n) => replaceUrlsInData(n, urlMap))
}

function replaceUrlsInExportData(node, files) {
  if (!node || files.size === 0) return
  const d = node.data || {}
  if (typeof d.image === 'string' && files.has(d.image)) d.image = 'data/' + files.get(d.image).name
  if (typeof d.attachmentUrl === 'string' && files.has(d.attachmentUrl)) d.attachmentUrl = 'data/' + files.get(d.attachmentUrl).name
  if (typeof d.text === 'string') {
    let t = d.text
    files.forEach((v, url) => { t = t.split(url).join('data/' + v.name) })
    d.text = t
  }
  ;(node.children || []).forEach((n) => replaceUrlsInExportData(n, files))
}

function ensureImageSize(root) {
  const tasks = []
  function walk(n) {
    if (n && n.data && typeof n.data.image === 'string' && !n.data.imageSize) {
      tasks.push(new Promise((resolve) => {
        const img = new Image()
        img.onload = () => { n.data.imageSize = { width: img.naturalWidth || 200, height: img.naturalHeight || 140, custom: false }; resolve() }
        img.onerror = () => { n.data.imageSize = { width: 200, height: 140, custom: false }; resolve() }
        img.src = n.data.image
      }))
    }
    ;(n.children || []).forEach(walk)
  }
  walk(root)
  return Promise.all(tasks)
}

const usmindFileInput = ref(null)

function onImportClick() {
  exportOpen.value = false
  if (importFileInput.value) importFileInput.value.click()
}

async function onImportFile(e) {
  const file = e.target.files && e.target.files[0]
  e.target.value = ''
  if (!file) return
  if (!mind.value) { toast('请先进入画布', 'error'); return }
  try {
    const data = JSON.parse(await file.text())
    if (data && data.root) {
      mind.value.setFullData(data)
      if (data.layout) {
        const key = LAYOUT_REV[data.layout] || 'r'
        layoutKey.value = key
        applyLayout(key)
      }
      if (data.theme && data.theme.template) {
        currentTheme.value = data.theme.template
        applyTheme(data.theme.template)
      }
      applyThemeFonts(data.theme && data.theme.template ? data.theme.template : currentTheme.value)
      applyCanvasBackground(data.theme && data.theme.template ? data.theme.template : currentTheme.value)
    } else if (data && data.data !== undefined) {
      mind.value.setData(data)
      applyThemeFonts(currentTheme.value)
    } else {
      toast('无法识别的导图文件', 'error')
      return
    }
    const doc = canvasTabs.value[docIndex.value]
    doc.data = { root: mind.value.getData() }
    applyBranchRainbow()
    markDirty()
    nextTick(() => { try { centerRoot(); updateScrollbars() } catch (e) {} })
    toast('导入成功')
  } catch (err) {
    toast('导入失败: ' + err.message, 'error')
  }
}
const shareDialog = ref(false)
const shareLink = ref('')
const sharePwd = ref('')
const shareReady = ref(false)
function onShare() {
  if (!mindMapId.value) return
  sharePwd.value = ''
  shareReady.value = false
  shareDialog.value = true
}
async function confirmShare() {
  if (!mindMapId.value) return
  try {
    const data = await mindmapApi.share(mindMapId.value, sharePwd.value || undefined)
    shareLink.value = location.origin + '/share/' + data.shareToken
    shareReady.value = true
  } catch (e) { toast('分享失败: ' + e.message, 'error') }
}
function copyShare() {
  try {
    const el = document.querySelector('.share-link-input')
    if (el) { el.select(); document.execCommand('copy') }
  } catch (e) {}
  shareDialog.value = false
}

// Ctrl+V 粘贴文件到当前选中节点（图片/音视频/文件）
function onPaste(e) {
  if (!mind.value) return
  const files = e.clipboardData && e.clipboardData.files
  if (!files || !files.length) return
  const active = mind.value.renderer.activeNodeList
  const node = active && active.length ? active[0] : null
  if (!node) { toast('请先选中一个节点再粘贴资源'); return }
  e.preventDefault()
  const f = files[0]
  insertFileToNode(node, f)
}

// 初始化
onMounted(async () => {
  window.addEventListener('resize', () => { updateCanvasBgSize(); updateScrollbars(); nextTick(checkBarOverflow) })
  nextTick(checkBarOverflow)
  mapEl.value.addEventListener('mousedown', onMapMouseDown)
  mapEl.value.addEventListener('touchstart', onMapTouchStart, { passive: false })
  mapEl.value.addEventListener('touchmove', onMapTouchMove, { passive: false })
  mapEl.value.addEventListener('touchend', onMapTouchEnd)
  mapEl.value.addEventListener('touchcancel', onMapTouchEnd)
  pageLoading.value = true
  if (mapEl.value) mapEl.value.classList.add('map-loading')
  // 拉取后台图标库（按分类分组，覆盖默认表情包）
  try {
    const icons = await fetchIcons()
    if (Array.isArray(icons) && icons.length) emojiGroups.value = icons
  } catch (e) { /* 后端不可用则用内置表情 */ }

  // 拉取后台配置的主题模板并合并（同 key 覆盖内置，新增追加）
  try {
    const remote = await fetchThemes()
    if (Array.isArray(remote) && remote.length) {
      const merged = [...themes.value]
      remote.forEach((t) => {
        const cfg = t.config && Object.keys(t.config).length ? t.config : themes.value[0].config
        try { MindMap.defineTheme(t.name, cfg) } catch (e) {}
        const item = { name: t.name, key: t.themeKey || ('theme-' + t.id), branchColors: t.branchColors || [], config: cfg }
        const idx = merged.findIndex(x => x.name === t.name || x.key === t.themeKey)
        if (idx >= 0) merged[idx] = item
        else merged.push(item)
      })
      themes.value = merged
      if (!merged.find(x => x.name === currentTheme.value)) currentTheme.value = merged[0].name
    }
  } catch (e) { /* 后端不可用则仅用内置主题 */ }

  let data
  if (mindMapId.value) {
    try {
      data = await mindmapApi.get(mindMapId.value)
    } catch (e) {
      loadingToken = 0
      pageLoading.value = false
      if (mapEl.value) mapEl.value.classList.remove('map-loading')
      toast('加载导图失败: ' + e.message, 'error')
      router.push('/maps')
      return
    }
  } else {
    // 新建
    data = await mindmapApi.create({ name: '未命名' })
    mindMapId.value = data.id
    data = await mindmapApi.get(data.id)
  }

  // 解析数据: 后端可能返回 JSON 字符串，需先解析为对象
  let d = data.data || {}
  if (typeof d === 'string' && d) { try { d = JSON.parse(d) } catch (e) { d = {} } }
  // 支持新格式 { root, _tabs, _activeTab } 或旧格式 { root }
  if (d._tabs && Array.isArray(d._tabs) && d._tabs.length) {
    canvasTabs.value = d._tabs
    docIndex.value = d._activeTab || 0
  } else {
    canvasTabs.value = [{
      name: data.name || '未命名',
      layout: data.layout || 'logicalStructure',
      theme: data.theme || themes.value[0].name,
      data: d.root ? { root: d.root } : defaultData(),
      viewData: data.viewData || null
    }]
    docIndex.value = 0
  }
  docName.value = data.name || '未命名'

  await nextTick()
  await createMind()

  beforeUnloadHandler = () => { if (dirty.value) saveDoc() }
  window.addEventListener('beforeunload', beforeUnloadHandler)
  document.addEventListener('click', onDocClick)
  document.addEventListener('paste', onPaste)
})

function onDocClick(e) {
  if (!e.target.closest('#ctx-menu')) hideCtxMenu()
}

onBeforeUnmount(() => {
  document.removeEventListener('mousemove', onBarDragMove)
  document.removeEventListener('mouseup', onBarDragEnd)
  if (beforeUnloadHandler) window.removeEventListener('beforeunload', beforeUnloadHandler)
  document.removeEventListener('click', onDocClick)
  document.removeEventListener('focusin', onEditFocusIn)
  document.removeEventListener('focusout', onEditFocusOut)
  document.removeEventListener('paste', onPaste)
  if (mind.value) { try { mind.value.dispose && mind.value.dispose() } catch(e){} }
})
</script>

<style scoped>
.editor-root { position: relative; height: 100%; width: 100%; }
.canvas-bg { position: absolute; inset: 0; overflow: hidden; }
.canvas-bg-color { position: absolute; inset: 0; }
.canvas-bg-image { position: absolute; inset: 0; background-size: cover; background-position: center; background-repeat: no-repeat; }
.canvas-bg-video { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; object-position: center center; pointer-events: none; z-index: 0; }
.canvas-bg-video { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; object-position: center center; pointer-events: none; z-index: 0; }
#map { position: relative; height: 100%; width: 100%; background: transparent !important; opacity: 1; transition: opacity .5s ease; }
#map.map-loading { opacity: 0; }
.page-loading { position: absolute; inset: 0; z-index: 100; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 14px; background: rgba(255,255,255,.7); backdrop-filter: blur(2px); }
.pl-spinner { width: 44px; height: 44px; border: 4px solid rgba(74,92,247,.18); border-top-color: #4A5CF7; border-radius: 50%; animation: pl-rot .8s linear infinite; }
.pl-text { font-size: 14px; color: #667; }
@keyframes pl-rot { to { transform: rotate(360deg) } }
.attp-box .attp-dl { color:#909399; font-size:14px; padding:40px 0 16px; text-align:center; } .attp-box .attp-dl-btn { display:inline-block; padding:8px 24px; background:#0e7c86; color:#fff; border-radius:6px; text-decoration:none; font-size:14px; }
/* 数学公式弹窗 */
/* 编辑态快速插入公式入口 */
.math-quick { position: fixed; z-index: 2600; width: 34px; height: 34px; border-radius: 50%;
  background: #0e7c86; color: #fff; border: 2px solid #fff; font-size: 20px; line-height: 1;
  cursor: pointer; box-shadow: 0 4px 14px rgba(0,0,0,.3); display: flex; align-items: center; justify-content: center;
  transition: transform .15s ease, box-shadow .15s ease; }
.math-quick:hover { transform: scale(1.1); box-shadow: 0 6px 18px rgba(0,0,0,.4); }

.math-mask { position: fixed; inset: 0; background: rgba(0,0,0,.45); z-index: 3000; display: flex; align-items: center; justify-content: center; }
.math-dialog { width: 460px; max-width: 90vw; background: #fff; border-radius: 14px; padding: 18px 20px; box-shadow: 0 12px 40px rgba(0,0,0,.25); }
.math-title { font-size: 15px; font-weight: 600; margin-bottom: 10px; }
.math-input { width: 100%; box-sizing: border-box; border: 1px solid #ddd; border-radius: 8px; padding: 8px 10px; font-size: 14px; font-family: Consolas, Menlo, monospace; resize: vertical; outline: none; }
.math-input:focus { border-color: #5b8ff9; }
.math-preview { min-height: 44px; margin-top: 10px; padding: 10px 12px; background: #f7f8fa; border-radius: 8px; display: flex; align-items: center; gap: 10px; overflow-x: auto; }
.math-preview-label { font-size: 12px; color: #909399; white-space: nowrap; }
.math-preview-empty { color: #b0b3ba; font-size: 13px; }
.math-actions { margin-top: 14px; display: flex; justify-content: flex-end; gap: 10px; }
.math-btn { padding: 6px 18px; border-radius: 8px; border: 1px solid #ddd; background: #fff; cursor: pointer; font-size: 14px; }
.math-btn-primary { background: #5b8ff9; border-color: #5b8ff9; color: #fff; }
.math-btn-primary:hover { background: #4a7de0; }
/* 节点内公式缩放适配 */
#map .katex { font-size: 1.1em; }
#map .katex-display { margin: 4px 0; }

/* 分享链接弹窗 */
.share-mask { position: fixed; inset: 0; z-index: 3100; background: rgba(0,0,0,.45); display: flex; align-items: center; justify-content: center; }
.share-box { width: 420px; max-width: 88vw; background: #fff; border-radius: 14px; padding: 18px 20px; box-shadow: 0 12px 40px rgba(0,0,0,.25); }
.share-title { font-size: 15px; font-weight: 600; margin-bottom: 8px; }
.share-tip { font-size: 12px; color: #8a8f99; margin-bottom: 12px; line-height: 1.5; }
.share-link-input { width: 100%; box-sizing: border-box; border: 1px solid #ddd; border-radius: 8px; padding: 9px 10px; font-size: 13px; color: #333; outline: none; background: #f7f8fa; }
.share-actions { margin-top: 14px; display: flex; justify-content: flex-end; gap: 10px; }
/* 富文本节点内容编辑弹窗 */
.rich-mask { position: fixed; inset: 0; z-index: 3000; background: rgba(0,0,0,.45); display: flex; align-items: center; justify-content: center; }
.rich-dialog { width: 640px; max-width: 92vw; max-height: 82vh; background: #fff; border-radius: 16px; padding: 18px 20px 16px; display: flex; flex-direction: column; box-shadow: 0 12px 40px rgba(0,0,0,.3); font-family: inherit; }
.rich-title { font-size: 15px; font-weight: 700; color: #1f2329; margin-bottom: 12px; }
.rich-toolbar { display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 10px; }
.rich-tb-btn { padding: 5px 12px; border: 1px solid #d9d9d9; border-radius: 8px; background: #fff; font-size: 13px; color: #1f2329; cursor: pointer; transition: all .2s; }
.rich-tb-btn:hover { border-color: #7c6cf0; color: #7c6cf0; background: #f7f6ff; }
.rich-math-bar { display: flex; align-items: center; gap: 8px; margin-bottom: 10px; padding: 8px 10px; background: #f7f7fa; border-radius: 10px; flex-wrap: wrap; }
.rich-math-input { flex: 1; min-width: 180px; padding: 5px 10px; border: 1px solid #d9d9d9; border-radius: 8px; font-size: 13px; font-family: Consolas, monospace; outline: none; }
.rich-math-input:focus { border-color: #7c6cf0; }
.rich-math-preview { padding: 2px 10px; background: #fff; border-radius: 6px; min-height: 26px; display: inline-flex; align-items: center; }
.rich-math-hint { color: #999; font-size: 12px; }
.rich-tip { font-size: 12px; color: #8a8f99; margin-bottom: 10px; line-height: 1.5; }
.rich-textarea { flex: 1; min-height: 180px; max-height: 46vh; border: 1px solid #e0e0e6; border-radius: 10px; padding: 10px 12px; font-size: 14px; line-height: 1.7; outline: none; resize: vertical; font-family: inherit; }
.rich-textarea:focus { border-color: #7c6cf0; box-shadow: 0 0 0 2px rgba(124,108,240,.15); }
.rich-actions { display: flex; justify-content: flex-end; gap: 10px; margin-top: 14px; }
/* 图标分类折叠 */
.sp-fold-toolbar { display: flex; justify-content: flex-end; padding: 4px 2px 6px; }
.sp-fold-all { background: none; border: 1px solid #d9d9d9; border-radius: 10px; padding: 2px 10px; font-size: 12px; color: #666; cursor: pointer; }
.sp-fold-all:hover { color: #333; border-color: #999; }
.sp-fold-head { cursor: pointer; user-select: none; transition: opacity .15s; }
.sp-fold-head:hover { opacity: .8; }
.sp-fold-arrow { display: inline-block; width: 14px; font-size: 10px; color: #999; transition: transform .18s; }
.sp-fold-head.open .sp-fold-arrow { color: #555; }

/* ===== 画布滚动条（透明细条，工具栏同风格） ===== */
.sb-h, .sb-v { position: fixed; z-index: 60; background: transparent; cursor: pointer; -webkit-tap-highlight-color: transparent; }
.sb-h { left: 50%; bottom: 54px; transform: translateX(-50%); width: min(72vw, 920px); height: 4px; border-radius: 999px; }
.sb-v { right: 10px; top: 50%; transform: translateY(-50%); height: min(56vh, 520px); width: 4px; border-radius: 999px; }
.sb-thumb { position: absolute; background: rgba(255,255,255,.42); border-radius: 999px; transition: background .2s ease, width .2s ease, height .2s ease; }
.sb-thumb-h { top: 0; height: 100%; }
.sb-thumb-v { left: 0; width: 100%; }
.sb-h:hover { height: 8px; }
.sb-v:hover { width: 8px; }
.sb-h:hover .sb-thumb, .sb-v:hover .sb-thumb { background: rgba(255,255,255,.72); }
@media (max-width: 768px) {
  .sb-h { bottom: 48px; width: 80vw; }
  .sb-v { right: 6px; }
}
</style>
