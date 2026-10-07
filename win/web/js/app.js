// USMIND 本地版 - 前端主逻辑
// 引擎初始化 / 主题注册 / 数据保存 / 历史 / 设置 / 导出
import MindMap from '../vendor/simpleMindMap.esm.min.js';
import { THEMES } from './themes.js';

// ================= 调试错误显示 =================
function showErr(msg) {
  console.error(msg);
  const el = document.getElementById('bootErr');
  if (!el) {
    const d = document.createElement('div');
    d.id = 'bootErr';
    d.style.cssText = 'position:fixed;left:50%;top:50%;transform:translate(-50%,-50%);background:#2a1020;color:#ff8f94;padding:16px 24px;border-radius:10px;z-index:999;max-width:80%;font-size:13px;white-space:pre-wrap;box-shadow:0 8px 30px rgba(0,0,0,.5)';
    document.body.appendChild(d);
  }
  document.getElementById('bootErr').textContent = msg;
}
window.addEventListener('error', (e) => showErr('脚本错误: ' + (e.message || String(e.error || '')) + '\n' + (e.filename || '')));
window.addEventListener('unhandledrejection', (e) => showErr('未处理异常: ' + (e.reason && e.reason.message ? e.reason.message : String(e.reason))));

// ================= 桥接 =================
const hasNative = !!(window.chrome && window.chrome.webview && window.chrome.webview.hostObjects);
const nativeObj = hasNative ? window.chrome.webview.hostObjects.usmind : null;

async function callNative(method, ...args) {
  if (!nativeObj) return { ok: false, error: 'no-native-bridge' };
  try {
    const r = await nativeObj[method](...args);
    return typeof r === 'string' ? JSON.parse(r) : r;
  } catch (e) {
    return { ok: false, error: String(e && e.message || e) };
  }
}

// 双击唤醒 / 单实例转发：C++ 通过 PostWebMessageAsJson 通知
if (window.chrome && window.chrome.webview) {
  window.chrome.webview.addEventListener('message', (ev) => {
    const msg = ev && ev.data;
    if (msg && msg.type === 'openRequest' && msg.path) {
      openPath(msg.path);
    }
  });
}

