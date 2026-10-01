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
        <div class="autosave-wrap" @click.stop="autoSaveOpen = !autoSaveOpen">
          <button title="自动保存设置" :class="{ active: autoSaveOpen }"><svg viewBox="0 0 24 24"><path d="M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8z" fill="none" stroke="currentColor" stroke-width="1.8"/><path d="M12 3v2M12 19v2M4.2 6l1.7 1M18.1 17l1.7 1M3 12h2M19 12h2M4.2 18l1.7-1M18.1 7l1.7-1" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg></button>
          <div v-if="autoSaveOpen" class="autosave-menu" @click.stop>
            <div class="as-row">
              <span class="as-label">自动保存</span>
              <label class="as-switch"><input type="checkbox" v-model="autoSaveEnabled" /><span></span></label>
            </div>
            <div class="as-row">
              <span class="as-label">间隔（秒）</span>
              <input class="as-input" type="number" min="3" max="600" v-model.number="autoSaveInterval" @change="onAutoSaveIntervalChange" />
            </div>
            <div class="as-tip">快捷键 Ctrl+S 立即保存</div>
          </div>
        </div>
        <div class="export-wrap" @click.stop="exportOpen = !exportOpen">
          <button title="导出" :class="{ active: exportOpen }"><svg viewBox="0 0 24 24"><path d="M12 3v12m0 0l-4-4m4 4l4-4" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/><path d="M5 19h14" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg></button>
          <div v-if="exportOpen" class="export-menu">
            <div class="export-menu-item" @click="runExport('json')"><span v-if="isMemberExport('json')" class="em-vip">👑</span><span class="em-tag" style="background:#f7b731">J</span>JSON 数据</div>
            <div class="export-menu-item" @click="exportUsmind"><span v-if="isMemberExport('usmind')" class="em-vip">👑</span><span class="em-tag" style="background:#2d98da">U</span>USMIND 压缩包</div>
            <div class="export-menu-item" @click="runExport('png')"><span v-if="isMemberExport('png')" class="em-vip">👑</span><span class="em-tag" style="background:#45b7d1">P</span>PNG 图片</div>
            <div class="export-menu-item" @click="runExport('jpg')"><span v-if="isMemberExport('jpg')" class="em-vip">👑</span><span class="em-tag" style="background:#e77f67">J</span>JPG 图片</div>
            <div class="export-menu-item" @click="runExport('jpeg')"><span v-if="isMemberExport('jpeg')" class="em-vip">👑</span><span class="em-tag" style="background:#ea8685">G</span>JPEG 图片</div>
            <div class="export-menu-item" @click="runExport('svg')"><span v-if="isMemberExport('svg')" class="em-vip">👑</span><span class="em-tag" style="background:#778beb">S</span>SVG 矢量图</div>
            <div class="export-menu-item" @click="runExport('pdf')"><span v-if="isMemberExport('pdf')" class="em-vip">👑</span><span class="em-tag" style="background:#fc5c65">P</span>PDF 文档</div>
            <div class="export-menu-item" @click="runExport('md')"><span v-if="isMemberExport('md')" class="em-vip">👑</span><span class="em-tag" style="background:#26de81">M</span>Markdown</div>
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
      <div id="cmt-badge-layer" class="cmt-badge-layer"></div>
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

    <!-- 导出进度遮罩 -->
    <div v-if="exportLoading" class="export-loading">
      <div class="pl-spinner"></div>
      <div class="el-title">正在导出</div>
      <div class="el-text">{{ exportTip }}</div>
      <div class="el-bar"><div class="el-bar-fill" :style="{ width: exportProgress + '%' }"></div></div>
      <div class="el-pct">{{ exportProgress }}%</div>
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
      <div class="sp-body" ref="spBodyRef" @scroll.passive="onIconScroll">
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
              <span v-if="isMemberTheme(t)" class="theme-vip" title="会员专属主题">👑</span>
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
              <span v-if="iconOpen[grp.category] && grp.emojis.length > ICON_STEP" class="sp-fold-count">{{ iconShown(grp) }}/{{ grp.emojis.length }}</span>
            </div>
            <div v-show="iconOpen[grp.category]" class="icon-grid">
              <button v-for="e in grp.emojis.slice(0, iconShown(grp))" :key="e" @click="insertEmoji(e)" :title="e">{{ e }}</button>
              <button v-if="iconShown(grp) < grp.emojis.length" class="sp-load-more" @click="loadMoreIcons(grp.category)">加载更多…</button>
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
        <button data-act="comments" @click="ctxAct('comments')">查看评论</button>
        <div class="ctx-sep"></div>
        <button v-if="ctxNode && canDeleteNode(ctxNode)" data-act="del" class="ctx-danger" @click="ctxAct('del')">删除节点</button>
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


    <!-- 节点评论面板（作者端） -->
    <div v-if="cmtPanel.open" class="cmt-panel">
      <div class="cmt-header">
        <span class="cmt-title">💬 {{ cmtPanel.nodeName || '节点' }}</span>
        <span class="cmt-hd-r">
          <button class="cmt-sound" :title="cmtSoundOn ? '关闭评论提示音' : '开启评论提示音'" @click="toggleCmtSound">{{ cmtSoundOn ? '🔊' : '🔇' }}</button>
          <button class="cmt-close" @click="closeCmtPanel">×</button>
        </span>
      </div>
      <div class="cmt-list">
        <div v-if="!cmtPanel.list.length" class="cmt-empty">还没有评论</div>
        <div v-for="c in cmtPanel.list" :key="c.id" class="cmt-item" :class="{ 'cmt-author': c.isAuthor === 1 }">
          <div class="cmt-head">
            <span class="cmt-src" :class="c.source === 'org' ? 'cmt-src-org' : 'cmt-src-share'">{{ c.source === 'org' ? '组织' : '分享' }}</span>
            <b>{{ c.userName }}</b>
            <span v-if="c.isAuthor === 1 && c.userId && myUid() === Number(c.userId)" class="cmt-author-tag">我</span>
            <span class="cmt-time">{{ fmtCmtTime(c.createdAt) }}</span>
          </div>
          <div class="cmt-body">{{ c.content }}</div>
          <div class="cmt-foot">
            <button class="cmt-reply" @click="setCmtReply(c)">{{ cmtPanel.replyTo && cmtPanel.replyTo.id === c.id ? '取消回复' : '回复' }}</button>
          </div>
          <div v-if="cmtPanel.replyTo && cmtPanel.replyTo.id === c.id" class="cmt-quick">
            <input v-model="cmtPanel.input" maxlength="128" :placeholder="'回复 ' + c.userName + '…'" @keyup.enter="submitComment" />
            <button @click="submitComment">发送</button>
          </div>
        </div>
      </div>
      <div class="cmt-tip">
        <template v-if="cmtPanel.cmtErr"><span class="cmt-err">{{ cmtPanel.cmtErr }}</span></template>
        <template v-else>共 {{ cmtPanel.list.length }} 条评论 ｜ 有新评论的节点会闪烁提示</template>
      </div>
      <div v-if="!cmtPanel.replyTo" class="cmt-new">
        <input v-model="cmtPanel.input" maxlength="128" placeholder="组织内评论（成员可见，标注为「组织」）…" @keyup.enter="submitComment" />
        <button @click="submitComment">发送</button>
      </div>
    </div>

    <!-- 分享链接弹窗 -->
  <!-- 协作保存冲突弹窗 -->
  <div v-if="conflictDialog" class="share-mask" @click.self="conflictDialog = false">
    <div class="share-box">
      <div class="share-title">协作冲突</div>
      <div class="share-tip">该导图已被其他成员修改（版本已更新）。请选择：</div>
      <div class="share-actions">
        <button class="math-btn math-btn-primary" @click="loadLatest">加载最新内容</button>
        <button class="math-btn" @click="forceSave">以我的为准覆盖</button>
        <button class="math-btn" @click="conflictDialog = false">取消</button>
      </div>
    </div>
  </div>

  <!-- 保存冲突 diff 弹窗（并发合并时同节点双方内容不一致） -->
  <div v-if="saveConflictDialog" class="share-mask">
    <div class="share-box" style="max-width:560px">
      <div class="share-title">保存冲突：以下节点内容不一致</div>
      <div class="share-tip">保存时导图已被其他成员修改，双方内容不同的节点如下（编辑锁定机制下通常不会出现）。其余节点将自动合并保留，互不覆盖。</div>
      <div class="conflict-list">
        <div v-for="c in saveConflictDialog.conflicts" :key="c.uid" class="conflict-item">
          <div class="conflict-node">{{ c.name }}</div>
          <div class="conflict-cols">
            <div class="conflict-col mine">
              <div class="conflict-label">我的内容</div>
              <div class="conflict-text">{{ c.myText || '（空）' }}</div>
            </div>
            <div class="conflict-col remote">
              <div class="conflict-label">对方内容</div>
              <div class="conflict-text">{{ c.remoteText || '（空）' }}</div>
            </div>
          </div>
        </div>
      </div>
      <div class="share-actions">
        <button class="math-btn math-btn-primary" @click="saveConflictResolve(true)">全部以我的为准</button>
        <button class="math-btn" @click="saveConflictResolve(false)">全部以对方的为准</button>
        <button class="math-btn" @click="saveConflictDialog = null">取消</button>
      </div>
    </div>
  </div>

  <!-- 机构已销毁提示弹窗 -->
  <div v-if="orgDisbanded" class="share-mask">
    <div class="share-box">
      <div class="share-title">机构已销毁</div>
      <div class="share-tip">机构{{ orgDisbandedName }}已被创建者解散，你已被移出协作并返回首页。</div>
      <div class="share-actions">
        <button class="math-btn math-btn-primary" @click="goHomeAfterDisband">确定</button>
      </div>
    </div>
  </div>

  <!-- 编辑中节点被他人修改增强冲突框 -->
  <div v-if="editConflict" class="share-mask">
    <div class="share-box">
      <div class="share-title">节点被其他成员修改</div>
      <div class="share-tip">你正在编辑的节点已被其他成员更新为：{{ (editConflict.remoteText || '（空）').slice(0, 60) }}</div>
      <div class="share-actions">
        <button class="math-btn math-btn-primary" @click="resolveEditConflict('load')">加载最新内容</button>
        <button class="math-btn" @click="resolveEditConflict('mine')">以我的为准</button>
      </div>
    </div>
  </div>

  <!-- 协作实时更新提示气泡 -->
  <div v-if="collabToast.show" class="collab-toast">
    <span class="collab-toast-text">{{ collabToast.text }}</span>
    <button class="math-btn math-btn-primary math-btn-sm" @click="applyCollabUpdate">立即加载</button>
    <button class="math-btn math-btn-sm" @click="collabToast.show = false">忽略</button>
  </div>

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
        <div class="share-tip">已开启分享<template v-if="sharePassword">（需密码访问）</template>，链接：</div>
        <input readonly :value="shareLink" class="share-link-input" @focus="e => e.target.select()" />
        <template v-if="sharePassword">
          <div class="share-tip" style="margin-top:8px">访问密码（点击复制）：</div>
          <input readonly :value="sharePassword" class="share-link-input" @focus="e => e.target.select()" />
        </template>
        <div v-else-if="shareHasPwd" class="share-tip" style="margin-top:8px;color:#e6b422">已设置访问密码（原密码不可找回，可点「重设密码」输入新密码）</div>
        <div class="share-actions">
          <button class="math-btn math-btn-primary" @click="copyShare('link')">复制链接</button>
          <button v-if="sharePassword" class="math-btn" @click="copyShare('pwd')">复制密码</button>
          <button class="math-btn" @click="shareReady = false">重设密码</button>
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
import { memberApi, configApi } from '../api/member'
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
import { PDFDocument } from 'pdf-lib'
import html2canvas from 'html2canvas'
import { THEMES } from '../utils/theme.js'
import { fetchThemes } from '../api/themes'
import { fetchPreviewConfig } from '../api/previewConfig'
import { mindmapApi } from '../api/mindmap'
import { commentApi } from '../api/comment'
import { fetchIcons } from '../api/icons'
import { uploadMedia } from '../utils/mediastore'
import { aiApi } from '../api/ai'
import { loadMathJax, tex2svgHtml, extractLatexFromHtml as extractLatexUtil, migrateKatexToSvg } from '../utils/mathRenderer'
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
// 懒加载：每分类分批发渲染，滚动到底/点击"加载更多"再追加（避免一次渲染上千 emoji）
const ICON_STEP = 40
const iconVisible = reactive({})
function iconShown(grp) {
  const total = (grp && Array.isArray(grp.emojis)) ? grp.emojis.length : 0
  return Math.min(iconVisible[grp.category] || ICON_STEP, total)
}
function toggleIconGroup(cat) {
  iconOpen[cat] = !iconOpen[cat]
  if (iconOpen[cat] && !iconVisible[cat]) iconVisible[cat] = ICON_STEP
}
function loadMoreIcons(cat) {
  iconVisible[cat] = (iconVisible[cat] || ICON_STEP) + ICON_STEP
}
function onIconScroll() {
  const el = spBodyRef.value
  if (!el) return
  // 距底不足 400 时，为所有展开分类追加一批（滚动接近底部持续加载）
  if (el.scrollHeight - el.scrollTop - el.clientHeight < 400) {
    emojiGroups.value.forEach(g => {
      if (iconOpen[g.category] && iconShown(g) < g.emojis.length) {
        iconVisible[g.category] = (iconVisible[g.category] || ICON_STEP) + ICON_STEP
      }
    })
  }
}
const allIconOpen = computed(() => emojiGroups.value.length > 0 && emojiGroups.value.every(g => iconOpen[g.category]))
function toggleAllIcons() {
  const allOpen = allIconOpen.value
  emojiGroups.value.forEach(g => {
    iconOpen[g.category] = !allOpen
    if (iconOpen[g.category] && !iconVisible[g.category]) iconVisible[g.category] = ICON_STEP
  })
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
const spBodyRef = ref(null)
const mind = ref(null)
// 预览/高亮类型配置：后台 sys_config 可配，内置默认兜底（离线也可用）
const CODE_MODE_DEFAULT = { js:'text/javascript', mjs:'text/javascript', cjs:'text/javascript', ts:'text/javascript', jsx:'text/javascript', tsx:'text/javascript', json:'application/json', map:'application/json', html:'text/html', htm:'text/html', vue:'text/html', xml:'application/xml', svg:'application/xml', css:'text/css', scss:'text/x-scss', less:'text/x-less', md:'text/x-markdown', markdown:'text/x-markdown', py:'text/x-python', python:'text/x-python', java:'text/x-java', kt:'text/x-kotlin', c:'text/x-csrc', h:'text/x-csrc', cpp:'text/x-c++src', cc:'text/x-c++src', cxx:'text/x-c++src', hpp:'text/x-c++src', hh:'text/x-c++src', cs:'text/x-csharp', go:'text/x-go', rs:'text/x-rust', php:'text/x-php', sh:'text/x-sh', bash:'text/x-sh', zsh:'text/x-sh', sql:'text/x-sql', rb:'text/x-ruby', yml:'text/x-yaml', yaml:'text/x-yaml', swift:'text/x-swift' }
const TEXT_EXT_DEFAULT = ['txt','log','ini','conf','cfg','csv','env','properties','tsv','gitignore','dockerfile','sass','plist']
const previewModes = ref({ ...CODE_MODE_DEFAULT })
const attachTextColor = ref('#e53935')
const textPreviewExts = ref([...TEXT_EXT_DEFAULT])
const mindMapId = ref(route.params.id || null)

// 状态
const docName = ref('未命名')
const dirty = ref(false)
// 加载/setData 引发的 data_change 抑制时间戳（避免把"加载渲染"误判为未保存修改）
let suppressDirtyUntil = 0
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
const exportLoading = ref(false)
const exporting = ref(false)
const exportTip = ref('')
const exportProgress = ref(0)
function showExportLoading(total, kind) {
  exportLoading.value = true
  exportProgress.value = 0
  exportTip.value = '正在生成画布数据...'
  // 兜底：异常未关闭时最多展示 10 分钟后自关，避免遮罩卡死
  clearTimeout(exportLoadingTimer)
  exportLoadingTimer = setTimeout(() => { if (exportLoading.value) hideExportLoading() }, 600000)
}
function updateExportLoading(done, total, label) {
  exportProgress.value = total > 0 ? Math.round(done / total * 100) : 100
  exportTip.value = label || ''
}
function hideExportLoading() {
  clearTimeout(exportLoadingTimer)
  exportLoading.value = false
  exportTip.value = ''
}
let exportLoadingTimer = null
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
let onCtrlSKeydown = null

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
  suppressDirtyUntil = Date.now() + 8000
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
    // 禁用库内置附件图标（内容区右侧），附件图标统一使用自定义注入的节点上方图标
    attachmentIcon: { style: { size: 0 }, icon: '' },
    rainbowLinesConfig: { open: true, colorsList: getBranchColors() },
    iconList: [
      { type: 'number', list: Array.from({length:10},(_,i)=>({name:String(i+1),icon:numSvg(i+1)})) },
      { type: 'emoji', list: FALLBACK_EMOJI.map(e=>({name:e,icon:emojiSvg(e)})) }
    ],
    // 协作锁：被他人锁定的节点不允许进入文本编辑（TextEdit 官方钩子）
    beforeTextEdit: (node) => {
      try {
        // 公式节点：双击走公式编辑弹窗，禁止库自动进入文本编辑（避免编辑框显示 SVG 源码乱码）
        const ndText = (node.nodeData && node.nodeData.text) || ''
        const getText = (() => { try { return node.getData ? node.getData('text') : '' } catch (e) { return '' } })()
        const text = ndText || getText || ''
        if (text && (text.indexOf('katex') >= 0 || text.indexOf('mjx-container') >= 0 || text.indexOf('MathJax') >= 0)) {
          return false
        }
        const nid = node.uid || (node.nodeData && node.nodeData.uid)
        if (nid && myEditNodeId !== nid) {
          const lk = nodeLocks[nid]
          if (lk && lk.userId !== myUid()) {
            toast('该节点已被 ' + lk.userName + ' 锁定，无法编辑', 'error')
            return false
          }
        }
      } catch (e) {}
      return true
    },

    // 导出时给克隆 SVG 加 viewBox：超大画布（如全展开的万级节点）无 viewBox 时，
    // 查看器/浏览器按 1:1 只显示左上角一小块，加 viewBox 后可完整 fit 显示全部内容
    handleBeingExportSvg: (svg) => {
      try {
        const w = parseFloat(svg.attr('width')) || 0
        const h = parseFloat(svg.attr('height')) || 0
        // 先按画布尺寸给默认 viewBox，再用内容实际边界扩展，
        // 解决缩放/平移后内容 transform 偏移导致导出 SVG 打开空白/只显示部分的问题
        if (w && h) {
          svg.attr('viewBox', '0 0 ' + w + ' ' + h)
          const container = svg.find('.smm-container')[0]
          if (container) {
            const rb = container.rbox()
            if (rb && isFinite(rb.x) && isFinite(rb.y) && rb.width > 0 && rb.height > 0) {
              const x = Math.min(0, rb.x)
              const y = Math.min(0, rb.y)
              const maxX = Math.max(w, rb.x + rb.width)
              const maxY = Math.max(h, rb.y + rb.height)
              svg.attr('viewBox', x + ' ' + y + ' ' + (maxX - x) + ' ' + (maxY - y))
            }
          }
        }
        // 懒加载隐藏的节点（applyLazyView 设置 visibility:hidden）导出时必须全显，否则导出的
        // SVG/PNG/PDF 只有当前视口附近的内容；节点其实都在 DOM 中，仅需把 hidden 改回 visible
        svg.find('*').each(function () {
          const st = this.attr('style')
          if (st && /visibility:\s*hidden/.test(st)) this.attr('style', st.replace(/visibility:\s*hidden/g, 'visibility:visible'))
        })
        // 浅色文字转深色：部分主题（如晨曦白）节点文字为白色，导出 SVG 后
        // 节点背景（CSS 渲染）不随 SVG 序列化，白字在浅色底上看不见，强制转深保证可读
        svg.find('text').each(function () {
          const el = this
          const fill = el.attr('fill')
          if (!fill) return
          const m = String(fill).match(/^#([0-9a-fA-F]{6})$/)
          if (m) {
            const r = parseInt(m[1].slice(0, 2), 16)
            const g = parseInt(m[1].slice(2, 4), 16)
            const b = parseInt(m[1].slice(4, 6), 16)
            const lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255
            if (lum > 0.7) el.attr('fill', '#333333')
          }
        })
      } catch (e) { /* 忽略，保持默认导出 */ }
      return svg
    }  })
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
  mind.value.on('data_change', () => { if (wsApplying) return; if (Date.now() < suppressDirtyUntil) return; markDirty(); scheduleAutoSave(); scheduleBranchRainbow(); clearTimeout(sbTimer); sbTimer = setTimeout(updateScrollbars, 400) })
  mind.value.on('view_data_change', (vd) => { try { updateScrollbars() } catch (e) {}; if (canvasTabs.value[docIndex.value]) canvasTabs.value[docIndex.value].viewData = vd; applyLazyView(); try { repositionCommentBadges() } catch (e) {} })
  mind.value.on('scale', (s) => { zoomPct.value = Math.round(s*100) + '%'; try { updateScrollbars() } catch (e) {}; applyLazyView(); try { repositionCommentBadges() } catch (e) {} })
  mind.value.on('back_forward', (cur, total) => {
    const bu = document.getElementById('btn-undo'); const br = document.getElementById('btn-redo')
    if (bu) bu.disabled = cur <= 0
    if (br) br.disabled = cur >= total-1
  })
  mind.value.on('node_contextmenu', (e, node) => { e.preventDefault(); if (node && node.nodeData) showCtxMenu(e, node) })
  mind.value.on('node_click', (node) => { hideCtxMenu(); onNodeClickComment(node) })
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
  // 删除键兼容兜底：部分 Chromium 变体（Edge / 微信内置浏览器）在节点编辑框里
  // Backspace/Delete 点击没反应（浏览器未执行删除）。这里在 keydown 后检查内容，
  // 若浏览器未删除则手动删除光标位置字符；Chrome 等正常处理时内容已变，兜底自动跳过，不影响。
  const onEditKeydownCompat = (e) => {
    if (e.key !== 'Backspace' && e.key !== 'Delete') return
    const w = e.target
    if (!w || !w.classList || !w.classList.contains('smm-node-edit-wrap')) return
    const before = w.innerText || ''
    setTimeout(() => {
      if (!document.body.contains(w)) return
      if ((w.innerText || '') !== before) return
      try {
        const sel = window.getSelection()
        if (!sel || !sel.rangeCount) return
        const r = sel.getRangeAt(0)
        if (!r.collapsed) {
          r.deleteContents()
          w.dispatchEvent(new Event('input', { bubbles: true }))
          return
        }
        const el = r.startContainer
        if (el.nodeType !== 3) return
        const txt = el.textContent || ''
        let off = r.startOffset
        if (e.key === 'Backspace') {
          if (off <= 0) return
          off--
          if (off > 0) {
            const c = txt.charCodeAt(off)
            if (c >= 0xDC00 && c <= 0xDFFF) off--
          }
          r.setStart(el, off)
        } else {
          if (off >= txt.length) return
          let end = off + 1
          if (end < txt.length) {
            const c = txt.charCodeAt(end)
            if (c >= 0xDC00 && c <= 0xDFFF) end++
          }
          r.setEnd(el, end)
        }
        r.deleteContents()
        w.dispatchEvent(new Event('input', { bubbles: true }))
      } catch (err) {}
    }, 0)
  }
  document.addEventListener('keydown', onCtrlEnterKeydown, true)
  document.addEventListener('keydown', onEditKeydownCompat, true)
  onBeforeUnmount(() => { document.removeEventListener('keydown', onCtrlEnterKeydown, true); document.removeEventListener('keydown', onEditKeydownCompat, true); document.removeEventListener('keydown', onNodeShortcutKeydown, true); window.removeEventListener('resize', updateCanvasBgSize) })
  onBeforeUnmount(() => { document.removeEventListener('mousemove', sbMove); document.removeEventListener('mouseup', sbUp); document.removeEventListener('touchmove', sbMove); document.removeEventListener('touchend', sbUp); document.removeEventListener('mousemove', rootPanMove); document.removeEventListener('mouseup', rootPanEnd); if (mapEl.value) { mapEl.value.removeEventListener('touchstart', onMapTouchStart); mapEl.value.removeEventListener('touchmove', onMapTouchMove); mapEl.value.removeEventListener('touchend', onMapTouchEnd); mapEl.value.removeEventListener('touchcancel', onMapTouchEnd) } })

  // KaTeX 字体异步加载：字体就绪后重渲染，修复公式行高按 fallback 字体测量导致的塌陷
  if (document.fonts && document.fonts.ready) {
    document.fonts.ready.then(() => {
      setTimeout(() => { try { if (mind.value) mind.value.render() } catch (e) {} }, 300)
    })
  }

  // 双击：图片节点 → 全屏浏览图片；附件节点 → 打开附件编辑与浏览（统一双击规则）；公式节点 → 公式编辑弹窗
  mind.value.on('node_dblclick', (node, e) => {
    try {
      const nid0 = node.uid || (node.nodeData && node.nodeData.uid)
      if (nid0 && myEditNodeId !== nid0) {
        const lk = nodeLocks[nid0]
        if (lk && lk.userId !== myUid()) {
          try { mind.value.renderer.textEdit.hideEditTextBox() } catch (err) {}
          setTimeout(() => { try { mind.value.renderer.textEdit.hideEditTextBox() } catch (err) {} }, 0)
          toast('该节点已被 ' + lk.userName + ' 锁定，无法编辑', 'error')
          return
        }
      }
      if (node.getData('attachmentUrl')) {
        try { mind.value.renderer.textEdit.hideEditTextBox() } catch (err) {}
        const nm = node.getData('attachmentName') || ''
        const ex = (nm.match(/\.([a-zA-Z0-9]+)$/) || [,''])[1].toLowerCase()
        const isImg = ['png','jpg','jpeg','gif','webp','bmp','ico','avif','svg'].includes(ex)
        if (isImg) { openImageLightbox(node.getData('attachmentUrl'), nm); return }
        openAttachmentPreview(node)
        return
      }
      const text = node.getData('text') || ''
      const rich = node.getData('richText') === true
      // 图片节点：内容只有一个 <img>（允许 div 包裹、无其它文字）→ 全屏浏览
      if (rich) {
        const tmp = document.createElement('div')
        tmp.innerHTML = text
        const imgs = tmp.querySelectorAll('img')
        const txt = (tmp.textContent || '').trim()
        if (imgs.length === 1 && !txt) {
          try { mind.value.renderer.textEdit.hideEditTextBox() } catch (err) {}
          openImageLightbox(imgs[0].src || imgs[0].getAttribute('src'), '')
          return
        }
      }
      if (text.indexOf('katex') >= 0 || text.indexOf('mjx-container') >= 0 || text.indexOf('MathJax') >= 0) {
        try { mind.value.renderer.textEdit.hideEditTextBox() } catch (err) {}
        acquireNodeLock(nid0)
        openMathEdit(node)
        return
      }
      acquireNodeLock(nid0)
    } catch (err) { console.warn(err) }
  })
  mind.value.on('draw_click', () => hideCtxMenu())
  mind.value.on('node_dragging', () => mapEl.value.classList.add('no-anim'))
  mind.value.on('node_dragend', () => mapEl.value.classList.remove('no-anim'))
  mind.value.on('node_text_edit_change', (ev) => {
    mapEl.value.classList.add('no-anim')
    const nd = ev && ev.node
    const nid = nd && (nd.uid || (nd.nodeData && nd.nodeData.uid))
    // 覆盖 fast input（单击直接输入）等未走双击路径的编辑：补获取锁，否则 node_change 会被后端锁校验拒绝
    if (nid && myEditNodeId !== nid) acquireNodeLock(nid)
    pushNodeChange(nd, ev && ev.text)
  })
mind.value.on('hide_text_edit', () => { textEditActive.value = false; releaseNodeLock() })
  // 结构类命令（创建/删除/移动/粘贴节点）执行后立即保存：让协作端秒级收到广播，无需等自动保存周期
  mind.value.on('afterExecCommand', (name) => {
    // 远端增量应用触发的命令（wsApplying 门闩）：不回环保存、不二次广播
    if (wsApplying) return
    if (STRUCT_CHANGE_CMDS.indexOf(name) >= 0) {
      scheduleStructureSave()
      // 新建节点标记创建者：后端 get 时已给历史节点补 creatorUid（导图创建者），
      // 这里只给仍无创建者的节点（即本次新插入的节点）补上当前用户
      if (name && name.indexOf('INSERT') === 0) {
        try {
          const rn = mind.value.renderer && mind.value.renderer.root
          if (rn) {
            const walk = (n) => {
              if (n && n.nodeData && !n.nodeData.creatorUid) n.nodeData.creatorUid = myUid()
              if (n.children) n.children.forEach(walk)
            }
            walk(rn)
          }
        } catch (e) {}
      }
      // 结构增量实时广播：对端零延迟局部应用（不拉全量、不闪屏）
      broadcastStructDelta()
    }
    // 折叠/展开后：渲染重绘，延迟重建评论特效（收起节点特效落到最近可见祖先=未收起的部分）
    if (name === 'SET_NODE_EXPAND' || name === 'UNEXPAND_ALL' || name === 'EXPAND_ALL') {
      setTimeout(() => { try { syncCommentFlash() } catch (e) {} }, 220)
    }
  })
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
      setTimeout(() => {
        if (!mind.value.renderer.textEdit.showTextEdit) {
          textEditActive.value = false
          // 让 250ms 节流的最后一次 node_change 先发出（约300ms），再释放锁，避免末尾编辑内容丢失
          setTimeout(() => { if (!myEditNodeId) return; releaseNodeLock() }, 320)
        }
      }, 250)
    }
  }
  document.addEventListener('focusin', onEditFocusIn)
  document.addEventListener('focusout', onEditFocusOut)
  // 移动端输入法兜底：部分浏览器 composition 期间不发 input 事件，compositionend 时补一次实时推送
  const onEditCompositionEnd = () => {
    try {
      const box = document.querySelector('.smm-node-edit-wrap, .smm-text-edit, [contenteditable=true]')
      if (!box || !myEditNodeId) return
      const node = mind.value.renderer.findNodeByUid(myEditNodeId)
      if (node) pushNodeChange(node, box.textContent || '')
    } catch (e) {}
  }
  document.addEventListener('compositionend', onEditCompositionEnd, true)
  mind.value.on('node_tree_render_end', () => { try { updateScrollbars() } catch (e) {}; clearTimeout(loadingTimer); if (!mind.value.editNodeClassList || !mind.value.editNodeClassList.length) mapEl.value.classList.remove('no-anim'); if (loadingToken > 0) hideLoading(loadingToken); applyLazyView(); try { fixAttachNodeTexts() } catch (e) {}; try { injectAttachIcons() } catch (e) {}; try { applyCommentFlash() } catch (e) {} })
  mind.value.on('node_attachmentClick', (node) => openAttachmentPreview(node))
  // 轮询注入附件图标（不依赖 render_end 时序）
  if (window.__attIconTimer) clearInterval(window.__attIconTimer)
  window.__attIconTimer = setInterval(() => {
    try {
      try { fixAttachNodeTexts() } catch (e) {}
      injectAttachIcons()
    } catch (e) {}
  }, 700)
}

function markDirty() { dirty.value = true }
function markSaved() {
  dirty.value = false
  const d = new Date()
  savedTime.value = '最近保存 ' + d.getHours() + ':' + String(d.getMinutes()).padStart(2,'0')
}

async function saveDoc(opts = {}) {
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
    const r = await mindmapApi.update(mindMapId.value, {
      name: docName.value,
      data: { root: data, _tabs: canvasTabs.value, _activeTab: docIndex.value },
      layout: doc.layout,
      theme: doc.theme,
      viewData: doc.viewData,
      manualStyles: Array.from(manualStyled),
      baseVersion: opts.baseVersion != null ? opts.baseVersion : curVersion.value,
      deletedUids: deletedUids.size ? Array.from(deletedUids) : [],
      resolveConflicts: opts.resolveConflicts || undefined
    })
    if (r && r.version != null) curVersion.value = r.version
    deletedUids.clear()
    markSaved()
  } catch (e) {
    if (e && e.code === 409 && e.data && e.data.conflicts && e.data.conflicts.length) {
      // 并发合并冲突：弹 diff 框由用户选择以谁为准（其余节点自动合并保留）
      saveConflictDialog.value = { conflicts: e.data.conflicts, serverVersion: e.data.serverVersion }
    } else if (e && e.code === 409) {
      conflictDialog.value = true
    } else {
      console.warn('保存失败:', e)
      toast((e && e.message) || '保存失败', 'error')
    }
  }
}

/** 保存冲突 diff 弹窗：选择以谁为准后重新保存（其余未冲突节点仍合并保留） */
async function saveConflictResolve(useMine) {
  const dlg = saveConflictDialog.value
  if (!dlg) return
  saveConflictDialog.value = null
  await saveDoc({ resolveConflicts: useMine ? 'mine' : 'theirs', baseVersion: dlg.serverVersion })
}