// ================= 常量 =================
const FALLBACK_EMOJI = ['😀','😄','😂','😍','🤔','😎','🤩','😭','😡','🤗','👍','👏','🔥','⭐','❤️','💡','🚀','🎯','🎉','✅','⚠️','📌','💰','📚','☕','🎵','🏆','💪'];
function numSvg(n) { return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32" width="32" height="32"><rect x="1" y="1" width="30" height="30" rx="7" fill="#eef2ff"/><text x="16" y="23" font-size="17" font-weight="bold" text-anchor="middle" fill="#4A5CF7">'+n+'</text></svg>'; }
function emojiSvg(e) { return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32" width="32" height="32"><text x="16" y="26" font-size="26" text-anchor="middle">'+e+'</text></svg>'; }
const FALLBACK_COLORS = ['#5B8FF9','#2FB36B','#F6A609','#9270CA','#E8684A','#22A7C9','#F08BB4','#8C9B6F'];
const DEFAULT_THEME = '晨曦';
let userDefaultTheme = DEFAULT_THEME;  // 用户在设置里选的默认主题，新建时用

// ================= 状态 =================
let mind = null;
let doc = {
  path: '',          // 当前文件路径（空 = 未保存过）
  name: '未命名导图',
  theme: DEFAULT_THEME,
  layout: 'logicalStructure',
};
let dirty = false;
let autoSaveTimer = null;
let suppressDirtyUntil = 0;
let booting = true;

// 多标签：每个标签保存一份文档快照（data/theme/layout/name/path）
let tabs = [];       // [{name, path, data, theme, layout}]
let activeTab = -1;

function snapshotActiveTab() {
  if (activeTab < 0 || !mind) return;
  try {
    tabs[activeTab].data = mind.getData();
    tabs[activeTab].name = doc.name;
    tabs[activeTab].path = doc.path;
    tabs[activeTab].theme = doc.theme;
    tabs[activeTab].layout = doc.layout;
  } catch (e) {}
}
function stripHtml(s) {
  if (!s) return '';
  return String(s).replace(/<[^>]+>/g, '').replace(/&nbsp;/g, ' ').trim();
}
function rootText(t) {
  try {
    if (mind && tabs[activeTab] === t) {
      const rd = mind.getData();
      if (rd && rd.root && rd.root.data && rd.root.data.text) return stripHtml(rd.root.data.text) || '未命名';
    }
  } catch (e) {}
  try {
    if (t.data) {
      if (t.data.data && t.data.data.text) return stripHtml(t.data.data.text) || '未命名';
      if (t.data.root && t.data.root.data && t.data.root.data.text) return stripHtml(t.data.root.data.text) || '未命名';
    }
  } catch (e) {}
  return '未命名';
}
function renderTabStrip() {
  const strip = document.getElementById('tabStrip');
  strip.innerHTML = '';
  tabs.forEach((t, i) => {
    const el = document.createElement('div');
    el.className = 'tab-item' + (i === activeTab ? ' active' : '');
    el.title = rootText(t);

    const label = document.createElement('span');
    label.textContent = rootText(t) + (t.dirty ? ' ●' : '');
    el.appendChild(label);

    const close = document.createElement('span');
    close.className = 'tab-close';
    close.textContent = '×';
    close.title = '关闭画布';
    close.addEventListener('click', (e) => {
      e.stopPropagation();
      closeTab(i);
    });
    el.appendChild(close);

    el.addEventListener('click', () => switchToTab(i));
    el.addEventListener('dblclick', (e) => {
      e.stopPropagation();
      const cur = rootText(t);
      showInputDialog('重命名中心主题', cur, (newName) => {
        if (newName && newName.trim()) {
          if (!t.data) t.data = { root: { data: { text: newName.trim() }, children: [] } };
          if (!t.data.root) t.data.root = { data: {}, children: [] };
          if (!t.data.root.data) t.data.root.data = {};
          t.data.root.data.text = newName.trim();
          if (i === activeTab && mind) {
            try {
              const rd = mind.getData();
              if (rd && rd.root && rd.root.data) { rd.root.data.text = newName.trim(); mind.setData(rd); mind.render(); }
            } catch (err) {}
          }
          t.dirty = true;
          renderTabStrip();
        }
      });
    });
    strip.appendChild(el);
  });
}
function closeTab(i) {
  if (tabs.length <= 1) return;
  const wasActive = i === activeTab;
  tabs.splice(i, 1);
  if (wasActive) {
    if (activeTab >= tabs.length) activeTab = tabs.length - 1;
    switchToTab(activeTab);
  } else if (i < activeTab) {
    activeTab--;
  }
  renderTabStrip();
}
function switchToTab(i) {
  if (i === activeTab) return;
  snapshotActiveTab();
  activeTab = i;
  const t = tabs[i];
  suppressDirtyUntil = Date.now() + 2000;
  doc.path = t.path || '';
  doc.name = t.name || '未命名导图';
  doc.theme = t.theme || userDefaultTheme;
  doc.layout = t.layout || 'logicalStructure';
  try { mind.setData(t.data); } catch (e) { console.error('setData', e); }
  try { mind.setLayout(doc.layout); } catch (e) {}
  try { mind.setTheme(doc.theme); } catch (e) {}
  try { mind.render(); } catch (e) {}
  setTimeout(() => { try { mind.view.fit(); } catch (e) {} }, 60);
  dirty = false;
  updateTitle();
  updateStatus();
  renderTabStrip();
  syncLayoutSelect();
}
function newTab() {
  snapshotActiveTab();
  suppressDirtyUntil = Date.now() + 2000;
  const t = {
    name: '未命名导图', path: '',
    data: defaultData().root, theme: userDefaultTheme, layout: 'logicalStructure',
  };
  tabs.push(t);
  activeTab = tabs.length - 1;
  doc.path = ''; doc.name = t.name; doc.theme = t.theme; doc.layout = t.layout;
  try { mind.setData(t.data); } catch (e) {}
  try { mind.setTheme(doc.theme); } catch (e) {}
  try { mind.setLayout(doc.layout); } catch (e) {}
  try { mind.render(); } catch (e) {}
  setTimeout(() => { try { mind.view.fit(); } catch (e) {} }, 60);
  dirty = false;
  updateTitle(); updateStatus(); renderTabStrip(); syncLayoutSelect();
}
function openInTab(path, name, parsed) {
  snapshotActiveTab();
  const t = {
    name: name || parsed.name || '未命名导图',
    path: path, data: parsed.root,
    theme: parsed.theme, layout: parsed.layout,
  };
  tabs.push(t);
  activeTab = tabs.length - 1;
  doc.path = path; doc.name = t.name; doc.theme = t.theme; doc.layout = t.layout;
  suppressDirtyUntil = Date.now() + 2000;
  try { mind.setData(t.data); } catch (e) {}
  try { mind.setLayout(doc.layout); } catch (e) {}
  try { mind.setTheme(doc.theme); } catch (e) {}
  try { mind.render(); } catch (e) {}
  setTimeout(() => { try { mind.view.fit(); } catch (e) {} }, 60);
  dirty = false;
  updateTitle(); updateStatus(); renderTabStrip(); syncLayoutSelect();
}

function themeBy(name) {
  return THEMES.find(t => t.name === name) || THEMES[0];
}
function branchColorsOf(name) {
  const t = themeBy(name);
  const colors = (t && t.branchColors || []).filter(Boolean);
  return colors.length ? colors : FALLBACK_COLORS;
}

// ================= 小工具 =================
function $(id) { return document.getElementById(id); }
function toast(msg, type) {
  const el = $('toast');
  el.textContent = msg;
  el.className = type === 'error' ? 'show error' : 'show';
  clearTimeout(toast._t);
  toast._t = setTimeout(() => { el.className = ''; }, 2200);
}
// 面板内滚轮滚动不触发脑图缩放
document.addEventListener('wheel', (e) => {
  if (e.target.closest && e.target.closest('.panel-body')) {
    e.stopPropagation();
  }
}, { passive: true, capture: true });

function showInputDialog(title, initial, onOk) {
  const ov = $('dlgOverlay'), box = $('dlgBox'), inp = $('dlgInput');
  $('dlgTitle').textContent = title;
  inp.value = initial || '';
  ov.style.display = 'flex';
  setTimeout(() => { inp.focus(); inp.select(); }, 50);
  const close = () => { ov.style.display = 'none'; };
  const ok = () => { close(); onOk(inp.value); };
  $('dlgOk').onclick = ok;
  $('dlgCancel').onclick = close;
  ov.onclick = (e) => { if (e.target === ov) close(); };
  inp.onkeydown = (e) => {
    if (e.key === 'Enter') ok();
    if (e.key === 'Escape') close();
  };
}
function updateTitle() {
  const name = doc.name || '未命名导图';
  const dt = $('docTitle'); if (dt) { dt.textContent = name; dt.title = doc.path || name; }
  const st = $('saveState');
  if (st) {
    if (dirty) { st.textContent = '● 未保存'; st.className = 'save-state saving'; }
    else if (doc.path) { st.textContent = '已保存'; st.className = 'save-state saved'; }
    else { st.textContent = ''; st.className = 'save-state'; }
  }
}
function updateStatus() {
  let nodes = 0;
  try {
    const root = mind && mind.getData();
    const walk = (n) => { if (!n) return; nodes++; (n.children || []).forEach(walk); };
    walk(root && root.root || root);
  } catch (e) {}
  $('nodeCount').textContent = '节点 ' + (nodes || 1);
}
function updateZoom() {
  try { $('zoomLevel').textContent = Math.round(mind.view.getScale() * 100) + '%'; } catch (e) {}
}
function markDirty() {
  if (Date.now() < suppressDirtyUntil || booting) return;
  dirty = true;
  if (activeTab >= 0) tabs[activeTab].dirty = true;
  updateTitle();
  renderTabStrip();
  scheduleAutoSave();
}
const LAYOUTS = [
  ['logicalStructure', '逻辑结构图(右)'],
  ['logicalStructureLeft', '逻辑结构图(左)'],
  ['mindMap', '思维导图'],
  ['organizationStructure', '组织结构图'],
  ['catalogOrganization', '目录组织'],
  ['timeline', '时间轴'],
  ['verticalTimeline', '竖向时间轴'],
  ['fishbone', '鱼骨图'],
];
function syncLayoutSelect() {
  const btn = document.getElementById('layoutBtn');
  const cur = LAYOUTS.find(l => l[0] === (doc.layout || 'logicalStructure'));
  if (btn && cur) btn.textContent = cur[1] + ' ▾';
  // 更新菜单项 active
  document.querySelectorAll('#layoutMenu .dm-item').forEach(el => {
    el.classList.toggle('active', el.dataset.v === (doc.layout || 'logicalStructure'));
  });
}
function initLayoutDrop() {
  const menu = document.getElementById('layoutMenu');
  LAYOUTS.forEach(([v, label]) => {
    const it = document.createElement('div');
    it.className = 'dm-item';
    it.dataset.v = v;
    it.textContent = label;
    it.addEventListener('click', () => {
      doc.layout = v;
      try { mind.setLayout(v); mind.render(); } catch (e) {}
      setTimeout(() => { try { mind.view.fit(); } catch (e) {} }, 60);
      menu.style.display = 'none';
      syncLayoutSelect();
      markDirty();
    });
    menu.appendChild(it);
  });
  document.getElementById('layoutBtn').addEventListener('click', (e) => {
    e.stopPropagation();
    menu.style.display = menu.style.display === 'none' ? 'block' : 'none';
  });
  document.addEventListener('click', () => { menu.style.display = 'none'; });
}
function scheduleAutoSave() {
  clearTimeout(autoSaveTimer);
  autoSaveTimer = setTimeout(() => { if (dirty && doc.path) saveNow(true); }, 3000);
}

// ================= 文档 =================
function defaultData() {
  return {
    root: {
      data: { text: '中心主题' },
      children: [
        { data: { text: '主题一' }, children: [] },
        { data: { text: '主题二' }, children: [] },
        { data: { text: '主题三' }, children: [] },
      ],
    },
  };
}

function serializeDoc() {
  const payload = { root: mind.getData() };
  return JSON.stringify({
    format: 'usmind',
    version: 1,
    name: doc.name,
    theme: doc.theme,
    layout: doc.layout,
    savedAt: new Date().toISOString(),
    data: payload,
  }, null, 2);
}

// 解析 .usmind / 兼容裸引擎 JSON
function parseDoc(text) {
  let obj;
  try { obj = JSON.parse(text); } catch (e) { throw new Error('文件不是有效的 JSON'); }
  const isUsmind = obj && obj.format === 'usmind';
  let root = null;
  if (isUsmind) root = obj.data && obj.data.root ? obj.data.root : (obj.data && obj.data.data && obj.data.data.root ? obj.data.data.root : null);
  else if (obj && obj.root) root = obj.root;
  else if (obj && obj.data && obj.data.root) root = obj.data.root;
  if (!root || !root.data) throw new Error('文件内容不是思维导图数据');
  return {
    root,
    theme: (isUsmind && obj.theme) || DEFAULT_THEME,
    layout: (isUsmind && obj.layout) || 'logicalStructure',
    name: (isUsmind && obj.name) || '',
  };
}

async function applyDoc(parsed, path, name) {
  suppressDirtyUntil = Date.now() + 2000;
  doc.path = path || '';
  doc.name = name || parsed.name || '未命名导图';
  doc.theme = parsed.theme;
  doc.layout = parsed.layout;
  try { mind.setData(parsed.root); } catch (e) { console.error('setData 失败', e); }
  try { mind.setLayout(parsed.layout); } catch (e) {}
  try { mind.setTheme(parsed.theme); } catch (e) {}
  try { mind.render(); } catch (e) { console.error('render 失败', e); }
  setTimeout(() => { try { mind.view.fit(); } catch (e) {} }, 60);
  dirty = false;
  booting = false;
  updateTitle();
  updateStatus();
  callNative('notifyDocumentState', doc.name);
  if (doc.path) callNative('saveSettings', JSON.stringify({ lastFile: doc.path }));
}

async function openPath(path) {
  if (!path) return;
  const r = await callNative('openPath', path);
  if (r && r.ok && r.data) {
    try {
      const parsed = parseDoc(r.data);
      openInTab(r.path || path, r.name, parsed);
      booting = false;
      toast('已打开：' + (r.name || doc.name));
      callNative('notifyDocumentState', doc.name);
      if (doc.path) callNative('saveSettings', JSON.stringify({ lastFile: doc.path }));
    } catch (e) {
      toast('打开失败：' + e.message, 'error');
    }
  } else if (r && r.ok === false && r.error) {
    toast('打开失败：' + r.error, 'error');
  }
}

async function openWithDialog() {
  const r = await callNative('openWithDialog');
  if (r && r.ok && r.data) {
    try {
      const parsed = parseDoc(r.data);
      openInTab(r.path, r.name, parsed);
      toast('已打开：' + r.name);
    } catch (e) {
      toast('打开失败：' + e.message, 'error');
    }
  }
  // 用户取消：静默
}

function newDoc() {
  if (dirty && doc.path) {
    saveNow(true).then(() => doNew());
  } else if (dirty && !doc.path) {
    if (!confirm('当前导图有未保存的修改，确定新建吗？')) return;
    doNew();
  } else {
    doNew();
  }
}
function doNew() {
  newTab();
  booting = false;
}

async function saveNow(silent) {
  if (!mind) return false;
  const content = serializeDoc();
  const r = await callNative('saveCurrent', content, doc.name);
  if (r && r.ok) {
    doc.path = r.path || doc.path;
    doc.name = r.name || doc.name;
    dirty = false;
    updateTitle();
    if (!silent) toast('已保存');
    callNative('notifyDocumentState', doc.name);
    callNative('saveSettings', JSON.stringify({ lastFile: doc.path }));
    return true;
  }
  if (r && r.cancel) { /* 用户取消另存为 */ }
  else if (!silent) toast('保存失败', 'error');
  return false;
}

// ================= 引擎 =================
function initMindMap() {
  // 注册 16 套主题
  THEMES.forEach(t => { try { MindMap.defineTheme(t.name, t.config); } catch (e) {} });

  mind = new MindMap({
    el: $('mindMapContainer'),
    data: defaultData().root,
    layout: doc.layout,
    theme: doc.theme,
    fit: true,
    maxHistoryCount: 60,
    textAutoWrapWidth: 99999,
    mousewheelAction: 'zoom',
    defaultInsertSecondLevelNodeText: '新主题',
    defaultInsertBelowSecondLevelNodeText: '新主题',
    useLeftKeySelectionRightKeyDrag: false,
    isLimitMindMapInCanvas: false,
    isLimitMindMapInCanvasWhenHasScrollbar: false,
    enableShortcutOnlyWhenMouseInSvg: false,
    openRealtimeRenderOnNodeTextEdit: true,
    attachmentIcon: { style: { size: 0 }, icon: '' },
    rainbowLinesConfig: { open: true, colorsList: branchColorsOf(doc.theme) },
    iconList: [
      { type: 'number', list: Array.from({length: 10}, (_, i) => ({ name: String(i + 1), icon: numSvg(i + 1) })) },
      { type: 'emoji', list: FALLBACK_EMOJI.map(e => ({ name: e, icon: emojiSvg(e) })) },
    ],
  });

  // 窗口大小变化时重绘画布
  window.addEventListener('resize', () => {
    try { mind.resize(); } catch (e) {}
  });

  mind.on('data_change', () => {
    markDirty();
    renderTabStrip();
  });
  // 轮询刷新标签名（内联编辑时 data_change 可能不触发）
  setInterval(() => { try { renderTabStrip(); } catch (e) {} }, 200);
  // 监听节点编辑结束
  try { mind.on('node_text_change', () => renderTabStrip()); } catch (e) {}
  try { mind.on('node_mouseleave', () => renderTabStrip()); } catch (e) {}
  mind.on('scale', () => updateZoom());
  mind.on('node_tree_render_end', () => { updateStatus(); renderTabStrip(); });
  // Ctrl+滚轮缩放后也刷新数字（scale 事件可能不触发）
  $('mindMapContainer').addEventListener('wheel', () => { setTimeout(updateZoom, 80); }, { passive: true });
  updateStatus();
  updateZoom();
}

// ================= 主题 =================
function applyTheme(name) {
  const t = themeBy(name);
  if (!t) return;
  doc.theme = name;
  try { mind.setTheme(name); } catch (e) {}
  try { mind.setRainbowLinesConfig && mind.setRainbowLinesConfig({ open: true, colorsList: branchColorsOf(name) }); } catch (e) {}
  dirty = true;
  updateTitle();
  toast('已切换主题：' + t.name);
}

function buildThemeGrid() {
  const grid = $('themeGrid');
  grid.innerHTML = '';
  grid.className = 'theme-grid';
  THEMES.forEach(t => {
    const card = document.createElement('div');
    card.className = 'theme-card' + (t.name === doc.theme ? ' active' : '');
    const colors = (t.branchColors || []).filter(Boolean);
    const cs = colors.length ? colors : FALLBACK_COLORS;
    const grad = 'linear-gradient(135deg, ' + cs.slice(0, 6).join(', ') + ')';
    const preview = document.createElement('div');
    preview.className = 'theme-preview';
    preview.style.background = grad;
    // 节点圆点示意
    cs.slice(0, 5).forEach((c, i) => {
      const bar = document.createElement('div');
      bar.className = 'bar';
      bar.style.background = 'rgba(255,255,255,.18)';
      const dot = document.createElement('div');
      dot.className = 'node-dot' + (i === 0 ? ' root' : '');
      dot.style.background = i === 0 ? '#ffffff' : c;
      bar.appendChild(dot);
      preview.appendChild(bar);
    });
    const nm = document.createElement('div');
    nm.className = 'tc-name';
    nm.textContent = t.name;
    card.appendChild(preview);
    card.appendChild(nm);
    card.addEventListener('click', () => {
      applyTheme(t.name);
      buildThemeGrid();
      closePanel('themePanel');
    });
    grid.appendChild(card);
  });
}

// ================= 面板 =================
function openPanel(id) {
  $('overlay').classList.add('show');
  $(id).classList.add('open');
  if (id === 'themePanel') buildThemeGrid();
  if (id === 'historyPanel') refreshHistory();
  if (id === 'settingsPanel') fillSettings();
}
function closePanel(id) {
  $(id).classList.remove('open');
  if (!document.querySelector('.panel.open')) $('overlay').classList.remove('show');
}
function closeAllPanels() {
  document.querySelectorAll('.panel.open').forEach(p => p.classList.remove('open'));
  $('overlay').classList.remove('show');
}

// ================= 历史 =================
async function refreshHistory() {
  const list = $('historyList');
  list.innerHTML = '';
  const r = await callNative('getHistory');
  const items = (r && r.ok && r.items) ? r.items : [];
  if (!items.length) {
    const empty = document.createElement('div');
    empty.className = 'history-empty';
    empty.textContent = '暂无历史版本\n（每次保存前会自动备份上一版）';
    list.appendChild(empty);
    return;
  }
  items.forEach(it => {
    const row = document.createElement('div');
    row.className = 'history-item';
    const icon = document.createElement('div');
    icon.className = 'hi-icon';
    icon.textContent = '🕘';
    const main = document.createElement('div');
    main.className = 'hi-main';
    const nm = document.createElement('div');
    nm.className = 'hi-name';
    nm.textContent = it.name || '历史版本';
    const tm = document.createElement('div');
    tm.className = 'hi-time';
    tm.textContent = it.time + ' · ' + (it.size > 1024 ? Math.round(it.size / 1024) + ' KB' : it.size + ' B');
    main.appendChild(nm);
    main.appendChild(tm);
    const btn = document.createElement('button');
    btn.className = 'hi-restore';
    btn.textContent = '回退';
    btn.addEventListener('click', () => restoreHistoryItem(it));
    const del = document.createElement('button');
    del.className = 'hi-del';
    del.textContent = '×';
    del.title = '删除此版本';
    del.addEventListener('click', async (e) => {
      e.stopPropagation();
      await callNative('deleteHistory', it.id);
      refreshHistory();
    });
    row.appendChild(icon);
    row.appendChild(main);
    row.appendChild(btn);
    row.appendChild(del);
    list.appendChild(row);
  });
}

async function restoreHistoryItem(it) {
  if (!confirm('回退到 ' + (it.time || '') + ' 的版本？\n当前内容将保持在工作区，下次保存时写入文件。')) return;
  const r = await callNative('restoreHistory', it.id);
  if (r && r.ok && r.data) {
    try {
      const parsed = parseDoc(r.data);
      await applyDoc(parsed, doc.path, doc.name);
      dirty = true;
      updateTitle();
      toast('已回退到历史版本，请保存确认');
    } catch (e) {
      toast('回退失败：' + e.message, 'error');
    }
  } else {
    toast('回退失败', 'error');
  }
}

async function clearHistory() {
  if (!confirm('确定清空所有历史版本缓存？此操作不可恢复。')) return;
  const r = await callNative('clearHistory');
  if (r && r.ok) { toast('历史缓存已清空'); refreshHistory(); }
  else toast('清空失败', 'error');
}

// ================= 设置 =================
async function fillSettings() {
  const r = await callNative('getSettings');
  const s = (r && r.ok) ? r : {};
  // 默认主题（自定义下拉）
  const themeMenu = $('themeMenu');
  themeMenu.innerHTML = '';
  const curTheme = s.defaultTheme || DEFAULT_THEME;
  $('themeBtn').textContent = curTheme + ' ▾';
  THEMES.forEach(t => {
    const it = document.createElement('div');
    it.className = 'dm-item';
    it.textContent = t.name;
    if (t.name === curTheme) it.classList.add('active');
    it.addEventListener('click', () => {
      $('themeBtn').textContent = t.name + ' ▾';
      themeMenu.querySelectorAll('.dm-item').forEach(x => x.classList.remove('active'));
      it.classList.add('active');
      themeMenu.style.display = 'none';
      saveSettingsFromUi(t.name);
    });
    themeMenu.appendChild(it);
  });
  $('themeBtn').addEventListener('click', (e) => {
    e.stopPropagation();
    themeMenu.style.display = themeMenu.style.display === 'none' ? 'block' : 'none';
  });
  // 历史目录
  const app = await callNative('getAppInfo');
  $('setHistoryDir').value = s.historyDir || '（默认：程序目录 \\history）';
  $('historyDirDesc').textContent = app && app.historyDir ? '当前实际目录：' + app.historyDir : '';
  $('assocState').textContent = s.registerAssoc ? '已注册，双击 .usmind 文件即可打开' : '未注册';
}

async function saveSettingsFromUi(newThemeName) {
  const newTheme = newThemeName || userDefaultTheme;
  if (newTheme && newTheme !== doc.theme) {
    applyTheme(newTheme);
  }
  userDefaultTheme = newTheme;
  const payload = { defaultTheme: newTheme };
  let r;
  try {
    r = await callNative('saveSettings', JSON.stringify(payload));
  } catch (e) {
    toast('调用失败: ' + (e && e.message ? e.message : String(e)), 'error');
    return;
  }
  if (r && r.ok) toast('设置已保存并应用');
  else toast('设置保存失败: ' + JSON.stringify(r), 'error');
}

async function pickHistoryDir() {
  const r = await callNative('pickFolder');
  if (r && r.ok && r.dir) {
    const s = await callNative('setHistoryDir', r.dir);
    if (s && s.ok) {
      $('setHistoryDir').value = r.dir;
      toast('历史目录已更新');
      fillSettings();
    } else {
      toast('目录设置失败', 'error');
    }
  }
}

// ================= 导出 =================
async function doExport(type) {
  if (!mind) return;
  try {
    const name = doc.name.replace(/\.usmind$/i, '');
    if (type === 'png') await mind.doExport.export('png', true, name);
    else if (type === 'jpeg') await mind.doExport.export('jpeg', true, name);
    else if (type === 'svg') await mind.doExport.export('svg', true, name);
    else if (type === 'md') await mind.doExport.export('md', true, name);
    else if (type === 'json') await mind.doExport.export('json', true, name);
    else if (type === 'xmind') await mind.doExport.export('xmind', true, name);
    toast('导出已启动，请选择保存位置');
  } catch (e) {
    toast('导出失败：' + e.message, 'error');
  }
}

// ================= 节点编辑增强（右键菜单 / 样式条 / 图标 / 公式 / 链接 / 备注） =================
let ctxNode = null;        // 右键目标节点
let styleNode = null;      // 样式条目标节点
let iconTarget = null;     // 图标弹窗目标节点
let mathTarget = null;     // 公式弹窗目标节点（插入位置）
let mathEditNode = null;   // 公式弹窗目标节点（编辑模式）
let linkTarget = null;     // 链接弹窗目标节点
let noteTarget = null;     // 备注弹窗目标节点
let mathOrigText = null;   // 公式编辑缓存（取消时恢复）
let mathOrigLatex = '';
let mathConfirmed = false;

function escHtml(s) { return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;'); }
function isRootNode(n) { try { return !!(n && (n.isRoot || (n.nodeData && n.nodeData.data && n.nodeData.data.isRoot))); } catch (e) { return false; } }
function nodeDataOf(n) { try { return (n && n.nodeData) || null; } catch (e) { return null; } }

function activeNodeList() {
  try { return mind.renderer.activeNodeList || []; } catch (e) { return []; }
}
function currentSelectedNode() {
  try {
    const l = mind.renderer.activeNodeList;
    if (l && l.length === 1) return l[0];
  } catch (e) {}
  try { return mind.renderer.currentNode || null; } catch (e) { return null; }
}

// ---- 右键菜单 ----
function showCtxMenu(x, y) {
  const m = $('ctxMenu');
  m.style.display = 'block';
  const del = m.querySelector('[data-act="delete"]');
  if (del) del.classList.toggle('disabled', !ctxNode || isRootNode(ctxNode));
  const mw = m.offsetWidth, mh = m.offsetHeight;
  const vw = window.innerWidth, vh = window.innerHeight;
  let px = x, py = y;
  if (px + mw > vw - 8) px = Math.max(8, vw - mw - 8);
  if (py + mh > vh - 8) py = Math.max(8, vh - mh - 8);
  m.style.left = px + 'px';
  m.style.top = py + 'px';
}
function hideCtxMenu() { $('ctxMenu').style.display = 'none'; }

function ctxAct(act) {
  const node = ctxNode;
  switch (act) {
    case 'child': try { mind.execCommand('INSERT_CHILD_NODE'); } catch (e) {} break;
    case 'sibling': try { mind.execCommand('INSERT_NODE'); } catch (e) {} break;
    case 'delete':
      if (node && !isRootNode(node)) { try { mind.execCommand('REMOVE_NODE'); } catch (e) {} }
      break;
    case 'copy': try { mind.copy(); } catch (e) {} break;
    case 'cut': try { mind.cut(); } catch (e) {} break;
    case 'paste': try { mind.handlePaste(); } catch (e) {} break;
    case 'undo': try { mind.undo(); } catch (e) { try { mind.command && mind.command.undo && mind.command.undo(); } catch (e2) {} } break;
    case 'redo': try { mind.redo(); } catch (e) { try { mind.command && mind.command.redo && mind.command.redo(); } catch (e2) {} } break;
    case 'icon': openIconDialog(node); break;
    case 'math': openMathDialog(node, false); break;
    case 'link': openLinkDialog(node); break;
    case 'note': openNoteDialog(node); break;
    case 'style': openStyleBar(node || currentSelectedNode()); break;
  }
  setTimeout(() => { try { mind.render(); } catch (e) {} }, 30);
}

// ---- 样式条 ----
function openStyleBar(node) {
  styleNode = node;
  syncStyleBar();
  $('styleBar').style.display = 'flex';
}
function syncStyleBar() {
  if (!styleNode) return;
  try {
    const st = styleNode.getStyle && styleNode.getStyle() || {};
    const fs = st.fontSize || 14;
    $('sbFontSize').textContent = fs;
    try { $('sbColor').value = st.color || '#000000'; } catch (e) {}
    try { $('sbBgColor').value = st.background || '#ffffff'; } catch (e) {}
    ['bold', 'italic', 'underline', 'strikeThrough'].forEach(k => {
      const b = $('styleBar').querySelector('[data-style="' + k + '"]');
      if (b) b.classList.toggle('on', !!st[k]);
    });
  } catch (e) {}
}
function setNodeStyle(styleObj) {
  if (!styleNode) return;
  try { mind.execCommand('SET_NODE_STYLE', styleNode, styleObj); } catch (e) { return; }
  markDirty();
  syncStyleBar();
  setTimeout(() => { try { mind.render(); } catch (e) {} }, 30);
}
function toggleStyle(k) {
  if (!styleNode) return;
  try {
    const st = styleNode.getStyle && styleNode.getStyle() || {};
    const next = {};
    next[k] = !st[k];
    setNodeStyle(next);
  } catch (e) {}
}

// ---- 图标 ----
function openIconDialog(node) {
  iconTarget = node;
  const gn = $('iconGridNum');
  gn.innerHTML = '';
  for (let i = 1; i <= 10; i++) {
    const c = document.createElement('div');
    c.className = 'icon-cell';
    c.innerHTML = numSvg(i);
    c.addEventListener('click', () => {
      try { mind.execCommand('SET_NODE_ICON', iconTarget, [String(i)]); } catch (e) {}
      finishIcon();
    });
    gn.appendChild(c);
  }
  const ge = $('iconGridEmoji');
  ge.innerHTML = '';
  FALLBACK_EMOJI.forEach(em => {
    const c = document.createElement('div');
    c.className = 'icon-cell';
    c.innerHTML = emojiSvg(em);
    c.addEventListener('click', () => {
      try { mind.execCommand('SET_NODE_ICON', iconTarget, [em]); } catch (e) {}
      finishIcon();
    });
    ge.appendChild(c);
  });
  $('iconDialog').style.display = 'flex';
}
function finishIcon() {
  $('iconDialog').style.display = 'none';
  iconTarget = null;
  markDirty();
  setTimeout(() => { try { mind.render(); } catch (e) {} }, 30);
}

// ---- 公式 ----
function renderLatex(latex) {
  if (!window.katex) return '<span style="color:#e74c3c">[公式引擎未加载]</span>';
  try {
    return window.katex.renderToString(latex, { throwOnError: false, displayMode: true });
  } catch (e) {
    return '<span style="color:#e74c3c">[公式错误]</span>';
  }
}
function updateMathPreview() {
  const raw = $('mathInput').value.trim();
  const pv = $('mathPreview');
  if (!raw) { pv.innerHTML = '<span class="muted-text">输入 LaTeX 后这里实时预览</span>'; return; }
  const title = ($('mathTitle').value.trim() || '公式');
  pv.innerHTML = escHtml(title) + ' ' + renderLatex(raw);
}
function openMathDialog(node, isEdit, preText) {
  mathTarget = node;
  mathEditNode = isEdit ? node : null;
  mathConfirmed = false;
  if (isEdit && node) {
    let latex = '';
    let title = '';
    try { latex = node.getData('mathLatex') || ''; } catch (e) {}
    mathOrigLatex = latex;
    try {
      const nd = nodeDataOf(node);
      mathOrigText = (preText !== undefined) ? preText : (((nd && nd.data) || nd || {}).text || '');
      const m = mathOrigText.match(/^([^<]*)/);
      title = m ? m[1].replace(/&nbsp;/g, ' ').trim() : '';
    } catch (e) {}
    $('mathTitle').value = title || '公式';
    $('mathInput').value = latex;
  } else {
    mathOrigText = null;
    mathOrigLatex = '';
    $('mathTitle').value = '公式';
    $('mathInput').value = '';
  }
  updateMathPreview();
  $('mathDialog').style.display = 'flex';
  setTimeout(() => $('mathInput').focus(), 60);
}
function closeMathDialog() {
  $('mathDialog').style.display = 'none';
  if (mathEditNode && !mathConfirmed && mathOrigText !== null) {
    // 取消编辑：恢复节点原文，避免 TextEdit 写回破坏 KaTeX
    try { mind.renderer.setNodeDataRender(mathEditNode, { text: mathOrigText, richText: true, mathLatex: mathOrigLatex }); } catch (e) {}
  }
  mathTarget = null;
  mathEditNode = null;
  mathOrigText = null;
  mathOrigLatex = '';
  mathConfirmed = false;
}
function mathConfirm() {
  const raw = $('mathInput').value.trim();
  if (!raw) { closeMathDialog(); return; }
  mathConfirmed = true;
  const html = renderLatex(raw);
  const title = ($('mathTitle').value.trim() || '公式');
  const full = escHtml(title) + ' ' + html;
  try {
    if (mathEditNode) {
      mind.renderer.setNodeDataRender(mathEditNode, { text: full, richText: true, mathLatex: raw });
    } else {
      let pd = mathTarget ? nodeDataOf(mathTarget) : null;
      if (!pd) {
        const sel = currentSelectedNode();
        if (sel) pd = nodeDataOf(sel);
      }
      if (!pd) {
        const d = mind.getData();
        pd = d.root || d;
      }
      if (pd) {
        pd.children = pd.children || [];
        pd.children.push({ data: { text: full, richText: true, mathLatex: raw }, children: [] });
      }
    }
    setTimeout(() => { try { mind.render(); } catch (e) {} }, 30);
    markDirty();
    toast('公式已插入');
  } catch (e) {}
  closeMathDialog();
}

// ---- 链接 ----
function openLinkDialog(node) {
  linkTarget = node;
  try { $('linkInput').value = node.getData('hyperlink') || ''; } catch (e) {}
  $('linkDialog').style.display = 'flex';
  setTimeout(() => $('linkInput').focus(), 60);
}
function closeLinkDialog() { $('linkDialog').style.display = 'none'; linkTarget = null; }
function linkConfirm() {
  const url = $('linkInput').value.trim();
  try { mind.execCommand('SET_NODE_HYPERLINK', linkTarget, url || ''); } catch (e) {}
  closeLinkDialog();
  markDirty();
  setTimeout(() => { try { mind.render(); } catch (e) {} }, 30);
}

// ---- 备注 ----
function openNoteDialog(node) {
  noteTarget = node;
  try { $('noteInput').value = node.getData('note') || ''; } catch (e) {}
  $('noteDialog').style.display = 'flex';
  setTimeout(() => $('noteInput').focus(), 60);
}
function closeNoteDialog() { $('noteDialog').style.display = 'none'; noteTarget = null; }
function noteConfirm() {
  const txt = $('noteInput').value;
  try { mind.execCommand('SET_NODE_NOTE', noteTarget, txt); } catch (e) {}
  closeNoteDialog();
  markDirty();
  setTimeout(() => { try { mind.render(); } catch (e) {} }, 30);
}

function initNodeEdit() {
  // 右键菜单
  try {
    mind.on('node_contextmenu', (a, b) => {
      const node = (a && (a.nodeData || a.getData)) ? a : (b || null);
      const ev = (a && a.clientX !== undefined) ? a : ((b && b.clientX !== undefined) ? b : null);
      try { if (ev && ev.stopPropagation) ev.stopPropagation(); } catch (e2) {}
      ctxNode = node;
      showCtxMenu(ev ? ev.clientX : 0, ev ? ev.clientY : 0);
    });
  } catch (e) {}
  // 左键选中节点 → 显示样式条（仅单选）
  try {
    mind.on('node_click', (node) => {
      setTimeout(() => {
        try {
          const l = mind.renderer.activeNodeList;
          if (l && l.length === 1) { styleNode = node; syncStyleBar(); $('styleBar').style.display = 'flex'; }
          else { $('styleBar').style.display = 'none'; styleNode = null; }
        } catch (e2) {}
      }, 30);
    });
  } catch (e) {}
  // 点击空白 → 隐藏样式条/右键菜单
  try { mind.on('draw_click', () => { $('styleBar').style.display = 'none'; styleNode = null; hideCtxMenu(); }); } catch (e) {}
  // 双击公式节点 → 公式编辑弹窗（并隐藏库默认文本编辑框）
  try {
    mind.on('node_dblclick', (a, b) => {
      try {
        const node = (a && (a.nodeData || a.getData)) ? a : (b || null);
        if (!node) return;
        const d = nodeDataOf(node);
        const dd = (d && d.data) || d || {};
        const t = dd.text || '';
        const latex = dd.mathLatex || '';
        if (latex || t.indexOf('katex') >= 0) {
          const d0 = nodeDataOf(node);
          const preText = ((d0 && d0.data) || d0 || {}).text || '';
          try { if (mind.renderer && mind.renderer.textEdit) mind.renderer.textEdit.hideEditTextBox(); } catch (e2) {}
          openMathDialog(node, true, preText);
        }
      } catch (e2) {}
    });
  } catch (e) {}
  // 右键菜单点击
  $('ctxMenu').addEventListener('click', (e) => {
    const it = e.target.closest('.ctx-item');
    if (!it || it.classList.contains('disabled')) return;
    hideCtxMenu();
    ctxAct(it.dataset.act);
  });
  // 全局点击关闭右键菜单
  document.addEventListener('mousedown', (e) => {
    if (e.target.closest && !e.target.closest('#ctxMenu')) hideCtxMenu();
  });
  // 样式条按钮
  $('styleBar').querySelectorAll('[data-style]').forEach(b => {
    b.addEventListener('click', () => toggleStyle(b.dataset.style));
  });
  $('sbFontPlus').addEventListener('click', () => {
    try { const st = styleNode.getStyle() || {}; setNodeStyle({ fontSize: (st.fontSize || 14) + 2 }); } catch (e) {}
  });
  $('sbFontMinus').addEventListener('click', () => {
    try { const st = styleNode.getStyle() || {}; setNodeStyle({ fontSize: Math.max(8, (st.fontSize || 14) - 2) }); } catch (e) {}
  });
  $('sbColor').addEventListener('input', (e) => { try { setNodeStyle({ color: e.target.value }); } catch (e2) {} });
  $('sbBgColor').addEventListener('input', (e) => { try { setNodeStyle({ background: e.target.value }); } catch (e2) {} });
  $('sbClose').addEventListener('click', () => { $('styleBar').style.display = 'none'; styleNode = null; });
  // 图标弹窗
  $('iconClear').addEventListener('click', () => {
    try { mind.execCommand('SET_NODE_ICON', iconTarget, []); } catch (e) {}
    finishIcon();
  });
  $('iconCancel').addEventListener('click', () => { $('iconDialog').style.display = 'none'; iconTarget = null; });
  // 公式弹窗
  $('mathInput').addEventListener('input', updateMathPreview);
  $('mathTitle').addEventListener('input', updateMathPreview);
  $('mathOk').addEventListener('click', mathConfirm);
  $('mathCancel').addEventListener('click', closeMathDialog);
  // 链接弹窗
  $('linkOk').addEventListener('click', linkConfirm);
  $('linkCancel').addEventListener('click', closeLinkDialog);
  $('linkClear').addEventListener('click', () => {
    try { mind.execCommand('SET_NODE_HYPERLINK', linkTarget, ''); } catch (e) {}
    closeLinkDialog();
    markDirty();
    setTimeout(() => { try { mind.render(); } catch (e) {} }, 30);
  });
  // 备注弹窗
  $('noteOk').addEventListener('click', noteConfirm);
  $('noteCancel').addEventListener('click', closeNoteDialog);
  $('noteClear').addEventListener('click', () => {
    try { mind.execCommand('SET_NODE_NOTE', noteTarget, ''); } catch (e) {}
    closeNoteDialog();
    markDirty();
    setTimeout(() => { try { mind.render(); } catch (e) {} }, 30);
  });
  // Escape 关闭弹窗
  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') {
      if ($('mathDialog').style.display !== 'none') { closeMathDialog(); return; }
      if ($('iconDialog').style.display !== 'none') { $('iconDialog').style.display = 'none'; iconTarget = null; return; }
      if ($('linkDialog').style.display !== 'none') { closeLinkDialog(); return; }
      if ($('noteDialog').style.display !== 'none') { closeNoteDialog(); return; }
    }
  });
}