/** 冲突后：以我的为准覆盖 */
async function forceSave() {
  conflictDialog.value = false
  const doc = canvasTabs.value[docIndex.value]
  const data = mind.value.getData()
  if (data && data.data && styleRadius.value > 0 && !data.data.shape) data.data.shape = 'rectangle'
  try {
    const r = await mindmapApi.update(mindMapId.value, {
      name: docName.value,
      data: { root: data, _tabs: canvasTabs.value, _activeTab: docIndex.value },
      layout: doc.layout,
      theme: doc.theme,
      viewData: doc.viewData,
      manualStyles: Array.from(manualStyled),
      baseVersion: curVersion.value,
      force: true
    })
    if (r && r.version != null) curVersion.value = r.version
    markSaved()
    toast('已以你的内容覆盖保存')
  } catch (e) {
    toast((e && e.message) || '覆盖保存失败', 'error')
  }
}

/** 冲突后：加载最新内容 */
async function loadLatest(silent = false) {
  conflictDialog.value = false
  try {
    const fresh = await mindmapApi.get(mindMapId.value, { params: { _t: Date.now() } })
    let d = fresh.data || {}
    if (typeof d === 'string' && d) { try { d = JSON.parse(d) } catch (e2) { d = {} } }
    if (d._tabs && Array.isArray(d._tabs) && d._tabs.length) {
      canvasTabs.value = d._tabs
      docIndex.value = d._activeTab || 0
      if (d.root) {
        const active = canvasTabs.value[docIndex.value]
        if (active) { if (active.data) active.data.root = d.root; else active.data = { root: d.root } }
      }
    } else {
      canvasTabs.value = [{ name: fresh.name || '未命名', layout: fresh.layout, theme: fresh.theme, viewData: fresh.viewData, data: { root: d.root || d }, manualStyles: [] }]
      docIndex.value = 0
    }
    curVersion.value = fresh.version || 0
    mapOwnerId.value = Number(fresh.userId) || 0
    suppressDirtyUntil = Date.now() + 8000
    await loadDoc(silent)
    if (!silent) toast('已加载最新内容')
  } catch (e) {
    toast('加载最新失败: ' + e.message, 'error')
  }
}

/** 远端更新 diff 局部应用：不重建整图、保留视图 transform、不闪屏 */
async function diffApplyLatest() {
  try {
    const fresh = await mindmapApi.get(mindMapId.value, { params: { _t: Date.now() } })
    curVersion.value = fresh.version || 0
    mapOwnerId.value = Number(fresh.userId) || 0
    let d = fresh.data || {}
    if (typeof d === 'string' && d) { try { d = JSON.parse(d) } catch (e2) { d = {} } }
    // 优先主数据 root（保存主字段）；_tabs 仅作兜底，避免 API 直改后 _tabs 与主数据不一致时 diff 用了旧树
    let remoteRoot = (d.root && d.root.data && d.root.data.uid) ? d.root : ((d.root && d.root.data) ? { data: d.root.data, children: d.root.children || [] } : (d || {}))
    if (!remoteRoot || !(remoteRoot.uid || (remoteRoot.data && remoteRoot.data.uid))) {
      if (d._tabs && Array.isArray(d._tabs) && d._tabs.length) {
        const active = d._tabs[d._activeTab || 0]
        if (active && active.data && active.data.root) remoteRoot = active.data.root
      }
    }
    if (!remoteRoot || !(remoteRoot.uid || (remoteRoot.data && remoteRoot.data.uid))) return
    const uidOf = (n) => (n && (n.uid || (n.data && n.data.uid))) || ''
    const textOf = (n) => (n && (n.text !== undefined ? n.text : (n.data && n.data.text)))
    const findRemoteByUid = (n, uid) => {
      if (!n) return null
      if (uidOf(n) === uid) return n
      for (const c of (n.children || [])) { const f = findRemoteByUid(c, uid); if (f) return f }
      return null
    }
    // 编辑中：不做树 diff（避免破坏正在编辑的本地树），只检测自己正在编辑的节点是否被远端改动→弹增强冲突框（带对方内容）
    if (myEditNodeId) {
      try {
        const myNode = mind.value.renderer && mind.value.renderer.findNodeByUid(myEditNodeId)
        if (myNode) {
          const rd = findRemoteByUid(remoteRoot, myEditNodeId)
          if (rd) {
            const rtext = textOf(rd)
            if (rtext !== undefined && myNode.nodeData.text !== rtext && !editConflict.value) {
              editConflict.value = { nodeId: myEditNodeId, remoteText: rtext == null ? '' : rtext }
            }
          }
        }
      } catch (e) {}
      return
    }
    const localRoot = mind.value.renderer && mind.value.renderer.root
    if (!localRoot) return
    // 局部应用期间关闭动画，避免批量 remove/insert 造成视觉跳动
    mapEl.value.classList.add('no-anim')
    const localMap = {}
    const depthOf = {}
    const walkLocal = (n, dep) => { if (!n || !n.uid) return; localMap[n.uid] = n; depthOf[n.uid] = dep; (n.children || []).forEach(c => walkLocal(c, dep + 1)) }
    walkLocal(localRoot, 0)
    const remoteUids = new Set()
    const walkRemote = (n) => { if (!n) return; const u = uidOf(n); if (u) remoteUids.add(u); (n.children || []).forEach(walkRemote) }
    walkRemote(remoteRoot)
    // 1. 删除本地多余节点（先删深层）：只删本地明确删除（deletedUids）的，其余保留
    //    （防止对方旧版本全量同步时把自己刚新增但尚未保存的节点删掉）
    const toRemove = Object.keys(localMap)
      .filter(uid => !remoteUids.has(uid) && localMap[uid].parent && deletedUids.has(uid))
      .sort((a, b) => (depthOf[b] || 0) - (depthOf[a] || 0))
      .map(uid => localMap[uid])
    if (toRemove.length) {
      wsApplying = true
      try { toRemove.forEach(nd => { try { if (nd.parent) mind.value.renderer.removeNode(nd) } catch (e) {} }) } finally { wsApplying = false }
    }
    // 2. 新增节点 + 文本变更 + creatorUid 同步
    const applyRemote = (rd, parentUid) => {
      if (!rd) return
      const uid = uidOf(rd)
      if (!uid) return
      // 本地明确删除过的节点不复活（防对方旧版本带回来）
      if (deletedUids.has(uid)) return
      let localNode = localMap[uid]
      if (!localNode) {
        const parentNode = parentUid ? localMap[parentUid] : null
        if (parentNode) {
          try {
            // 引擎 insertChildNode 的 appointData 是节点 data 对象（含 uid/text/creatorUid）
            const nodeData = (rd.data && typeof rd.data === 'object') ? rd.data : { uid: uid, text: textOf(rd) || '' }
            const children = rd.children || []
            wsApplying = true
            mind.value.renderer.insertChildNode(false, [parentNode], JSON.parse(JSON.stringify(nodeData)), children)
            wsApplying = false
            const nn = mind.value.renderer.findNodeByUid(uid)
            if (nn) { localMap[uid] = nn; localNode = nn }
          } catch (e) { wsApplying = false }
        }
        if (!localNode) return
      }
      // 文本变更
      const rtext = textOf(rd)
      if (rtext !== undefined && localNode.nodeData.text !== rtext) {
        if (myEditNodeId === uid) {
          // 自己正在编辑的节点被改：弹增强冲突框（带对方内容），不静默覆盖
          if (!editConflict.value) {
            editConflict.value = { nodeId: uid, remoteText: rtext == null ? '' : rtext }
          }
        } else {
          wsApplying = true
          try { mind.value.renderer.setNodeText(localNode, rtext == null ? '' : rtext) } catch (e) {}
          wsApplying = false
        }
      }
      const rcreator = rd.creatorUid || (rd.data && rd.data.creatorUid)
      if (rcreator && !localNode.nodeData.creatorUid) localNode.nodeData.creatorUid = rcreator
      ;(rd.children || []).forEach(c => applyRemote(c, uid))
    }
    applyRemote(remoteRoot, null)
    suppressDirtyUntil = Date.now() + 3000
    try { canvasTabs.value[docIndex.value].data = mind.value.getData() } catch (e) {}
    try { structSnap = snapshotStruct() } catch (e) {}
  } catch (e) { console.warn('diffApplyLatest err', e) }
  try { mapEl.value.classList.remove('no-anim') } catch (e) {}
}

/** 增强冲突框：加载最新 / 以我为准 */
function resolveEditConflict(choice) {
  const c = editConflict.value
  if (!c) return
  if (choice === 'load') {
    const remoteText = c.remoteText == null ? '' : c.remoteText
    try {
      const te = mind.value.renderer.textEdit
      if (te && te.textEditNode) {
        te.textEditNode.textContent = remoteText
        if (te.showTextEdit) { te.hideEditTextBox(); setTimeout(() => { try { if (te.showTextEdit) te.hideEditTextBox() } catch (e2) {} }, 0) }
      }
    } catch (e) {}
    try {
      const node = mind.value.renderer.findNodeByUid(c.nodeId)
      if (node) {
        wsApplying = true
        node.nodeData.text = remoteText
        delete node.nodeData.richText
        mind.value.renderer.renderNode(node)
        wsApplying = false
      }
    } catch (e) { wsApplying = false }
    // 兜底：若编辑框仍未关闭，强制移除并复位状态
    setTimeout(() => {
      try {
        const te = mind.value.renderer.textEdit
        if (te && te.textEditNode && te.textEditNode.style && te.textEditNode.style.display !== 'none') {
          te.textEditNode.style.display = 'none'
          te.textEditNode.innerHTML = ''
          te.showTextEdit = false
          te.currentNode = null
          textEditActive.value = false
          releaseNodeLock(true)
        }
      } catch (e3) {}
    }, 50)
    try { canvasTabs.value[docIndex.value].data = mind.value.getData() } catch (e) {}
    toast('已加载最新内容')
  }
  editConflict.value = null
}

function scheduleAutoSave() {
  if (!autoSaveEnabled.value) return
  if (autoSaveTimer) clearTimeout(autoSaveTimer)
  autoSaveTimer = setTimeout(() => { if (dirty.value) saveDoc() }, Math.max(3, autoSaveInterval.value) * 1000)
}

// 结构类命令：创建/删除/移动/粘贴节点——执行后立即保存（500ms 防抖），协作端实时感知结构变化
const STRUCT_CHANGE_CMDS = [
  'INSERT_NODE', 'INSERT_CHILD_NODE', 'INSERT_MULTI_NODE', 'INSERT_MULTI_CHILD_NODE', 'INSERT_PARENT_NODE',
  'REMOVE_NODE', 'REMOVE_CURRENT_NODE', 'PASTE_NODE', 'CUT_NODE', 'MOVE_NODE', 'UP_NODE', 'DOWN_NODE',
  'MOVE_UP_ONE_LEVEL', 'MOVE_DOWN_ONE_LEVEL'
]
function scheduleStructureSave() {
  if (!autoSaveEnabled.value) return
  if (!dirty.value) return
  if (autoSaveTimer) clearTimeout(autoSaveTimer)
  autoSaveTimer = setTimeout(() => { if (dirty.value) saveDoc() }, 500)
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

async function loadDoc(keepView = false) {
  const tk = ++loadingToken
  // 静默加载（协作广播触发）不显示 loading 遮罩，避免整树重建时闪烁
  if (!keepView) {
    pageLoading.value = true
    loadingStart = Date.now()
    clearTimeout(loadingTimer)
    loadingTimer = setTimeout(() => { if (loadingToken === tk) hideLoading(tk) }, 8000)
    if (mapEl.value) mapEl.value.classList.add('map-loading')
  }
  await nextTick()
  const doc = canvasTabs.value[docIndex.value]
  manualStyled = new Set(doc.manualStyles || [])
  docName.value = doc.name || '未命名'
  applyStyleState(doc)
  applyMediaCardStyle(doc.data.root, doc.theme)
  if (mind.value) {
    const docRoot = (doc.data && doc.data.root) ? doc.data.root : defaultData()
    let keepTransform = null
    if (keepView) {
      try { keepTransform = mind.value.view.getTransformData() } catch (e) {}
    }
    suppressDirtyUntil = Date.now() + 8000
    try { mind.value.setData(docRoot) } catch (e) { console.error('setData失败', e) }
    if (keepView) {
      // 协作静默加载：保留当前视图位置，不强制居中（否则导图会跳位）
      if (keepTransform) {
        try { mind.value.view.setTransformData(keepTransform) } catch (e) {}
        // setData 内部可能异步重置 transform，延迟再恢复一次保证视图不跳动
        ;[300, 900].forEach((d) => setTimeout(() => { try { mind.value.view.setTransformData(keepTransform) } catch (e) {} }, d))
      }
      ;[200, 600].forEach((d) => setTimeout(() => { try { updateScrollbars() } catch (e) {} }, d))
    } else {
      mind.value.view.fit()
      ;[300, 900, 1800].forEach((d) => setTimeout(() => { try { centerRoot(); updateScrollbars() } catch (e) {} }, d))
    }
    if (doc.layout) { mind.value.setLayout(doc.layout); layoutKey.value = LAYOUT_REV[doc.layout] || 'r' }
    mind.value.setTheme(doc.theme)
    ;[400, 1200].forEach((d) => setTimeout(() => { try { applyThemeFonts(doc.theme) } catch (e) {} }, d))
    applyCanvasBackground(doc.theme)
    currentTheme.value = doc.theme
  }
  applyBranchRainbow()
  try { structSnap = snapshotStruct() } catch (e) {}
  markSaved()
  clearTimeout(autoSaveTimer)
  // 延迟 data_change 可能把页面误判为未保存，8s 后（抑制期结束）补一次 markSaved
  setTimeout(() => { if (Date.now() > suppressDirtyUntil) markSaved() }, 8000)
  // setData 重渲染后 SVG 角标会丢失，重挂所有他人锁角标
  try {
    Object.keys(nodeLocks).forEach((nid) => {
      const lk = nodeLocks[nid]
      if (lk && Number(lk.userId) !== myUid()) setLockMark(nid, true, lk.userName)
    })
  } catch (e) {}
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
let pinch = null
function onMapTouchStart(e) {
  if (e.touches.length >= 2) {
    // 双指：开始捏合缩放（只缩放脑图画布，不缩放背景/工具条）
    if (!mind.value) return
    const t1 = e.touches[0], t2 = e.touches[1]
    pinch = { dist: Math.hypot(t1.clientX - t2.clientX, t1.clientY - t2.clientY), scale: mind.value.view.scale }
    touchPan = null
    return
  }
  if (e.touches.length !== 1) return
  const t = e.target
  if (t && t.closest && (t.closest('.sb-h') || t.closest('.sb-v') || t.closest('.toolbar') || t.closest('.style-panel') || t.closest('.ai-panel') || t.closest('.bar-nav') || t.closest('.view-bar') || t.closest('#ctx-menu') || t.closest('.math-dialog') || t.closest('.rich-dialog') || t.closest('#att-preview'))) return
  touchPan = { sx: e.touches[0].clientX, sy: e.touches[0].clientY, st: getViewState(), moved: false }
}
function onMapTouchMove(e) {
  if (pinch && mind.value && e.touches.length >= 2) {
    e.preventDefault()
    e.stopPropagation()
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
    try { mind.value.view.setScale(ns, cx, cy) } catch (e2) {}
    return
  }
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
function onMapTouchEnd() { touchPan = null; pinch = null }

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
  if (!checkMemberFeature('theme', name)) { toast('该主题为会员专属，开通会员后可用', 'error'); return }
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
    // 公式/富元素写入后自动重渲染画布，让节点尺寸按实际 SVG 高度重算，避免公式节点塌陷（替代整页刷新）
    setTimeout(() => { try { if (mind.value) mind.value.render() } catch (e) {} }, 30)
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
  // 打开即预览已有公式（不依赖用户再次编辑）
  if (latex && latex.trim()) {
    loadMathJax().then(() => {
      try { mathPreview.value = tex2svgHtml(latex.trim(), true) } catch (e) { mathPreview.value = '' }
    }).catch(() => { mathPreview.value = '' })
  }
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

async function insertMathToNode() {
  const node = mathNode.value
  const raw = mathInput.value.trim()
  try { await loadMathJax() } catch (e) {}
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
        setTimeout(() => { try { if (mind.value) mind.value.render() } catch (e) {} }, 30)
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
  // 公式写入后自动重渲染画布，避免公式节点塌陷（替代整页刷新）
  setTimeout(() => { try { if (mind.value) mind.value.render() } catch (e) {} }, 30)
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
      // 附件节点文字替换为文件名+后缀（强制纯文本渲染，框随文字自适应，避免文字飘出框外）
      mind.value.renderer.setNodeDataRender(node, { text: file.name || '附件', richText: false })
      // 附件文件名默认染成红色（用户可再手动改回）
      mind.value.execCommand('SET_NODE_STYLES', node, { shape:'roundedRectangle', borderRadius:12, borderWidth:1, borderColor:'#C9CDD6', borderDasharray:'2 4', fillColor:'#F4F5F7', paddingX:6, paddingY:6, textColor: attachTextColor.value })
    }
    if (node.getData('uid')) manualStyled.add(node.getData('uid'))
    scheduleBranchRainbow()
    saveDoc()
  } catch (e) {
    toast('插入媒体失败: ' + (e.message || e), 'error')
  }
}

// 右键菜单：按鼠标位置自适应展开方向（靠近上方向下展开、靠近下方向上展开，右侧不足向左）
function showCtxMenu(e, node) {
  ctxMode.value = 'node'
  ctxNode = node
  ctxNoAnim.value = false
  ctxClosing.value = false
  ctxHidden.value = false
  const x = e.clientX != null ? e.clientX : e.pageX
  const y = e.clientY != null ? e.clientY : e.pageY
  ctxStyle.value = { left: x + 'px', top: y + 'px' }
  nextTick(() => {
    const menu = document.getElementById('ctx-menu')
    if (!menu) return
    const mw = menu.offsetWidth || 200
    const mh = menu.offsetHeight || 320
    const gap = 8
    let left = x
    let top = y
    // 右侧空间不足 → 向左展开；左侧仍不足 → 贴左边界
    if (left + mw > window.innerWidth - gap) left = Math.max(gap, window.innerWidth - mw - gap)
    // 下方空间不足 → 向上展开（菜单底部在鼠标位置）；上方仍不足 → 贴顶
    if (top + mh > window.innerHeight - gap) top = Math.max(gap, y - mh)
    ctxStyle.value = { left: left + 'px', top: top + 'px' }
  })
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
    mind.value.execCommand('SET_NODE_ACTIVE', node, true)
    doCopyNode(node)
  } else if (act === 'cut') {
    mind.value.execCommand('SET_NODE_ACTIVE', node, true)
    doCutNode(node)
  } else if (act === 'paste') {
    doPasteNode(node)
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
  } else if (act === 'comments') {
    hideCtxMenu()
    openCmtPanel(node)
  } else if (act === 'collapse') {
    mind.value.execCommand('SET_NODE_EXPAND', node, false)
  } else if (act === 'expand') {
    mind.value.execCommand('SET_NODE_EXPAND', node, true)
  } else if (act === 'del') {
    mind.value.execCommand('SET_NODE_ACTIVE', node, true)
    doDeleteNode(node)
  }
  hideCtxMenu()
}

// 节点快捷键：选中节点（非编辑态）时 Ctrl+C/X/V、Delete 实现复制/剪切/粘贴/删除。
// 在 setup 顶层注册（先于 createMind 里库的 keyCommand 注册），capture 阶段先执行并 stopImmediate，
// 避免与库自带的 Ctrl+C/X/V、Del 删除快捷键冲突造成双删/双处理。
const onNodeShortcutKeydown = (e) => {
  if (textEditActive.value) return
  const k = e.key
  const mod = e.ctrlKey || e.metaKey
  if (mod && (k === 'c' || k === 'C')) {
    if (getActiveNode()) { e.preventDefault(); e.stopImmediatePropagation(); doCopyNode() }
  } else if (mod && (k === 'x' || k === 'X')) {
    if (getActiveNode()) { e.preventDefault(); e.stopImmediatePropagation(); doCutNode() }
  } else if (mod && (k === 'v' || k === 'V')) {
    if (nodeClipboard && getActiveNode()) { e.preventDefault(); e.stopImmediatePropagation(); doPasteNode() }
  } else if (k === 'Delete') {
    if (getActiveNode()) { e.preventDefault(); e.stopImmediatePropagation(); doDeleteNode() }
  }
}
document.addEventListener('keydown', onNodeShortcutKeydown, true)

// ---- 节点复制/剪切/粘贴/删除（内部剪贴板，菜单与快捷键共用） ----
let nodeClipboard = null
onBeforeUnmount(() => { document.removeEventListener('keydown', onNodeShortcutKeydown, true) })
let nodeClipIsCut = false
function stripUid(d) {
  if (!d) return
  delete d.uid; delete d.isActive
  if (d && d.data) { delete d.data.uid; delete d.data.isActive }
  if (d && Array.isArray(d.children)) d.children.forEach(stripUid)
}
function getActiveNode() {
  try {
    const list = mind.value ? mind.value.renderer.activeNodeList : []
    return list && list.length ? list[list.length - 1] : null
  } catch (e) { return null }
}
// 只复制节点数据（data + children，跳过内部 _ 字段与 uid/isActive），避免循环引用无法序列化
function cloneNodeData(d) {
  const src = d && d.nodeData ? d.nodeData : d
  if (!src) return { data: {}, children: [] }
  const data = {}
  for (const k in (src.data || {})) {
    if (!k.startsWith('_')) data[k] = src.data[k]
  }
  delete data.uid
  delete data.isActive
  let dataSafe
  try { dataSafe = JSON.parse(JSON.stringify(data)) } catch (e) { dataSafe = data }
  const children = (src.children || []).map(c => cloneNodeData(c))
  return { data: dataSafe, children }
}
function doCopyNode(node) {
  node = node || getActiveNode()
  if (!node) { toast('请先选中节点再复制'); return }
  nodeClipboard = cloneNodeData(node)
  nodeClipIsCut = false
  toast('已复制节点')
}
function doCutNode(node) {
  node = node || getActiveNode()
  if (!node) { toast('请先选中节点再剪切'); return }
  nodeClipboard = cloneNodeData(node)
  nodeClipIsCut = true
  try { mind.value.execCommand('SET_NODE_ACTIVE', node, true) } catch (e) {}
  try { mind.value.execCommand('REMOVE_NODE', [node]) } catch (e) { console.warn('cut remove err', e) }
  saveDoc()
  toast('已剪切节点')
}
function doPasteNode(target) {
  if (!nodeClipboard) { toast('剪贴板为空，请先复制或剪切节点'); return }
  target = target || getActiveNode()
  if (!target) { toast('请先选中目标节点再粘贴'); return }
  try {
    mind.value.execCommand('INSERT_CHILD_NODE', false, [target], nodeClipboard.data || nodeClipboard, nodeClipboard.children || [])
  } catch (err) {
    console.warn(err); toast('粘贴失败')
  }
  if (nodeClipIsCut) nodeClipboard = null
  saveDoc()
}
/** 删除权限：仅 该节点创建人 或 其双亲节点创建人 可删；根节点（无父）仅导图创建者可删 */
function canDeleteNode(node) {
  try {
    if (!node) return false
    const me = myUid()
    if (!me) return false
    if (!node.parent) return Number(mapOwnerId.value) === me
    const nodeCreator = Number(node.getData('creatorUid')) || 0
    const parentCreator = Number(node.parent.getData('creatorUid')) || 0
    return me === nodeCreator || me === parentCreator
  } catch (e) { return false }
}

/** 收集节点及其整棵子树的 uid（删除时记录，供保存合并区分删除 vs 对方新增） */
function collectSubtreeUids(node, set) {
  if (!node) return
  const nid = node.uid || (node.nodeData && node.nodeData.uid)
  if (nid) set.add(nid)
  ;(node.children || []).forEach(c => collectSubtreeUids(c, set))
}

function doDeleteNode(node) {
  node = node || getActiveNode()
  if (!node) { toast('请先选中节点再删除'); return }
  if (!canDeleteNode(node)) {
    toast('无权限删除该节点（仅该节点创建者或其双亲节点创建者可删除）', 'error')
    return
  }
  collectSubtreeUids(node, deletedUids)
  try { mind.value.execCommand('SET_NODE_ACTIVE', node, true) } catch (e) {}
  try { mind.value.execCommand('REMOVE_NODE', [node]) } catch (e) { console.warn('del remove err', e) }
  saveDoc()
}

// 附件预览
// 全屏浏览图片（lightbox）：双击图片节点进入，点 × 或遮罩或 Esc 返回脑图
function openImageLightbox(url, name) {
  if (!url) return
  let wrap = document.getElementById('img-lightbox')
  if (wrap) wrap.remove()
  wrap = document.createElement('div')
  wrap.id = 'img-lightbox'
  wrap.style.cssText = 'position:fixed;inset:0;z-index:2147483647;display:flex;align-items:center;justify-content:center;'
  wrap.innerHTML = '<div class="imglight-mask"></div><img class="imglight-img" alt=""><button class="imglight-close" title="关闭">×</button><div class="imglight-name"></div>'
  document.body.appendChild(wrap)
  const mask = wrap.querySelector('.imglight-mask')
  mask.style.cssText = 'position:absolute;inset:0;background:rgba(0,0,0,0.88)'
  const img = wrap.querySelector('.imglight-img')
  img.style.cssText = 'position:relative;max-width:94vw;max-height:92vh;object-fit:contain;border-radius:6px;box-shadow:0 12px 48px rgba(0,0,0,.5);user-select:none'
  const btn = wrap.querySelector('.imglight-close')
  btn.style.cssText = 'position:absolute;top:14px;right:14px;width:42px;height:42px;border-radius:50%;border:none;background:rgba(255,255,255,0.14);color:#fff;font-size:26px;line-height:1;cursor:pointer;z-index:2;transition:background .2s'
  btn.onmouseenter = () => { btn.style.background = 'rgba(255,255,255,0.3)' }
  btn.onmouseleave = () => { btn.style.background = 'rgba(255,255,255,0.14)' }
  const nmEl = wrap.querySelector('.imglight-name')
  nmEl.style.cssText = 'position:absolute;bottom:16px;left:50%;transform:translateX(-50%);color:#fff;font-size:14px;opacity:0.85;z-index:2;max-width:70vw;overflow:hidden;text-overflow:ellipsis;white-space:nowrap'
  nmEl.textContent = name || ''
  const freshUrl = url + (url.indexOf('?') > -1 ? '&' : '?') + 'v=' + Date.now()
  img.src = freshUrl
  const esc = (ev) => { if (ev.key === 'Escape') close() }
  function close() {
    document.removeEventListener('keydown', esc)
    if (wrap && wrap.parentNode) wrap.remove()
  }
  btn.addEventListener('click', close)
  mask.addEventListener('click', close)
  document.addEventListener('keydown', esc)
}

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
  // 附件文件可被编辑，URL 不变但内容会变；加时间戳参数绕过 /uploads 强缓存，保证各浏览器看到最新内容
  const freshUrl = url + (url.indexOf('?') > -1 ? '&' : '?') + 'v=' + Date.now()
  if (isImg) {
    const im = document.createElement('img')
    im.src = freshUrl; im.style.cssText = 'max-width:100%;max-height:62vh;border-radius:10px'
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
    // Word 在线预览（docx-preview CDN 按需加载）
    body.innerHTML = '<div class="attp-loading" style="padding:30px;text-align:center;color:#8a93a6">正在加载文档预览…</div>'
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
          body.innerHTML = '<div class="attp-dl">文档渲染失败，请下载查看</div><a class="attp-dl-btn" href="' + freshUrl + '" target="_blank" rel="noopener">下载文件</a>'
        })
      }).catch(() => {
        body.innerHTML = '<div class="attp-dl">文档读取失败，请下载查看</div><a class="attp-dl-btn" href="' + freshUrl + '" target="_blank" rel="noopener">下载文件</a>'
      })
    }).catch(() => {
      body.innerHTML = '<div class="attp-dl">预览组件加载失败，请下载查看</div><a class="attp-dl-btn" href="' + freshUrl + '" target="_blank" rel="noopener">下载文件</a>'
    })
  } else if (ext === 'xlsx' || ext === 'xls' || ext === 'csv') {
    // Excel 在线预览（SheetJS CDN 按需加载）
    body.innerHTML = '<div class="attp-loading" style="padding:30px;text-align:center;color:#8a93a6">正在加载表格预览…</div>'
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
          body.innerHTML = '<div class="attp-dl">表格解析失败，请下载查看</div><a class="attp-dl-btn" href="' + freshUrl + '" target="_blank" rel="noopener">下载文件</a>'
        }
      }).catch(() => {
        body.innerHTML = '<div class="attp-dl">表格读取失败，请下载查看</div><a class="attp-dl-btn" href="' + freshUrl + '" target="_blank" rel="noopener">下载文件</a>'
      })
    }).catch(() => {
      body.innerHTML = '<div class="attp-dl">预览组件加载失败，请下载查看</div><a class="attp-dl-btn" href="' + freshUrl + '" target="_blank" rel="noopener">下载文件</a>'
    })
  } else {
    // 代码/文本类：CodeMirror 高亮浏览 + 可编辑保存；二进制类仅下载
    // 类型映射来自后台配置（/api/preview-config），未拉到则用内置默认
    const CODE_MODE = Object.keys(previewModes.value).length ? previewModes.value : CODE_MODE_DEFAULT
    const TEXT_EXT = textPreviewExts.value.length ? textPreviewExts.value : TEXT_EXT_DEFAULT
    if (CODE_MODE[ext] || TEXT_EXT.includes(ext)) {
      fetch(freshUrl, { cache: 'no-store' }).then(r => r.text()).then(t => {
        // CodeMirror：浏览（高亮只读）→ 点击编辑可写 → 保存
        const cmWrap = document.createElement('div')
        cmWrap.style.cssText = 'border:1px solid #333;border-radius:10px;overflow:hidden;background:#1e1e1e'
        body.appendChild(cmWrap)
        const cm = CodeMirror(cmWrap, {
          value: t,
          mode: CODE_MODE[ext] || null,
          lineNumbers: true,
          lineWrapping: false,
          readOnly: 'nocursor',
          indentUnit: 2,
          tabSize: 2,
          scrollbarStyle: 'null'
        })
        cm.setSize('100%', '50vh')
        const bar = document.createElement('div')
        bar.style.cssText = 'text-align:right;margin-top:10px;display:flex;justify-content:flex-end;gap:8px'
        const editBtn = document.createElement('button')
        editBtn.textContent = '编辑'
        editBtn.className = 'attp-dl-btn'
        editBtn.style.cssText = 'padding:6px 18px;background:#0e7c86;color:#fff;border:0;border-radius:6px;cursor:pointer;font-size:13px'
        const cancelEdit = document.createElement('button')
        cancelEdit.textContent = '取消编辑'
        cancelEdit.style.cssText = 'padding:6px 18px;background:#555;color:#fff;border:0;border-radius:6px;cursor:pointer;font-size:13px;display:none'
        const saveBtn = document.createElement('button')
        saveBtn.textContent = '保存'
        saveBtn.style.cssText = 'padding:6px 18px;background:#0e7c86;color:#fff;border:0;border-radius:6px;cursor:pointer;font-size:13px;display:none'
        bar.appendChild(cancelEdit); bar.appendChild(saveBtn); bar.appendChild(editBtn)
        body.appendChild(bar)
        editBtn.addEventListener('click', () => {
          cm.setOption('readOnly', false)
          editBtn.style.display = 'none'
          cancelEdit.style.display = ''; saveBtn.style.display = ''
          cm.focus()
        })
        cancelEdit.addEventListener('click', () => {
          cm.setOption('readOnly', 'nocursor')
          editBtn.style.display = ''
          cancelEdit.style.display = 'none'; saveBtn.style.display = 'none'
        })
        saveBtn.addEventListener('click', async () => {
          saveBtn.disabled = true; saveBtn.textContent = '保存中...'
          try {
            const res = await fetch('/api/file/content', {
              method: 'PUT',
              headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + (localStorage.getItem('usmind.token') || '') },
              body: JSON.stringify({ url, content: cm.getValue() })
            })
            const j = await res.json()
            if (j.code === 200) { toast('已保存'); wrap.remove() } else { toast('保存失败: ' + (j.message || ''), 'error') }
          } catch (e) { toast('保存失败: ' + e.message, 'error') }
          saveBtn.disabled = false; saveBtn.textContent = '保存'
        })
      }).catch(() => {
        body.innerHTML = '<div class="attp-dl">无法读取内容，请下载查看</div><a class="attp-dl-btn" href="' + freshUrl + '" target="_blank" rel="noopener">下载文件</a>'
      })
    } else {
      // 二进制/程序安装包/office 等：弹窗内下载
      body.innerHTML = '<div class="attp-dl">该类型暂不支持在线预览，请下载后查看</div><a class="attp-dl-btn" href="' + freshUrl + '" target="_blank" rel="noopener">下载文件</a>'
    }
  }
}

// 渲染前：附件节点文件名默认色（后台 attachTextColor 可配），未手动设置过文字色的节点补默认色
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

// 渲染期修正：附件节点文字统一为文件名（纯文本），并触发尺寸重算，避免文字飘出框外
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