// ================= UI 事件 =================
function bindUi() {
  $('btnNew').addEventListener('click', newDoc);
  $('btnOpen').addEventListener('click', openWithDialog);
  $('btnSave').addEventListener('click', () => saveNow(false));
  $('btnTheme').addEventListener('click', () => openPanel('themePanel'));

  // 插入图片
  $('btnInsertImage').addEventListener('click', () => {
    $('imgFileInput').click();
  });
  $('btnExpandAll').addEventListener('click', () => {
    try { mind.execCommand('EXPAND_ALL'); setTimeout(() => mind.view.fit(), 100); } catch (e) {}
  });
  $('btnCollapseAll').addEventListener('click', () => {
    try { mind.execCommand('UNEXPAND_ALL'); setTimeout(() => mind.view.fit(), 100); } catch (e) {}
  });

  // 添加附件
  $('btnAttach').addEventListener('click', async () => {
    document.title = 'DBG:点击附件';
    try {
      const r = await callNative('pickAnyFile');
      document.title = 'DBG:返回=' + JSON.stringify(r).slice(0, 100);
      if (!r || !r.ok) return;
      const dataUrl = 'data:application/octet-stream;base64,' + r.b64;
      let parentNode = null;
      try {
        const cn = mind.renderer.currentNode;
        if (cn && cn.nodeData) parentNode = cn.nodeData;
      } catch (err) {}
      if (!parentNode) {
        const d = mind.getData();
        parentNode = d.root || d;
      }
      parentNode.attachmentUrl = dataUrl;
      parentNode.attachmentName = r.name;
      mind.render();
      markDirty();
      document.title = 'DBG:完成';
    } catch (e) {
      document.title = 'DBG:异常=' + e.message;
    }
  });

  // Ctrl+V 粘贴图片/文件
  window.addEventListener('paste', (e) => {
    if (!e.clipboardData) return;
    const items = e.clipboardData.items;
    if (!items) return;
    // 如果正在编辑节点输入框，不拦截
    const tag = document.activeElement && document.activeElement.tagName;
    if (tag === 'INPUT' || tag === 'TEXTAREA' || (document.activeElement && document.activeElement.isContentEditable)) return;
    function getParent() {
      try {
        const cn = mind.renderer.currentNode;
        if (cn && cn.nodeData) return cn.nodeData;
      } catch (err) {}
      const d = mind.getData();
      return d.root || d;
    }
    for (const it of items) {
      if (it.type && it.type.startsWith('image/')) {
        e.preventDefault();
        const blob = it.getAsFile();
        if (!blob) continue;
        const reader = new FileReader();
        reader.onload = (ev) => {
          const p = getParent();
          if (!p) return;
          p.children = p.children || [];
          p.children.push({
            data: { text: '', image: ev.target.result, imageSize: { width: 200, height: 200 } },
            children: []
          });
          mind.render();
          markDirty();
        };
        reader.readAsDataURL(blob);
        return;
      }
      if (it.kind === 'file') {
        e.preventDefault();
        const blob = it.getAsFile();
        if (!blob) continue;
        const reader = new FileReader();
        reader.onload = (ev) => {
          const p = getParent();
          if (!p) return;
          p.attachmentUrl = ev.target.result;
          p.attachmentName = blob.name || '粘贴附件';
          mind.render();
          markDirty();
        };
        reader.readAsDataURL(blob);
        return;
      }
    }
  }, true);

  // 双击播放附件
  setTimeout(() => {
    if (mind && mind.on) {
      mind.on('node_dblclick', (node) => {
        try {
          const d = node.nodeData || node;
          const img = d.image || (d.data && d.data.image);
          if (img) {
            $('imgViewerImg').src = img;
            $('imgViewer').style.display = 'flex';
            return;
          }
          const attUrl = d.attachmentUrl || (d.data && d.data.attachmentUrl);
          const attName = d.attachmentName || (d.data && d.data.attachmentName) || '';
          if (attUrl) {
            const mv = $('mediaViewer');
            const v = $('mediaVideo'), a = $('mediaAudio');
            $('mediaFileName').textContent = attName;
            const ext = attName.split('.').pop().toLowerCase();
            if (['mp4','webm','ogg','mov'].includes(ext)) {
              v.src = attUrl; v.style.display = 'block'; a.style.display = 'none';
            } else if (['mp3','wav','m4a','flac'].includes(ext)) {
              a.src = attUrl; a.style.display = 'block'; v.style.display = 'none';
            } else {
              const a2 = document.createElement('a');
              a2.href = attUrl; a2.download = attName;
              a2.click(); return;
            }
            mv.style.display = 'flex';
          }
        } catch (e) {}
      });
    }
  }, 1000);
  $('mediaClose').addEventListener('click', (e) => {
    e.stopPropagation();
    $('mediaVideo').pause(); $('mediaAudio').pause();
    $('mediaViewer').style.display = 'none';
  });
  $('mediaViewer').addEventListener('click', () => {
    $('mediaVideo').pause(); $('mediaAudio').pause();
    $('mediaViewer').style.display = 'none';
  });
  $('imgFileInput').addEventListener('change', (e) => {
    const file = e.target.files[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = (ev) => {
      const dataUrl = ev.target.result;
      const img = new Image();
      img.onload = () => {
        const w = Math.min(img.width, 280);
        const h = img.height * (w / img.width);
        // 直接往当前选中节点的 children 里加
        let parentNode = null;
        try {
          const cn = mind.renderer.currentNode;
          if (cn && cn.nodeData) parentNode = cn.nodeData;
        } catch (err) {}
        if (!parentNode) {
          const d = mind.getData();
          parentNode = d.root;
        }
        if (!parentNode.children) parentNode.children = [];
        parentNode.children.push({
          data: { text: '', image: dataUrl, imageSize: { width: w, height: h } },
          children: []
        });
        mind.render();
        markDirty();
      };
      img.src = dataUrl;
    };
    reader.readAsDataURL(file);
    e.target.value = '';
  });

  // 双击图片节点全屏查看（mind 可能还没创建，延迟绑定）
  setTimeout(() => {
    if (mind && mind.on) {
      mind.on('node_dblclick', (node) => {
        try {
          const d = node.nodeData || node;
          const img = d.image || (d.data && d.data.image);
          if (img) {
            $('imgViewerImg').src = img;
            $('imgViewer').style.display = 'flex';
          }
        } catch (e) {}
      });
    }
  }, 1000);
  $('imgViewer').addEventListener('click', () => { $('imgViewer').style.display = 'none'; });
  $('imgViewerClose').addEventListener('click', (e) => { e.stopPropagation(); $('imgViewer').style.display = 'none'; });
  $('btnHistory').addEventListener('click', () => openPanel('historyPanel'));
  $('btnSettings').addEventListener('click', () => openPanel('settingsPanel'));
  $('overlay').addEventListener('click', closeAllPanels);
  document.querySelectorAll('.panel-close').forEach(b => b.addEventListener('click', () => closePanel(b.dataset.close)));
  $('btnClearHistory').addEventListener('click', clearHistory);
  $('btnPickHistoryDir').addEventListener('click', pickHistoryDir);
  $('btnRegAssoc').addEventListener('click', async () => {
    const r = await callNative('registerAssoc');
    if (r && r.ok) { toast('文件关联已注册'); fillSettings(); }
  });
  $('btnSaveSettings').addEventListener('click', saveSettingsFromUi);
  // 工具栏收起/展开
  $('btnToggleBar').addEventListener('click', () => {
    $('topBar').style.display = 'none';
    $('btnExpandBar').style.display = 'inline-flex';
  });
  $('btnExpandBar').addEventListener('click', () => {
    $('btnExpandBar').style.display = 'none';
    $('topBar').style.display = 'flex';
  });
  // 多标签 + 布局 + 缩放
  $('btnTabAdd').addEventListener('click', newTab);
  $('tabScrollLeft').addEventListener('click', () => { $('tabStrip').scrollBy({ left: -120, behavior: 'smooth' }); });
  $('tabScrollRight').addEventListener('click', () => { $('tabStrip').scrollBy({ left: 120, behavior: 'smooth' }); });
  initLayoutDrop();
  $('btnZoomIn').addEventListener('click', () => { try { mind.view.zoomIn(); } catch (e) {} setTimeout(updateZoom, 50); });
  $('btnZoomOut').addEventListener('click', () => { try { mind.view.zoomOut(); } catch (e) {} setTimeout(updateZoom, 50); });
  $('btnFit').addEventListener('click', () => { try { mind.view.fit(); } catch (e) {} setTimeout(updateZoom, 50); });
  // 导出下拉
  $('btnExport').addEventListener('click', (e) => {
    e.stopPropagation();
    $('exportMenu').classList.toggle('open');
  });
  document.addEventListener('click', () => $('exportMenu').classList.remove('open'));
  document.querySelectorAll('#exportMenuItems a').forEach(a => {
    a.addEventListener('click', () => { doExport(a.dataset.type); $('exportMenu').classList.remove('open'); });
  });
  // 快捷键
  document.addEventListener('keydown', (e) => {
    const c = e.ctrlKey || e.metaKey;
    if (c && !e.shiftKey && !e.altKey) {
      const k = e.key.toLowerCase();
      if (k === 's') { e.preventDefault(); saveNow(false); }
      else if (k === 'o') { e.preventDefault(); openWithDialog(); }
      else if (k === 'n') { e.preventDefault(); newDoc(); }
      else if (k === 'h') { e.preventDefault(); openPanel('historyPanel'); }
      else if (k === ',') { e.preventDefault(); openPanel('settingsPanel'); }
    }
    if (e.key === 'Escape') closeAllPanels();
  });
}

// ================= 启动 =================
async function boot() {
  try {
    bindUi();
    // 应用设置（默认主题）
    const s = await callNative('getSettings');
    if (s && s.ok && s.defaultTheme && THEMES.some(t => t.name === s.defaultTheme)) {
      userDefaultTheme = s.defaultTheme;
      doc.theme = userDefaultTheme;
    }
    initMindMap();
    initNodeEdit();
    // 创建初始标签页
    tabs.push({ name: '未命名导图', path: '', data: defaultData().root, theme: doc.theme, layout: doc.layout });
    activeTab = 0;
    renderTabStrip();
    syncLayoutSelect();
    // 打开启动文件（双击 .usmind 传入 或 上次文件）
    const st = await callNative('getStartupFile');
    const startupPath = st && st.ok && st.path ? st.path : '';
    if (startupPath) {
      await openPath(startupPath);
    } else {
      booting = false;
      updateTitle();
    }
  } catch (e) {
    showErr('启动失败: ' + (e && e.message ? e.message : String(e)) + '\n' + (e && e.stack ? e.stack.split('\n').slice(0, 3).join('\n') : ''));
  }
}

boot();