// 给有附件的节点注入可点击的附件图标（右上角），单击打开附件预览
function injectAttachIcons() {
  if (!mind.value || !mind.value.renderer) return
  const NS = 'http://www.w3.org/2000/svg'
  // 构建 uidMap 与 附件名->数据 映射（来自导图数据树）
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
  // 主路径：渲染器节点缓存（兼容 group / group.node 两种结构）
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
  // 兜底：DOM 遍历（不依赖渲染器缓存结构），按附件名匹配数据
  try {
    const svg = document.querySelector('.smm-container svg')
    if (!svg) return
    svg.querySelectorAll('title').forEach(t => {
      const name = (t.textContent || '').trim()
      if (!name || !nameMap[name]) return
      const gEl = t.closest('g.smm-node') || t.closest('g')
      if (!gEl || gEl.querySelector('.smm-att-icon')) return
      injectIntoGroup(gEl, nameMap[name])
    })
  } catch (e) {}
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

// 从 SVG 字符串解析画布宽高与原点偏移（支持任意 viewBox 格式 "x y W H"）
function parseSvgSize(svgStr) {
  const mv = svgStr.match(/viewBox="([^"]*)"/)
  if (mv) {
    const parts = mv[1].trim().split(/[\s,]+/).map(Number)
    if (parts.length >= 4 && isFinite(parts[2]) && isFinite(parts[3]) && parts[2] > 0 && parts[3] > 0) {
      return { W: parts[2], H: parts[3], X: isFinite(parts[0]) ? parts[0] : 0, Y: isFinite(parts[1]) ? parts[1] : 0 }
    }
  }
  const mw = svgStr.match(/width="([\d.]+)"/)
  const mh = svgStr.match(/height="([\d.]+)"/)
  return { W: mw ? parseFloat(mw[1]) : 0, H: mh ? parseFloat(mh[1]) : 0, X: 0, Y: 0 }
}
// 生成指定区域的块 SVG：保持全部节点，仅通过 viewBox/宽高裁剪视口（保留原点偏移 X/Y）
function makeChunkSvg(svgStr, W, ch, y0, X, Y) {
  const vx = X || 0
  const vy = (Y || 0) + y0
  return svgStr
    .replace(/width="[\d.]+"/, 'width="' + W + '"')
    .replace(/height="[\d.]+"/, 'height="' + ch + '"')
    .replace(/viewBox="[^"]*"/, 'viewBox="' + vx + ' ' + vy + ' ' + W + ' ' + ch + '"')
}
// 导出前处理：把 foreignObject 里的 MathJax 公式 SVG 提升到 SVG 原生层。
// Chrome 对含 foreignObject 的 SVG 绘制到 canvas 后会判定 canvas 被污染（Tainted canvases may not be exported），
// 导致分块导出的 toDataURL 失败。公式节点（richText）用 foreignObject 承载 HTML 公式，
// 这里把其中内联的 <svg> 提升为 <g> 下的嵌套 SVG，并唯一化 defs/use 的 id 引用，避免跨公式冲突。
function liftFormulaSvg(svgStr) {
  const NS = 'http://www.w3.org/2000/svg'
  const XLink = 'http://www.w3.org/1999/xlink'
  try {
    const doc = new DOMParser().parseFromString(svgStr, 'image/svg+xml')
    if (doc.querySelector('parsererror')) return svgStr
    const root = doc.documentElement
    const fos = Array.from(doc.querySelectorAll('foreignObject'))
    if (!fos.length) return svgStr
    fos.forEach((fo) => {
      const x = parseFloat(fo.getAttribute('x') || '0')
      const y = parseFloat(fo.getAttribute('y') || '0')
      const style = fo.getAttribute('style') || ''
      const svgs = Array.from(fo.querySelectorAll('svg'))
      if (!svgs.length) return
      // 累积祖先 transform 链（foreignObject 自身 x/y 常为 0，节点位置在祖先链上）
      const ancestors = []
      let el = fo.parentNode
      while (el && el.nodeName !== 'svg') {
        const at = el.getAttribute('transform')
        if (at) ancestors.push(at) // 内→外应用SVG变换，unshift会致坐标错位成黑条
        el = el.parentNode
      }
      // 把矩阵链应用到 (x, y)，得到公式在画布中的绝对坐标
      let px = x
      let py = y
      ancestors.forEach((t) => {
        const i = t.indexOf('matrix(')
        if (i >= 0) {
          const j = t.indexOf(')', i)
          const p = t.slice(i + 7, j).split(',').map(Number)
          const nx = p[0] * px + p[2] * py + p[4]
          const ny = p[1] * px + p[3] * py + p[5]
          px = nx
          py = ny
        }
      })
      // MathJax svg 的 width 是 ex 单位；SVG 图片模式下 ex 无字体上下文，
      // 按 foreignObject 的 font-size 换算：1ex ≈ 0.5em
      const fsMatch = style.match(/font-size:\s*([\d.]+)px/)
      const fontSize = fsMatch ? parseFloat(fsMatch[1]) : 16
      svgs.forEach((sv) => {
        // 嵌套 SVG 里 <use> 的 href 会解析到外层文档（找不到 defs 导致公式空白），
        // 因此 use 展开为 <path>，并移除 defs（同时消除跨公式 id 冲突）
        const uses = sv.querySelectorAll('use')
        uses.forEach((u) => {
          const ref = u.getAttributeNS(XLink, 'href') || u.getAttribute('href')
          let target = null
          if (ref && ref.charAt(0) === '#') target = doc.querySelector('[id="' + ref.slice(1) + '"]')
          if (target) {
            const p = doc.createElementNS(NS, 'path')
            const d = target.getAttribute('d')
            if (d) p.setAttribute('d', d)
            const t = u.getAttribute('transform')
            if (t) p.setAttribute('transform', t)
            u.parentNode.replaceChild(p, u)
          }
        })
        sv.querySelectorAll('defs').forEach((d) => d.remove())
        const wAttr = sv.getAttribute('width')
        const hAttr = sv.getAttribute('height')
        if (wAttr && wAttr.indexOf('ex') >= 0) sv.setAttribute('width', String(parseFloat(wAttr) * fontSize * 0.5)) // 无单位数字，SVG图片模式可解析
        if (hAttr && hAttr.indexOf('ex') >= 0) sv.setAttribute('height', String(parseFloat(hAttr) * fontSize * 0.5))
        // 关键：实测 SVG 图片模式中嵌套 <svg> 只要祖先链存在任何 transform
        // （matrix/translate/scale，含单位矩阵）均不渲染。因此把公式 svg 提升到
        // 根 svg 的直接子级（无 transform 祖先），用 x/y 绝对定位。
        sv.setAttribute('xmlns', 'http://www.w3.org/2000/svg')
        // 防止公式 svg 内容溢出盒子绘制成黑条：裁剪到盒子范围
        sv.setAttribute('overflow', 'hidden')
        sv.setAttribute('x', px)
        sv.setAttribute('y', py)
        root.appendChild(sv)
      })
      fo.remove()
    })
    return new XMLSerializer().serializeToString(doc)
  } catch (e) {
    console.warn('公式 SVG 提升失败，回退原样导出: ', e)
    return svgStr
  }
}
function loadImg(url) {
  const img = new Image()
  return new Promise((res, rej) => {
    img.onload = () => {
      // SVG 作为图片源时，onload 触发不代表内部节点/渐变已绘制完成，
      // 等两帧再绘制，避免 drawImage 拿到未渲染完成的图像
      requestAnimationFrame(() => requestAnimationFrame(() => res(img)))
    }
    img.onerror = rej
    img.src = url
  })
}

// ===== 用户方案：先导出完整 SVG（foreignObject 完整保留、质量高），
// 再提取为「SVG 层 + 绝对定位的 HTML 富文本层」混合 DOM，用 html2canvas 捕获成图片/PDF。
// 原理：SVG 作为 <img> 加载时 foreignObject 一律不渲染（安全限制），html2canvas 直接捕获
// 内联 SVG 时也会把 svg 序列化成图片（foreignObject 照样丢）；只有把 foreignObject 内容
// 提到 HTML 层（浏览器原生渲染 HTML + MathJax SVG），html2canvas 才能逐元素捕获。 =====

// 解析 SVG：收集所有 foreignObject（算绝对坐标 + 保留子节点），移除后返回纯 SVG 与 fo 项
function collectFoItems(svgStr) {
  const doc = new DOMParser().parseFromString(svgStr, 'image/svg+xml')
  if (doc.querySelector('parsererror')) return { svg: svgStr, items: [] }
  const root = doc.documentElement
  const fos = Array.from(doc.querySelectorAll('foreignObject'))
  const items = []
  fos.forEach((fo) => {
    const x = parseFloat(fo.getAttribute('x') || '0')
    const y = parseFloat(fo.getAttribute('y') || '0')
    // 累积祖先 transform 链（内→外应用）
    const ancestors = []
    let el = fo.parentNode
    while (el && el.nodeName !== 'svg') {
      const at = el.getAttribute('transform')
      if (at) ancestors.push(at)
      el = el.parentNode
    }
    let px = x
    let py = y
    ancestors.forEach((t) => {
      const i = t.indexOf('matrix(')
      if (i >= 0) {
        const j = t.indexOf(')', i)
        const pp = t.slice(i + 7, j).split(',').map(Number)
        const nx = pp[0] * px + pp[2] * py + pp[4]
        const ny = pp[1] * px + pp[3] * py + pp[5]
        px = nx
        py = ny
      }
    })
    items.push({ x: px, y: py, fo })
    fo.remove()
  })
  const svg = new XMLSerializer().serializeToString(doc)
  return { svg, items }
}

// 渲染一块：HTML 容器（纯 SVG 层 + fo 内容层）→ html2canvas → canvas
async function renderChunkByHtml(svgStr, W, ch, y0, X, Y, bg) {
  const { svg: svgNoFo, items } = collectFoItems(svgStr)
  const chunkSvg = makeChunkSvg(svgNoFo, W, ch, y0, X, Y)
  const wrap = document.createElement('div')
  wrap.style.cssText = 'position:fixed;left:-99999px;top:0;z-index:-1000;overflow:hidden;'
  wrap.style.width = W + 'px'
  wrap.style.height = ch + 'px'
  wrap.style.background = bg || '#ffffff'
  // SVG 层（连线/节点形状，无 foreignObject）
  const svgBox = document.createElement('div')
  svgBox.style.cssText = 'position:absolute;left:0;top:0;width:' + W + 'px;height:' + ch + 'px;'
  svgBox.innerHTML = chunkSvg
  wrap.appendChild(svgBox)
  // HTML 富文本层（原 foreignObject 内容，绝对定位到画布坐标）
  items.forEach((it) => {
    const left = it.x - (X || 0)
    const top = it.y - (Y || 0) - y0
    if (top + 20 < 0 || top > ch) return // 视口外跳过
    const holder = document.createElement('div')
    holder.style.cssText = 'position:absolute;left:' + left + 'px;top:' + top + 'px;'
    it.fo.childNodes.forEach((c) => holder.appendChild(c.cloneNode(true)))
    // 视频/音频：贴链接占位（html2canvas 无法稳定绘制媒体画面）
    holder.querySelectorAll('video, audio').forEach((m) => {
      const label = m.nodeName === 'VIDEO' ? '▶ 视频' : '♪ 音频'
      const src = m.getAttribute('src') || ''
      const nm = decodeURIComponent((src.split('/').pop() || '').replace(/\?.*$/, ''))
      const d = document.createElement('div')
      d.textContent = label + (nm ? '：' + nm : '')
      d.style.cssText = 'font-size:13px;color:#555;padding:2px 8px;border:1px dashed #bbb;border-radius:6px;background:#fafafa;'
      if (m.parentNode) m.parentNode.replaceChild(d, m)
    })
    // MathJax 公式 svg 的 ex 单位 → px（html2canvas 序列化 svg 为图片时无字体上下文，ex 无法解析）
    const fsMatch = (it.fo.getAttribute('style') || '').match(/font-size:\s*([\d.]+)px/)
    const fontSize = fsMatch ? parseFloat(fsMatch[1]) : 16
    holder.querySelectorAll('svg').forEach((sv) => {
      const w = sv.getAttribute('width')
      const h = sv.getAttribute('height')
      if (w && w.indexOf('ex') >= 0) sv.setAttribute('width', String(parseFloat(w) * fontSize * 0.5))
      if (h && h.indexOf('ex') >= 0) sv.setAttribute('height', String(parseFloat(h) * fontSize * 0.5))
    })
    wrap.appendChild(holder)
  })
  document.body.appendChild(wrap)
  try {
    const canvas = await html2canvas(wrap, {
      backgroundColor: bg || '#ffffff',
      scale: 1,
      useCORS: true,
      logging: false,
      width: W,
      height: ch,
      windowWidth: W,
      windowHeight: ch,
      imageTimeout: 20000
    })
    return canvas
  } finally {
    wrap.remove()
  }
}

// 分块导出图片（PNG/JPG/JPEG）：超大画布按块切割打包 zip；小图直接导出单张
// 不依赖全量大图 Image 解码（浏览器对大 SVG 有高度截断），改为逐块生成小 SVG 渲染
async function exportChunkedImages(type) {
  const name = docName.value || '思维导图'
  const { str: strRaw } = await mind.value.doExport.getSvgData()
  const { W, H, X, Y } = parseSvgSize(strRaw)
  if (!W || !H) { hideExportLoading(); toast('画布尺寸解析失败', 'error'); return }
  const bg = (mind.value.themeConfig && mind.value.themeConfig.backgroundColor) || '#ffffff'
  const CHUNK = 5000
  const chunks = Math.max(1, Math.ceil(H / CHUNK))
  const isJpg = type === 'jpg' || type === 'jpeg'
  const ext = isJpg ? 'jpg' : 'png'
  const parts = []
  for (let i = 0; i < chunks; i++) {
    const y0 = i * CHUNK
    const ch = Math.min(CHUNK, H - y0)
    updateExportLoading(i, chunks, '正在导出第 ' + (i + 1) + '/' + chunks + ' 块（' + ext.toUpperCase() + '）...')
    const canvas = await renderChunkByHtml(strRaw, W, ch, y0, X, Y, bg)
    parts.push(canvas.toDataURL(isJpg ? 'image/jpeg' : 'image/png', isJpg ? 0.92 : undefined))
  }
  if (chunks === 1) {
    updateExportLoading(1, 1, '正在保存图片...')
    downloadDataUrl(parts[0], name + '.' + ext)
    hideExportLoading()
    toast('导出完成')
  } else {
    updateExportLoading(chunks, chunks, '正在打包压缩包...')
    const zip = new JSZip()
    parts.forEach((d, i) => zip.file(name + '_' + (i + 1) + '.' + ext, d.split(',')[1], { base64: true }))
    const blob = await zip.generateAsync({ type: 'blob' })
    downloadBlob(blob, name + '_分块导出.zip')
    hideExportLoading()
    toast('导出完成，共 ' + chunks + ' 块（已打包 zip）')
  }
}

// 分页导出 PDF：超大画布自动分页，每页一块内容
async function exportChunkedPdf() {
  const name = docName.value || '思维导图'
  const { str: strRaw } = await mind.value.doExport.getSvgData()
  const { W, H, X, Y } = parseSvgSize(strRaw)
  if (!W || !H) { hideExportLoading(); toast('画布尺寸解析失败', 'error'); return }
  const bg = (mind.value.themeConfig && mind.value.themeConfig.backgroundColor) || '#ffffff'
  const CHUNK = 5000
  const chunks = Math.max(1, Math.ceil(H / CHUNK))
  const pdfDoc = await PDFDocument.create()
  for (let i = 0; i < chunks; i++) {
    const y0 = i * CHUNK
    const ch = Math.min(CHUNK, H - y0)
    updateExportLoading(i, chunks, '正在生成 PDF 第 ' + (i + 1) + '/' + chunks + ' 页...')
    const canvas = await renderChunkByHtml(strRaw, W, ch, y0, X, Y, bg)
    const b64 = canvas.toDataURL('image/png').split(',')[1]
    const bytes = Uint8Array.from(atob(b64), c => c.charCodeAt(0))
    const pngImage = await pdfDoc.embedPng(bytes)
    const page = pdfDoc.addPage([W, ch])
    page.drawImage(pngImage, { x: 0, y: 0, width: W, height: ch })
  }
  updateExportLoading(chunks, chunks, '正在保存 PDF 文件...')
  const pdfBytes = await pdfDoc.save()
  downloadBlob(new Blob([pdfBytes], { type: 'application/pdf' }), name + '.pdf')
  hideExportLoading()
  toast('导出完成，共 ' + chunks + ' 页')
}
async function runExport(type) {
  exportOpen.value = false
  if (!mind.value) { toast('请先进入画布', 'error'); return }
  if (exporting.value) { toast('正在导出中，请稍候...', 'warn'); return }
  if (!checkMemberFeature('export', type)) { toast('该导出格式为会员专属，开通会员后可用', 'error'); return }
  exporting.value = true
  showExportLoading(0, '')
  // 先让出主线程：Vue 响应式 DOM 更新 + 浏览器绘制遮罩后再开始耗时同步计算（getSvgData 序列化大 SVG 会阻塞主线程）
  await nextTick()
  await new Promise(r => setTimeout(r, 60))
  try {
    if (type === 'png' || type === 'jpg' || type === 'jpeg') {
      await exportChunkedImages(type)
    } else if (type === 'pdf') {
      await exportChunkedPdf()
    } else {
      updateExportLoading(0, 1, '正在导出 ' + type.toUpperCase() + ' 文件...')
      const name = docName.value || '思维导图'
      await mind.value.doExport.export(type, true, name)
      hideExportLoading()
      toast('导出完成')
    }
  } catch (e) {
    hideExportLoading()
    toast('导出失败: ' + e.message, 'error')
  } finally {
    exporting.value = false
  }
}
async function exportUsmind() {
  exportOpen.value = false
  if (!mind.value) { toast('请先进入画布', 'error'); return }
  if (!checkMemberFeature('export', 'usmind')) { toast('该导出格式为会员专属，开通会员后可用', 'error'); return }
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
const curVersion = ref(0)
const conflictDialog = ref(false)
// ---- 协作实时同步（WebSocket） ----
const collabToast = reactive({ show: false, text: '', version: 0 })
let ws = null
let wsReconnectTimer = null
let wsSubscribed = false

function connectWs() {
  if (ws) { try { ws.close() } catch (e) {} ws = null }
  if (!mindMapId.value) return
  const token = localStorage.getItem('usmind.token') || ''
  if (!token) return
  const proto = location.protocol === 'https:' ? 'wss://' : 'ws://'
  try {
    ws = new WebSocket(proto + location.host + '/ws?token=' + encodeURIComponent(token))
  } catch (e) { scheduleWsReconnect(); return }
  ws.onopen = () => {
    console.log('[ws] open, mindMapId=', mindMapId.value)
    if (mindMapId.value) {
      ws.send(JSON.stringify({ type: 'subscribe', mindMapId: String(mindMapId.value) }))
      wsSubscribed = true
    }
  }
  ws.onmessage = (ev) => {
    try {
      const msg = JSON.parse(ev.data)
      console.log('[ws] recv', msg.event || msg.type)
      if (msg.event === 'node_lock') {
        if (String(msg.mindMapId) !== String(mindMapId.value)) return
        applyRemoteLock(msg.nodeId, msg.action, Number(msg.userId), msg.userName || '')
        return
      }
      if (msg.event === 'node_change') {
        if (String(msg.mindMapId) !== String(mindMapId.value)) return
        if (Number(msg.userId) === myUid()) return
        applyRemoteNodeChange(msg.nodeId, msg.text, Number(msg.userId))
        return
      }
      if (msg.event === 'node_added') {
        if (String(msg.mindMapId) !== String(mindMapId.value)) return
        if (Number(msg.userId) === myUid()) return
        if (msg.nodeDataTooLarge) return
        applyRemoteNodeAdded(msg)
        return
      }
      if (msg.event === 'node_removed') {
        if (String(msg.mindMapId) !== String(mindMapId.value)) return
        if (Number(msg.userId) === myUid()) return
        applyRemoteNodeRemoved(msg.nodeId)
        return
      }
      if (msg.event === 'node_moved') {
        if (String(msg.mindMapId) !== String(mindMapId.value)) return
        if (Number(msg.userId) === myUid()) return
        applyRemoteNodeMoved(msg.nodeId, msg.parentId)
        return
      }
      if (msg.event === 'org_disbanded') {
        orgDisbandedName.value = msg.orgName || ''
        orgDisbanded.value = true
        try { if (ws) ws.close() } catch (e) {}
        ws = null
        return
      }
      if (msg.event !== 'mindmap_updated') return
      if (String(msg.mindMapId) !== String(mindMapId.value)) return
      let myUidVal = 0
      try { myUidVal = Number((JSON.parse(localStorage.getItem('usmind.user') || '{}')).id) || 0 } catch (e) { myUidVal = 0 }
      if (myUidVal && Number(msg.updatedBy) === myUidVal) return
      const v = Number(msg.version)
      if (!v || v <= curVersion.value) return
      // 静默 diff 局部应用：不弹提示、不全量刷新、保留视图位置（自己正在编辑的节点被改则弹增强冲突框）
      diffApplyLatest()
    } catch (e) { console.warn('ws msg err', e) }
  }
  ws.onclose = () => { ws = null; wsSubscribed = false; scheduleWsReconnect() }
  ws.onerror = () => { try { ws.close() } catch (e) {} }
}

function scheduleWsReconnect() {
  if (wsReconnectTimer) clearTimeout(wsReconnectTimer)
  if (!mindMapId.value) return
  wsReconnectTimer = setTimeout(() => { if (mindMapId.value) connectWs() }, 5000)
}

function applyCollabUpdate() {
  collabToast.show = false
  loadLatest()
}

function disconnectWs() {
  if (wsReconnectTimer) { clearTimeout(wsReconnectTimer); wsReconnectTimer = null }
  releaseNodeLock(true)
  if (ws) {
    try {
      if (mindMapId.value) ws.send(JSON.stringify({ type: 'unsubscribe', mindMapId: String(mindMapId.value) }))
    } catch (e) {}
    try { ws.close() } catch (e) {}
    ws = null
  }
  wsSubscribed = false
}

// ============ 节点级协作：锁定 + 实时编辑推送 ============
const nodeLocks = reactive({}) // nodeId -> { userId, userName }
let myEditNodeId = null
const orgDisbanded = ref(false)   // 机构已销毁提示弹窗
const orgDisbandedName = ref('')  // 机构名称
const editConflict = ref(null)     // 编辑中节点被他人修改的增强冲突框
const saveConflictDialog = ref(null) // 保存冲突 diff 弹窗 { conflicts, serverVersion }
const mapOwnerId = ref(0)          // 导图创建者 uid
const deletedUids = new Set()      // 本次会话删除的节点 uid（含子树），保存时提交供并发合并区分「我方删除」与「对方新增」
let myLockTimer = null
let nodeChangeTimer = null
let wsApplying = false

function myUid() {
  try { return Number((JSON.parse(localStorage.getItem('usmind.user') || '{}')).id) || 0 } catch (e) { return 0 }
}

function sendWs(obj) {
  if (ws && ws.readyState === 1) { try { ws.send(JSON.stringify(obj)) } catch (e) {} }
}

function goHomeAfterDisband() {
  try { if (ws) ws.close() } catch (e) {}
  ws = null
  orgDisbanded.value = false
  window.location.href = '/maps'
}
function acquireNodeLock(nid) {
  if (!nid) return
  myEditNodeId = nid
  sendWs({ type: 'lock', mindMapId: String(mindMapId.value), nodeId: nid, action: 'acquire' })
  if (myLockTimer) clearInterval(myLockTimer)
  myLockTimer = setInterval(() => {
    if (myEditNodeId) sendWs({ type: 'lock', mindMapId: String(mindMapId.value), nodeId: myEditNodeId, action: 'acquire' })
  }, 20000)
}

function releaseNodeLock(silent) {
  if (myEditNodeId && ws && ws.readyState === 1) {
    sendWs({ type: 'lock', mindMapId: String(mindMapId.value), nodeId: myEditNodeId, action: 'release' })
  }
  myEditNodeId = null
  if (myLockTimer) { clearInterval(myLockTimer); myLockTimer = null }
  if (!silent && nodeChangeTimer) { clearTimeout(nodeChangeTimer); nodeChangeTimer = null }
}

function pushNodeChange(node, text) {
  if (!ws || ws.readyState !== 1) return
  const nid = node && (node.uid || (node.nodeData && node.nodeData.uid))
  if (!nid) return
  try { if (node.nodeData && !node.nodeData.creatorUid) node.nodeData.creatorUid = myUid() } catch (e) {}
  if (nodeChangeTimer) clearTimeout(nodeChangeTimer)
  nodeChangeTimer = setTimeout(() => {
    sendWs({ type: 'node_change', mindMapId: String(mindMapId.value), nodeId: nid, text: (text == null ? '' : text).slice(0, 8000), nodeCreatorUid: Number((node.nodeData && node.nodeData.creatorUid) || 0) })
  }, 250)
}

function applyRemoteNodeChange(nid, text, fromUid) {
  if (myEditNodeId === nid) return
  try {
    const node = mind.value.renderer.findNodeByUid(nid)
    if (!node) return
    wsApplying = true
    mind.value.renderer.setNodeText(node, text == null ? '' : text)
    wsApplying = false
    const lk = nodeLocks[nid]
    if (lk) setLockMark(nid, true, lk.userName)
  } catch (e) { wsApplying = false; console.warn('applyRemoteNodeChange err', e) }
}

// ============ 结构变更 WS 增量：快照 diff 广播 + 对端局部应用 ============
let structSnap = {} // uid -> { p: parentUid|'', i: index }
function snapshotStruct() {
  const snap = {}
  const rn = mind.value && mind.value.renderer && mind.value.renderer.root
  if (!rn) return snap
  const walk = (n) => {
    if (!n) return
    const kids = n.children || []
    kids.forEach((c, i) => {
      const cu = c.uid || (c.nodeData && c.nodeData.uid)
      if (cu) snap[cu] = { p: (n.uid || (n.nodeData && n.nodeData.uid)) || '', i }
      walk(c)
    })
  }
  walk(rn)
  return snap
}
function toDataStruct(n) {
  if (!n) return null
  return { data: n.nodeData || {}, children: (n.children || []).map(toDataStruct) }
}
function collectDeletedDesc(uid, old) {
  try {
    deletedUids.add(uid)
    for (const u2 in old) if (old[u2].p === uid) collectDeletedDesc(u2, old)
  } catch (e) {}
}
function broadcastStructDelta() {
  try {
    if (!ws || ws.readyState !== 1 || !mind.value || !mind.value.renderer) return
    const ns = snapshotStruct()
    const old = structSnap || {}
    const mm = String(mindMapId.value)
    // 删除：广播 + 收集被删子树 uid（含后代）到 deletedUids，防止并发合并时被对方旧版本"复活"
    for (const uid in old) if (!ns[uid]) {
      sendWs({ type: 'node_removed', mindMapId: mm, nodeId: uid })
      collectDeletedDesc(uid, old)
    }
    // 新增
    for (const uid in ns) if (!old[uid]) {
      const node = mind.value.renderer.findNodeByUid(uid)
      if (!node) continue
      sendWs({ type: 'node_added', mindMapId: mm, nodeId: uid, parentId: ns[uid].p || '', index: ns[uid].i, nodeData: node.nodeData || {}, children: (node.children || []).map(toDataStruct) })
    }
    // 移动（父节点或同级索引变化）
    for (const uid in old) if (ns[uid] && (ns[uid].p !== old[uid].p || ns[uid].i !== old[uid].i)) {
      sendWs({ type: 'node_moved', mindMapId: mm, nodeId: uid, parentId: ns[uid].p || '', index: ns[uid].i })
    }
    structSnap = ns
  } catch (e) { console.warn('broadcastStructDelta err', e) }
}
// 远端结构变更会触发库的 hideEditTextBox：先把正在编辑的内容 flush 到节点并立即推送，防止输入内容被吞
function flushMyEdit() {
  try {
    if (!myEditNodeId) return
    const box = document.querySelector('.smm-node-edit-wrap, .smm-text-edit, [contenteditable=true]')
    const node = mind.value.renderer.findNodeByUid(myEditNodeId)
    if (!box || !node) return
    const t = (box.textContent || '')
    if (node.nodeData.text !== t) {
      node.nodeData.text = t
      delete node.nodeData.richText
      try { mind.value.renderer.renderNode(node) } catch (e) {}
    }
    // 立即推送（不走 250ms 防抖，避免末尾编辑内容丢失）
    sendWs({ type: 'node_change', mindMapId: String(mindMapId.value), nodeId: myEditNodeId, text: t.slice(0, 8000), nodeCreatorUid: Number((node.nodeData && node.nodeData.creatorUid) || 0) })
  } catch (e) {}
}
function applyRemoteNodeAdded(msg) {
  try {
    flushMyEdit()
    if (myEditNodeId === msg.nodeId) return
    if (mind.value.renderer.findNodeByUid(msg.nodeId)) return
    const parentNode = msg.parentId ? mind.value.renderer.findNodeByUid(msg.parentId) : null
    if (!parentNode) return // 父节点尚未同步：交给保存广播全量兜底
    const nd = msg.nodeData || {}
    if (!nd.uid) nd.uid = msg.nodeId
    if (!nd.creatorUid) nd.creatorUid = Number(msg.userId) || 0
    wsApplying = true
    mapEl.value.classList.add('no-anim')
    mind.value.renderer.insertChildNode(false, [parentNode], nd, msg.children || [])
    mapEl.value.classList.remove('no-anim')
    wsApplying = false
    suppressDirtyUntil = Date.now() + 1500
    try { canvasTabs.value[docIndex.value].data = mind.value.getData() } catch (e) {}
  } catch (e) { wsApplying = false; try { mapEl.value.classList.remove('no-anim') } catch (e2) {} }
}
function applyRemoteNodeRemoved(nid) {
  try {
    flushMyEdit()
    if (myEditNodeId === nid) return
    const node = mind.value.renderer.findNodeByUid(nid)
    if (!node || !node.parent) return
    wsApplying = true
    mapEl.value.classList.add('no-anim')
    mind.value.renderer.removeNode(node)
    mapEl.value.classList.remove('no-anim')
    wsApplying = false
    suppressDirtyUntil = Date.now() + 1500
    try { canvasTabs.value[docIndex.value].data = mind.value.getData() } catch (e) {}
  } catch (e) { wsApplying = false; try { mapEl.value.classList.remove('no-anim') } catch (e2) {} }
}
function applyRemoteNodeMoved(nid, parentId) {
  try {
    flushMyEdit()
    if (myEditNodeId === nid) return
    const node = mind.value.renderer.findNodeByUid(nid)
    const parent = parentId ? mind.value.renderer.findNodeByUid(parentId) : null
    if (!node || !parent || node.isRoot || node.parent === parent) return
    wsApplying = true
    mapEl.value.classList.add('no-anim')
    // 从原父节点摘除
    const oldParent = node.parent
    if (oldParent && oldParent.children) oldParent.children = oldParent.children.filter(c => c !== node)
    // 挂到目标父节点（追加末尾；同级精确顺序由保存广播校准）
    if (!parent.nodeData.children) parent.nodeData.children = []
    const exists = parent.nodeData.children.some(c => (c.uid || (c.nodeData && c.nodeData.uid)) === nid)
    if (!exists) parent.nodeData.children.push(node)
    try { mind.value.reRender() } catch (e) { try { mind.value.renderer.renderNode(parent) } catch (e2) {} }
    mapEl.value.classList.remove('no-anim')
    wsApplying = false
    suppressDirtyUntil = Date.now() + 1500
    try { canvasTabs.value[docIndex.value].data = mind.value.getData() } catch (e) {}
  } catch (e) { wsApplying = false; try { mapEl.value.classList.remove('no-anim') } catch (e2) {} }
}

function applyRemoteLock(nid, action, userId, userName) {
  if (!nid) return
  if (action === 'acquire') {
    if (nodeLocks[nid] && nodeLocks[nid].userId === userId) return
    nodeLocks[nid] = { userId, userName }
    if (userId !== myUid()) setLockMark(nid, true, userName)
  } else if (action === 'denied') {
    // 锁已被他人持有：提示只读，停止发送 node_change（避免被后端静默丢弃造成"编辑了但不同步"）
    if (myEditNodeId === nid) {
      if (nodeChangeTimer) { clearTimeout(nodeChangeTimer); nodeChangeTimer = null }
      if (userId && Number(userId) !== myUid()) toast('该节点正被 ' + (userName || '其他成员') + ' 编辑，当前为只读', 'warning')
      releaseNodeLock(true)
      try { mind.value.renderer.textEdit.hideEditTextBox() } catch (e) {}
    }
  } else if (action === 'release') {
    const cur = nodeLocks[nid]
    if (!cur || (userId && cur.userId !== userId)) return
    delete nodeLocks[nid]
    setLockMark(nid, false)
  }
}

// 自定义节点锁角标（SVG 覆盖层，不依赖库的 cooperate 插件）
function setLockMark(nid, show, userName) {
  try {
    const node = mind.value.renderer.findNodeByUid(nid)
    if (!node || !node.group || !node.group.node) return
    const g = node.group.node
    let badge = g.querySelector('.collab-lock-badge')
    if (show) {
      if (!badge) {
        badge = document.createElementNS('http://www.w3.org/2000/svg', 'g')
        badge.setAttribute('class', 'collab-lock-badge')
        g.appendChild(badge)
      }
      let bb = { width: 0, height: 0 }
      try { bb = g.getBBox() } catch (e) {}
      const w = Math.max(64, String(userName || '').length * 14 + 34)
      const h = 22
      const x = Math.max(2, bb.width - w - 2)
      const y = -h - 2
      badge.setAttribute('transform', '')
      badge.innerHTML = ''
      const rect = document.createElementNS('http://www.w3.org/2000/svg', 'rect')
      rect.setAttribute('x', x); rect.setAttribute('y', y)
      rect.setAttribute('width', w); rect.setAttribute('height', h)
      rect.setAttribute('rx', 11); rect.setAttribute('fill', '#e67e22'); rect.setAttribute('opacity', '0.94')
      badge.appendChild(rect)
      const txt = document.createElementNS('http://www.w3.org/2000/svg', 'text')
      txt.setAttribute('x', x + w / 2); txt.setAttribute('y', y + 15)
      txt.setAttribute('text-anchor', 'middle'); txt.setAttribute('font-size', '12')
      txt.setAttribute('fill', '#fff')
      badge.textContent = '🔒 ' + (userName || '协作中')
      badge.appendChild(txt)
    } else if (badge) {
      badge.remove()
    }
  } catch (e) { console.warn('setLockMark err', e) }
}

// 自动保存配置
const autoSaveEnabled = ref(true)
const autoSaveInterval = ref(10)
const autoSaveOpen = ref(false)
try {
  const as = JSON.parse(localStorage.getItem('usmind.autosave') || '{}')
  autoSaveEnabled.value = as.enabled !== false
  autoSaveInterval.value = Math.max(3, Math.min(600, as.interval || 10))
} catch (e) {}
function saveAutoSaveCfg() {
  localStorage.setItem('usmind.autosave', JSON.stringify({ enabled: autoSaveEnabled.value, interval: autoSaveInterval.value }))
}
watch(autoSaveEnabled, (v) => { saveAutoSaveCfg(); if (!v && autoSaveTimer) { clearTimeout(autoSaveTimer); autoSaveTimer = null } })
watch(autoSaveInterval, (v) => { if (v < 3) autoSaveInterval.value = 3; if (v > 600) autoSaveInterval.value = 600; saveAutoSaveCfg() })
function onAutoSaveIntervalChange() {
  if (dirty.value) scheduleAutoSave()
}
const shareDialog = ref(false)
const shareLink = ref('')
const sharePwd = ref('')
const shareReady = ref(false)
const sharePassword = ref('')
const shareHasPwd = ref(false)
function onShare() {
  if (!mindMapId.value) return
  sharePwd.value = ''
  shareReady.value = false
  sharePassword.value = ''
  shareHasPwd.value = false
  shareDialog.value = true
  // 已分享：直接复用原链接与密码，不再生成新分享
  mindmapApi.shareInfo(mindMapId.value).then(d => {
    if (d.isShared && d.shareUrl) {
      shareLink.value = location.origin + d.shareUrl
      sharePassword.value = d.sharePassword || ''
      shareHasPwd.value = !!d.hasPassword
      shareReady.value = true
    }
  }).catch(() => { shareDialog.value = false; toast('查询分享状态失败', 'error') })
}
async function confirmShare() {
  if (!mindMapId.value) return
  try {
    const data = await mindmapApi.share(mindMapId.value, sharePwd.value)
    shareLink.value = location.origin + '/share/' + data.shareToken
    sharePassword.value = data.sharePassword || ''
    shareHasPwd.value = !!data.hasPassword
    shareReady.value = true
  } catch (e) { toast('分享失败: ' + e.message, 'error') }
}
function copyShare(type) {
  try {
    const els = document.querySelectorAll('.share-link-input')
    const el = type === 'pwd' ? els[1] : els[0]
    if (el) { el.select(); document.execCommand('copy'); toast(type === 'pwd' ? '密码已复制' : '链接已复制') }
  } catch (e) {}
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

let memberFeature = { memberThemeIds: '', memberExportFormats: '', memberLevel: 0, enabled: true }
function memberIdsArr() {
  if (!memberFeature.memberThemeIds) return []
  return String(memberFeature.memberThemeIds).split(',').map(x => x.trim()).filter(Boolean)
}
function isMemberTheme(t) {
  if (!memberFeature.enabled || !memberFeature.memberThemeIds) return false
  const ids = memberIdsArr()
  // theme object has no id; backend config matches theme name or key
  const name = t && t.name != null ? String(t.name) : ""
  const key = t && t.key != null ? String(t.key) : ""
  return ids.includes(name) || ids.includes(key)
}
function isMemberExport(fmt) {
  if (!memberFeature.enabled || !memberFeature.memberExportFormats) return false
  const fmts = String(memberFeature.memberExportFormats).split(',').map(x => x.trim())
  const v = fmt === 'md' ? 'markdown' : fmt
  return fmts.includes(v)
}
function checkMemberFeature(kind, value) {
  const cfg = memberFeature
  if (!cfg.enabled) return true // 会员功能总开关关闭：全部开放
  if (kind === 'theme') {
    if (!cfg.memberThemeIds) return true
    const ids = String(cfg.memberThemeIds).split(',').map(x => x.trim())
    // 主题名 -> 主题ID 映射（后台配置为 ID 列表）
    const item = themes.value.find(t => t.name === value)
    const tid = item && (item.key != null ? String(item.key) : (item.name != null ? String(item.name) : ''))
    if (!ids.includes(tid) && !ids.includes(value)) return true
  } else if (kind === 'export') {
    if (!cfg.memberExportFormats) return true
    const fmts = String(cfg.memberExportFormats).split(',').map(x => x.trim())
    const v = value === 'md' ? 'markdown' : value
    if (!fmts.includes(v)) return true
  }
  return cfg.memberLevel > 0
}
async function loadMemberFeature() {
  try {
    const c = await configApi.client()
    const f = c.feature || {}
    memberFeature.enabled = c.memberEnabled !== false
    memberFeature.memberThemeIds = f.memberThemeIds || ''
    memberFeature.memberExportFormats = f.memberExportFormats || ''
    try {
      const m = await memberApi.info()
      memberFeature.memberLevel = m && m.level ? m.level : 0
    } catch (e) { memberFeature.memberLevel = 0 }
  } catch (e) { /* 忽略 */ }
}


// ===== 节点评论（作者端：轮询未读 + 闪烁 + 查看/回复 + 已读）=====
const cmtPanel = reactive({ open:false, nodeId:'', nodeName:'', list:[], input:'', replyTo:null, cmtErr:'' })
const unreadNodes = reactive({})
let cmtAfterId = 0
let cmtTimer = null
let cmtLoading = false
let cmtWs = null          // 评论实时推送通道（/ws/comment）
let cmtWsTimer = null     // 评论通道重连定时器
let cmtSubscribed = false

function nodeElOf(uid) {
  if (!mind.value || !mind.value.renderer) return null
  const n = (mind.value.renderer.nodeCache || {})[uid]
  return (n && n.group) ? (n.group.node || n.group) : null
}
/** 评论特效落点：目标节点可见则用目标；否则沿父链向上找最近可见节点（收起时展示到未收起的部分）。
 * 注意：折叠后收起节点会从 nodeCache 与渲染树移除，此时从 getData() 数据树（始终保留全部节点）
 * 找到目标→根的父链 uid 路径，再沿路径向下找第一个在 nodeCache 中可见的祖先作为落点。 */
function cmtAnchorOf(uid) {
  try {
    const cache = mind.value && mind.value.renderer ? (mind.value.renderer.nodeCache || {}) : {}
    const vis = (c) => {
      if (!c || !c.group) return null
      const g = c.group.node || c.group
      try {
        const rc = g.getBoundingClientRect()
        const win = g.ownerDocument && g.ownerDocument.defaultView ? g.ownerDocument.defaultView : null
        const cs = win ? win.getComputedStyle(g) : null
        if (rc.width > 1 && rc.height > 1 && (!cs || (cs.display !== 'none' && cs.visibility !== 'hidden'))) return g
      } catch (e) {}
      return null
    }
    // 1) cache 直接命中（目标可见）
    let t = cache[uid] || null
    if (t) {
      const g = vis(t)
      if (g) return { node: t, el: g }
    }
    // 2) 未命中/不可见：数据树找父链路径（目标→根），再沿路径找最近可见祖先。
    // 注意：mind.getData() 返回 { data: 根节点, children: 根的直接子节点, theme, layout... }，
    // 根节点的 children 在顶层，其余节点 children 在各自节点对象内。
    try {
      const data = mind.value && mind.value.getData ? mind.value.getData() : null
      const rootNode = data && data.data ? data.data : null
      if (rootNode) {
        if (!rootNode.children && data.children) rootNode.children = data.children
        const find = (n, path) => {
          if (!n) return null
          const nid = (n.data || {}).uid
          if (nid === uid) return path.concat(nid)
          for (const c of (n.children || [])) { const f = find(c, path.concat(nid)); if (f) return f }
          return null
        }
        const path = find(rootNode, [])
        if (path && path.length) {
          for (let i = path.length - 1; i >= 0; i--) {
            const c = cache[path[i]]
            const g = vis(c)
            if (g) return { node: c, el: g }
          }
        }
      }
    } catch (e) {}
    // 3) 兜底：沿 node.parent 链
    let guard = 0
    while (t && guard < 30) {
      const g = vis(t)
      if (g) return { node: t, el: g }
      t = t.parent
      guard++
    }
  } catch (e) {}
  return null
}
function applyCommentFlash() {
  if (!mind.value || !mind.value.renderer) return
  try {
    // 清空旧的动画类与流动电子线（clone 出来的纯视觉元素）
    document.querySelectorAll('.cmt-flash, .cmt-breath-path, .cmt-breath-line, .cmt-flow-line').forEach(el => {
      if (el.classList.contains('cmt-flow-line')) { try { el.remove() } catch (e) {} return }
      el.classList.remove('cmt-flash', 'cmt-breath-path', 'cmt-breath-line')
    })
    // 清空徽标层
    const layer = document.getElementById('cmt-badge-layer')
    if (layer) layer.innerHTML = ''
    const nodeCache = mind.value.renderer.nodeCache || {}
    const mapRect = mapEl.value ? mapEl.value.getBoundingClientRect() : null
    // 按锚点分组：同一落点（如收起时多个未读节点落到同一可见祖先）合并呼吸灯/路径/徽标，避免重叠
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
      const n = g0.node
      const gEl = g0.el
      const cnt = g0.cnt
      // 1) 落点节点呼吸灯（节点收起时落在最近可见祖先，即"未收起的部分"）
      const rect = gEl.querySelector('rect')
      if (rect) rect.classList.add('cmt-flash')
      // 2) 路径呼吸灯：根 → 落点的所有祖先节点矩形 + 每层"通往落点"的那条连线
      let p = n.parent
      let child = n
      let guard = 0
      while (p && guard < 30) {
        const pg = p.group ? (p.group.node || p.group) : null
        if (pg) {
          const pr = pg.querySelector('rect')
          if (pr) pr.classList.add('cmt-breath-path')
        }
        // 精确连线：child 在 p.children 中的下标 → 对应 p._lines 中那条线
        if (p._lines && p._lines.length && p.children && p.children.length) {
          const idx = p.children.indexOf(child)
          if (idx >= 0 && idx < p._lines.length) {
            const l = p._lines[idx]
            try {
              const el = l && l.node ? l.node : l
              if (el && el.classList) el.classList.add('cmt-breath-line')
              // 流动电子线：clone 原连线（同组，自动跟随画布缩放/平移），加流动动画类
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
        child = p
        p = p.parent
        guard++
      }
      // 3) 右上角未读数徽标（HTML 覆盖层，固定屏幕尺寸，不随画布缩放；动态元素用内联样式避开 scoped CSS）
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
          st.minWidth = '18px'
          st.height = '18px'
          st.lineHeight = '18px'
          st.textAlign = 'center'
          st.fontSize = '11px'
          st.fontWeight = '700'
          st.color = '#fff'
          st.background = 'linear-gradient(135deg, #ff6b6b, #e53935)'
          st.borderRadius = '9px'
          st.padding = '0 5px'
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
  // 渲染布局稳定前 getBoundingClientRect 可能未就绪：延迟补偿重建 + 徽标重定位
  setTimeout(() => { try { applyCommentFlash(); repositionCommentBadges() } catch (e) {} }, 200)
  setTimeout(() => { try { applyCommentFlash(); repositionCommentBadges() } catch (e) {} }, 500)
}
/** 轻量徽标重定位：平移/缩放画布时仅更新未读徽标位置（与 applyCommentFlash 创建顺序一致），不重建、不打断呼吸灯动画 */
function repositionCommentBadges() {
  try {
    const layer = document.getElementById('cmt-badge-layer')
    if (!layer || !layer.children.length) return
    const mapRect = mapEl.value ? mapEl.value.getBoundingClientRect() : null
    if (!mapRect) return
    const nodeCache = mind.value.renderer ? (mind.value.renderer.nodeCache || {}) : {}
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
async function refreshCmtList() {
  if (!mindMapId.value || !cmtPanel.open) return
  try {
    const r = await commentApi.listByMindMap(mindMapId.value, 0)
    cmtPanel.list = (Array.isArray(r.comments) ? r.comments : []).filter(c => c.nodeId === cmtPanel.nodeId)
  } catch (e) { cmtPanel.cmtErr = e.message || '加载评论失败' }
}
async function openCmtPanel(node) {
  const uid = node && node.getData ? node.getData('uid') : null
  if (!uid) return
  cmtPanel.nodeId = uid
  cmtPanel.nodeName = String(node.getData('text') || node.getData('attachmentName') || '').slice(0, 24) || '节点'
  cmtPanel.open = true
  cmtPanel.replyTo = null
  cmtPanel.input = ''
  cmtPanel.cmtErr = ''
  await refreshCmtList()
}
function closeCmtPanel() { cmtPanel.open = false; cmtPanel.replyTo = null; cmtPanel.input = '' }
async function onNodeClickComment(node) {
  if (!node || !node.getData) return
  const uid = node.getData('uid')
  if (!uid) return
  if (unreadNodes[uid]) {
    delete unreadNodes[uid]
    applyCommentFlash()
    try { await commentApi.markRead(mindMapId.value, uid) } catch (e) {}
    openCmtPanel(node)
  }
}
function setCmtReply(c) {
  cmtPanel.replyTo = (cmtPanel.replyTo && cmtPanel.replyTo.id === c.id) ? null : c
  cmtPanel.input = ''
}
async function submitComment() {
  const content = (cmtPanel.input || '').trim()
  if (!content) return
  if (content.length > 128) { cmtPanel.cmtErr = '最多 128 字'; return }
  const target = cmtPanel.replyTo
  cmtPanel.cmtErr = ''
  try {
    let c
    if (target) {
      c = await commentApi.reply(mindMapId.value, target.id, content)
    } else {
      c = await commentApi.create(mindMapId.value, { nodeId: cmtPanel.nodeId, content })
    }
    if (c && c.id) {
      cmtPanel.list.push(c)
      // 组织内评论也计入未读统计（自己发的除外，后端已按 userId 排除）
    }
    cmtPanel.input = ''
    cmtPanel.replyTo = null
  } catch (e) { cmtPanel.cmtErr = e.message || '发送失败（可能评论过于频繁）' }
}
function connectCmtWs() {
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
  let me = {}
  try { me = JSON.parse(localStorage.getItem('usmind.user') || '{}') } catch (e) {}
  // 自己发的评论不提醒自己（仅打开面板时刷新列表）
  if (Number(c.isAuthor) === 1 && me.id != null && c.userId != null && Number(c.userId) === Number(me.id)) {
    if (cmtPanel.open && c.nodeId === cmtPanel.nodeId) refreshCmtList()
    return
  }
  // 定向提醒：后端已算好 targetUserIds（数组存在即权威——空数组=无人需提醒，不闪；仅缺失=旧广播，回退全闪）
  const targets = c.targetUserIds
  if (Array.isArray(targets)) {
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
const cmtSoundOn = ref(localStorage.getItem('usmind.cmtSound') !== '0')
function toggleCmtSound() {
  cmtSoundOn.value = !cmtSoundOn.value
  localStorage.setItem('usmind.cmtSound', cmtSoundOn.value ? '1' : '0')
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

function startCommentPoll() {
  if (!mindMapId.value || cmtTimer) return
  cmtTimer = setInterval(async () => {
    if (cmtLoading) return
    cmtLoading = true
    try {
      const r = await commentApi.listByMindMap(mindMapId.value, cmtAfterId)
      const news = Array.isArray(r.comments) ? r.comments : []
      if (news.length) news.forEach(c => { if (c.id > cmtAfterId) cmtAfterId = c.id })
      if (cmtPanel.open && news.some(c => c.nodeId === cmtPanel.nodeId)) refreshCmtList()
      if (r.unread && typeof r.unread === 'object') {
        Object.keys(r.unread).forEach(k => { if (!r.unread[k]) delete unreadNodes[k]; else unreadNodes[k] = r.unread[k] })
        syncCommentFlash()
      }
    } catch (e) {} finally { cmtLoading = false }
  }, 5000)
}

function fmtCmtTime(s) {
  if (!s) return ''
  const d = new Date(s)
  if (isNaN(d.getTime())) return String(s).slice(0, 16).replace('T', ' ')
  const p = n => (n < 10 ? '0' + n : '' + n)
  return d.getFullYear() + '-' + p(d.getMonth() + 1) + '-' + p(d.getDate()) + ' ' + p(d.getHours()) + ':' + p(d.getMinutes())
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
  // 拉取后台预览/高亮类型配置（覆盖内置默认）
  try {
    const pc = await fetchPreviewConfig()
    if (pc && pc.previewModes && Object.keys(pc.previewModes).length) previewModes.value = pc.previewModes
    if (pc && Array.isArray(pc.textPreviewExts) && pc.textPreviewExts.length) textPreviewExts.value = pc.textPreviewExts
    if (pc && pc.attachTextColor) attachTextColor.value = pc.attachTextColor
  } catch (e) { /* 后端不可用则用内置默认 */ }

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
        const item = { name: t.name, id: t.id, key: t.themeKey || ('theme-' + t.id), branchColors: t.branchColors || [], config: cfg }
        const idx = merged.findIndex(x => x.name === t.name || x.key === t.themeKey)
        if (idx >= 0) merged[idx] = item
        else merged.push(item)
      })
      themes.value = merged
      if (!merged.find(x => x.name === currentTheme.value)) currentTheme.value = merged[0].name
    }
  } catch (e) { /* 后端不可用则仅用内置主题 */ }
  loadMemberFeature()

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
    data = await mindmapApi.create({ name: '未命名', theme: themes.value[0].name })
    mindMapId.value = data.id
    data = await mindmapApi.get(data.id)
  }
  curVersion.value = data.version || 0
  mapOwnerId.value = Number(data.userId) || 0

  // 解析数据: 后端可能返回 JSON 字符串，需先解析为对象
  let d = data.data || {}
  if (typeof d === 'string' && d) { try { d = JSON.parse(d) } catch (e) { d = {} } }
  // 附件节点文件名默认色：未手动设置文字色时使用后台配置（渲染前改数据，避免渲染回调副作用）
  applyAttachDefaultColor(d)
  // 支持新格式 { root, _tabs, _activeTab } 或旧格式 { root }
  if (d._tabs && Array.isArray(d._tabs) && d._tabs.length) {
    canvasTabs.value = d._tabs
    docIndex.value = d._activeTab || 0
    // 顶层 root 是活动画布的最新数据（保存/脚本写入时同步），加载时兜底同步到 _tabs 活动项，
    // 避免 _tabs 与顶层 root 不一致导致附件等字段丢失
    if (d.root) {
      const active = canvasTabs.value[docIndex.value]
      if (active) {
        if (active.data) active.data.root = d.root
        else active.data = { root: d.root }
      }
    }
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
  onCtrlSKeydown = (e) => { if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 's') { e.preventDefault(); if (mindMapId.value) saveDoc() } }
  window.addEventListener('keydown', onCtrlSKeydown)
  document.addEventListener('click', onDocClick)
  document.addEventListener('paste', onPaste)
  connectCmtWs()
  connectWs()
  markSaved()
  clearTimeout(autoSaveTimer)
  setTimeout(() => { markSaved(); clearTimeout(autoSaveTimer) }, 2500)
})

function onDocClick(e) {
  if (!e.target.closest('#ctx-menu')) hideCtxMenu()
}

onBeforeUnmount(() => {
  disconnectWs()
  disconnectCmtWs()
  if (onCtrlSKeydown) window.removeEventListener('keydown', onCtrlSKeydown)
  document.removeEventListener('mousemove', onBarDragMove)
  document.removeEventListener('mouseup', onBarDragEnd)
  if (beforeUnloadHandler) window.removeEventListener('beforeunload', beforeUnloadHandler)
  document.removeEventListener('click', onDocClick)
  document.removeEventListener('focusin', onEditFocusIn)
  document.removeEventListener('focusout', onEditFocusOut)
  document.removeEventListener('paste', onPaste)
  if (window.__attIconTimer) { clearInterval(window.__attIconTimer); window.__attIconTimer = null }
  if (cmtTimer) { clearInterval(cmtTimer); cmtTimer = null }
  if (mind.value) { try { mind.value.dispose && mind.value.dispose() } catch(e){} }
})
</script>

<style scoped>
.editor-root { position: relative; height: 100%; width: 100%; }

/* ===== 节点评论面板 + 未读闪烁 ===== */
.cmt-panel { position: fixed; top: 74px; right: 18px; bottom: 86px; width: 320px; max-width: calc(100vw - 36px); background: rgba(23, 26, 32, .88); backdrop-filter: blur(14px); -webkit-backdrop-filter: blur(14px); border: 1px solid rgba(255,255,255,.09); border-radius: 14px; box-shadow: 0 14px 44px rgba(0,0,0,.4); display: flex; flex-direction: column; overflow: hidden; z-index: 300; color: #e8eaf2; font-size: 13px; }
.cmt-header { display: flex; align-items: center; justify-content: space-between; padding: 12px 14px; border-bottom: 1px solid rgba(255,255,255,.08); }
.cmt-title { font-weight: 600; font-size: 14px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.cmt-close { border: 0; background: none; color: #aab; font-size: 18px; cursor: pointer; line-height: 1; padding: 2px 6px; border-radius: 6px; }
.cmt-close:hover { background: rgba(255,255,255,.12); color: #fff; }
.cmt-hd-r { display: flex; align-items: center; gap: 4px; }
.cmt-sound { border: 0; background: none; color: #aab; font-size: 15px; cursor: pointer; line-height: 1; padding: 2px 6px; border-radius: 6px; }
.cmt-sound:hover { background: rgba(255,255,255,.12); color: #fff; }
.cmt-list { flex: 1; overflow-y: auto; padding: 10px 12px; display: flex; flex-direction: column; gap: 8px; }
.cmt-empty { text-align: center; color: #8890a3; padding: 26px 0; }
.cmt-item { background: rgba(255,255,255,.05); border: 1px solid rgba(255,255,255,.06); border-radius: 10px; padding: 8px 10px; }
.cmt-item.cmt-author { border-color: rgba(245, 158, 11, .35); background: rgba(245, 158, 11, .07); }
.cmt-head { display: flex; align-items: center; gap: 6px; margin-bottom: 4px; }
.cmt-head b { color: #f5f7ff; font-size: 13px; }
.cmt-author-tag { font-size: 11px; color: #fff; background: #f59e0b; border-radius: 4px; padding: 1px 5px; }
.cmt-time { margin-left: auto; color: #7c8399; font-size: 11px; white-space: nowrap; }
.cmt-body { color: #cdd3e3; line-height: 1.55; word-break: break-word; }
.cmt-foot { margin-top: 4px; }
.cmt-reply { border: 0; background: none; color: #7aa2f7; cursor: pointer; font-size: 12px; padding: 0; }
.cmt-reply:hover { text-decoration: underline; }
.cmt-quick { display: flex; gap: 6px; margin-top: 6px; }
.cmt-quick input { flex: 1; min-width: 0; border: 1px solid rgba(255,255,255,.14); background: rgba(255,255,255,.06); color: #eef; border-radius: 8px; padding: 7px 10px; font-size: 13px; outline: none; }
.cmt-quick input:focus { border-color: rgba(122, 162, 247, .6); }
.cmt-quick button { border: 0; background: #4A5CF7; color: #fff; border-radius: 8px; padding: 7px 14px; font-size: 13px; cursor: pointer; white-space: nowrap; }
.cmt-quick button:hover { background: #3a4ae0; }
.cmt-tip { padding: 10px 12px; color: #7c8399; font-size: 11px; text-align: center; border-top: 1px solid rgba(255,255,255,.08); }
.cmt-err { color: #f87171; }
.cmt-list::-webkit-scrollbar { width: 5px; }
.cmt-list::-webkit-scrollbar-thumb { background: rgba(255,255,255,.16); border-radius: 3px; }
.cmt-list::-webkit-scrollbar-track { background: transparent; }
/* 未读节点：呼吸灯（节点+根路径）+ 右上角未读数徽标（HTML 覆盖层，不随画布缩放） */
/* 保存冲突 diff 弹窗 */
.conflict-list { max-height: 42vh; overflow-y: auto; margin: 10px 0; display: flex; flex-direction: column; gap: 8px; }
.conflict-item { border: 1px solid rgba(255,255,255,.12); border-radius: 10px; padding: 8px 10px; background: rgba(255,255,255,.04); }
.conflict-node { font-size: 13px; font-weight: 700; color: #e6b422; margin-bottom: 6px; word-break: break-all; }
.conflict-cols { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
.conflict-col { min-width: 0; }
.conflict-col.mine .conflict-label { color: #4fc3f7; }
.conflict-col.remote .conflict-label { color: #ff8a65; }
.conflict-label { font-size: 11px; font-weight: 600; margin-bottom: 3px; }
.conflict-text { font-size: 12px; color: #e6e6e6; background: rgba(0,0,0,.25); border-radius: 6px; padding: 5px 7px; word-break: break-all; max-height: 90px; overflow-y: auto; white-space: pre-wrap; }
@media (max-width: 480px) {
  .conflict-cols { grid-template-columns: 1fr; }
}
.cmt-badge-layer { position: absolute; inset: 0; pointer-events: none; z-index: 2; overflow: hidden; }
.cmt-badge-el { position: absolute; min-width: 18px; height: 18px; line-height: 18px; text-align: center; font-size: 11px; font-weight: 700; color: #fff; background: linear-gradient(135deg, #ff6b6b, #e53935); border-radius: 9px; padding: 0 5px; box-shadow: 0 2px 8px rgba(229,57,53,.6); transform: translate(-50%, -50%); }
.cmt-src { display: inline-block; font-size: 10px; line-height: 16px; height: 16px; padding: 0 6px; border-radius: 4px; margin-right: 6px; font-weight: 600; vertical-align: 1px; }
.cmt-src-share { background: rgba(74,92,247,.12); color: #4A5CF7; }
.cmt-src-org { background: rgba(14,124,134,.12); color: #0e7c86; }
.cmt-new { display: flex; gap: 8px; padding: 10px 14px; border-top: 1px solid rgba(255,255,255,.08); }
.cmt-new input { flex: 1; min-width: 0; background: rgba(255,255,255,.08); border: 1px solid rgba(255,255,255,.12); border-radius: 6px; color: #e6e6e6; font-size: 13px; padding: 7px 10px; outline: none; }
.cmt-new input:focus { border-color: rgba(74,92,247,.6); }
.cmt-new button { background: linear-gradient(135deg, #4A5CF7, #7c6cf0); border: none; color: #fff; font-size: 13px; padding: 7px 16px; border-radius: 6px; cursor: pointer; white-space: nowrap; }
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
/* 流动电子线：克隆原连线路径，虚线点沿路径流动（同组元素，随画布缩放/平移） */
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
.canvas-bg { position: absolute; inset: 0; overflow: hidden; pointer-events: none; z-index: 0; }
.canvas-bg-color { position: absolute; inset: 0; pointer-events: none; z-index: 0; }
.canvas-bg-image { position: absolute; inset: 0; background-size: cover; background-position: center; background-repeat: no-repeat; pointer-events: none; z-index: 0; }
.canvas-bg-video { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; object-position: center center; pointer-events: none; z-index: 0; }
.smm-mind-map-container { position: relative; z-index: 1; pointer-events: auto; }
#map { position: relative; height: 100%; width: 100%; background: transparent !important; opacity: 1; transition: opacity .5s ease; }
#map.map-loading { opacity: 0; }
.page-loading { position: absolute; inset: 0; z-index: 100; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 14px; background: rgba(255,255,255,.7); backdrop-filter: blur(2px); }
.pl-spinner { width: 44px; height: 44px; border: 4px solid rgba(74,92,247,.18); border-top-color: #4A5CF7; border-radius: 50%; animation: pl-rot .8s linear infinite; }
.pl-text { font-size: 14px; color: #667; }
@keyframes pl-rot { to { transform: rotate(360deg) } }
.export-loading { position: absolute; inset: 0; z-index: 101; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 12px; background: rgba(255,255,255,.72); backdrop-filter: blur(2px); }
.el-title { font-size: 16px; font-weight: 600; color: #333; }
.el-text { font-size: 13px; color: #667; }
.el-bar { width: 260px; height: 6px; border-radius: 999px; background: rgba(74,92,247,.15); overflow: hidden; }
.el-bar-fill { height: 100%; border-radius: 999px; background: linear-gradient(90deg, #4A5CF7, #7c6cf0); transition: width .25s ease; }
.el-pct { font-size: 12px; color: #4A5CF7; font-weight: 600; }
.attp-box .attp-dl { color:#909399; font-size:14px; padding:40px 0 16px; text-align:center; } .attp-box .attp-dl-btn { display:inline-block; padding:8px 24px; background:#0e7c86; color:#fff; border-radius:6px; text-decoration:none; font-size:14px; }
.attp-body .CodeMirror { background:#1e1e1e; color:#e6e6e6; font-family:Consolas,'Courier New',monospace; font-size:13px; line-height:1.6; height:100% }
.attp-body .CodeMirror-gutters { background:#252526; border-right:1px solid #333 }
.attp-body .CodeMirror-linenumber { color:#6e6e6e }
.attp-body .CodeMirror-cursor { border-left:1px solid #fff }
.attp-body .CodeMirror-selected { background:#264f78 }
.attp-body span.cm-keyword { color:#569cd6 }
.attp-body span.cm-string, .attp-body span.cm-string-2 { color:#ce9178 }
.attp-body span.cm-comment { color:#6a9955 }
.attp-body span.cm-number { color:#b5cea8 }
.attp-body span.cm-atom { color:#d7ba7d }
.attp-body span.cm-variable, .attp-body span.cm-variable-2, .attp-body span.cm-property { color:#9cdcfe }
.attp-body span.cm-def, .attp-body span.cm-builtin, .attp-body span.cm-qualifier { color:#dcdcaa }
.attp-body span.cm-operator { color:#d4d4d4 }
.attp-body span.cm-tag { color:#569cd6 }
.attp-body span.cm-attribute { color:#9cdcfe }
.attp-body span.cm-meta { color:#d4d4d4 }
.attp-body span.cm-error { color:#f44747 }
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
.collab-toast {
  position: fixed;
  top: 56px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 2000;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 14px;
  background: rgba(30, 34, 42, 0.95);
  border: 1px solid rgba(255, 200, 87, 0.35);
  border-radius: 10px;
  box-shadow: 0 4px 18px rgba(0, 0, 0, 0.45);
  max-width: 90vw;
}
.collab-toast-text { font-size: 13px; color: #f5d98b; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.math-btn-sm { padding: 3px 10px; font-size: 12px; }
.autosave-wrap { position: relative; display: inline-flex; }
.autosave-wrap button.active { background: rgba(255,255,255,.2); }
.autosave-menu { position: absolute; top: 44px; left: 50%; transform: translateX(-50%); z-index: 210; width: 220px; padding: 10px 12px; background: rgba(30,34,42,.97); border: 1px solid rgba(255,255,255,.14); border-radius: 12px; box-shadow: 0 8px 28px rgba(0,0,0,.5); backdrop-filter: blur(8px); }
.as-row { display: flex; align-items: center; justify-content: space-between; gap: 10px; padding: 5px 0; }
.as-label { font-size: 13px; color: #e8eaf0; white-space: nowrap; }
.as-input { width: 74px; padding: 3px 8px; background: rgba(255,255,255,.08); border: 1px solid rgba(255,255,255,.18); border-radius: 7px; color: #fff; font-size: 13px; text-align: center; }
.as-input:focus { outline: none; border-color: rgba(122,162,247,.7); }
.as-switch { position: relative; display: inline-flex; width: 42px; height: 22px; flex-shrink: 0; }
.as-switch input { opacity: 0; width: 0; height: 0; }
.as-switch span { position: absolute; inset: 0; background: rgba(255,255,255,.16); border-radius: 999px; transition: .25s; cursor: pointer; }
.as-switch span::before { content: ''; position: absolute; left: 3px; top: 3px; width: 16px; height: 16px; border-radius: 50%; background: #fff; transition: .25s; }
.as-switch input:checked + span { background: #4A5CF7; }
.as-switch input:checked + span::before { transform: translateX(20px); }
.as-tip { margin-top: 6px; padding-top: 7px; border-top: 1px solid rgba(255,255,255,.08); font-size: 11px; color: #8a92a8; text-align: center; }
</style>

<style>
/* 全局：隐藏 MathJax 辅助数学标记，防止部分浏览器下与主公式叠加显示成两层 */
mjx-assistive-mml { display: none !important; }

.theme-card { position: relative; }
.theme-vip { position: absolute; top: -6px; right: -4px; z-index: 3; font-size: 13px; line-height: 1; filter: drop-shadow(0 1px 2px rgba(0,0,0,.35)); }
.em-vip { margin-right: 5px; font-size: 13px; line-height: 1; }
</style>
