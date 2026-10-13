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
function showRenderLoading() {
  const el = document.getElementById('renderLoading');
  if (el) el.classList.add('show');
}
function hideRenderLoading() {
  const el = document.getElementById('renderLoading');
  if (el) el.classList.remove('show');
}
window.addEventListener('error', (e) => showErr('脚本错误: ' + (e.message || String(e.error || '')) + '\n' + (e.filename || '')));
window.addEventListener('unhandledrejection', (e) => showErr('未处理异常: ' + (e.reason && e.reason.message ? e.reason.message : String(e.reason))));
// 全局禁止浏览器原生右键菜单（任何地方都不出现原生菜单，应用内自定义菜单不受影响）
document.addEventListener('contextmenu', (e) => e.preventDefault());

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
const DEFAULT_THEME = '晨光熹微';
// 内置主题旧名 → 新诗意名映射（兼容已保存导图/自定义主题引用的旧名）
const LEGACY_THEME_NAMES = { '晨曦':'晨光熹微', '彩虹':'虹霓织锦', '活力':'春山新雨', '舞动':'蝶舞翩跹', '代码':'墨韵星轨', '和风':'和风细雨', '岛屿':'碧海岛屿', '玫瑰':'玫瑰晨露', '薄荷':'薄荷微凉', '晨曦白':'月白清风', '深空黑':'星河永夜', '极简白':'素雪留白', '暮色紫':'暮色烟霞', '森林绿':'森林秘境', '暖阳橙':'暖阳初照', '黑曜石':'墨玉流辉' };
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
let switchingTab = false;   // 切换渲染中，防止连点重复切换

// 深拷贝导图数据：跳过 parent/_parent 等循环引用字段，seen 集合兜底防任意循环（如栈溢出导致快照失败、切换无效）
function cloneMindData(node, seen) {
  if (!node || typeof node !== 'object') return node;
  if (Array.isArray(node)) return node.map(n => cloneMindData(n, seen));
  if (seen.has(node)) return seen.get(node);
  const out = {};
  seen.set(node, out);
  for (const k in node) {
    if (k === 'parent' || k === '_parent' || k === 'owner') continue;
    const v = node[k];
    if (v && typeof v === 'object') {
      out[k] = Array.isArray(v) ? v.map(n => cloneMindData(n, seen)) : cloneMindData(v, seen);
    } else out[k] = v;
  }
  return out;
}
function snapshotActiveTab() {
  if (activeTab < 0 || !mind) return;
  try {
    // 深拷贝快照；统一为"直接根节点"格式（库 setData 期望 {data,children}，不接受 {root:...}）
    const gd = mind.getData();
    tabs[activeTab].data = gd && gd.root ? cloneMindData(gd.root, new Map()) : cloneMindData(gd, new Map());
    tabs[activeTab].name = doc.name;
    tabs[activeTab].path = doc.path;
    tabs[activeTab].theme = doc.theme;
    tabs[activeTab].layout = doc.layout;
  } catch (e) { console.error('快照失败', e); }
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
    // 活跃标签实时取画布根节点文字（t.data 可能是旧快照，避免节点改名后标签不跟随）
    let labelText = rootText(t);
    if (i === activeTab && mind) {
      try {
        const rd = mind.getData();
        if (rd && rd.root && rd.root.data && rd.root.data.text) labelText = stripHtml(rd.root.data.text) || labelText;
      } catch (e) {}
    }
    const el = document.createElement('div');
    el.className = 'tab-item' + (i === activeTab ? ' active' : '');
    // el.title = labelText; // 不再显示 tag hover 原生提示
    // 标签可按住拖动排序
    el.draggable = true;
    el.dataset.tabIndex = String(i);
    el.addEventListener('dragstart', (e) => {
      el.classList.add('dragging');
      e.dataTransfer.effectAllowed = 'move';
      e.dataTransfer.setData('text/plain', String(i));
    });
    el.addEventListener('dragover', (e) => { e.preventDefault(); e.dataTransfer.dropEffect = 'move'; });
    el.addEventListener('drop', (e) => {
      e.preventDefault();
      e.stopPropagation();
      const from = parseInt(e.dataTransfer.getData('text/plain') || '-1');
      el.classList.remove('dragging');
      if (from >= 0 && from < tabs.length && from !== i) moveTab(from, i);
    });
    el.addEventListener('dragend', () => el.classList.remove('dragging'));

    const label = document.createElement('span');
    label.className = 'tab-label';
    label.textContent = labelText + (t.dirty ? ' ●' : '');
    el.appendChild(label);

    const close = document.createElement('span');
    close.className = 'tab-close';
    close.textContent = '×';
    // close.title = '关闭画布'; // 不再显示关闭按钮 hover 提示
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
              const rootN = rd && rd.root ? rd.root : rd;
              if (rootN && rootN.data) { rootN.data.text = newName.trim(); mind.setData(cloneMindData(rootN, new Map())); mind.render(); }
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
// 标签拖拽排序（不重载画布数据）
function moveTab(from, to) {
  const t = tabs.splice(from, 1)[0];
  tabs.splice(to, 0, t);
  if (from === activeTab) activeTab = to;
  else if (from < activeTab && to >= activeTab) activeTab--;
  else if (from > activeTab && to <= activeTab) activeTab++;
  renderTabStrip();
}
function switchToTab(i) {
  if (i === activeTab || switchingTab) return;
  snapshotActiveTab();
  activeTab = i;
  const t = tabs[i];
  suppressDirtyUntil = Date.now() + 2000;
  doc.path = t.path || '';
  doc.name = t.name || '未命名导图';
  doc.theme = t.theme || userDefaultTheme;
  doc.layout = t.layout || 'logicalStructure';
  // 统一直接根节点格式（旧快照可能是 {root:...}，库 setData 不接受）
  // 直接用快照引用（快照已是深拷贝副本），省去大导图重复克隆造成的切换卡顿
  const dd = t.data && t.data.root ? t.data.root : t.data;
  // 先显示渲染 loading，再异步渲染（避免同步渲染阻塞导致 loading 来不及显示）
  showRenderLoading();
  switchingTab = true;
  setTimeout(() => {
    try { mind.setData(dd); } catch (e) { showErr('切换失败 setData: ' + e.message); }
    try { mind.setLayout(doc.layout); } catch (e) {}
    try { mind.setTheme(doc.theme); } catch (e) {}
    try { mind.render(); } catch (e) { showErr('切换失败 render: ' + e.message); }
    hideRenderLoading();
    switchingTab = false;
    setTimeout(() => { try { mind.view.fit(); } catch (e) {} }, 60);
    dirty = false;
    updateTitle();
    updateStatus();
    renderTabStrip();
    syncLayoutSelect();
  }, 40);
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
  // 异步渲染（与切换导图同一套逻辑）：启动早期同步 setData/render 可能因库未稳定而失败，
  // 导致双击打开后画布空白；延迟一拍让库稳定、loading 先显示再渲染，
  // 渲染后再做一次"画布为空则强制重渲染"的确认，保证打开即有内容
  showRenderLoading();
  setTimeout(() => {
    try { mind.setData(t.data); } catch (e) { showErr('打开失败 setData: ' + e.message); }
    try { mind.setLayout(doc.layout); } catch (e) {}
    try { mind.setTheme(doc.theme); } catch (e) {}
    try { mind.render(); } catch (e) { showErr('打开失败 render: ' + e.message); }
    hideRenderLoading();
    setTimeout(() => { try { mind.view.fit(); } catch (e) {} }, 60);
    setTimeout(() => {
      try {
        const tree = mind.renderer.renderTree;
        const hasNode = !!(tree && (tree._node || tree.node || tree.shapeNode));
        if (!hasNode) {
          try { mind.render(); } catch (e) {}
          setTimeout(() => { try { mind.view.fit(); } catch (e) {} }, 60);
        }
        // 图片节点布局居中修复：库对含图片的富文本节点测量偏小，图片加载后
        // 子节点组中心会相对父节点偏移（上下不对齐）；图片加载完成的此刻强制重排一次恢复垂直居中
        try { mind.setLayout(doc.layout || 'logicalStructure'); } catch (eL) {}
      } catch (e) {}
    }, 350);
  }, 40);
  dirty = false;
  updateTitle(); updateStatus(); renderTabStrip(); syncLayoutSelect();
}
// 恢复多标签文件（JSON multi-tabs 导出）
function restoreAllTabs(tabArr) {
  if (!tabArr || !tabArr.length) { toast('文件没有标签数据', 'error'); return; }
  const valid = tabArr.filter(t => t && t.data);
  if (!valid.length) { toast('文件标签数据无效', 'error'); return; }
  tabs = valid.map(t => ({
    name: t.name || '未命名导图', path: '',
    theme: t.theme || userDefaultTheme, layout: t.layout || 'logicalStructure',
    data: t.data && t.data.root ? t.data.root : t.data,
  }));
  activeTab = 0;
  const t = tabs[0];
  doc.path = ''; doc.name = t.name; doc.theme = t.theme; doc.layout = t.layout;
  suppressDirtyUntil = Date.now() + 2000;
  // 异步渲染（与 openInTab/切换一致）：启动早期同步渲染可能失败导致画布空白
  showRenderLoading();
  setTimeout(() => {
    try { mind.setData(t.data); } catch (e) { console.error('setData 失败', e); }
    try { mind.setLayout(t.layout); } catch (e) {}
    try { mind.setTheme(t.theme); } catch (e) {}
    try { mind.render(); } catch (e) { console.error('render 失败', e); }
    hideRenderLoading();
    setTimeout(() => { try { mind.view.fit(); } catch (e) {} }, 60);
    setTimeout(() => {
      try {
        const tree = mind.renderer.renderTree;
        const hasNode = !!(tree && (tree._node || tree.node || tree.shapeNode));
        if (!hasNode) { try { mind.render(); } catch (e) {} }
        // 图片节点布局居中修复（同上）：图片加载后强制重排一次，子节点组与父节点垂直居中
        try { mind.setLayout(t.layout || 'logicalStructure'); } catch (eL) {}
      } catch (e) {}
    }, 350);
  }, 40);
  dirty = false; booting = false;
  updateTitle(); updateStatus(); renderTabStrip(); syncLayoutSelect();
}

function themeBy(name) {
  return THEMES.find(t => t.name === name) || THEMES.find(t => t.name === (LEGACY_THEME_NAMES[name] || '')) || THEMES[0];
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
  const dt = $('docTitle'); if (dt) { dt.textContent = name; dt.setAttribute('data-tip', doc.path || name); }
  const st = $('saveState');
  if (st) {
    if (dirty) { st.textContent = '● 未保存'; st.className = 'save-state saving'; }
    else if (doc.path) { st.textContent = '已保存'; st.className = 'save-state saved'; }
    else { st.textContent = ''; st.className = 'save-state'; }  }
}
// 双击标题改名（改后即文件名）
function renameDocTitle() {
  const dt = $('docTitle');
  if (!dt || dt.querySelector('input')) return;
  const inp = document.createElement('input');
  inp.type = 'text'; inp.className = 'doc-title-input';
  inp.value = doc.name || '未命名导图';
  dt.replaceWith(inp);
  inp.focus(); inp.select();
  let done = false;
  const finish = (save) => {
    if (done) return; done = true;
    if (save) {
      const v = inp.value.trim();
      const nm = v || '未命名导图';
      doc.name = nm;
      if (tabs[activeTab]) tabs[activeTab].name = nm;
      markDirty();
    }
    const sp = document.createElement('span');
    sp.id = 'docTitle'; sp.className = 'doc-title';
    inp.replaceWith(sp);
    updateTitle(); renderTabStrip();
    callNative('notifyDocumentState', doc.name);
  };
  inp.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') { e.preventDefault(); finish(true); }
    else if (e.key === 'Escape') { finish(false); }
  });
  inp.addEventListener('blur', () => finish(true));
}
document.addEventListener('dblclick', (e) => {
  if (e.target && e.target.id === 'docTitle') renameDocTitle();
});
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
  try { $('zoomLevel').textContent = Math.round((mind.view.scale || 1) * 100) + '%'; } catch (e) {}
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
  const sec = localSetting('autoSaveSec', 3);
  autoSaveTimer = setTimeout(() => {
    if (!dirty) return;
    if (!localSetting('autoSave', true)) return;
    saveNow(true); // 有路径覆盖原文件；新文档静默存到项目目录（同名才弹框）
  }, Math.max(1, parseInt(sec) || 3) * 1000);
}

// ================= 文档 =================
function defaultData() {
  return {
    root: {
      data: { text: '中心主题' },
      children: [
        { data: { text: '分支主题' }, children: [] },
        { data: { text: '分支主题' }, children: [] },
        { data: { text: '分支主题' }, children: [] },
      ],
    },
  };
}

function serializeDoc() {
  const root = mind.getData();
  // 保存前把渲染用 blob: src 还原为 dataUrl（保证 .usmind 自包含），序列化后恢复 blob 保持内存流畅
  restoreTreeMediaSrc(root);
  let json;
  try {
    json = JSON.stringify({
      format: 'usmind',
      version: 1,
      name: doc.name,
      theme: doc.theme,
      layout: doc.layout,
      savedAt: new Date().toISOString(),
      data: { root },
    }, null, 2);
  } finally {
    patchTreeMediaSrc(root);
  }
  return json;
}

// 解析 .usmind / 兼容裸引擎 JSON
function parseDoc(text) {
  let obj;
  try { obj = JSON.parse(text); } catch (e) { throw new Error('文件不是有效的 JSON'); }
  // 多标签导出格式：{app:'usmind', type:'multi-tabs', tabs:[...]}
  if (obj && obj.app === 'usmind' && obj.type === 'multi-tabs' && Array.isArray(obj.tabs)) {
    return { multiTabs: obj.tabs };
  }
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
  // 修复旧数据：画布原点(0,0)的非根节点删除位置字段，让库重新布局到父旁（修复早期插入偏移的附件）
  try {
    const fix = (n, isRoot) => {
      if (!n || !n.data) return;
      if (!isRoot && n.data.x === 0 && n.data.y === 0) { delete n.data.x; delete n.data.y; }
      (n.children || []).forEach(c => fix(c, false));
    };
    fix(parsed.root, true);
  } catch (eF) {}
  // 媒体 src 优化：打开文件后把 dataUrl 换成 blob objectURL，渲染不再反复解码大文件
  try { patchTreeMediaSrc(parsed.root); } catch (eP) {}
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
  // 优先 v3 自包含格式（媒体自动落盘附件目录，引用无需改写）
  const r3 = await callNative('openSelfContained', path);
  if (r3 && r3.ok && r3.manifest) {
    try {
      const mf = JSON.parse(r3.manifest);
      if (mf && Array.isArray(mf.tabs) && mf.tabs.length) {
        restoreAllTabs(mf.tabs);
        tabs[activeTab].path = path;
        doc.path = path;
        doc.name = mf.name || r3.name || doc.name;
        booting = false;
        updateTitle();
        toast('已打开：' + doc.name);
        callNative('notifyDocumentState', doc.name);
        callNative('saveSettings', JSON.stringify({ lastFile: path }));
        return;
      }
    } catch (e) { toast('打开失败：' + e.message, 'error'); return; }
  }
  const r = await callNative('openPath', path);
  if (r && r.ok && (r.data || r.b64)) {
    try {
      if (r.isZip) { await importSelfContained(r.b64, r.path || path, r.name); return; }
      const parsed = parseDoc(r.data);
      if (parsed.multiTabs) {
        restoreAllTabs(parsed.multiTabs);
        tabs[activeTab].path = r.path || path;
        doc.path = tabs[activeTab].path;
        doc.name = r.name || parsed.name || doc.name;
        booting = false;
        updateTitle();
        toast('已恢复 ' + parsed.multiTabs.length + ' 个标签');
        callNative('saveSettings', JSON.stringify({ lastFile: doc.path }));
        return;
      }
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
  if (r && r.ok && r.path) {
    // 优先 v3 自包含格式
    const r3 = await callNative('openSelfContained', r.path);
    if (r3 && r3.ok && r3.manifest) {
      try {
        const mf = JSON.parse(r3.manifest);
        if (mf && Array.isArray(mf.tabs) && mf.tabs.length) {
          restoreAllTabs(mf.tabs);
          tabs[activeTab].path = r.path;
          doc.path = r.path;
          doc.name = mf.name || r3.name || doc.name;
          booting = false;
          updateTitle();
          toast('已打开：' + doc.name);
          callNative('notifyDocumentState', doc.name);
          callNative('saveSettings', JSON.stringify({ lastFile: r.path }));
          return;
        }
      } catch (e) { toast('打开失败：' + e.message, 'error'); return; }
    }
  }
  if (r && r.ok && (r.data || r.b64)) {
    try {
      if (r.isZip) { await importSelfContained(r.b64, r.path, r.name); return; }
      const parsed = parseDoc(r.data);
      if (parsed.multiTabs) {
        restoreAllTabs(parsed.multiTabs);
        tabs[activeTab].path = r.path;
        doc.path = r.path;
        doc.name = r.name || parsed.name || doc.name;
        booting = false;
        updateTitle();
        toast('已恢复 ' + parsed.multiTabs.length + ' 个标签');
        callNative('saveSettings', JSON.stringify({ lastFile: r.path }));
        return;
      }
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
    defaultInsertSecondLevelNodeText: '分支主题',
    defaultInsertBelowSecondLevelNodeText: '分支主题',
    useLeftKeySelectionRightKeyDrag: false,
    isLimitMindMapInCanvas: false,
    isLimitMindMapInCanvasWhenHasScrollbar: false,
    enableShortcutOnlyWhenMouseInSvg: false,
    openRealtimeRenderOnNodeTextEdit: true,
    // 附件节点（文本附件/图片/音视频等）不进入库的文本编辑流程：
    // 双击附件时我们走自己的预览/编辑弹窗；若让库启动文本编辑，编辑框（textEditNode）
    // 会残留在画布原点（附件文字"左移"），且关闭时库 SET_NODE_TEXT 写回会破坏
    // richText 节点的 HTML（图片消失）。返回 false 从根源阻止。
    beforeTextEdit: (node) => {
      try {
        if (!node) return true;
        const dd = node.getData ? (node.getData() || {}) : {};
        const hasAtt = !!(dd.attachmentUrl || dd.attachUrl || dd.attachmentName);
        if (hasAtt) return false;
        // 公式节点不进入库的文本编辑（richText 序列化会把 text 实体化累积，双击公式走公式弹窗）
        if (dd.mathLatex || /katex|mjx/.test(dd.text || '')) return false;
      } catch (e) {}
      return true;
    },
    attachmentIcon: { style: { size: 0 }, icon: '' },
    rainbowLinesConfig: { open: true, colorsList: branchColorsOf(doc.theme) },
    iconList: [
      { type: 'number', list: Array.from({length: 10}, (_, i) => ({ name: String(i + 1), icon: numSvg(i + 1) })) },
      { type: 'emoji', list: FALLBACK_EMOJI.map(e => ({ name: e, icon: emojiSvg(e) })).concat([{ name: '📎', icon: emojiSvg('📎') }]) },
    ],
  });

  // 窗口大小变化时重绘画布
  window.addEventListener('resize', () => {
    try { mind.resize(); } catch (e) {}
  });

  mind.on('data_change', () => {
    markDirty();
    renderTabStrip();
    scheduleBranchRainbow();
  });
  // 轮询刷新活跃标签文字（内联编辑时 data_change 可能不触发）：
  // 只更新文字不重建 DOM，避免 100ms 全量重建导致点击事件丢失（需多次点击才选中）
  setInterval(() => {
    try {
      const act = document.querySelector('.tab-item.active .tab-label');
      if (!act || !mind) return;
      const rd = mind.getData();
      const rootN = rd && rd.root ? rd.root : rd;
      const txt = rootN && rootN.data && rootN.data.text ? stripHtml(rootN.data.text) : '';
      if (!txt) return;
      const dirtyMark = tabs[activeTab] && tabs[activeTab].dirty ? ' ●' : '';
      if (act.textContent !== txt + dirtyMark) act.textContent = txt + dirtyMark;
    } catch (e) {}
  }, 200);
  // 双击编辑框飘到左上角修复：节点未完成布局时库用 (0,0) 定位输入框，
  // 发现 left/top 为 0 即调用库自带 updateTextEditNode 按真实屏幕坐标刷新（不打断编辑）
  setInterval(() => {
    try {
      const te = mind && mind.renderer && mind.renderer.textEdit;
      if (!te || !te.showTextEdit || !te.currentNode || !te.textEditNode) return;
      if (parseFloat(te.textEditNode.style.left) === 0 || parseFloat(te.textEditNode.style.top) === 0) {
        if (te.updateTextEditNode) te.updateTextEditNode();
      }
    } catch (e) {}
  }, 200);
  // 监听节点编辑结束
  try { mind.on('node_text_change', () => renderTabStrip()); } catch (e) {}
  try { mind.on('node_mouseleave', () => renderTabStrip()); } catch (e) {}
  mind.on('scale', () => updateZoom());
  window.addEventListener('resize', () => { try { applyCanvasBg(); } catch (e) {} });
  mind.on('node_tree_render_end', () => { updateStatus(); renderTabStrip(); try { fixAttachNodeTexts(); } catch (e) {} scheduleMathInject(); });
  // Ctrl+滚轮缩放后也刷新数字（scale 事件可能不触发）
  $('mindMapContainer').addEventListener('wheel', () => { setTimeout(updateZoom, 80); }, { passive: true });
  updateStatus();
  setTimeout(() => { try { applyBranchRainbow(); applyCanvasBg(); } catch (e) {} }, 600);
  updateZoom();
}

// ================= 主题 =================
function applyTheme(name) {
  const t = themeBy(name);
  if (!t) return;
  doc.theme = t.name;
  try { mind.setTheme(t.name); } catch (e) {}
  try { mind.setRainbowLinesConfig && mind.setRainbowLinesConfig({ open: true, colorsList: branchColorsOf(t.name) }); } catch (e) {}
  scheduleBranchRainbow();
  applyCanvasBg();
  dirty = true;
  updateTitle();
  toast('已切换主题：' + t.name);
}

// ===== 彩虹分支节点配色（移植自原项目 Editor.vue）=====
let rainbowIdx = 0;
function hexRgb(hex) {
  if (!hex || typeof hex !== 'string') return [0, 0, 0];
  const h = hex.replace('#', '');
  return [parseInt(h.slice(0, 2), 16), parseInt(h.slice(2, 4), 16), parseInt(h.slice(4, 6), 16)];
}
function mix(hex1, hex2, ratio) {
  const a = hexRgb(hex1), b = hexRgb(hex2);
  const c = a.map((v, i) => Math.round(v * ratio + b[i] * (1 - ratio)));
  return '#' + c.map(v => v.toString(16).padStart(2, '0')).join('');
}
function isDarkColor(hex) {
  if (!hex || typeof hex !== 'string') return false;
  const [r, g, b] = hexRgb(hex);
  return (r * 299 + g * 587 + b * 114) / 1000 < 150;
}
function getBranchColors() {
  const colors = branchColorsOf(doc.theme);
  return colors.length ? colors : FALLBACK_COLORS;
}
function applyBranchTo(node, depth, branchColor) {
  if (!node || !node.data) return;
  if (depth === 0) {
    delete node.data.fillColor; delete node.data.color; delete node.data.borderColor; delete node.data.lineColor; delete node.data.borderWidth;
    (node.children || []).forEach(c => applyBranchTo(c, 1, null));
  } else if (depth === 1) {
    const colors = getBranchColors();
    const c = colors[rainbowIdx++ % colors.length];
    node.data.fillColor = c;
    node.data.color = isDarkColor(c) ? '#ffffff' : '#1a1a1a';
    node.data.borderColor = c;
    node.data.borderWidth = 1;
    node.data.lineColor = c;
    (node.children || []).forEach(cn => applyBranchTo(cn, 2, c));
  } else {
    node.data.fillColor = mix(branchColor, '#ffffff', 0.14);
    node.data.color = isDarkColor(branchColor) ? '#475569' : '#334155';
    node.data.borderColor = mix(branchColor, '#ffffff', 0.38);
    node.data.borderWidth = 1;
    node.data.lineColor = branchColor;
    (node.children || []).forEach(cn => applyBranchTo(cn, depth + 1, branchColor));
  }
}
function applyBranchRainbow() {
  if (!mind || !mind.renderer || !mind.renderer.renderTree) return;
  rainbowIdx = 0;
  applyBranchTo(mind.renderer.renderTree, 0, null);
  mind.render(() => {});
}
let rainbowTimer = null;
function scheduleBranchRainbow() {
  if (rainbowTimer) clearTimeout(rainbowTimer);
  rainbowTimer = setTimeout(() => applyBranchRainbow(), 350);
}

// 背景视频（引擎不支持，前端自渲染，同原项目）
function applyCanvasBg() {
  const mc = $('mindMapContainer');
  if (mc) mc.style.background = 'transparent';
  const colorEl = $('canvasBgColor');
  const imgEl = $('canvasBgImage');
  const video = $('canvasBgVideo');
  const t = themeBy(doc.theme);
  const cfg = (t && t.config) || {};
  const bgColor = cfg.backgroundColor || '#fafbff';
  const img = (cfg.backgroundImage && cfg.backgroundImage !== 'none') ? cfg.backgroundImage : '';
  const vid = (cfg.backgroundVideo && cfg.backgroundVideo !== 'none') ? cfg.backgroundVideo : '';
  const op = (cfg.backgroundOpacity != null) ? cfg.backgroundOpacity : 1;
  const zoom = (cfg.backgroundZoom != null) ? cfg.backgroundZoom : 1;
  if (colorEl) colorEl.style.background = bgColor;
  if (vid) {
    let src = vid;
    if (vid.indexOf('attachments/') === 0) { try { src = resolveMediaUrl(vid); } catch (e) {} }
    if (video) {
      video.src = src;
      video.style.display = 'block';
      video.style.opacity = op;
      video.style.transform = 'scale(' + zoom + ')';
      try { video.play().catch(() => {}); } catch (e) {}
    }
    if (imgEl) imgEl.style.display = 'none';
  } else if (img) {
    let src = img;
    if (img.indexOf('attachments/') === 0) { try { src = resolveMediaUrl(img); } catch (e) {} }
    if (imgEl) {
      imgEl.style.backgroundImage = "url('" + String(src).replace(/'/g, '%27') + "')";
      imgEl.style.display = 'block';
      imgEl.style.opacity = op;
      imgEl.style.transform = 'scale(' + zoom + ')';
    }
    if (video) { video.style.display = 'none'; try { video.pause(); } catch (e) {} video.removeAttribute('src'); }
  } else {
    if (imgEl) imgEl.style.display = 'none';
    if (video) { video.style.display = 'none'; try { video.pause(); } catch (e) {} video.removeAttribute('src'); }
  }
}

function buildThemeGrid() {
  const grid = $('themeGrid');
  grid.innerHTML = '';
  grid.className = 'panel-body theme-grid';
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

// ================= 设置 =================
// 主题管理绑定（页签 + 编辑弹窗）
document.querySelectorAll('.set-tab').forEach(b => b.addEventListener('click', () => {
  document.querySelectorAll('.set-tab').forEach(x => x.classList.remove('active'));
  b.classList.add('active');
  const g = b.dataset.stab === 'themes';
  $('setTabGeneral').style.display = g ? 'none' : '';
  $('setTabThemes').style.display = g ? '' : 'none';
  if (g) renderThemesList();
}));
const _ba = $('btnAddTheme'); if (_ba) _ba.addEventListener('click', () => openThemeEdit(''));
const _ts = $('teSave'); if (_ts) _ts.addEventListener('click', saveThemeFromDialog);
const _tc = $('teCancel'); if (_tc) _tc.addEventListener('click', () => $('themeEditDialog').classList.remove('open'));
const _ta = $('teAddColor'); if (_ta) _ta.addEventListener('click', () => { const arr = currentTeColors(); arr.push('#4a6cf7'); renderTeColors(arr); updateTePreview(); });
// 背景图片选择（压缩为 dataURL 存储）
const _bi = $('teBgImageBtn');
if (_bi) _bi.addEventListener('click', () => $('teBgImageFile').click());
const _bif = $('teBgImageFile');
if (_bif) _bif.addEventListener('change', () => {
  const f = _bif.files && _bif.files[0];
  if (!f) return;
  const rd = new FileReader();
  rd.onload = () => {
    const img = new Image();
    img.onload = () => {
      let w = img.width, h = img.height;
      const mx = 1600;
      if (w > mx || h > mx) { const r = Math.min(mx / w, mx / h); w = Math.round(w * r); h = Math.round(h * r); }
      const cv = document.createElement('canvas'); cv.width = w; cv.height = h;
      cv.getContext('2d').drawImage(img, 0, 0, w, h);
      const data = cv.toDataURL('image/jpeg', 0.85);
      $('teBgImage').dataset.data = data;
      $('teBgImage').value = '已选择：' + f.name;
      updateTePreview();
      toast('背景图已选择');
    };
    img.onerror = () => toast('图片读取失败', 'error');
    img.src = rd.result;
  };
  rd.readAsDataURL(f);
});
// 背景视频选择：exe 走附件目录（media.local 流式），测试页走 blob 预览
const _bv = $('teBgVideoBtn');
if (_bv) _bv.addEventListener('click', () => $('teBgVideoFile').click());
const _bvf = $('teBgVideoFile');
if (_bvf) _bvf.addEventListener('change', async () => {
  const f = _bvf.files && _bvf.files[0];
  if (!f) return;
  let stored = '';
  if (hasNative) {
    stored = await storeFileExternal(f);
  } else {
    try { stored = URL.createObjectURL(f); } catch (e) { stored = ''; }
  }
  $('teBgVideo').dataset.data = stored || f.name;
  $('teBgVideo').value = '已选择：' + f.name;
  updateTePreview();
  _bvf.value = '';
});
// 面板滚动：拦截滚轮冒泡，防止被导图缩放逻辑抢走
document.querySelectorAll('.panel-body').forEach(pb => {
  pb.addEventListener('wheel', e => { e.stopPropagation(); }, { passive: false });
});
loadCustomThemes();

async function fillSettings() {
  loadCustomThemes();
  const r = await callNative('getSettings');
  const s = (r && r.ok) ? r : {};
  // 默认主题（自定义下拉）
  const themeMenu = $('themeMenu');
  themeMenu.innerHTML = '';
  const curTheme = s.defaultTheme || DEFAULT_THEME;
  const curThemeName = THEMES.some(t => t.name === curTheme) ? curTheme : (LEGACY_THEME_NAMES[curTheme] || curTheme);
  $('themeBtn').textContent = curThemeName + ' ▾';
  THEMES.forEach(t => {
    const it = document.createElement('div');
    it.className = 'dm-item';
    it.textContent = t.name;
    if (t.name === curThemeName) it.classList.add('active');
    it.addEventListener('click', () => {
      $('themeBtn').textContent = t.name + ' ▾';
      themeMenu.querySelectorAll('.dm-item').forEach(x => x.classList.remove('active'));
      it.classList.add('active');
      themeMenu.style.display = 'none';
      saveSettingsFromUi(t.name);
    });
    themeMenu.appendChild(it);
  });
  if (!$('themeBtn')._bound) {
    $('themeBtn')._bound = true;
    $('themeBtn').addEventListener('click', (e) => {
      e.stopPropagation();
      themeMenu.style.display = themeMenu.style.display === 'none' ? 'block' : 'none';
    });
  }
  themeMenu.style.display = 'none';
  // 项目文件保存位置
  const app = await callNative('getAppInfo');
  $('setHistoryDir').value = s.historyDir || (app && app.projectDir ? app.projectDir : '（默认：C:\\Users\\<用户名>\\usmind）');
  $('assocState').textContent = s.registerAssoc ? '已注册，双击 .usmind 文件即可打开' : '未注册';
  // 本地偏好（localStorage）
  $('setRainbow').checked = localSetting('rainbow', true);
  $('setAutoSave').checked = localSetting('autoSave', true);
  $('setAutoSaveSec').value = localSetting('autoSaveSec', 3);
  $('setMaxAttach').value = localSetting('maxAttach', 200);
  $('setAllowTypes').value = localSetting('allowTypes', '*');
}

async function saveSettingsFromUi(newThemeName) {
  const newTheme = newThemeName || userDefaultTheme;
  if (newTheme && newTheme !== doc.theme) {
    applyTheme(newTheme);
  }
  userDefaultTheme = newTheme;
  // 本地偏好（localStorage）
  const ls = getLocalSettings();
  ls.rainbow = $('setRainbow').checked;
  ls.autoSave = $('setAutoSave').checked;
  ls.autoSaveSec = parseInt($('setAutoSaveSec').value) || 3;
  ls.maxAttach = parseInt($('setMaxAttach').value) || 200;
  ls.allowTypes = $('setAllowTypes').value.trim() || '*';
  setLocalSettings(ls);
  // 即时应用彩虹线开关
  try {
    if (mind && mind.setRainbowLinesConfig) {
      mind.setRainbowLinesConfig({ open: ls.rainbow, colorsList: branchColorsOf(doc.theme) });
    }
  } catch (e) {}
  const payload = { defaultTheme: newTheme };
  try {
    await callNative('saveSettings', JSON.stringify(payload));
  } catch (e) {
    toast('调用失败: ' + (e && e.message ? e.message : String(e)), 'error');
    return;
  }
}

// ================= 主题模板管理（本地） =================
function getCustomThemes() { try { return JSON.parse(localStorage.getItem('usmind.themes') || '[]'); } catch (e) { return []; } }
function setCustomThemes(arr) { try { localStorage.setItem('usmind.themes', JSON.stringify(arr)); } catch (e) {} }

// 本地偏好设置（前端 localStorage 持久化，无需服务端）
function getLocalSettings() { try { return JSON.parse(localStorage.getItem('usmind.localSettings') || '{}'); } catch (e) { return {}; } }
function setLocalSettings(s) { try { localStorage.setItem('usmind.localSettings', JSON.stringify(s)); } catch (e) {} }
function localSetting(k, d) { const s = getLocalSettings(); return s[k] != null ? s[k] : d; }

function loadCustomThemes() {
  getCustomThemes().forEach(t => {
    if (!t || !t.name) return;
    const base = themeBy(t.base || THEMES[0].name);
    const config = JSON.parse(JSON.stringify(base.config || {}));
    const colors = (t.colors || []).filter(Boolean);
    if (colors.length) { config.branchColors = colors; if (t.lineColor) config.lineColor = t.lineColor; }
    // 节点背景与原项目一致：second/node 不按配色染色（白底），branchColors 仅用于彩虹分支线
    if (t.canvasBg) config.backgroundColor = t.canvasBg;
    if (t.lineWidth != null) config.lineWidth = t.lineWidth;
    if (t.bgImage) config.backgroundImage = t.bgImage; else delete config.backgroundImage;
    if (t.bgVideo) config.backgroundVideo = t.bgVideo; else delete config.backgroundVideo;
    if (t.bgOpacity != null) config.backgroundOpacity = t.bgOpacity;
    if (t.bgZoom != null) config.backgroundZoom = t.bgZoom;
    if (t.attachText) config.attachTextColor = t.attachText;
    if (config.root) {
      config.root.fillColor = t.rootFill || config.root.fillColor;
      if (t.rootColor) config.root.color = t.rootColor;
      if (t.rootBorder) config.root.borderColor = t.rootBorder;
      if (t.rootRadius != null) config.root.borderRadius = t.rootRadius;
      if (t.rootFontSize != null) config.root.fontSize = t.rootFontSize;
      if (t.rootBorderWidth != null) config.root.borderWidth = t.rootBorderWidth;
      if (t.rootFont) config.root.fontFamily = t.rootFont;
    }
    const entry = { name: t.name, key: t.key || '', branchColors: colors.length ? colors : (base.branchColors || []), config };
    // 同名（含内置主题被编辑后）用自定义版替换，保证重启后仍生效
    const idx = THEMES.findIndex(x => x.name === t.name);
    if (idx >= 0) THEMES[idx] = entry; else THEMES.push(entry);
    try { MindMap.defineTheme(t.name, config); } catch (e) {}
  });
}

function renderThemesList() {
  loadCustomThemes();
  const list = $('themeMgrList');
  if (!list) return;
  const customs = getCustomThemes();
  const all = THEMES.map((t, i) => {
    const c = customs.find(x => x.name === t.name);
    return { name: t.name, key: t.key || (c && c.key) || '', colors: t.branchColors || [], builtin: !c, sort: c ? (c.sort || 999) : (i + 1) };
  }).sort((a, b) => a.sort - b.sort);
  $('themeMgrCount').textContent = '共 ' + all.length + ' 个主题模板';
  list.innerHTML = '';
  all.forEach(t => {
    const item = document.createElement('div');
    item.className = 'theme-item' + (t.builtin ? ' builtin' : '');
    const dots = document.createElement('div');
    dots.className = 't-dots';
    (t.colors || []).slice(0, 6).forEach(c => { const i = document.createElement('i'); i.style.background = c; dots.appendChild(i); });
    item.appendChild(dots);
    const nm = document.createElement('span'); nm.className = 't-name'; nm.textContent = t.name; item.appendChild(nm);
    const ky = document.createElement('span'); ky.className = 't-key'; ky.textContent = t.key || t.name; item.appendChild(ky);
    const ops = document.createElement('div');
    ops.className = 't-ops';
    const ed = document.createElement('button'); ed.className = 'mini-btn'; ed.textContent = '编辑'; ed.onclick = () => openThemeEdit(t.name); ops.appendChild(ed);
    if (t.builtin) { const st = document.createElement('span'); st.className = 'muted-text'; st.style.fontSize = '11px'; st.textContent = '内置'; ops.appendChild(st); }
    else {
      const del = document.createElement('button'); del.className = 'mini-btn'; del.style.color = '#c0392b'; del.textContent = '删除'; del.onclick = () => deleteTheme(t.name); ops.appendChild(del);
    }
    item.appendChild(ops);
    list.appendChild(item);
  });
}

function openThemeEdit(name) {
  const customs = getCustomThemes();
  const editing = customs.find(x => x.name === name);
  const builtin = !editing ? THEMES.find(x => x.name === name) : null;
  const cfg = editing ? null : (builtin ? builtin.config : null);
  $('teName').value = editing ? editing.name : (builtin ? builtin.name : '');
  $('teKey').value = editing ? (editing.key || '') : (builtin ? (builtin.key || '') : '');
  $('teSort').value = editing ? (editing.sort || 1) : (builtin ? (THEMES.indexOf(builtin) + 1) : 1);
  $('teEnabled').checked = editing ? editing.enabled !== false : true;
  const sel = $('teBase');
  sel.innerHTML = '';
  THEMES.forEach(t => { const o = document.createElement('option'); o.value = t.name; o.textContent = t.name; sel.appendChild(o); });
  sel.value = editing ? themeBy(editing.base || THEMES[0].name).name : (builtin ? builtin.name : THEMES[0].name);
  $('teBaseRow').style.display = (editing || builtin) ? 'none' : '';
  renderTeColors(editing ? (editing.colors || []) : (builtin ? (builtin.branchColors || []) : []));
  $('teCanvasBg').value = editing ? (editing.canvasBg || (cfg && cfg.backgroundColor) || '#fafbff') : (cfg ? (cfg.backgroundColor || '#fafbff') : '#fafbff');
  $('teLineColor').value = editing ? (editing.lineColor || (editing.colors || [])[0] || '#FF9F43') : (cfg ? (cfg.lineColor || '#FF9F43') : '#FF9F43');
  $('teLineWidth').value = editing ? (editing.lineWidth != null ? editing.lineWidth : 4) : (cfg ? (cfg.lineWidth || 4) : 4);
  $('teRootFill').value = editing ? (editing.rootFill || (editing.colors || [])[0] || '#FF9F43') : (cfg ? (cfg.root.fillColor || '#FF9F43') : '#FF9F43');
  $('teRootColor').value = editing ? (editing.rootColor || '#ffffff') : (cfg ? (cfg.root.color || '#ffffff') : '#ffffff');
  $('teRootBorder').value = editing ? (editing.rootBorder || '#ffffff') : (cfg ? (cfg.root.borderColor || '#ffffff') : '#ffffff');
  $('teRootRadius').value = editing ? (editing.rootRadius != null ? editing.rootRadius : 20) : (cfg ? (cfg.root.borderRadius || 20) : 20);
  $('teRootFontSize').value = editing ? (editing.rootFontSize != null ? editing.rootFontSize : 36) : (cfg ? (cfg.root.fontSize || 36) : 36);
  $('teRootBorderWidth').value = editing ? (editing.rootBorderWidth != null ? editing.rootBorderWidth : 0) : (cfg ? (cfg.root.borderWidth || 0) : 0);
  $('teRootFont').value = editing ? (editing.rootFont || 'SimHei, Microsoft YaHei, sans-serif') : (cfg ? (cfg.root.fontFamily || 'SimHei, Microsoft YaHei, sans-serif') : 'SimHei, Microsoft YaHei, sans-serif');
  $('teAttachColor').value = editing ? (editing.attachText || '#e53935') : (cfg ? (cfg.attachTextColor || '#e53935') : '#e53935');
  $('teBgImage').dataset.data = editing ? (editing.bgImage || '') : (cfg && cfg.backgroundImage && cfg.backgroundImage !== 'none' ? cfg.backgroundImage : '');
  $('teBgImage').value = $('teBgImage').dataset.data ? '已选择：' + bgFileName($('teBgImage').dataset.data) : '';
  $('teBgVideo').dataset.data = editing ? (editing.bgVideo || '') : (cfg && cfg.backgroundVideo && cfg.backgroundVideo !== 'none' ? cfg.backgroundVideo : '');
  $('teBgVideo').value = $('teBgVideo').dataset.data ? '已选择：' + bgFileName($('teBgVideo').dataset.data) : '';
  $('teBgOpacity').value = editing ? (editing.bgOpacity != null ? Math.round(editing.bgOpacity * 100) : 80) : (cfg && cfg.backgroundOpacity != null ? Math.round(cfg.backgroundOpacity * 100) : 80);
  $('teBgZoom').value = editing ? (editing.bgZoom != null ? editing.bgZoom : 1.4) : (cfg ? (cfg.backgroundZoom || 1.4) : 1.4);
  updateTePreview();
  $('themeEditDialog').classList.add('open');
  $('themeEditDialog').dataset.editing = name || '';
}
function renderTeColors(colors) {
  const box = $('teColors');
  box.innerHTML = '';
  (colors.length ? colors : ['#FF9F43', '#FECB33', '#FF6B6B', '#F08BB4']).forEach((c, i) => {
    const tc = document.createElement('div');
    tc.className = 'tc';
    const sw = document.createElement('div'); sw.className = 'tc-swatch';
    const inp = document.createElement('input');
    inp.type = 'color'; inp.value = c;
    inp.addEventListener('input', () => {
      const arr = currentTeColors(); if (arr[i]) arr[i] = inp.value;
      sw.style.background = inp.value;
      tc.querySelector('.tc-hex').textContent = inp.value.toUpperCase();
      updateTePreview();
    });
    sw.appendChild(inp);
    const hex = document.createElement('div'); hex.className = 'tc-hex'; hex.textContent = c.toUpperCase();
    const x = document.createElement('span'); x.className = 'tc-x'; x.textContent = '×';
    x.onclick = () => { const arr = currentTeColors(); arr.splice(i, 1); renderTeColors(arr); updateTePreview(); };
    tc.appendChild(sw); tc.appendChild(hex); tc.appendChild(x);
    box.appendChild(tc);
  });
}
function currentTeColors() { return Array.from($('teColors').querySelectorAll('input[type=color]')).map(i => i.value); }
function bgFileName(u) { try { return decodeURIComponent(String(u).split(/[\\/]/).pop() || ''); } catch (e) { return ''; } }
function updateTePreview() {
  const colors = currentTeColors();
  const root = $('tpRoot');
  root.style.background = $('teRootFill').value || (colors[0] || '#FF9F43');
  root.style.color = $('teRootColor').value || '#fff';
  root.style.borderRadius = ($('teRootRadius').value || 20) + 'px';
  const br = $('tpBranches');
  br.innerHTML = '';
  (colors.length ? colors.slice(0, 4) : []).forEach((c, i) => {
    const row = document.createElement('div');
    row.className = 'tp-branch';
    const sw = document.createElement('i'); sw.style.background = c;
    const sp = document.createElement('span'); sp.textContent = '分支' + (i + 1);
    row.appendChild(sw); row.appendChild(sp);
    br.appendChild(row);
  });
  // 背景：画布颜色 + 背景图片/视频 实时预览
  const pv = document.querySelector('.te-preview');
  if (!pv) return;
  pv.querySelectorAll('.tp-bg-media').forEach(x => x.remove());
  const bgColor = $('teCanvasBg').value || '#fafbff';
  const bgImg = $('teBgImage').dataset.data || '';
  const bgVid = $('teBgVideo').dataset.data || '';
  pv.style.background = bgColor;
  if (bgImg) {
    pv.style.backgroundImage = 'url("' + bgImg.replace(/"/g, '\\"') + '")';
    pv.style.backgroundSize = 'cover';
    pv.style.backgroundPosition = 'center';
  } else {
    pv.style.backgroundImage = 'none';
    pv.style.backgroundSize = 'auto';
    pv.style.backgroundPosition = '0 0';
  }
  if (bgVid) {
    const v = document.createElement('video');
    v.className = 'tp-bg-media';
    v.src = bgVid;
    v.autoplay = true; v.loop = true; v.muted = true; v.playsInline = true;
    v.style.cssText = 'position:absolute;inset:0;width:100%;height:100%;object-fit:cover;z-index:0;border-radius:12px;pointer-events:none;';
    pv.appendChild(v);
  }
  root.style.position = 'relative'; root.style.zIndex = '1';
  br.style.position = 'relative'; br.style.zIndex = '1';
}
function saveThemeFromDialog() {
  const name = $('teName').value.trim();
  if (!name) { toast('请填写主题名称', 'error'); return; }
  const editingName = $('themeEditDialog').dataset.editing || '';
  const colors = currentTeColors();
  if (!colors.length) { toast('请至少保留一个分支颜色', 'error'); return; }
  const rec = {
    name, key: $('teKey').value.trim() || ('theme-' + Date.now()),
    base: $('teBase').value || THEMES[0].name,
    sort: parseInt($('teSort').value) || 1,
    enabled: $('teEnabled').checked,
    colors,
    canvasBg: $('teCanvasBg').value,
    lineColor: $('teLineColor').value,
    lineWidth: parseInt($('teLineWidth').value) || 4,
    rootFill: $('teRootFill').value,
    rootColor: $('teRootColor').value,
    rootBorder: $('teRootBorder').value,
    rootRadius: parseInt($('teRootRadius').value) || 20,
    rootFontSize: parseInt($('teRootFontSize').value) || 36,
    rootBorderWidth: parseInt($('teRootBorderWidth').value) || 0,
    rootFont: $('teRootFont').value,
    attachText: $('teAttachColor').value,
    bgImage: $('teBgImage').dataset.data || '',
    bgVideo: $('teBgVideo').dataset.data || '',
    bgOpacity: parseInt($('teBgOpacity').value) / 100,
    bgZoom: parseFloat($('teBgZoom').value) || 1.4
  };
  const customs = getCustomThemes();
  if (editingName) {
    const isBuiltin = !customs.some(x => x.name === editingName);
    if (isBuiltin) {
      const idx = THEMES.findIndex(x => x.name === editingName);
      if (idx >= 0) THEMES.splice(idx, 1);
      customs.push(rec);
    } else {
      const t = customs.find(x => x.name === editingName);
      if (t) Object.assign(t, rec);
    }
  } else {
    if (customs.some(x => x.name === name)) { toast('已存在同名主题', 'error'); return; }
    customs.push(rec);
  }
  setCustomThemes(customs);
  let appliedName = '';
  if (doc.theme === name || doc.theme === editingName) {
    if (doc.theme === editingName && name !== editingName) doc.theme = name;
    appliedName = doc.theme;
  }
  const idx = THEMES.findIndex(x => x.name === editingName);
  if (idx >= 0) THEMES.splice(idx, 1);
  loadCustomThemes();
  if (appliedName) {
    try {
      const ent = THEMES.find(x => x.name === appliedName);
      if (ent) {
        MindMap.defineTheme(appliedName, ent.config);
        mind.setTheme(appliedName);
        mind.render(() => {});
      }
    } catch (e) {}
    scheduleBranchRainbow();
    applyCanvasBg();
  }
  $('themeEditDialog').classList.remove('open');
  toast('主题已保存');
  renderThemesList();
  fillSettings();
}
function deleteTheme(name) {
  setCustomThemes(getCustomThemes().filter(x => x.name !== name));
  const idx = THEMES.findIndex(x => x.name === name);
  if (idx >= 0) THEMES.splice(idx, 1);
  renderThemesList();
  toast('已删除主题：' + name);
}

async function pickProjectDir() {
  const r = await callNative('pickFolder');
  if (r && r.ok && r.dir) {
    const s = await callNative('setProjectDir', r.dir);
    if (s && s.ok) {
      $('setHistoryDir').value = r.dir;
      toast('项目文件保存位置已更新');
      fillSettings();
    } else {
      toast('目录设置失败', 'error');
    }
  }
}

// ================= 导出 =================
// 导出前重建"未渲染"的公式节点：旧数据/KaTeX 渲染失败时 text 里存的是 LaTeX 源码（无 katex 标志），
// 用 mathLatex 重新渲染成 KaTeX HTML（剥 $ 包裹），保证导出 PNG/SVG/JPEG 时公式正常显示
// 用渲染树遍历找节点实例（findNodeByUid 在 setData 后可能匹配不到，这里最稳）
function findRenderInstByUid(uid) {
  try {
    const rt = mind.renderer && mind.renderer.renderTree;
    if (!rt) return null;
    let found = null;
    const walk = (t) => {
      if (found || !t) return;
      if (t.data && t.data.uid === uid) { found = t._node || t; return; }
      (t.children || []).forEach(walk);
    };
    walk(rt);
    return found;
  } catch (e) { return null; }
}
function rebuildRawMathNodes() {
  try {
    const tree = mind.getData();
    const rootNode = (tree && tree.root) || tree; // getData 返回 { root: {...} }，要从 root 开始遍历
    let changed = false;
    const walk = (t) => {
      if (t && t.data && typeof t.data.text === 'string') {
        const text = t.data.text;
        // 已是占位格式（标题 + [公式]）：无需重建
        if (text.indexOf('[公式]') >= 0) { (t.children || []).forEach(walk); return; }
        let latex = t.data.mathLatex;
        if (!latex) {
          const m = text.match(/\$([^$]+)\$/);
          if (m) latex = m[1];
        }
        // 有 LaTeX 但 text 不是占位格式（旧数据：纯源码 $..$、已渲染 KaTeX HTML、多层转义污染）
        // → 统一重建为占位格式，避免库序列化把公式 HTML 打散累积
        if (latex) {
          const title = extractPlainTitle(text);
          const inst = (mind.findNodeByUid && mind.findNodeByUid(t.data.uid)) || findRenderInstByUid(t.data.uid);
          if (inst) {
            mind.renderer.setNodeDataRender(inst, { text: mathPlaceholderText(title), richText: true, mathLatex: latex });
          } else {
            t.data.text = mathPlaceholderText(title);
          }
          changed = true;
        }
      }
      (t.children || []).forEach(walk);
    };
    walk(rootNode);
    if (changed) scheduleMathInject();
  } catch (e) {}
}
function clearSelectionForExport() {
  try { if (mind.renderer.clearActiveNodeList) mind.renderer.clearActiveNodeList(); } catch (e) {}
  try {
    const walk = (t) => {
      if (!t) return;
      const node = t._node || t;
      try {
        if (node.hoverNode) {
          if (node.hoverNode.node) { node.hoverNode.node.style.display = 'none'; node.hoverNode.node.style.visibility = 'hidden'; }
          else if (node.hoverNode.remove) node.hoverNode.remove();
        }
      } catch (e) {}
      (t.children || []).forEach(walk);
    };
    walk(mind.renderer.renderTree);
  } catch (e) {}
  try {
    document.querySelectorAll('svg [class*="hover"], svg [class*="Hover"], [class*="add-btn"], [class*="AddBtn"]').forEach((el) => { try { el.style.display = 'none'; } catch (e) {} });
  } catch (e) {}
}
async function doExport(type) {
  if (!mind) return;
  try {
    // 导出前清 hover/选中（否则蓝色选中框、悬浮按钮会进导出图）
    clearSelectionForExport();
    // 公式/富文本字体（KaTeX）可能仍在异步加载：先等字体就绪再导出，避免导出时公式换行/错位
    try { await document.fonts.ready; } catch (e) {}
    try { rebuildRawMathNodes(); await new Promise(r => setTimeout(r, 300)); } catch (e) {} // 重建后等布局重排完成，导出布局才与屏幕一致
    // 字体就绪后强制全量重渲染，节点尺寸按已加载字体重新测量（避免导出节点被撑大/留白过多）
    try { mind.render(); await new Promise(r => setTimeout(r, 500)); } catch (e) {}
    // 渲染完成后把公式占位注入为 KaTeX（foreignObject 在导出序列化时携带真实公式 HTML）
    try { injectMathIntoFo(); await new Promise(r => setTimeout(r, 120)); } catch (e) {}
    const name = doc.name.replace(/\.usmind$/i, '');
    if (type === 'png' || type === 'jpeg' || type === 'jpg') await exportChunkedImages(type, name);
    else if (type === 'svg') await exportSvgSelf(name);
    else if (type === 'md') await mind.doExport.export('md', true, name);
    else if (type === 'json') await exportJSONAllTabs(name);
    else if (type === 'xmind') await mind.doExport.export('xmind', true, name);
    else if (type === 'zip') await exportSelfContained();
    toast('导出完成，请选择保存位置');
  } catch (e) {
    toast('导出失败：' + e.message, 'error');
  }
}

// ===== 图片导出（PNG/JPEG）—— 同原项目机制 =====
// 不用 canvas 直接绘制含 foreignObject 的 SVG（Chrome 判定 Tainted canvas 导致 toDataURL 失败、
// 且 foreignObject 内公式/富文本内容无法渲染）。
// 改为：拆出 foreignObject 的 HTML 内容 → 纯 SVG 图形层 + HTML 富文本层分开放入 DOM → html2canvas 整页截图
function parseSvgSize(svgStr) {
  const mv = svgStr.match(/viewBox="([^"]*)"/);
  if (mv) {
    const parts = mv[1].trim().split(/[\s,]+/).map(Number);
    if (parts.length >= 4 && isFinite(parts[2]) && isFinite(parts[3]) && parts[2] > 0 && parts[3] > 0) {
      return { W: parts[2], H: parts[3], X: isFinite(parts[0]) ? parts[0] : 0, Y: isFinite(parts[1]) ? parts[1] : 0 };
    }
  }
  const mw = svgStr.match(/width="([\d.]+)"/);
  const mh = svgStr.match(/height="([\d.]+)"/);
  return { W: mw ? parseFloat(mw[1]) : 0, H: mh ? parseFloat(mh[1]) : 0, X: 0, Y: 0 };
}
function makeChunkSvg(svgStr, W, ch, y0, X, Y) {
  const vx = X || 0;
  const vy = (Y || 0) + y0;
  return svgStr
    .replace(/width="[\d.]+"/, 'width="' + W + '"')
    .replace(/height="[\d.]+"/, 'height="' + ch + '"')
    .replace(/viewBox="[^"]*"/, 'viewBox="' + vx + ' ' + vy + ' ' + W + ' ' + ch + '"');
}
function collectFoItems(svgStr) {
  const doc = new DOMParser().parseFromString(svgStr, 'image/svg+xml');
  if (doc.querySelector('parsererror')) return { svg: svgStr, items: [] };
  const fos = Array.from(doc.querySelectorAll('foreignObject'));
  const items = [];
  fos.forEach((fo) => {
    const x = parseFloat(fo.getAttribute('x') || '0');
    const y = parseFloat(fo.getAttribute('y') || '0');
    const ancestors = [];
    let el = fo.parentNode;
    while (el && el.nodeName !== 'svg') {
      const at = el.getAttribute('transform');
      if (at) ancestors.push(at);
      el = el.parentNode;
    }
    let px = x, py = y;
    ancestors.forEach((t) => {
      const i = t.indexOf('matrix(');
      if (i >= 0) {
        const j = t.indexOf(')', i);
        const pp = t.slice(i + 7, j).split(',').map(Number);
        const nx = pp[0] * px + pp[2] * py + pp[4];
        const ny = pp[1] * px + pp[3] * py + pp[5];
        px = nx; py = ny;
      }
    });
    items.push({ x: px, y: py, fo });
    fo.remove();
  });
  const svg = new XMLSerializer().serializeToString(doc);
  return { svg, items };
}
// 渲染一块：HTML 容器（纯 SVG 层 + fo 内容层）→ html2canvas → canvas
async function renderChunkByHtml(svgStr, W, ch, y0, X, Y, bg) {
  const { svg: svgNoFo, items } = collectFoItems(svgStr);
  const chunkSvg = makeChunkSvg(svgNoFo, W, ch, y0, X, Y);
  const wrap = document.createElement('div');
  wrap.style.cssText = 'position:fixed;left:-99999px;top:0;z-index:-1000;overflow:hidden;';
  wrap.style.width = W + 'px';
  wrap.style.height = ch + 'px';
  wrap.style.background = bg || '#ffffff';
  const svgBox = document.createElement('div');
  svgBox.style.cssText = 'position:absolute;left:0;top:0;width:' + W + 'px;height:' + ch + 'px;';
  svgBox.innerHTML = chunkSvg;
  wrap.appendChild(svgBox);
  items.forEach((it) => {
    const left = it.x - (X || 0);
    const top = it.y - (Y || 0) - y0;
    if (top + 20 < 0 || top > ch) return;
    const holder = document.createElement('div');
    // 继承 foreignObject 的样式（font-size/line-height/字体等）并固定宽高，节点才与屏幕一致
    const foStyle = it.fo.getAttribute('style') || '';
    const w = parseFloat(it.fo.getAttribute('width') || '0');
    const h = parseFloat(it.fo.getAttribute('height') || '0');
    holder.style.cssText = 'position:absolute;left:' + left + 'px;top:' + top + 'px;width:' + w + 'px;height:' + h + 'px;overflow:hidden;' + foStyle;
    // 还原 XML 转义的 <p>/<br>（库的非富文本节点 <p> 无 xmlns，XML 序列化时被转义成 &lt;p&gt;；
    // 直接克隆 DOM 会显示字面 "<p>" 字符串并撑大节点）
    let foHtml = it.fo.innerHTML || '';
    foHtml = foHtml.replace(/&lt;p&gt;/g, '<p>').replace(/&lt;\/p&gt;/g, '</p>').replace(/&lt;br&gt;/g, '<br>');
    holder.innerHTML = foHtml;
    // 覆盖富文本 <p> 的默认 margin/padding，防止把节点撑高变形
    holder.querySelectorAll('p').forEach((p) => { p.style.margin = '0'; p.style.padding = '0'; });
    // 视频/音频：贴链接占位（html2canvas 无法稳定绘制媒体画面）
    holder.querySelectorAll('video, audio').forEach((m) => {
      const label = m.nodeName === 'VIDEO' ? '▶ 视频' : '♪ 音频';
      const src = m.getAttribute('src') || '';
      const nm = decodeURIComponent((src.split('/').pop() || '').replace(/\?.*$/, ''));
      const d = document.createElement('div');
      d.textContent = label + (nm ? '：' + nm : '');
      d.style.cssText = 'font-size:13px;color:#555;padding:2px 8px;border:1px dashed #bbb;border-radius:6px;background:#fafafa;';
      if (m.parentNode) m.parentNode.replaceChild(d, m);
    });
    wrap.appendChild(holder);
  });
  document.body.appendChild(wrap);
  try {
    if (typeof html2canvas !== 'function') throw new Error('缺少 html2canvas 组件');
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
    });
    return canvas;
  } finally {
    wrap.remove();
  }
}
// 分块导出 PNG/JPEG：超大画布自动分块，避免 canvas 超限
async function exportChunkedImages(type, name) {
  const ext = (type === 'jpeg' || type === 'jpg') ? 'jpg' : 'png';
  const { str: strRaw } = await mind.doExport.getSvgData();
  const { W, H, X, Y } = parseSvgSize(strRaw);
  if (!W || !H) { toast('画布尺寸解析失败', 'error'); return; }
  const bg = (mind.themeConfig && mind.themeConfig.backgroundColor) || '#ffffff';
  const CHUNK = 5000;
  const chunks = Math.max(1, Math.ceil(H / CHUNK));
  const isJpg = ext === 'jpg';
  const parts = [];
  for (let i = 0; i < chunks; i++) {
    const y0 = i * CHUNK;
    const ch = Math.min(CHUNK, H - y0);
    toast('正在导出第 ' + (i + 1) + '/' + chunks + ' 块...');
    const canvas = await renderChunkByHtml(strRaw, W, ch, y0, X, Y, bg);
    parts.push(canvas.toDataURL(isJpg ? 'image/jpeg' : 'image/png', isJpg ? 0.92 : undefined));
  }
  if (chunks === 1) {
    const b64 = parts[0].split(',')[1];
    await saveBinaryViaNative(b64, name + '.' + ext);
  } else {
    toast('正在打包分块压缩包...');
    const zip = new JSZip();
    parts.forEach((d, i) => zip.file(name + '_' + (i + 1) + '.' + ext, d.split(',')[1], { base64: true }));
    const blob = await zip.generateAsync({ type: 'blob' });
    const ab = await blob.arrayBuffer();
    let b64 = '';
    const bytes = new Uint8Array(ab);
    for (let i2 = 0; i2 < bytes.length; i2 += 0x8000) {
      b64 += String.fromCharCode.apply(null, bytes.subarray(i2, i2 + 0x8000));
    }
    b64 = btoa(b64);
    await saveBinaryViaNative(b64, name + '_分块导出.zip');
  }
}
async function saveBinaryViaNative(b64, filename) {
  const r = await callNative('saveBinary', b64, '', filename);
  if (!r || !r.ok) throw new Error('保存被取消');
}

function bufToB64(buf) {
  let s = '';
  const chunk = 0x8000;
  for (let i = 0; i < buf.length; i += chunk) {
    s += String.fromCharCode.apply(null, buf.subarray(i, Math.min(i + chunk, buf.length)));
  }
  return btoa(s);
}
// 获取 KaTeX CSS 并把字体相对路径转成 base64 内联（导出 SVG 本地打开时公式也正常显示）
async function getKatexCssForSvg() {
  try {
    const cssUrl = new URL('vendor/katex/katex.min.css', location.href);
    const r = await fetch(cssUrl);
    if (!r.ok) return '';
    let css = await r.text();
    const re = /url\(([^)]+)\)/g;
    const urls = [];
    let m;
    while ((m = re.exec(css))) urls.push(m[1]);
    for (const u of urls) {
      if (u.startsWith('data:')) continue;
      try {
        const fr = await fetch(new URL(u, cssUrl));
        const buf = new Uint8Array(await fr.arrayBuffer());
        if (!buf.length) continue;
        const mime = u.indexOf('.ttf') >= 0 ? 'font/ttf' : (u.indexOf('.otf') >= 0 ? 'font/otf' : 'font/woff2');
        css = css.split('url(' + u + ')').join('url(data:' + mime + ';base64,' + bufToB64(buf) + ')');
      } catch (e) {}
    }
    return css;
  } catch (e) { return ''; }
}
// SVG 导出（自实现）：库导出不带 KaTeX CSS，打开时公式无样式/错位/换行；这里注入内联字体的 CSS
async function exportSvgSelf(name) {
  const { str: svgStr } = await mind.doExport.getSvgData();
  let out = svgStr;
  const kc = await getKatexCssForSvg();
  if (kc) out = out.replace('</svg>', '<style>' + kc + '</style></svg>');
  // 富文本里的图片转 base64 内联（本地/他人打开 SVG 图片不裂）
  try {
    const imgDoc = new DOMParser().parseFromString(out, 'image/svg+xml');
    if (!imgDoc.querySelector('parsererror')) {
      const imgs = imgDoc.querySelectorAll('img');
      for (const img of imgs) {
        const src = img.getAttribute('src');
        if (src && !src.startsWith('data:') && !src.startsWith('blob:')) {
          try {
            const ir = await fetch(new URL(src, location.href));
            const buf = new Uint8Array(await ir.arrayBuffer());
            if (buf.length) img.setAttribute('src', 'data:image/png;base64,' + bufToB64(buf));
          } catch (e) {}
        }
      }
      if (imgs.length) out = new XMLSerializer().serializeToString(imgDoc);
    }
  } catch (e) {}
  const b64 = bufToB64(new TextEncoder().encode(out));
  saveBinaryViaNative(b64, name + '.svg');
}

// 导出多标签 JSON（含所有标签页，恢复时一键还原）
async function exportJSONAllTabs(name) {
  const p = await buildSelfContainedPayload();
  const payload = {
    app: 'usmind', type: 'multi-tabs', version: 1,
    tabs: p.tabs,
  };
  const json = JSON.stringify(payload);
  const b64 = btoa(unescape(encodeURIComponent(json)));
  const r = await callNative('saveBinary', b64, '', (name || '思维导图') + '.json');
  if (!r || !r.ok) throw new Error('保存失败');
}

function arrayBufferToBase64(buf) {
  const bytes = new Uint8Array(buf);
  let bin = '';
  const CH = 0x8000;
  for (let i = 0; i < bytes.length; i += CH) bin += String.fromCharCode.apply(null, bytes.subarray(i, i + CH));
  return btoa(bin);
}

// 自包含保存载荷：多标签结构 + 媒体引用归一（dataUrl → 落盘附件目录 → 相对引用；外置相对引用保留）
// 返回 { tabs, mediaRels }；mediaRels 顺序 = manifest 引用顺序（native 组装/解包按序）
function dataUrlToB64(u) { const i = u.indexOf(','); return i >= 0 ? u.slice(i + 1) : u; }
function extFromDataUrl(u) {
  const m = /data:([^;,]+)/.exec(u);
  const mt = m ? m[1].toLowerCase() : '';
  const map = { 'image/png': 'png', 'image/jpeg': 'jpg', 'image/gif': 'gif', 'image/webp': 'webp', 'image/svg+xml': 'svg', 'video/mp4': 'mp4', 'video/webm': 'webm', 'audio/mpeg': 'mp3', 'audio/wav': 'wav', 'application/pdf': 'pdf', 'application/json': 'json', 'text/plain': 'txt', 'text/markdown': 'md' };
  if (map[mt]) return map[mt];
  const k = mt.split('/').pop().replace('+xml', '');
  return k && k.length <= 6 ? k : 'bin';
}
async function buildSelfContainedPayload() {
  snapshotActiveTab();
  const mediaRels = [];
  const seen = new Set();
  async function ensureRel(u) {
    if (!u || typeof u !== 'string') return u;
    if (u.indexOf('attachments/') === 0) {
      const k = u.replace(/[?#].*$/, '');
      if (!seen.has(k)) { seen.add(k); mediaRels.push(k); }
      return u;
    }
    if (u.indexOf('data:') === 0) {
      const rel = 'attachments/att_' + Date.now() + '_' + Math.random().toString(36).slice(2, 8) + '.' + extFromDataUrl(u);
      const r = await callNative('writeAttachment', rel, dataUrlToB64(u));
      if (r && r.ok) { if (!seen.has(rel)) { seen.add(rel); mediaRels.push(rel); } return rel; }
      return u;
    }
    if (u.indexOf('blob:') === 0) {
      // blob 不持久化：落盘为附件，引用改相对路径
      try {
        const resp = await fetch(u);
        const buf = await resp.arrayBuffer();
        const mime = resp.headers.get('Content-Type') || '';
        const rel = 'attachments/att_' + Date.now() + '_' + Math.random().toString(36).slice(2, 8) + '.' + extFromDataUrl('data:' + mime + ';base64,');
        const r = await callNative('writeAttachment', rel, arrayBufferToBase64(buf));
        if (r && r.ok) { if (!seen.has(rel)) { seen.add(rel); mediaRels.push(rel); } return rel; }
      } catch (e) {}
      return u;
    }
    return u; // http: 外链保留
  }
  async function walk(n) {
    if (!n) return;
    const dd = n.data;
    if (dd) {
      if (dd.attachUrl) dd.attachUrl = await ensureRel(dd.attachUrl);
      if (dd.image && dd.image.indexOf('data:') === 0) dd.image = await ensureRel(dd.image);
      if (typeof dd.text === 'string' && dd.text.indexOf('src="') !== -1) {
        const srcs = [];
        const pat = /src="(data:[^"]+|attachments\/[^"]+|blob:[^"]+)"/g;
        let m;
        while ((m = pat.exec(dd.text))) srcs.push({ raw: m[1], full: m[0] });
        for (const s of srcs) {
          const rel = await ensureRel(s.raw);
          if (rel !== s.raw) dd.text = dd.text.split(s.full).join('src="' + rel + '"');
        }
      }
      // markdown 附件：扫描正文中引用的 attachments/ 媒体，一并打入自包含包
      if (dd.attachUrl || dd.attachmentUrl) {
        const an = dd.attachmentName || dd.attachName || '';
        const aext = (an.split('.').pop() || '').toLowerCase();
        if (aext === 'md' || aext === 'markdown') {
          try {
            const txt = await loadAttachmentText({ url: dd.attachmentUrl || dd.attachUrl, name: an });
            const refs = txt.match(/attachments\/[A-Za-z0-9_.\-]+/g) || [];
            for (const ref of refs) {
              if (!seen.has(ref)) { seen.add(ref); mediaRels.push(ref); }
            }
          } catch (eM) {}
        }
      }
    }
    if (n.children) { for (const c of n.children) await walk(c); }
  }
  for (const t of tabs) {
    if (!t || !t.data) continue;
    try { restoreTreeMediaSrc(t.data.root || t.data); } catch (e) {}
    await walk(t.data.root || t.data);
  }
  const tabsOut = tabs.map(t => ({
    name: t.name || '未命名导图',
    path: t.path || '',
    theme: t.theme || userDefaultTheme,
    layout: t.layout || 'logicalStructure',
    data: t.data && t.data.root ? t.data.root : t.data,
  }));
  return { tabs: tabsOut, mediaRels };
}

// 保存 = 自包含包（多标签 + 全部资源，拷走即开）：native 二进制直拷，快
async function saveNow(silent) {
  if (!mind) return false;
  try {
    const p = await buildSelfContainedPayload();
    const manifest = {
      format: 'usmind', version: 3, selfContained: true,
      name: doc.name, savedAt: new Date().toISOString(),
      tabs: p.tabs,
    };
    const r = await callNative('saveSelfContained', JSON.stringify(manifest), JSON.stringify(p.mediaRels), 0, doc.name, doc.path);
    if (r && r.ok) {
      doc.path = r.path || doc.path;
      doc.name = r.name || doc.name;
      dirty = false;
      updateTitle();
      if (!silent) toast('已保存（自包含）');
      callNative('notifyDocumentState', doc.name);
      callNative('saveSettings', JSON.stringify({ lastFile: doc.path }));
      return true;
    }
    if (r && r.cancel) { /* 用户取消另存为 */ }
    else if (!silent) toast('保存失败' + (r ? ' ' + JSON.stringify(r) : ''), 'error');
    return false;
  } catch (e) {
    if (!silent) toast('保存失败：' + e.message, 'error');
    return false;
  }
}

// 导出"自包含"包（v3 新格式）：结构与所有媒体直拷进单文件 .usmind，拷走即开（另存为）
async function exportSelfContained() {
  try {
    const p = await buildSelfContainedPayload();
    const manifest = {
      format: 'usmind', version: 3, selfContained: true,
      name: doc.name, savedAt: new Date().toISOString(),
      tabs: p.tabs,
    };
    const r = await callNative('saveSelfContained', JSON.stringify(manifest), JSON.stringify(p.mediaRels), 1, doc.name);
    if (r && r.ok) { toast('已导出自包含包'); return { ok: true, mediaCount: p.mediaRels.length }; }
    if (r && r.cancel) { toast('导出已取消', 'info'); return { ok: false, cancel: true }; }
    toast('导出失败', 'error');
    return { ok: false, error: '导出失败' };
  } catch (e) {
    toast('导出失败：' + e.message, 'error');
    return { ok: false, error: e.message };
  }
}

// 导入"自包含"包：解 zip → 媒体落盘 exe/data/attachments → 引用改外置相对路径 → 打开（外置模式渲染）
async function importSelfContained(b64, path, name) {
  try {
    if (typeof JSZip === 'undefined') return { ok: false, error: '缺少打包组件' };
    const bin = atob(b64);
    const bytes = new Uint8Array(bin.length);
    for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
    const zip = await JSZip.loadAsync(bytes);
    const mf = zip.file('manifest.json');
    if (!mf) { toast('不是有效的自包含包', 'error'); return; }
    const manifest = JSON.parse(await mf.async('string'));
    const ts = Date.now();
    const replaceMap = {};
    const mediaKeys = Object.keys(zip.files).filter(k => k.indexOf('media/') === 0 && !zip.files[k].dir);
    for (const k of mediaKeys) {
      try {
        const ab = await zip.files[k].async('arraybuffer');
        const rel = 'attachments/att_' + ts + '_' + k.replace('media/', '').replace(/[\\/:*?"<>|]/g, '_');
        const wr = await callNative('writeAttachment', rel, arrayBufferToBase64(ab));
        if (wr && wr.ok) replaceMap[k] = rel;
      } catch (eM) {}
    }
    const walk = async (n) => {
      if (!n) return;
      const dd = n.data;
      if (dd) {
        if (typeof dd.text === 'string' && dd.text.indexOf('src="media/') !== -1) {
          dd.text = dd.text.replace(/src="(media\/[^"]+)"/g, (m, ref) => {
            const rel = replaceMap[ref];
            return rel ? 'src="' + rel + '"' : m;
          });
        }
        const u = dd.attachUrl || dd.attachmentUrl;
        const zipOldRel = (u && u.indexOf('media/') === 0) ? u : null;
        if (u && u.indexOf('media/') === 0) {
          const rel = replaceMap[u];
          if (rel) { dd.attachUrl = rel; delete dd.attachmentUrl; }
        }
        if (dd.image && dd.image.indexOf('media/') === 0) {
          const rel = replaceMap[dd.image];
          if (rel) dd.image = resolveMediaUrl(rel);
        }
        // markdown 附件：正文引用的媒体 rel 与导入写回的 rel 不一致，替换后重写附件
        if (dd.attachUrl || dd.attachmentUrl) {
          const an = dd.attachmentName || dd.attachName || '';
          const aext = (an.split('.').pop() || '').toLowerCase();
          if (aext === 'md' || aext === 'markdown' && zipOldRel) {
            try {
              let txt = '';
              const f = zip.file(zipOldRel);
              if (f) txt = await f.async('string');
              if (txt) {
                const newTxt = txt.replace(/attachments\/([A-Za-z0-9_.\-]+)/g, (m0, nm) => {
                  const rel2 = replaceMap['media/' + nm];
                  return rel2 ? 'attachments/' + rel2.split('/').pop() : m0;
                });
                if (newTxt !== txt) {
                  const nrel = 'attachments/md_' + ts + '_' + (an.replace(/[\\/:*?"<>|]/g, '_') || 'doc.md');
                  const wr = await callNative('writeAttachment', nrel, utf8Encode(newTxt));
                  if (wr && wr.ok) { dd.attachUrl = nrel; delete dd.attachmentUrl; }
                }
              }
            } catch (eM) {}
          }
        }
      }
      if (n.children) { for (const c of n.children) await walk(c); }
    };
    await walk(manifest.data.root);
    const parsed = {
      root: manifest.data.root,
      theme: manifest.theme || DEFAULT_THEME,
      layout: manifest.layout || 'logicalStructure',
      name: manifest.name || name || '未命名导图',
    };
    openInTab(path, name, parsed);
    toast('已导入自包含包');
    return { ok: true, media: Object.keys(replaceMap).length };
  } catch (e) {
    toast('导入失败：' + e.message, 'error');
    return { ok: false, error: e.message };
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
let mathCleanTitle = null; // 打开弹窗时从节点提取的干净标题（取消重建用）

function htmlUnescape(s) {
  return String(s || '')
    .replace(/&lt;/g, '<').replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"').replace(/&#39;/g, "'")
    .replace(/&nbsp;/g, ' ')
    .replace(/&amp;/g, '&');
}
// 迭代反转义直到稳定（兼容历史版本累积的 &lt;p&gt; / &amp;lt;p&amp;gt; 多层污染）
function htmlUnescapeAll(s) {
  let prev = String(s || ''), cur = prev, guard = 0;
  do { prev = cur; cur = htmlUnescape(prev); guard++; } while (cur !== prev && guard < 8);
  return cur;
}
// 迭代剥标签直到稳定（含未闭合标签兜底）
function stripTagsAll(s) {
  let prev = String(s || ''), cur = prev, guard = 0;
  do { prev = cur; cur = prev.replace(/<[^>]*>/g, ' '); guard++; } while (cur !== prev && guard < 8);
  cur = cur.replace(/<[^>]*/g, ' ').replace(/\s+/g, ' ').trim();
  return cur;
}
// 从节点 text（richText HTML，可能含历史转义污染）提取纯文本标题：
// 库渲染会把「标题 + 公式 span」规范化为多段 <p><span>…</span></p>（公式内容单列一段），
// 所以先反转义成真标签，按 <p> 切段取第一个有内容的段作为标题，再剥标签压缩空白
function extractPlainTitle(text) {
  let s = htmlUnescapeAll(String(text || ''));
  const parts = s.split(/<p[^>]*>/i);
  for (const part of parts) {
    let seg = stripTagsAll(part);
    seg = seg.replace(/\[公式\d*\]/g, ' ').replace(/\s+/g, ' ').trim();
    if (seg) return seg;
  }
  return stripTagsAll(s).replace(/\[公式\d*\]/g, ' ').replace(/\s+/g, ' ').trim();
}
// 公式节点文本存储格式：纯文本「标题 + [公式]」（不含任何 HTML/实体）。
// 库的 Quill 规范化对纯文本只包 <p>，不会把公式 HTML 打散成文本、也不会产生转义累积；
// 真实 KaTeX 公式 HTML 由 injectMathIntoFo 在渲染完成后注入 foreignObject（显示层，不写入数据）。
function mathPlaceholderText(title) { return escHtml(title || '公式') + ' [公式]'; }
let _mathInjectTimer = null;
function scheduleMathInject() {
  clearTimeout(_mathInjectTimer);
  _mathInjectTimer = setTimeout(() => { try { injectMathIntoFo(); } catch (e) {} }, 60);
}
// KaTeX 是行高塌陷结构（inline 行框只有一行高，上标/分式等视觉内容溢出容器），
// 节点尺寸按占位文本测量会偏小 → 注入后按内容实测把 foreignObject 撑到真实宽高，
// 公式才完整显示（导出 SVG 时这些尺寸属性也会被序列化携带）。
function resizeMathFos() {
  try {
    const fos = Array.from(document.querySelectorAll('.smm-container foreignObject'));
    fos.forEach(fo => {
      const inner = fo.firstElementChild;
      if (!inner) return;
      const aw = parseInt(fo.getAttribute('width')) || 0;
      const ah = parseInt(fo.getAttribute('height')) || 0;
      const nw = Math.max(aw, Math.ceil(inner.scrollWidth) + 2);
      const nh = Math.max(ah, Math.ceil(inner.scrollHeight) + 2);
      if (nw !== aw || nh !== ah) {
        fo.setAttribute('width', nw);
        fo.setAttribute('height', nh);
        fo.style.width = nw + 'px';
        fo.style.height = nh + 'px';
      }
    });
  } catch (e) {}
}
let _mathResizeTimer = null;
// 渲染完成后把 foreignObject 内的 [公式] 占位文本替换为真实 KaTeX 渲染 HTML。
// foreignObject 的文档顺序与数据树 DFS 顺序一致（折叠隐藏节点会减少 foreignObject，
// 展开后 node_tree_render_end 会再次注入恢复），故用顺序映射取 latex。
function injectMathIntoFo() {
  try {
    if (!mind || !mind.renderer) return;
    const tree = mind.getData();
    const rootNode = (tree && tree.root) || tree;
    const flat = [];
    const walk = (t) => { if (t && t.data) { flat.push(t.data); (t.children || []).forEach(walk); } };
    walk(rootNode);
    const fos = Array.from(document.querySelectorAll('.smm-container foreignObject'));
    fos.forEach((fo, idx) => {
      const dd = flat[idx];
      if (!dd || !dd.mathLatex) return;
      const div = fo.querySelector('div');
      if (!div || !/\[公式\]/.test(div.textContent)) return;
      const walker = document.createTreeWalker(div, NodeFilter.SHOW_TEXT);
      let node;
      while ((node = walker.nextNode())) {
        const txt = node.textContent;
        const idx2 = txt.indexOf('[公式]');
        if (idx2 === -1) continue;
        // 只替换 [公式] 占位片段，保留前后的标题文本（否则标题一并被公式吃掉，无法双击定位/编辑）
        const span = document.createElement('span');
        span.className = 'katex-inline';
        span.innerHTML = renderLatex(dd.mathLatex);
        const before = document.createTextNode(txt.slice(0, idx2));
        const after = document.createTextNode(txt.slice(idx2 + '[公式]'.length));
        node.replaceWith(before, span, after);
      }
    });
    // 注入后按内容实测撑高/撑宽 foreignObject（KaTeX 行高塌陷，节点测量高度不足）
    resizeMathFos();
    clearTimeout(_mathResizeTimer);
    // 延迟一次再修正：KaTeX 布局/字体渲染完成后高度才稳定，重试一次保证收敛
    _mathResizeTimer = setTimeout(() => { try { resizeMathFos(); } catch (e) {} }, 200);
  } catch (e) {}
}

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
    case 'paste':
      try { if (ctxNode) mind.renderer.currentNode = ctxNode; } catch (e2) {}
      try { mind.handlePaste(); } catch (e) {}
      break;
    case 'undo': try { mind.undo(); } catch (e) { try { mind.command && mind.command.undo && mind.command.undo(); } catch (e2) {} } break;
    case 'redo': try { mind.redo(); } catch (e) { try { mind.command && mind.command.redo && mind.command.redo(); } catch (e2) {} } break;
    case 'icon': openIconDialog(node); break;
    case 'math': openMathDialog(node, false); break;
    case 'attach': openAttachManage(node || currentSelectedNode()); break;
    case 'link': openLinkDialog(node); break;
    case 'note': openNoteDialog(node); break;
    case 'style': openStyleBar(node || currentSelectedNode()); break;
  }
  setTimeout(() => { try { mind.render(); } catch (e) {} }, 30);
}

// ---- 附件 ----
let attachNode = null; // 附件管理弹窗目标节点
function nodeAttachData(node) {
  try {
    const nd = nodeDataOf(node);
    const dd = (nd && nd.data) || nd || {};
    if (dd.attachUrl || dd.attachmentUrl) return { url: dd.attachmentUrl || dd.attachUrl, name: dd.attachmentName || dd.attachName || '附件' };
    return null;
  } catch (e) { return null; }
}
function setNodeAttachment(node, url, name) {
  try {
    const nd = nodeDataOf(node);
    const dd = (nd && nd.data) || nd;
    if (dd) { dd.attachmentUrl = url; dd.attachmentName = name; }
  } catch (e) {}
  // 附件图标由 injectAttachIcons() 注入（SVG 回形针 foreignObject，右上角，与原项目一致），
  // 不再使用库的 emoji 📎 图标机制（其节点缓存复用会导致图标偏移/文字左偏）
  // 清渲染缓存强制全量重建：确保附件节点文字按最新布局坐标绘制，不残留旧位置
  try { if (mind && mind.renderer) { mind.renderer.nodeCache = {}; mind.renderer.lastNodeCache = {}; } } catch (eC) {}
  try { mind.render(); } catch (e) {}
  markDirty();
  toast('附件已添加：' + name);
}
function removeNodeAttachment(node) {
  try {
    const nd = nodeDataOf(node);
    const dd = (nd && nd.data) || nd;
    // 释放媒体 blob objectURL 缓存
    try { revokeMediaUrl(dd.attachUrl || dd.attachmentUrl); } catch (eR) {}
    delete dd.attachUrl;
    delete dd.attachName;
    delete dd.attachmentUrl;
    delete dd.attachmentName;
    // 只移除 📎 图标（保留用户其它图标）：直写数据避免库部分重渲染+缓存复用残留文字位置
    try {
      const uid = nd && nd.data && nd.data.uid;
      let real = null;
      if (nd && nd._node) real = nd._node;                 // 渲染树挂载的节点实例
      if (!real && mind && mind.renderer && mind.renderer.renderTree) {
        const findInTree = (t) => {
          if (!t) return null;
          if (t.data && t.data.uid === uid) return t._node || null;
          if (t.children) for (const c of t.children) { const r = findInTree(c); if (r) return r; }
          return null;
        };
        real = findInTree(mind.renderer.renderTree) || null;
      }
      if (real && real.getData) {
        const icons = real.getData('icon') || [];
        const rest = icons.filter(i => i !== 'emoji_📎');
        try {
          const ndd = nodeDataOf(real);
          const ddd = (ndd && ndd.data) || ndd;
          if (ddd) { if (rest.length) ddd.icon = rest; else delete ddd.icon; }
        } catch (e2) {}
      } else {
        const icons = dd.icon || [];
        const rest = icons.filter(i => i !== 'emoji_📎');
        if (rest.length) dd.icon = rest; else delete dd.icon;
      }
    } catch (e) {
      const icons = dd.icon || [];
      const rest = icons.filter(i => i !== 'emoji_📎');
      if (rest.length) dd.icon = rest; else delete dd.icon;
    }
  } catch (e) {}
  // 清渲染缓存强制全量重建（同 setNodeAttachment）
  try { if (mind && mind.renderer) { mind.renderer.nodeCache = {}; mind.renderer.lastNodeCache = {}; } } catch (eC) {}
  try { mind.render(); } catch (e) {}
  markDirty();
  toast('附件已移除');
}
// 代码/文本附件预览（CodeMirror 内置，与在线版一致）
const CODE_MODE_DEFAULT = { js:'text/javascript', mjs:'text/javascript', cjs:'text/javascript', ts:'text/javascript', jsx:'text/javascript', tsx:'text/javascript', json:'application/json', map:'application/json', html:'text/html', htm:'text/html', vue:'text/html', xml:'application/xml', svg:'application/xml', css:'text/css', scss:'text/x-scss', less:'text/x-less', md:'text/x-markdown', markdown:'text/x-markdown', py:'text/x-python', python:'text/x-python', java:'text/x-java', kt:'text/x-kotlin', c:'text/x-csrc', h:'text/x-csrc', cpp:'text/x-c++src', cc:'text/x-c++src', cxx:'text/x-c++src', hpp:'text/x-c++src', hh:'text/x-c++src', cs:'text/x-csharp', go:'text/x-go', rs:'text/x-rust', php:'text/x-php', sh:'text/x-sh', bash:'text/x-sh', zsh:'text/x-sh', sql:'text/x-sql', rb:'text/x-ruby', yml:'text/x-yaml', yaml:'text/x-yaml', swift:'text/x-swift' };
const TEXT_EXT_DEFAULT = ['txt','log','ini','conf','cfg','csv','env','properties','tsv','gitignore','dockerfile','sass','plist'];
function isTextExt(ext) { if (!ext) return false; return !!CODE_MODE_DEFAULT[ext] || TEXT_EXT_DEFAULT.includes(ext); }
function utf8Encode(str) { try { return btoa(unescape(encodeURIComponent(str))); } catch (e) { try { return btoa(str); } catch (e2) { return ''; } } }
function utf8Decode(b64) { try { return decodeURIComponent(escape(atob(b64))); } catch (e) { try { return atob(b64); } catch (e2) { return ''; } } }

// 扩展名 → MIME（离线版无 file.type 时用）
function mimeFromName(name) {
  const ext = (name || '').split('.').pop().toLowerCase();
  const map = { png:'image/png', jpg:'image/jpeg', jpeg:'image/jpeg', gif:'image/gif', bmp:'image/bmp', webp:'image/webp', svg:'image/svg+xml', ico:'image/x-icon',
    mp3:'audio/mpeg', wav:'audio/wav', m4a:'audio/mp4', flac:'audio/flac', ogg:'audio/ogg', aac:'audio/aac',
    mp4:'video/mp4', webm:'video/webm', mov:'video/quicktime', mkv:'video/x-matroska', avi:'video/x-msvideo',
    txt:'text/plain', md:'text/markdown', json:'application/json', js:'text/javascript', py:'text/x-python', c:'text/x-csrc', cpp:'text/x-c++src', h:'text/x-csrc', hpp:'text/x-c++src', java:'text/x-java', html:'text/html', css:'text/css', xml:'application/xml', sql:'text/x-sql', sh:'text/x-sh', pdf:'application/pdf', docx:'application/vnd.openxmlformats-officedocument.wordprocessingml.document', xlsx:'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', zip:'application/zip', rar:'application/x-rar-compressed', '7z':'application/x-7z-compressed' };
  return map[ext] || 'application/octet-stream';
}
// 图片尺寸探测
function getImageSize(src) {
  return new Promise(res => {
    const im = new Image();
    im.onload = () => res({ width: im.naturalWidth, height: im.naturalHeight });
    im.onerror = () => res({ width: 210, height: 157 });
    im.src = src;
  });
}
// 渲染节点 + 同步数据树（getData 递归按 uid 改），保证保存/重渲染后不丢失
function setNodeDataPersist(node, data, skipRender) {
  try {
    if (!skipRender) {
      mind.renderer.setNodeDataRender(node, data);
    } else {
      // 跳过 setNodeDataRender（它会重建节点、丢失新节点的布局位置）：手动写渲染树/实例数据
      try { if (node && node.data) { for (const k in data) node.data[k] = data[k]; } } catch (e1) {}
      try {
        const nd = nodeDataOf(node);
        const dd = (nd && nd.data) || nd;
        if (dd) { for (const k in data) dd[k] = data[k]; }
      } catch (e2) {}
    }
    const dd = nodeDataOf(node);
    const uid = ((dd && dd.data) || dd || {}).uid;
    if (uid) {
      const d = mind.getData();
      const walk = (n) => {
        if (!n) return;
        if (n.data && n.data.uid === uid) { const nd = n.data; for (const k in data) { nd[k] = data[k]; } return; }
        (n.children || []).forEach(walk);
      };
      walk(d.root || d);
    }
  } catch (e) {}
}
// 仅写附件字段（不设 📎 图标；媒体节点内容即附件）。
// 用自定义字段 attachUrl/attachName（库不识别，不会触发节点内容重建、不丢布局位置）
function setNodeMediaAttachment(node, url, name) {
  setNodeDataPersist(node, { attachUrl: url, attachName: name }, true);
}
// 从渲染树按 uid 找节点实例
function findNodeInstance(uid) {
  try {
    const findInTree = (t) => {
      if (!t) return null;
      if (t.data && t.data.uid === uid) return t._node || null;
      if (t.children) for (const c of t.children) { const r = findInTree(c); if (r) return r; }
      return null;
    };
    return findInTree(mind.renderer.renderTree);
  } catch (e) { return null; }
}
// 与在线版一致：在当前选中节点下新建子节点，图片/音视频内嵌渲染，文件显示文件名（红字虚线框）
function insertFileToNode(file) {
  if (!file || !mind) return;
  try {
    // 未选中节点时：库 INSERT 依赖选中/渲染树，直接走数据树根插入（可靠且不偏移）
    let hasActive = false;
    try { const l = mind.renderer.activeNodeList; hasActive = !!(l && l.length); } catch (eA) {}
    if (!hasActive) {
      try {
        const d = mind.getData();
        if (!d.root) d.root = { data: { text: '中心主题' }, children: [] };
        if (!d.root.children) d.root.children = [];
        const nd = { data: { text: '' }, children: [] };
        d.root.children.push(nd);
        try { mind.render(); } catch (eR) {}
        setTimeout(() => { renderFileNode(nd, file); }, 600);
        return;
      } catch (eQ) {}
    }
    // 插入前父（当前选中节点；无选中 → 根）
    let parent = null, parentUid = null;
    try { const l = mind.renderer.activeNodeList; if (l && l[0]) parent = l[0]; } catch (e) {}
    if (parent) { try { const dd = nodeDataOf(parent); parentUid = ((dd && dd.data) || dd || {}).uid || null; } catch (e2) {} }
    if (!parentUid) { try { const d = mind.getData(); parentUid = (d.root && d.root.uid) || null; } catch (e3) {} }
    // 关键：用"渲染树节点"作为 INSERT 目标（库对渲染树节点插入会正确异步布局到父旁；
    // 传节点实例/无参插入会把新节点留在画布原点或布局错位）
    let inserted = false;
    let insertTarget = null;
    if (parentUid) {
      try {
        const findInTree = (t) => {
          if (!t) return null;
          if (t.data && t.data.uid === parentUid) return t._node || null;   // 库命令需要 Node 实例
          if (t.children) for (const c of t.children) { const r = findInTree(c); if (r) return r; }
          return null;
        };
        insertTarget = findInTree(mind.renderer.renderTree);
      } catch (e0) {}
    }
    if (insertTarget) {
      try { mind.execCommand('INSERT_CHILD_NODE', insertTarget); inserted = true; } catch (e0b) {}
    }
    if (!inserted && parentUid) {
      // 兜底：库 INSERT 失败（如未选中节点时）→ 直接往数据树父节点追加子节点，渲染后写媒体
      try {
        const d = mind.getData();
        const walkP = (n) => {
          if (!n) return null;
          if (n.data && n.data.uid === parentUid) return n;
          for (const c of (n.children || [])) { const r = walkP(c); if (r) return r; }
          return null;
        };
        const ptD = walkP(d);
        if (ptD) {
          if (!ptD.children) ptD.children = [];
          const nd = { data: { text: '' }, children: [] };
          ptD.children.push(nd);
          inserted = true;
          try { mind.render(); } catch (eR) {}
          setTimeout(() => { renderFileNode(nd, file); }, 600);
          return; // 已完成（数据树直接插入），跳过后续库插入流程
        }
      } catch (eF) {}
    }
    if (!inserted) {
      try { if (parent) mind.renderer.currentNode = parent; } catch (e0c) {}
      try { mind.execCommand('INSERT_CHILD_NODE'); } catch (e0d) {}
    }
    // 注意：不能在这里立即 hideEditTextBox——库的 INSERT 布局是异步的，立即取消编辑会打断布局，
    // 导致新节点留在画布原点（偏移）。等 120ms 布局完成后，renderFileNode 内统一处理。
    // 库在 execCommand 后异步刷新渲染树+异步布局，多等一会（布局完成后新节点才在父旁）
    setTimeout(() => {
      try {
        let child = null;
        if (parentUid) {
          try {
            const findInTree = (t) => {
              if (!t) return null;
              if (t.data && t.data.uid === parentUid) return t;
              if (t.children) for (const c of t.children) { const r = findInTree(c); if (r) return r; }
              return null;
            };
            const pt = findInTree(mind.renderer.renderTree);
            if (pt && pt.children && pt.children.length) {
              const last = pt.children[pt.children.length - 1];
              child = last._node || null;
            }
          } catch (e4) {}
        }
        if (!child) { try { const l = mind.renderer.activeNodeList; if (l && l[0]) child = l[0]; } catch (e5) {} }
        if (!child) {
          // 兜底：未选中/渲染树未挂载时，从数据树取父节点的最后一个子节点（保证插入根场景不丢附件）
          try {
            const d = mind.getData();
            const walk2 = (n) => {
              if (!n) return null;
              if (n.data && n.data.uid === parentUid) return n;
              for (const c of (n.children || [])) { const r2 = walk2(c); if (r2) return r2; }
              return null;
            };
            const pt2 = walk2(d);
            if (pt2 && pt2.children && pt2.children.length) child = pt2.children[pt2.children.length - 1];
          } catch (e7) {}
        }
        if (!child) return;
        renderFileNode(child, file);
      } catch (e6) {}
    }, 600);
  } catch (e) {}
}
// 直接写节点样式数据字段（不 execCommand，避免库重建新节点导致布局位置丢失到画布原点）
function applyNodeStylesDirect(node, styles) {
  try {
    const nd = nodeDataOf(node);
    const dd = (nd && nd.data) || nd;
    if (!dd || !styles) return;
    const patch = {};
    if (styles.shape) patch.shape = styles.shape;
    if (styles.borderRadius != null) patch.borderRadius = styles.borderRadius;
    if (styles.borderWidth != null) patch.borderWidth = styles.borderWidth;
    if (styles.borderColor) patch.borderColor = styles.borderColor;
    if (styles.borderDasharray) patch.borderDasharray = styles.borderDasharray;
    if (styles.fillColor) patch.fillColor = styles.fillColor;
    if (styles.paddingX != null) patch.paddingX = styles.paddingX;
    if (styles.paddingY != null) patch.paddingY = styles.paddingY;
    if (styles.textColor) patch.textColor = styles.textColor;
    for (const k in patch) dd[k] = patch[k];
    // 同步数据树（getData 递归按 uid），保证保存/重开后样式不丢；skipRender 直写不重建
    if (Object.keys(patch).length) setNodeDataPersist(node, patch, true);
  } catch (e) {}
}
// 在已创建的子节点上按类型渲染媒体/文件
function renderFileNode(child, file) {
  try {
    const name = file.name || '附件';
    const type = file.type || mimeFromName(name);
    const src = file.dataUrl || file.relPath || '';
    if (!src) return;
    // 统一流程：先退出 INSERT 后库自动进入的文本编辑态（避免其把内容重置回"新主题"），再写媒体内容
    const apply = (data, styles) => {
      try { if (mind.renderer && mind.renderer.textEdit) mind.renderer.textEdit.hideEditTextBox(); } catch (eH) {}
      // skipRender：跳过 setNodeDataRender，避免库重建新节点导致布局位置丢失（画布原点）
      setNodeDataPersist(child, data, true);
      try { if (styles) applyNodeStylesDirect(child, styles); } catch (e2) {}
      setNodeMediaAttachment(child, src, name);
      mind.render();
      markDirty();
      toast('已插入: ' + name);
    };
    if (type.indexOf('image/') === 0) {
      const objUrl = resolveMediaUrl(src);
      getImageSize(objUrl).then(size => {
        try {
          const MAX_W = 360, MAX_H = 240;
          const scale = Math.min(1, MAX_W / (size.width || 1), MAX_H / (size.height || 1));
          const w = Math.max(100, Math.round((size.width || 210) * scale));
          const h = Math.max(75, Math.round((size.height || 157) * scale));
          apply({ richText: true, text:
            '<div style="position:relative;width:'+w+'px;height:'+h+'px;overflow:hidden;border-radius:12px;line-height:0">'+
            '<img src="'+objUrl+'" style="width:100%;height:100%;object-fit:cover;display:block;margin:0"></div>' },
            { shape:'roundedRectangle', borderRadius:12, borderWidth:0, borderColor:'transparent', fillColor:'transparent', paddingX:0, paddingY:0 });
          // 图片节点尺寸由小变大后强制重排一次，保证子节点组与父节点垂直居中（不上下偏移）
          setTimeout(() => { try { mind.setLayout(doc.layout || 'logicalStructure'); } catch (eL) {} }, 180);
        } catch (e3) {}
      });
    } else if (type.indexOf('audio/') === 0) {
      apply({ richText: true, text:
        '<div style="position:relative;width:280px;height:40px;overflow:hidden;border-radius:12px;line-height:0;background:#000">'+
        '<audio controls style="width:100%;height:100%;display:block" src="'+resolveMediaUrl(src)+'"></audio></div>' }, null);
    } else if (type.indexOf('video/') === 0) {
      apply({ richText: true, text:
        '<div style="position:relative;width:300px;height:170px;overflow:hidden;border-radius:12px;background:#000;line-height:0">'+
        '<video controls style="width:100%;height:100%;object-fit:cover;display:block;background:#000" src="'+resolveMediaUrl(src)+'"></video></div>' },
        { shape:'roundedRectangle', borderRadius:12, borderWidth:0, borderColor:'transparent', fillColor:'transparent', paddingX:0, paddingY:0 });
    } else {
      // 普通文件：文件名（红字虚线框，双击打开/预览）
      // 纯文本渲染（同原项目），渲染结束后由 fixAttachNodeTexts() 做 DOM 兜底对齐（文字回框内）
      setNodeMediaAttachment(child, src, name);
      apply({ text: name, richText: false },
        { shape:'roundedRectangle', borderRadius:12, borderWidth:1, borderColor:'#C9CDD6', borderDasharray:'2 4', fillColor:'#F4F5F7', paddingX:6, paddingY:6, textColor:'#e53935' });
    }
    // 附件字段写入后：触发一次完整渲染（render_end 会跑 fixAttachNodeTexts+injectAttachIcons），
    // 并显式再注入一次别针——保证"粘贴附件即显示别针"，不依赖下次编辑保存。
    setTimeout(() => {
      try { mind.render(); } catch (e) {}
      try { injectAttachIcons(); } catch (e) {}
    }, 120);
  } catch (e) {}
}

// 文本/代码附件：CodeMirror 高亮只读 → 编辑 → 保存（base64 写回节点，无需联网）
// ---- 附件弹窗全屏模式（弹窗 ⛶ 进入；ESC 或再次 ⛶ / 弹窗 × 退出） ----
let _fsEl = null;
function fsEscHandler(e) { if (e.key === 'Escape') exitFullscreen(); }
function enterFullscreen(el) {
  _fsEl = el;
  el.classList.add('att-fullscreen');
  document.addEventListener('keydown', fsEscHandler);
}
function exitFullscreen() {
  if (_fsEl) { _fsEl.classList.remove('att-fullscreen'); _fsEl = null; }
  document.removeEventListener('keydown', fsEscHandler);
}

// startEditable=true 时（markdown 入口）直接进入可编辑+保存，无需再点一次"编辑"
async function openTextPreview(node, att, startEditable) {
  try {
    exitFullscreen(); // 打开新附件前清理可能残留的全屏状态
    // 兜底：打开 CodeMirror 前强制隐藏库文本编辑框（库的 show 晚于双击监听，需状态+DOM 双重复位）
    forceHideTextEdit();
    if (typeof CodeMirror === 'undefined') { toast('预览组件缺失', 'error'); return; }
    const name = att.name || '附件';
    const ext = (name.split('.').pop() || '').toLowerCase();
    const mode = CODE_MODE_DEFAULT[ext] || null;
    let text = '';
    try { text = await loadAttachmentText(att); } catch (e) { text = ''; }
    let wrap = document.getElementById('att-preview');
    if (wrap) wrap.remove();
    wrap = document.createElement('div');
    wrap.id = 'att-preview';
    wrap.innerHTML = '<div class="attp-mask"></div><div class="attp-box"><div class="attp-head"><span class="attp-title"></span><button class="attp-close">×</button></div><div class="attp-body"></div></div>';
    document.body.appendChild(wrap);
    wrap.querySelector('.attp-title').textContent = name;
    const close = () => { if (wrap && wrap.parentNode) wrap.remove(); forceHideTextEdit(); exitFullscreen(); };
    wrap.querySelector('.attp-close').addEventListener('click', close);
    wrap.querySelector('.attp-mask').addEventListener('click', close);
    const body = wrap.querySelector('.attp-body');
    // 库可能在双击监听之后才异步启动文本编辑：延迟补发强制隐藏
    setTimeout(forceHideTextEdit, 180);
    setTimeout(forceHideTextEdit, 600);
    const cmWrap = document.createElement('div');
    cmWrap.style.cssText = 'border:1px solid #333;border-radius:10px;overflow:hidden;background:#1e1e1e';
    body.appendChild(cmWrap);
    const cm = CodeMirror(cmWrap, {
      value: text,
      mode: mode || null,
      lineNumbers: true,
      lineWrapping: false,
      readOnly: 'nocursor',
      indentUnit: 2,
      tabSize: 2,
      scrollbarStyle: 'null'
    });
    cm.setSize('100%', '50vh');
    // ---- 富媒体工具栏（仅编辑态显示）：插入公式 / 图片 / 视频 / 音频 ----
    const mediaTool = document.createElement('div');
    mediaTool.style.cssText = 'display:none;gap:6px;padding:6px 8px;background:#262a38;border-bottom:1px solid #333;flex-wrap:wrap;align-items:center';
    const mkToolBtn = (txt, title) => {
      const b = document.createElement('button');
      b.textContent = txt; b.title = title || '';
      b.style.cssText = 'padding:3px 10px;background:#3a4156;color:#dbe2f0;border:0;border-radius:6px;cursor:pointer;font-size:12px';
      return b;
    };
    const mdInsFile = document.createElement('input');
    mdInsFile.type = 'file';
    mdInsFile.multiple = true;
    mdInsFile.accept = 'image/*,video/*,audio/*';
    mdInsFile.style.display = 'none';
    async function mdInsertMedia(file, kind) {
      try {
        const ab = await file.arrayBuffer();
        const b64 = arrayBufferToBase64(ab);
        const ext0 = (file.name.split('.').pop() || '').toLowerCase().replace(/[^a-z0-9]/g, '');
        const ext = ext0 || (kind === 'img' ? 'png' : kind === 'video' ? 'mp4' : 'mp3');
        const rel = 'attachments/md_' + Date.now() + '_' + Math.random().toString(36).slice(2, 8) + '.' + ext;
        const r = await callNative('writeAttachment', rel, b64);
        if (!r || !r.ok) { toast('媒体写入失败', 'error'); return; }
        const url = 'https://media.local/' + rel.split('/').map(encodeURIComponent).join('/');
        if (kind === 'img') cm.replaceSelection('![](' + url + ')');
        else if (kind === 'video') cm.replaceSelection('\n<video src="' + url + '" controls style="max-width:100%"></video>\n');
        else cm.replaceSelection('\n<audio src="' + url + '" controls></audio>\n');
      } catch (e) { toast('插入媒体失败：' + e.message, 'error'); }
    }
    const btnFormula = mkToolBtn('∑ 公式', '插入 LaTeX 公式（$$...$$）');
    btnFormula.addEventListener('click', () => {
      const sel = cm.getSelection();
      const tex = window.prompt('输入 LaTeX 公式内容', sel || '');
      if (tex === null) return;
      cm.replaceSelection('$$' + tex + '$$');
      markDirty();
    });
    const btnImg = mkToolBtn('图片', '插入图片');
    btnImg.addEventListener('click', () => { mdInsFile.accept = 'image/*'; mdInsFile.click(); });
    const btnVid = mkToolBtn('视频', '插入视频');
    btnVid.addEventListener('click', () => { mdInsFile.accept = 'video/*'; mdInsFile.click(); });
    const btnAud = mkToolBtn('音频', '插入音频');
    btnAud.addEventListener('click', () => { mdInsFile.accept = 'audio/*'; mdInsFile.click(); });
    mdInsFile.addEventListener('change', () => {
      const files = Array.from(mdInsFile.files || []);
      const kind = mdInsFile.accept.indexOf('image') === 0 ? 'img' : mdInsFile.accept.indexOf('video') === 0 ? 'video' : 'audio';
      (async () => { for (const f of files) await mdInsertMedia(f, kind); markDirty(); })();
      mdInsFile.value = '';
    });
    mediaTool.appendChild(btnFormula); mediaTool.appendChild(btnImg);
    mediaTool.appendChild(btnVid); mediaTool.appendChild(btnAud);
    cmWrap.insertBefore(mediaTool, cmWrap.firstChild);
    // 粘贴富媒体：编辑态下图片/视频/音频文件直接写入附件目录并插入引用代码
    cmWrap.addEventListener('paste', (e) => {
      if (cm.getOption('readOnly')) return;
      const cd = e.clipboardData;
      if (!cd || !cd.files || cd.files.length === 0) return;
      const media = Array.from(cd.files).filter(f => {
        const t = (f.type || '').toLowerCase();
        return t.indexOf('image/') === 0 || t.indexOf('video/') === 0 || t.indexOf('audio/') === 0;
      });
      if (media.length === 0) return;
      e.preventDefault();
      const kd = (m) => (m.type || '').indexOf('image/') === 0 ? 'img' : (m.type || '').indexOf('video/') === 0 ? 'video' : 'audio';
      (async () => { for (const m of media) await mdInsertMedia(m, kd(m)); markDirty(); })();
    });
    // 操作按钮（编辑/取消编辑/保存）放到弹窗头部右上角（标题右侧、× 左侧），与 markdown 预览弹窗一致
    const bar = document.createElement('div');
    bar.style.cssText = 'display:flex;align-items:center;gap:8px;margin-left:auto';
    const editBtn = document.createElement('button');
    editBtn.textContent = '编辑';
    editBtn.className = 'attp-dl-btn';
    editBtn.style.cssText = 'padding:5px 14px;background:#0e7c86;color:#fff;border:0;border-radius:8px;cursor:pointer;font-size:12px';
    const cancelEdit = document.createElement('button');
    cancelEdit.textContent = '取消编辑';
    cancelEdit.style.cssText = 'padding:5px 14px;background:#555;color:#fff;border:0;border-radius:8px;cursor:pointer;font-size:12px;display:none';
    const saveBtn = document.createElement('button');
    saveBtn.textContent = '保存';
    saveBtn.style.cssText = 'padding:5px 14px;background:#0e7c86;color:#fff;border:0;border-radius:8px;cursor:pointer;font-size:12px;display:none';
    bar.appendChild(cancelEdit); bar.appendChild(saveBtn); bar.appendChild(editBtn);
    const closeBtn = wrap.querySelector('.attp-close');
    closeBtn.insertAdjacentElement('beforebegin', bar);
    // 全屏按钮（头部右上角 × 左侧）
    const fsBtn = document.createElement('button');
    fsBtn.textContent = '⛶';
    fsBtn.title = '全屏';
    fsBtn.style.cssText = 'width:28px;height:28px;border:0;background:#f1f3f5;border-radius:8px;font-size:15px;cursor:pointer;color:#555;line-height:1';
    fsBtn.addEventListener('click', () => {
      const box = wrap.querySelector('.attp-box');
      if (box.classList.contains('att-fullscreen')) { exitFullscreen(); }
      else {
        enterFullscreen(box);
        try { const cmEl = wrap.querySelector('.CodeMirror'); if (cmEl) cmEl.style.height = 'calc(100vh - 76px)'; } catch (e) {}
      }
    });
    closeBtn.insertAdjacentElement('beforebegin', fsBtn);
    // markdown 入口直接进入可编辑+保存
    if (startEditable) {
      cm.setOption('readOnly', false);
      editBtn.style.display = 'none';
      cancelEdit.style.display = ''; saveBtn.style.display = '';
      mediaTool.style.display = 'flex';
      cm.focus();
    }
    editBtn.addEventListener('click', () => {
      cm.setOption('readOnly', false);
      editBtn.style.display = 'none';
      cancelEdit.style.display = ''; saveBtn.style.display = '';
      mediaTool.style.display = 'flex';
      cm.focus();
    });
    cancelEdit.addEventListener('click', () => {
      cm.setOption('readOnly', 'nocursor');
      editBtn.style.display = '';
      mediaTool.style.display = 'none';
      cancelEdit.style.display = 'none'; saveBtn.style.display = 'none';
    });
    saveBtn.addEventListener('click', () => {
      saveBtn.disabled = true; saveBtn.textContent = '保存中...';
      try {
        const newB64 = utf8Encode(cm.getValue());
        setNodeAttachment(node, 'data:text/plain;charset=utf-8;base64,' + newB64, name);
        // 清除旧的外置文件引用（attachUrl/attachName），确保下次读取走刚保存的内嵌新内容
        try { const nd = nodeDataOf(node); const dd = (nd && nd.data) || nd; if (dd) { delete dd.attachUrl; delete dd.attachName; } } catch (e2) {}
        close();
        toast('已保存');
        markDirty();
        forceHideTextEdit();
      } catch (e) { toast('保存失败: ' + e.message, 'error'); }
      saveBtn.disabled = false; saveBtn.textContent = '保存';
    });
  } catch (e) {}
}

function dataUrlToBlob(u) {
  const parts = String(u).split(',');
  const mime = (parts[0].match(/data:([^;]+)/) || [, 'application/octet-stream'])[1];
  const bin = atob(parts[1] || '');
  const bytes = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
  return new Blob([bytes], { type: mime });
}

// 媒体渲染优化：dataUrl → Blob objectURL 缓存（避免大文件每次渲染都解码 base64 导致卡顿）
const _mediaUrlCache = new Map();
function mediaObjectUrl(dataUrl) {
  if (!dataUrl || dataUrl.indexOf('data:') !== 0) return dataUrl;
  if (_mediaUrlCache.has(dataUrl)) return _mediaUrlCache.get(dataUrl);
  try {
    const u = URL.createObjectURL(dataUrlToBlob(dataUrl));
    _mediaUrlCache.set(dataUrl, u);
    return u;
  } catch (e) { return dataUrl; }
}
function revokeMediaUrl(dataUrl) {
  if (_mediaUrlCache.has(dataUrl)) {
    try { URL.revokeObjectURL(_mediaUrlCache.get(dataUrl)); } catch (e) {}
    _mediaUrlCache.delete(dataUrl);
  }
}

// 附件 URL 解析（双模式）：外置相对路径 attachments/xxx → media.local 虚拟主机 URL（浏览器流式加载）；
// dataUrl 内嵌 → blob objectURL；其他（blob:/http:）原样
function resolveMediaUrl(u) {
  if (!u || typeof u !== 'string') return u || '';
  if (u.indexOf('attachments/') === 0) {
    try { return 'https://media.local/' + u.split('/').map(encodeURIComponent).join('/'); }
    catch (e) { return u; }
  }
  if (u.indexOf('data:') === 0) return mediaObjectUrl(u);
  return u;
}
// 附件文本读取（markdown/文本预览）：data: → 解码；外置相对路径 → fetch(media.local)
async function loadAttachmentText(att) {
  const u = (att && att.url) || '';
  if (u.indexOf('data:') === 0) {
    const b64 = u.split(',')[1] || '';
    try { return decodeURIComponent(escape(atob(b64))); } catch (e) { try { return atob(b64); } catch (e2) { return ''; } }
  }
  try { const r = await fetch(resolveMediaUrl(u)); return await r.text(); } catch (e) { return ''; }
}
// 附件二进制读取（docx/xlsx/pdf 预览）：返回 ArrayBuffer
async function loadAttachmentBuffer(att) {
  const u = (att && att.url) || '';
  if (u.indexOf('data:') === 0) {
    try { return dataUrlToBlob(u).arrayBuffer(); } catch (e) { throw e; }
  }
  const r = await fetch(resolveMediaUrl(u));
  if (!r.ok) throw new Error('附件读取失败');
  return r.arrayBuffer();
}

// 外置模式：把 File 写入 exe/data/attachments，返回相对路径 attachments/xxx（失败返回 null）
async function storeFileExternal(file) {
  try {
    const buf = await file.arrayBuffer();
    const bytes = new Uint8Array(buf);
    let bin = '';
    const CH = 0x8000;
    for (let i = 0; i < bytes.length; i += CH) {
      bin += String.fromCharCode.apply(null, bytes.subarray(i, i + CH));
    }
    const b64 = btoa(bin);
    const safeName = String(file.name || 'file').replace(/[\\/:*?"<>|]/g, '_');
    const rel = 'attachments/att_' + Date.now() + '_' + safeName;
    const r = await callNative('writeAttachment', rel, b64);
    if (r && r.ok) return rel;
    return null;
  } catch (e) { return null; }
}
// 统一插入：hasNative 走外置（快），否则内嵌 dataUrl；toSelf=true 时挂到当前选中节点本身（而非新建子节点）
async function insertExternalOrInline(file, name, type, toSelf) {
  const fin = (f) => { if (toSelf) attachToActiveNode(f); else insertFileToNode(f); };
  if (hasNative) {
    const rel = await storeFileExternal(file);
    if (rel) { fin({ name: name || file.name, type: type || file.type || mimeFromName(name || file.name), relPath: rel }); return; }
  }
  const rd = new FileReader();
  rd.onload = () => fin({ name: name || file.name, type: type || file.type || mimeFromName(name || file.name), dataUrl: String(rd.result) });
  rd.readAsDataURL(file);
}
// 把附件直接挂到当前选中节点本身（无选中/无 currentNode 时回退为新建子节点）
function attachToActiveNode(file) {
  try {
    let node = null;
    try { const l = mind.renderer.activeNodeList; if (l && l[0]) node = l[0]; } catch (e) {}
    if (!node) { try { node = mind.renderer.currentNode; } catch (e) {} }
    if (!node || !node.nodeData) { insertFileToNode(file); return; }
    renderFileNode(node, file);
  } catch (e) { try { insertFileToNode(file); } catch (e2) {} }
}
// 粘贴纯文本：在当前选中节点下新建子节点（替代被拦截的库 ctrl+v 文本粘贴）
function insertTextAsNode(text) {
  try {
    let node = null;
    try { const l = mind.renderer.activeNodeList; if (l && l[0]) node = l[0]; } catch (e) {}
    if (!node) { try { node = mind.renderer.currentNode; } catch (e) {} }
    if (node && node.nodeData) {
      mind.execCommand('INSERT_CHILD_NODE', node, [], { text: text });
      return;
    }
  } catch (e) {}
  try { mind.execCommand('INSERT_CHILD_NODE'); } catch (e) {}
  try {
    const d = mind.getData();
    if (d && d.root && d.root.children && d.root.children.length) {
      const last = d.root.children[d.root.children.length - 1];
      if (last && last.data) last.data.text = text;
      mind.render();
    }
  } catch (e2) {}
}

// 树级媒体 src 优化：dataUrl ↔ blob objectURL（渲染用 blob 流畅，保存前还原 dataUrl 保证文件自包含）
function walkTree(node, fn) {
  if (!node) return;
  try { fn(node); } catch (e) {}
  (node.children || []).forEach(c => walkTree(c, fn));
}
function patchTreeMediaSrc(root) {
  walkTree(root, n => {
    const t = n && n.data && n.data.text;
    if (typeof t === 'string' && (t.indexOf('data:') !== -1 || t.indexOf('attachments/') !== -1)) {
      n.data.text = t
        .replace(/src="(data:[^"]+)"/g, (m, d) => {
          try { return 'src="' + mediaObjectUrl(d) + '"'; } catch (e) { return m; }
        })
        .replace(/src="(attachments\/[^"]+)"/g, (m, r) => {
          try { return 'src="' + resolveMediaUrl(r) + '"'; } catch (e) { return m; }
        });
    }
  });
}
function restoreTreeMediaSrc(root) {
  walkTree(root, n => {
    const t = n && n.data && n.data.text;
    if (typeof t === 'string' && t.indexOf('blob:') !== -1) {
      n.data.text = t.replace(/src="(blob:[^"]+)"/g, (m, b) => {
        for (const [d, u] of _mediaUrlCache) { if (u === b) return 'src="' + d + '"'; }
        return m;
      });
    }
  });
}

// xlsx 网格编辑器（SheetJS 读写）：多表切换、单元格直接输入（数字自动转 number、= 开头识别为公式）、行列增删
let _xlsxState = null;
function buildXlsxEditor(wb, body) {
  const sheets = wb.SheetNames;
  const aoaMap = {};
  sheets.forEach(sn => { aoaMap[sn] = XLSX.utils.sheet_to_json(wb.Sheets[sn], { header: 1, defval: '' }); });
  const state = { sheets, cur: 0, aoaMap };
  _xlsxState = state;
  body.innerHTML = '<div class="xlsx-wrap">'
    + '<div class="xlsx-toolbar">'
    + '<span class="xlsx-label">工作表：</span><select class="xlsx-sheets"></select>'
    + '<button class="xlsx-tbtn" data-op="addRow">＋ 行</button>'
    + '<button class="xlsx-tbtn" data-op="delRow">－ 行</button>'
    + '<button class="xlsx-tbtn" data-op="addCol">＋ 列</button>'
    + '<button class="xlsx-tbtn" data-op="delCol">－ 列</button>'
    + '<span class="xlsx-hint">单元格直接输入，以 = 开头为公式</span>'
    + '</div><div class="xlsx-gridwrap"><table class="xlsx-grid"></table></div></div>';
  const sel = body.querySelector('.xlsx-sheets');
  sheets.forEach((sn, i) => { const o = document.createElement('option'); o.textContent = sn; o.value = i; sel.appendChild(o); });
  sel.onchange = () => { state.cur = +sel.value; renderGrid(); };
  body.querySelectorAll('.xlsx-tbtn').forEach(b => b.onclick = () => {
    const op = b.dataset.op;
    const sn = state.sheets[state.cur];
    const aoa = state.aoaMap[sn];
    if (op === 'addRow') aoa.push(Array(aoa[0] ? aoa[0].length : 1).fill(''));
    if (op === 'delRow') { if (aoa.length > 1) aoa.pop(); else aoa.length = 1; }
    if (op === 'addCol') aoa.forEach(r => r.push(''));
    if (op === 'delCol') { const max = Math.max.apply(null, aoa.map(r => r.length)); if (max > 1) aoa.forEach(r => r.pop()); }
    renderGrid();
  });
  function colName(i) {
    let s = ''; i = i + 1;
    while (i > 0) { const m = (i - 1) % 26; s = String.fromCharCode(65 + m) + s; i = Math.floor((i - 1) / 26); }
    return s;
  }
  function renderGrid() {
    const sn = state.sheets[state.cur];
    const aoa = state.aoaMap[sn];
    const grid = body.querySelector('.xlsx-grid');
    const rows = Math.max(aoa.length, 1);
    const cols = Math.max(aoa[0] ? aoa[0].length : 1, 1);
    let html = '<thead><tr><th class="xlsx-corner"></th>';
    for (let c = 0; c < cols; c++) html += '<th class="xlsx-hd">' + colName(c) + '</th>';
    html += '</tr></thead><tbody>';
    for (let r = 0; r < rows; r++) {
      html += '<tr><th class="xlsx-rh">' + (r + 1) + '</th>';
      for (let c = 0; c < cols; c++) {
        let v = (aoa[r] && aoa[r][c] !== undefined && aoa[r][c] !== null) ? String(aoa[r][c]) : '';
        html += '<td><input class="xlsx-cell" data-r="' + r + '" data-c="' + c + '" value="' + v.replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;') + '"></td>';
      }
      html += '</tr>';
    }
    html += '</tbody>';
    grid.innerHTML = html;
    grid.addEventListener('input', (e) => {
      const t = e.target;
      if (t.classList && t.classList.contains('xlsx-cell')) {
        const r = +t.dataset.r, c = +t.dataset.c;
        if (!state.aoaMap[sn][r]) state.aoaMap[sn][r] = [];
        let v = t.value;
        const tv = v.trim();
        if (/^[+-]?\d+(\.\d+)?$/.test(tv)) v = Number(tv);
        state.aoaMap[sn][r][c] = v;
      }
    });
  }
  renderGrid();
  return state;
}

// docx 富文本编辑态：工具条（execCommand）+ contenteditable，保存时 HTML → docx（docx.js 重建写回节点）
function enterDocxEdit(node, att, html) {
  const body = $('docPreviewBody');
  body.innerHTML = '<div class="docx-edit">'
    + '<div class="docx-tb">'
    + '<button data-cmd="bold" title="加粗"><b>B</b></button>'
    + '<button data-cmd="italic" title="斜体"><i>I</i></button>'
    + '<button data-cmd="underline" title="下划线"><u>U</u></button>'
    + '<button data-cmd="strikeThrough" title="删除线"><s>S</s></button>'
    + '<span class="docx-sep"></span>'
    + '<button data-cmd="formatBlock" data-val="H1" title="一级标题">H1</button>'
    + '<button data-cmd="formatBlock" data-val="H2" title="二级标题">H2</button>'
    + '<button data-cmd="formatBlock" data-val="H3" title="三级标题">H3</button>'
    + '<span class="docx-sep"></span>'
    + '<button data-cmd="insertUnorderedList" title="无序列表">• 列表</button>'
    + '<button data-cmd="insertOrderedList" title="有序列表">1. 列表</button>'
    + '<span class="docx-sep"></span>'
    + '<button data-cmd="justifyLeft" title="左对齐">左</button>'
    + '<button data-cmd="justifyCenter" title="居中">中</button>'
    + '<span class="docx-hint">直接编辑，工具栏即时生效</span>'
    + '</div>'
    + '<div class="docx-editable" contenteditable="true"></div></div>';
  body.querySelector('.docx-editable').innerHTML = html || '';
  body.querySelectorAll('.docx-tb button').forEach(b => b.addEventListener('mousedown', (e) => {
    e.preventDefault();
    const cmd = b.dataset.cmd;
    const val = b.dataset.val || null;
    const ed = body.querySelector('.docx-editable');
    try { ed.focus(); } catch (eF) {}
    try { document.execCommand(cmd, false, val); } catch (eC) {}
  }));
  let sb = document.getElementById('docDocxSaveBtn');
  if (!sb) {
    sb = document.createElement('button');
    sb.id = 'docDocxSaveBtn';
    sb.textContent = '保存';
    sb.style.cssText = 'padding:5px 14px;background:#0e7c86;color:#fff;border:0;border-radius:8px;cursor:pointer;font-size:12px;display:none';
    const anchor = document.getElementById('docFsBtn') || document.getElementById('docPreviewClose');
    if (anchor) anchor.insertAdjacentElement('beforebegin', sb);
  }
  sb.style.display = '';
  sb.onclick = async () => {
    try {
      const html2 = body.querySelector('.docx-editable').innerHTML;
      if (!window.docx) { toast('缺少 docx 生成库', 'error'); return; }
      const b64 = await htmlToDocx(html2);
      if (!b64) { toast('docx 生成失败', 'error'); return; }
      setNodeAttachment(node, 'data:application/vnd.openxmlformats-officedocument.wordprocessingml.document;base64,' + b64, att.name);
      $('docPreview').style.display = 'none';
      $('docPreviewBody').innerHTML = '';
      sb.style.display = 'none';
      exitFullscreen();
      toast('已保存: ' + att.name);
    } catch (eS) { toast('保存失败: ' + (eS && eS.message || eS), 'error'); }
  };
}
// 简化 HTML → docx：段落/标题/列表/引用 + 行内粗体/斜体/下划线/删除线/代码 + 图片（ImageRun）
async function htmlToDocx(html) {
  const D = window.docx;
  if (!D) return null;
  const tmp = document.createElement('div');
  tmp.innerHTML = html;
  // 图片归一化：blob/http → data URL；gif/webp → canvas 转 png；统一尺寸上限
  const imgEls = Array.from(tmp.querySelectorAll('img'));
  for (const img of imgEls) {
    let src = img.getAttribute('src') || '';
    const w = img.naturalWidth || parseInt(img.getAttribute('width') || '', 10) || 0;
    const h = img.naturalHeight || parseInt(img.getAttribute('height') || '', 10) || 0;
    try {
      if (!/^data:/i.test(src)) {
        const blob = await (await fetch(src)).blob();
        src = await new Promise((res, rej) => { const fr = new FileReader(); fr.onload = () => res(fr.result); fr.onerror = rej; fr.readAsDataURL(blob); });
      }
      let m = src.match(/^data:image\/(png|jpe?g|gif|webp);base64,(.+)$/i);
      if (!m) continue;
      if (m[1] !== 'png' && m[1] !== 'jpeg') {
        const blob = await (await fetch(src)).blob();
        const url = URL.createObjectURL(blob);
        const im = new Image();
        await new Promise((res, rej) => { im.onload = res; im.onerror = rej; im.src = url; });
        const cv = document.createElement('canvas');
        cv.width = im.naturalWidth || w || 300; cv.height = im.naturalHeight || h || 200;
        cv.getContext('2d').drawImage(im, 0, 0);
        src = cv.toDataURL('image/png');
        URL.revokeObjectURL(url);
        m = src.match(/^data:image\/(png|jpe?g);base64,(.+)$/i);
        if (!m) continue;
      }
      const type = m[1] === 'jpeg' ? 'jpg' : 'png';
      let tw = w || 300, th = h || 200;
      const maxW = 540;
      if (tw > maxW) { th = Math.round(th * maxW / tw); tw = maxW; }
      img.dataset.ink = JSON.stringify({ type, b64: m[2], w: tw, h: th });
    } catch (eImg) { /* 无法转换的图片跳过 */ }
  }
  const paraList = [];
  function inlineRuns(el) {
    const runs = [];
    const walk = (node) => {
      if (node.nodeType === 3) { if (node.textContent) runs.push({ t: node.textContent }); }
      else if (node.nodeType === 1) {
        const tag = node.tagName.toLowerCase();
        if (tag === 'img') {
          const ink = node.dataset && node.dataset.ink;
          if (ink) {
            try {
              const k = JSON.parse(ink);
              const bytes = atob(k.b64);
              const u8 = new Uint8Array(bytes.length);
              for (let i = 0; i < bytes.length; i++) u8[i] = bytes.charCodeAt(i);
              runs.push({ img: { type: k.type, data: u8, w: k.w, h: k.h } });
            } catch (eI) {}
          }
          return;
        }
        const sub = [];
        walkChildren(node, sub);
        if (!sub.length) sub.push({ t: '' });
        const a = {};
        if (tag === 'strong' || tag === 'b') a.b = true;
        if (tag === 'em' || tag === 'i') a.i = true;
        if (tag === 'u') a.u = true;
        if (tag === 's' || tag === 'strike' || tag === 'del') a.s = true;
        if (tag === 'code') a.code = true;
        if (tag === 'span' && node.style) {
          if (node.style.fontWeight === 'bold') a.b = true;
          if (node.style.fontStyle === 'italic') a.i = true;
          if (node.style.textDecorationLine && node.style.textDecorationLine.indexOf('underline') >= 0) a.u = true;
        }
        sub.forEach(k => Object.assign(k, a));
        runs.push(...sub);
      }
    };
    function walkChildren(node, out) {
      Array.from(node.childNodes).forEach(c => {
        if (c.nodeType === 3) { if (c.textContent) out.push({ t: c.textContent }); }
        else if (c.nodeType === 1) walk(c);
      });
    }
    walk(el);
    return runs;
  }
  Array.from(tmp.childNodes).forEach(n => {
    if (n.nodeType !== 1) return;
    const tag = n.tagName.toLowerCase();
    if (tag === 'p' || tag === 'div' || tag === 'section') paraList.push({ type: 'p', runs: inlineRuns(n) });
    else if (tag === 'h1' || tag === 'h2' || tag === 'h3' || tag === 'h4') paraList.push({ type: 'h' + tag[1], runs: inlineRuns(n) });
    else if (tag === 'ul' || tag === 'ol') Array.from(n.children).forEach(li => paraList.push({ type: 'li', list: tag, runs: inlineRuns(li) }));
    else if (tag === 'blockquote') paraList.push({ type: 'quote', runs: inlineRuns(n) });
    else if (tag === 'br') paraList.push({ type: 'p', runs: [] });
    else paraList.push({ type: 'p', runs: inlineRuns(n) });
  });
  const mkRun = (k) => new D.TextRun({
    text: k.t || '',
    bold: !!k.b,
    italics: !!k.i,
    underline: k.u ? {} : undefined,
    strike: !!k.s,
    font: k.code ? 'Consolas' : undefined,
    size: k.code ? 22 : undefined,
    color: k.code ? 'C7254E' : undefined
  });
  const children = paraList.map(p => {
    const runs = (p.runs || []).map(k => k.img
      ? new D.ImageRun({ type: k.img.type, data: k.img.data, transformation: { width: k.img.w, height: k.img.h } })
      : mkRun(k));
    if (p.type === 'h1') return new D.Paragraph({ heading: D.HeadingLevel.HEADING_1, children: runs });
    if (p.type === 'h2') return new D.Paragraph({ heading: D.HeadingLevel.HEADING_2, children: runs });
    if (p.type === 'h3') return new D.Paragraph({ heading: D.HeadingLevel.HEADING_3, children: runs });
    if (p.type === 'h4') return new D.Paragraph({ heading: D.HeadingLevel.HEADING_4, children: runs });
    if (p.type === 'li') return new D.Paragraph({ bullet: { level: 0 }, children: runs });
    if (p.type === 'quote') return new D.Paragraph({ children: runs, indent: { left: 360 }, style: 'Quote' });
    return new D.Paragraph({ children: runs.length ? runs : [new D.TextRun('')] });
  });
  const doc = new D.Document({ sections: [{ children }] });
  try { return await D.Packer.toBase64String(doc); } catch (e) { return null; }
}

// docx/xlsx/pdf 内置预览（离线：mammoth / SheetJS / Chromium 原生 PDF）
async function openDocPreview(node, att) {
  try {
    exitFullscreen(); // 打开新附件前清理可能残留的全屏状态
    const ext = (att.name.split('.').pop() || '').toLowerCase();
    const body = $('docPreviewBody'), nameEl = $('docPreviewName');
    body.innerHTML = '<div style="padding:40px;text-align:center;color:#999;">正在加载…</div>';
    nameEl.textContent = att.name;
    // 非 markdown 预览隐藏头部"编辑"按钮
    const hb = $('mdHeadEditBtn'); if (hb) hb.style.display = 'none';
    $('docPreview').style.display = 'flex';
    const buf = await loadAttachmentBuffer(att);
    if (ext === 'pptx') {
      // PPT 预览：jszip 解析逐页文本+图片（离线只读，不做编辑）
      try {
        const zip = await JSZip.loadAsync(buf);
        const slideFiles = Object.keys(zip.files).filter(n => /^ppt\/slides\/slide\d+\.xml$/.test(n)).sort((a, b) => {
          const na = parseInt(a.match(/slide(\d+)\.xml$/)[1], 10), nb = parseInt(b.match(/slide(\d+)\.xml$/)[1], 10);
          return na - nb;
        });
        const ML = 'http://schemas.openxmlformats.org/drawingml/2006/main';
        const REL = 'http://schemas.openxmlformats.org/officeDocument/2006/relationships';
        const pages = [];
        for (const sf of slideFiles) {
          const num = parseInt(sf.match(/slide(\d+)\.xml$/)[1], 10);
          const xml = await zip.file(sf).async('string');
          const doc = new DOMParser().parseFromString(xml, 'application/xml');
          const texts = [];
          Array.from(doc.getElementsByTagNameNS(ML, 't')).forEach(t => {
            const v = (t.textContent || '').trim();
            if (v) texts.push(v);
          });
          const imgs = [];
          const blips = Array.from(doc.getElementsByTagNameNS(ML, 'blip'));
          const relsFile = sf.replace(/slides\/(slide\d+\.xml)$/, 'slides/_rels/$1.rels');
          let relMap = {};
          const relsZip = zip.file(relsFile);
          if (relsZip) {
            try {
              const relsDoc = new DOMParser().parseFromString(await relsZip.async('string'), 'application/xml');
              relMap = {};
              Array.from(relsDoc.getElementsByTagName('Relationship')).forEach(r => { relMap[r.getAttribute('Id')] = r.getAttribute('Target'); });
            } catch (eR) {}
          }
          for (const b of blips) {
            const rid = b.getAttribute('r:embed') || b.getAttributeNS(REL, 'embed');
            if (!rid || !relMap[rid]) continue;
            let target = relMap[rid];
            while (target.indexOf('../') === 0) target = target.slice(3);
            target = target.replace(/^\/+/, '');
            let f = zip.file(target);
            if (!f && target.indexOf('ppt/') !== 0) f = zip.file('ppt/' + target);
            if (!f && target.indexOf('ppt/') === 0) f = zip.file(target.slice(4));
            if (f) {
              const b64 = await f.async('base64');
              const ext2 = (target.split('.').pop() || 'png').toLowerCase();
              imgs.push('data:image/' + (ext2 === 'jpg' ? 'jpeg' : ext2) + ';base64,' + b64);
            }
          }
          pages.push({ num, texts, imgs });
        }
        const esc = s => String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
        let html = '<div class="pptx-wrap">';
        if (!pages.length) html += '<div style="padding:40px;text-align:center;color:#999;">未找到幻灯片内容（空演示文稿）</div>';
        pages.forEach(p => {
          html += '<div class="pptx-page"><div class="pptx-page-head">第 ' + p.num + ' 页</div><div class="pptx-page-body">';
          if (!p.imgs.length && !p.texts.length) html += '<div class="pptx-txt" style="color:#bbb;">（空白页）</div>';
          p.imgs.forEach(i => { html += '<img src="' + i + '" alt="图片">'; });
          p.texts.forEach(t => { html += '<div class="pptx-txt">' + esc(t) + '</div>'; });
          html += '</div></div>';
        });
        html += '</div>';
        body.innerHTML = html;
      } catch (eP) {
        body.innerHTML = '<div style="padding:40px;text-align:center;color:#d33;">PPT 预览失败：' + (eP && eP.message || eP) + '</div>';
      }
      return;
    }
    if (ext === 'pdf') {
      const url = URL.createObjectURL(new Blob([buf], { type: 'application/pdf' }));
      body.innerHTML = '<iframe class="doc-pdf-frame" src="' + url + '"></iframe>';
      return;
    }
    if (ext === 'docx' || ext === 'doc') {
      if (!window.mammoth) { body.innerHTML = '<div style="padding:40px;text-align:center;color:#d33;">缺少 mammoth 库</div>'; return; }
      mammoth.convertToHtml({ arrayBuffer: buf }).then(res => {
        body.innerHTML = '<div class="doc-docx">' + (res.value || '<em>（空文档）</em>') + '</div>';
        // docx：头部"编辑"按钮 → 富文本编辑态（工具条 + contenteditable，保存用 docx.js 重建写回）
        const hb = $('mdHeadEditBtn');
        if (hb) {
          hb.style.display = '';
          hb.onclick = () => enterDocxEdit(node, att, res.value || '');
        }
      }).catch(e => { body.innerHTML = '<div style="padding:40px;text-align:center;color:#d33;">docx 解析失败：' + (e && e.message || e) + '</div>'; });
      return;
    }
    if (!window.XLSX) { body.innerHTML = '<div style="padding:40px;text-align:center;color:#d33;">缺少 SheetJS 库</div>'; return; }
    const wb = XLSX.read(buf, { type: 'array' });
    // 网格编辑器：直接可编辑（单元格输入、公式、行列增删、多表切换），保存写回节点
    const state = buildXlsxEditor(wb, body);
    let saveBtn = document.getElementById('docXlsxSaveBtn');
    if (!saveBtn) {
      saveBtn = document.createElement('button');
      saveBtn.id = 'docXlsxSaveBtn';
      saveBtn.textContent = '保存';
      saveBtn.style.cssText = 'padding:5px 14px;background:#0e7c86;color:#fff;border:0;border-radius:8px;cursor:pointer;font-size:12px;display:none';
      const anchor = document.getElementById('docFsBtn') || document.getElementById('docPreviewClose');
      if (anchor) anchor.insertAdjacentElement('beforebegin', saveBtn);
    }
    saveBtn.style.display = '';
    saveBtn.onclick = () => {
      try {
        if (!state) { toast('没有可保存的内容', 'error'); return; }
        const nwb = XLSX.utils.book_new();
        state.sheets.forEach(sn => {
          const aoa = state.aoaMap[sn];
          const ws = XLSX.utils.aoa_to_sheet(aoa.map(row => row.slice()));
          XLSX.utils.book_append_sheet(nwb, ws, sn);
        });
        const b64 = XLSX.write(nwb, { bookType: 'xlsx', type: 'base64' });
        setNodeAttachment(node, 'data:application/vnd.openxmlformats-officedocument.spreadsheetml.sheet;base64,' + b64, att.name);
        $('docPreview').style.display = 'none';
        $('docPreviewBody').innerHTML = '';
        saveBtn.style.display = 'none';
        state.__done = true;
        _xlsxState = null;
        exitFullscreen();
        toast('已保存: ' + att.name);
      } catch (eS) { toast('保存失败: ' + (eS && eS.message || eS), 'error'); }
    };
  } catch (e) {
    try {
      const body = $('docPreviewBody');
      body.innerHTML = '<div style="padding:40px;text-align:center;color:#d33;">读取失败：' + (e && e.message || e) + '</div>';
    } catch (e2) { try { $('docPreview').style.display = 'none'; } catch (e3) {} }
  }
}

// markdown 附件：渲染预览（marked + KaTeX 公式），头部固定"编辑"按钮一键切换到 CodeMirror 编辑+保存
async function openMarkdownPreview(node, att) {
  try {
    const body = $('docPreviewBody'), nameEl = $('docPreviewName');
    nameEl.textContent = att.name;
    // 头部右上角固定"编辑"按钮（× 左侧，不随内容滚动，用户可直接进入编辑保存）
    const headBtn = $('mdHeadEditBtn');
    if (headBtn) {
      headBtn.style.display = '';
      headBtn.onclick = () => {
        $('docPreview').style.display = 'none';
        $('docPreviewBody').innerHTML = '';
        exitFullscreen();
        openTextPreview(node, att, true); // 直接进入可编辑+保存
      };
    }
    body.innerHTML = '<div style="padding:40px;text-align:center;color:#999;">正在渲染…</div>';
    $('docPreview').style.display = 'flex';
    let text = '';
    try { text = await loadAttachmentText(att); } catch (e) { text = ''; }
    const raw = text || '';
    let html = '';
    if (window.marked) {
      html = marked.parse(raw);
    } else {
      html = '<pre>' + raw.replace(/&/g, '&amp;').replace(/</g, '&lt;') + '</pre>';
    }
    // KaTeX 渲染公式（$$ 块级 → display；$ 内联 → inline）
    try {
      if (window.katex) {
        html = html.replace(/\$\$([\s\S]+?)\$\$/g, (m, tex) => {
          try { return katex.renderToString(tex.trim(), { displayMode: true, throwOnError: false }); } catch (e) { return m; }
        });
        html = html.replace(/(^|[^$])\$([^$\n]+?)\$(?![\d$])/g, (m, pre, tex) => {
          try { return pre + katex.renderToString(tex.trim(), { displayMode: false, throwOnError: false }); } catch (e) { return m; }
        });
      }
    } catch (eK) {}
    // 双栏：左侧目录（按标题层级缩进，点击平滑滚动）+ 右侧渲染内容
    body.innerHTML = '<div class="md-layout">'
      + '<div class="md-toc"><div class="md-toc-title">目录</div><div class="md-toc-list"></div></div>'
      + '<div class="md-content"><div class="doc-md">' + html + '</div></div></div>';
    // 富媒体引用解析：attachments/ 相对路径 → media.local 虚拟主机 URL（图片/视频/音频）
    try {
      body.querySelectorAll('.doc-md img,.doc-md video,.doc-md audio').forEach(el => {
        const s = el.getAttribute('src');
        if (s && s.indexOf('attachments/') === 0) el.setAttribute('src', resolveMediaUrl(s));
      });
    } catch (eM) {}
    try {
      const content = body.querySelector('.md-content');
      const tocList = body.querySelector('.md-toc-list');
      const heads = Array.from(content.querySelectorAll('h1,h2,h3,h4'));
      if (heads.length === 0) { tocList.innerHTML = '<span style="color:#bbb;font-size:12px;">（无标题）</span>'; }
      heads.forEach((h, i) => {
        h.id = 'mdh' + i;
        const lv = parseInt(h.tagName[1], 10) || 1;
        const a = document.createElement('a');
        a.href = 'javascript:void(0)';
        a.className = 'md-toc-item lv' + lv;
        a.textContent = h.textContent || ('标题' + lv);
        a.style.paddingLeft = ((lv - 1) * 12 + 4) + 'px';
        a.addEventListener('click', () => {
          const top = h.getBoundingClientRect().top - content.getBoundingClientRect().top + content.scrollTop;
          try { content.scrollTo({ top: Math.max(0, top - 8), behavior: 'smooth' }); } catch (e) { content.scrollTop = Math.max(0, top - 8); }
        });
        tocList.appendChild(a);
      });
    } catch (eT) {}
  } catch (e) { try { $('docPreview').style.display = 'none'; } catch (e2) {} }
}

// 打开附件：图片→查看器，音视频→播放器，文本/代码→内置预览编辑，其他→系统默认程序（浏览器环境降级下载）
// 强制隐藏库的文本编辑框（状态复位+DOM直藏；**不调库的 hideEditTextBox**——
// 它会对当前编辑节点执行 SET_NODE_TEXT 写回，用编辑框文本覆盖节点 text；
// 对 richText 节点（图片/音视频附件）会破坏其 richText HTML → 图片消失）。
// 原因：双击附件时库 emit node_dblclick 后才启动文本编辑（show 晚于我们的 hide），
// 只调一次会被库随后 show 覆盖 → 编辑框残留画布原点（附件文字"偏移"）。
function forceHideTextEdit() {
  try {
    const te = mind && mind.renderer && mind.renderer.textEdit;
    if (!te) return;
    try { te.showTextEdit = false; } catch (e) {}
    try { te.currentNode = null; } catch (e) {}
    try { if (te.textEditNode) te.textEditNode.style.display = 'none'; } catch (e) {}
    try { if (te.textEditNode) te.textEditNode.innerHTML = ''; } catch (e) {}
    // 库 startTextEdit 会 keyCommand.save()（暂停快捷键）；未走 hideEditTextBox 收尾时手动恢复
    try { if (mind && mind.keyCommand) mind.keyCommand.restore(); } catch (e) {}
  } catch (e) {}
}
function openNodeAttachment(node) {
  try {
    // 兜底：打开附件前强制隐藏库文本编辑框（防双击附件时编辑框残留画布原点）
    forceHideTextEdit();
    const att = nodeAttachData(node);
    if (!att) return;
    const ext = (att.name.split('.').pop() || '').toLowerCase();
    const IMG = ['jpg','jpeg','png','gif','bmp','webp','svg'];
    const VID = ['mp4','webm','ogg','mov'];
    const AUD = ['mp3','wav','m4a','flac'];
    if (IMG.includes(ext)) {
      $('imgViewerImg').src = resolveMediaUrl(att.url);
      $('imgViewer').style.display = 'flex';
      return;
    }
    if (VID.includes(ext) || AUD.includes(ext)) {
      $('mediaFileName').textContent = att.name;
      const v = $('mediaVideo'), a = $('mediaAudio');
      if (VID.includes(ext)) { v.src = resolveMediaUrl(att.url); v.style.display = 'block'; a.style.display = 'none'; }
      else { a.src = resolveMediaUrl(att.url); a.style.display = 'block'; v.style.display = 'none'; }
      $('mediaViewer').style.display = 'flex';
      return;
    }
    // markdown → 渲染预览（marked + KaTeX，可切换 CodeMirror 编辑）
    if (ext === 'md' || ext === 'markdown') { openMarkdownPreview(node, att); return; }
    // 文本/代码 → 内置 CodeMirror 预览编辑
    if (isTextExt(ext)) { openTextPreview(node, att); return; }
    // docx/xlsx/pdf → 内置预览（mammoth/SheetJS/Chromium PDF）
    if (ext === 'pdf' || ext === 'pptx' || ext === 'docx' || ext === 'doc' || ext === 'xlsx' || ext === 'xls') { openDocPreview(node, att); return; }
    // 其他文件：C++ 打开（外置相对路径直接打开；内嵌 base64 写临时文件）；浏览器环境降级为下载
    if (hasNative) {
      const relLike = String(att.url || '').indexOf('attachments/') === 0;
      const b64 = relLike ? String(att.url) : ((att.url || '').split(',')[1] || '');
      callNative('openAttachment', b64, att.name).then(r => {
        if (!r || !r.ok) {
          const a2 = document.createElement('a');
          a2.href = resolveMediaUrl(att.url); a2.download = att.name; a2.click();
        }
      });
    } else {
      const a2 = document.createElement('a');
      a2.href = resolveMediaUrl(att.url); a2.download = att.name; a2.click();
    }
  } catch (e) {}
}
async function openAttachManage(node) {
  if (!node) return;
  attachNode = node;
  const att = nodeAttachData(node);
  if (!att) {
    // 无附件 → 直接选择文件插入
    const r = await callNative('pickAnyFile');
    if (!r || !r.ok) return;
    setNodeAttachment(node, 'data:application/octet-stream;base64,' + r.b64, r.name);
    return;
  }
  $('attachFileName').textContent = att.name;
  $('attachDialog').style.display = 'flex';
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
    let st = null;
    // 优先读数据树（各环境 getStyle 返回内容不一致，数据树最可靠）
    try {
      const nd = nodeDataOf(styleNode);
      const dd = (nd && nd.data) || nd || {};
      st = { fontSize: dd.fontSize, color: dd.color, fillColor: dd.fillColor, bold: dd.bold, italic: dd.italic, underline: dd.underline, strikeThrough: dd.strikeThrough };
    } catch (e) { st = null; }
    if (!st) { try { st = styleNode.getStyle && styleNode.getStyle() || {}; } catch (e) { st = {}; } }
    st = st || {};
    const fs = st.fontSize || 14;
    $('sbFontSize').textContent = fs;
    try { $('sbColor').value = st.color || '#000000'; } catch (e) {}
    try { $('sbBgColor').value = st.fillColor || '#ffffff'; } catch (e) {}
    ['bold', 'italic', 'underline', 'strikeThrough'].forEach(k => {
      const b = $('styleBar').querySelector('[data-style="' + k + '"]');
      if (b) b.classList.toggle('on', !!st[k]);
    });
  } catch (e) {}
}
function setNodeStyle(styleObj) {
  if (!styleNode) return;
  // 库单数命令 SET_NODE_STYLE 对 bold/fontSize 等不生效（实测数据不写），复数 SET_NODE_STYLES 才生效
  try { mind.execCommand('SET_NODE_STYLES', styleNode, styleObj); } catch (e) { return; }
  markDirty();
  syncStyleBar();
  setTimeout(() => { try { mind.render(); } catch (e) {} }, 30);
}
function currentStyleFontSize() {
  try {
    let st = null;
    try { st = styleNode.getStyle && styleNode.getStyle(); } catch (e) { st = null; }
    if (!st) {
      try {
        const nd = nodeDataOf(styleNode);
        const dd = (nd && nd.data) || nd || {};
        st = { fontSize: dd.fontSize };
      } catch (e) { st = {}; }
    }
    return (st && st.fontSize) || 14;
  } catch (e) { return 14; }
}
function toggleStyle(k) {
  if (!styleNode) return;
  try {
    // 直接读数据树当前值取反（getStyle 在各环境返回不一致，弃用）
    const nd = nodeDataOf(styleNode);
    const dd = (nd && nd.data) || nd || {};
    const next = {};
    next[k] = !dd[k];
    setNodeStyle(next);
  } catch (e) {}
}

// ---- 图标 ----
function setNodeIconData(node, icons) {
  try {
    const nd = nodeDataOf(node);
    const dd = (nd && nd.data) || nd || {};
    if (icons && icons.length) dd.icon = icons; else delete dd.icon;
    // 清渲染缓存强制全量重建（同附件逻辑，确保图标按最新布局渲染）
    try { if (mind && mind.renderer) { mind.renderer.nodeCache = {}; mind.renderer.lastNodeCache = {}; } } catch (eC) {}
    try { mind.render(); } catch (e) {}
    markDirty();
  } catch (e) {}
}
function openIconDialog(node) {
  iconTarget = node;
  const gn = $('iconGridNum');
  gn.innerHTML = '';
  for (let i = 1; i <= 10; i++) {
    const c = document.createElement('div');
    c.className = 'icon-cell';
    c.innerHTML = numSvg(i);
    c.addEventListener('click', () => { setNodeIconData(iconTarget, [String(i)]); finishIcon(); });
    gn.appendChild(c);
  }
  const ge = $('iconGridEmoji');
  ge.innerHTML = '';
  FALLBACK_EMOJI.forEach(em => {
    const c = document.createElement('div');
    c.className = 'icon-cell';
    c.innerHTML = emojiSvg(em);
    c.addEventListener('click', () => { setNodeIconData(iconTarget, [em]); finishIcon(); });
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
    // KaTeX 不接受 $...$ 包裹（那是 Markdown 习惯）：剥掉首尾 $ 再渲染，否则原样显示源码
    let tex = String(latex || '').trim();
    tex = tex.replace(/^\$\$?/, '').replace(/\$\$?$/, '');
    // 用内联模式（displayMode:false）：公式与标题同行显示，
    // 避免 display 模式的块级元素在导出（PNG/SVG foreignObject）时被拆成两行
    return '<span class="katex-inline">' +
      window.katex.renderToString(tex, { throwOnError: false, displayMode: false }) +
      '</span>';
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
      mathCleanTitle = extractPlainTitle(mathOrigText);
      title = mathCleanTitle;
    } catch (e) {}
    $('mathTitle').value = title || '公式';
    $('mathInput').value = latex;
  } else {
    mathOrigText = null;
    mathOrigLatex = '';
    mathCleanTitle = null;
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
    try {
      const cur = $('mathTitle').value.trim();
      if (mathOrigLatex && (cur === '' || cur === mathCleanTitle)) {
        // 标题未修改（或清空）：写回打开时看到的干净版本（纯文本标题 + [公式] 占位），
        // 顺带修复历史版本累积的 &lt;p&gt; 转义污染，避免再次编辑时多一层
        const title = mathCleanTitle || '公式';
        mind.renderer.setNodeDataRender(mathEditNode, { text: mathPlaceholderText(title), richText: true, mathLatex: mathOrigLatex });
        scheduleMathInject();
      } else {
        // 用户已修改标题却取消：恢复原始 text，不破坏
        mind.renderer.setNodeDataRender(mathEditNode, { text: mathOrigText, richText: true, mathLatex: mathOrigLatex });
      }
    } catch (e) {}
  }
  mathTarget = null;
  mathEditNode = null;
  mathOrigText = null;
  mathOrigLatex = '';
  mathCleanTitle = null;
  mathConfirmed = false;
}
function mathConfirm() {
  const raw = $('mathInput').value.trim();
  if (!raw) { closeMathDialog(); return; }
  mathConfirmed = true;
  const title = ($('mathTitle').value.trim() || '公式');
  const full = mathPlaceholderText(title);
  try {
    if (mathEditNode) {
      mind.renderer.setNodeDataRender(mathEditNode, { text: full, richText: true, mathLatex: raw });
      scheduleMathInject();
    } else {
      // 插入模式：写到选中节点本身（同原项目 setNodeDataRender 整体替换），而不是新建子节点
      let target = null;
      try { target = mathTarget && (mathTarget._node || mathTarget); } catch (e) {}
      if (!target) { const sel = currentSelectedNode(); if (sel) target = sel._node || sel; }
      if (target && target.getData) {
        try { mind.renderer.setNodeDataRender(target, { text: full, richText: true, mathLatex: raw }); }
        catch (e) { target = null; }
      }
      if (!target) {
        // 最终兜底：无可用节点实例时，才新建为根的子节点
        const d = mind.getData();
        const pd = d.root || d;
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
  // 公式节点拦截：阻止库 TextEdit 打开文本编辑（其 richText 序列化会把节点 text 包成
  // <p><span> 并实体化，每次双击保存就多一层转义污染）；公式节点一律走公式弹窗
  try {
    const te = mind.renderer && mind.renderer.textEdit;
    if (te && !te.__formulaGuard) {
      te.__formulaGuard = true;
      const origShow = te.show.bind(te);
      te.show = function (opts) {
        try {
          const n = opts && opts.node;
          const dd = n && n.getData ? (n.getData() || {}) : {};
          if (dd.mathLatex || /katex/.test(dd.text || '')) return; // 公式节点：不进入文本编辑
        } catch (e) {}
        return origShow(opts);
      };
    }
  } catch (e) {}
  // 右键菜单（平时隐藏样式条，仅点"节点样式"才显示）
  try {
    mind.on('node_contextmenu', (a, b) => {
      const node = (a && (a.nodeData || a.getData)) ? a : (b || null);
      const ev = (a && a.clientX !== undefined) ? a : ((b && b.clientX !== undefined) ? b : null);
      try { if (ev && ev.stopPropagation) ev.stopPropagation(); } catch (e2) {}
      ctxNode = node;
      $('styleBar').style.display = 'none'; styleNode = null;
      showCtxMenu(ev ? ev.clientX : 0, ev ? ev.clientY : 0);
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
  // 点击节点右上角附件图标 → 打开附件
  try { mind.on('node_attachmentClick', (node) => { openNodeAttachment(node); }); } catch (e) {}
  // iconList 图标（📎）点击 → 打开附件
  try { mind.on('node_icon_click', (node, icon) => {
    if (icon === 'emoji_📎') openNodeAttachment(node);
  }); } catch (e) {}
  // 点击节点图标（📎）→ 打开附件
  try {
    mind.on('node_icon_click', (node, iconVal) => {
      if (String(iconVal || '').indexOf('📎') >= 0) openNodeAttachment(node);
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
  // 样式条按钮（mousedown 事件委托：个别环境 click 不冒泡/被吞，按下即生效最稳）
  document.addEventListener('mousedown', (e) => {
    const b = e.target && e.target.closest ? e.target.closest('#styleBar [data-style]') : null;
    if (b) { toggleStyle(b.dataset.style); }
  });
  $('sbFontPlus').addEventListener('click', () => {
    try { setNodeStyle({ fontSize: (currentStyleFontSize() || 14) + 2 }); } catch (e) {}
  });
  $('sbFontMinus').addEventListener('click', () => {
    try { setNodeStyle({ fontSize: Math.max(8, (currentStyleFontSize() || 14) - 2) }); } catch (e) {}
  });
  $('sbColor').addEventListener('input', (e) => { try { setNodeStyle({ color: e.target.value }); } catch (e2) {} });
  $('sbColor').addEventListener('change', (e) => { try { setNodeStyle({ color: e.target.value }); } catch (e2) {} });
  $('sbBgColor').addEventListener('input', (e) => { try { setNodeStyle({ fillColor: e.target.value }); } catch (e2) {} });
  $('sbBgColor').addEventListener('change', (e) => { try { setNodeStyle({ fillColor: e.target.value }); } catch (e2) {} });
  $('sbClose').addEventListener('click', () => { $('styleBar').style.display = 'none'; styleNode = null; });
  // 图标弹窗
  $('iconClear').addEventListener('click', () => {
    setNodeIconData(iconTarget, null);
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
      if ($('attachDialog').style.display !== 'none') { $('attachDialog').style.display = 'none'; attachNode = null; return; }
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

  // 添加附件（与在线版一致：在当前选中节点下新建子节点，图片/音视频节点内嵌预览）
  $('btnAttach').addEventListener('click', async () => {
    try {
      if (hasNative) {
        const r = await callNative('pickAnyFile');
        if (!r || !r.ok) return;
        const type = mimeFromName(r.name);
        if (r.relPath) {
          insertFileToNode({ name: r.name, type: type, relPath: r.relPath });
        } else {
          insertFileToNode({ name: r.name, type: type, dataUrl: 'data:' + type + ';base64,' + r.b64 });
        }
      } else {
        attachFileInput.value = '';
        attachFileInput.click();
      }
    } catch (e) {}
  });
  attachFileInput.addEventListener('change', () => {
    try {
      const f = attachFileInput.files && attachFileInput.files[0];
      if (!f) return;
      insertExternalOrInline(f, f.name, f.type || mimeFromName(f.name));
    } catch (e) {}
  });
  // 附件管理弹窗
  $('attachOpen').addEventListener('click', () => {
    if (attachNode) openNodeAttachment(attachNode);
    $('attachDialog').style.display = 'none';
    attachNode = null;
  });
  $('attachReplace').addEventListener('click', async () => {
    const n = attachNode;
    $('attachDialog').style.display = 'none';
    attachNode = null;
    if (!n) return;
    const r = await callNative('pickAnyFile');
    if (!r || !r.ok) return;
    if (r.relPath) {
      setNodeAttachment(n, r.relPath, r.name);
    } else {
      setNodeAttachment(n, 'data:application/octet-stream;base64,' + r.b64, r.name);
    }
  });
  $('attachRemove').addEventListener('click', () => {
    if (attachNode) removeNodeAttachment(attachNode);
    $('attachDialog').style.display = 'none';
    attachNode = null;
  });
  $('attachCancel').addEventListener('click', () => {
    $('attachDialog').style.display = 'none';
    attachNode = null;
  });

  // 附件弹窗全屏：docPreview 内层框（docFsBtn）+ 磨砂悬浮退出按钮
  const docBox = document.querySelector('#docPreview > div');
  const docFs = $('docFsBtn');
  if (docFs && docBox) docFs.addEventListener('click', () => {
    if (docBox.classList.contains('att-fullscreen')) exitFullscreen();
    else enterFullscreen(docBox);
  });

  // Ctrl+V 粘贴接管：非编辑态只 stopPropagation 拦住库的 keyCommand 快捷键（window keydown），
  // 不 preventDefault（否则浏览器粘贴流程被取消、paste 事件不再触发 → 粘贴完全失效）。
  // 图片/文件/纯文本统一由下方 paste 事件处理；库的 SET_NODE_IMAGE 小图不再触发 → 解决一大一小双图。
  document.addEventListener('keydown', (e) => {
    if (e.ctrlKey && (e.key === 'v' || e.key === 'V')) {
      const tag = document.activeElement && document.activeElement.tagName;
      const editing = tag === 'INPUT' || tag === 'TEXTAREA' || (document.activeElement && document.activeElement.isContentEditable);
      if (!editing) { e.stopPropagation(); }
      const act = (mind.renderer && mind.renderer.activeNodeList && mind.renderer.activeNodeList[0]) || null;
      if (act) { try { mind.renderer.currentNode = act; } catch (e2) {} }
    }
  }, true);
  window.addEventListener('paste', (e) => {
    if (!e.clipboardData) return;
    const items = e.clipboardData.items;
    if (!items) return;
    // 如果正在编辑节点输入框，不拦截
    const tag = document.activeElement && document.activeElement.tagName;
    if (tag === 'INPUT' || tag === 'TEXTAREA' || (document.activeElement && document.activeElement.isContentEditable)) return;
    for (const it of items) {
      if (it.type && it.type.startsWith('image/')) {
        e.preventDefault();
        e.stopPropagation(); // 阻止库/其它监听器二次插入
        const blob = it.getAsFile();
        if (!blob) continue;
        insertExternalOrInline(blob, blob.name || '图片.png', it.type, true);
        return;
      }
      if (it.kind === 'file') {
        e.preventDefault();
        e.stopPropagation();
        const blob = it.getAsFile();
        if (!blob) continue;
        insertExternalOrInline(blob, blob.name || '附件', blob.type || mimeFromName(blob.name || ''), true);
        return;
      }
    }
    // 纯文本/节点粘贴（无图无文件；keydown 已拦截库的 ctrl+v）
    const text = e.clipboardData.getData('text/plain');
    if (text && text.trim()) {
      e.preventDefault();
      e.stopPropagation();
      // 内部节点复制（Ctrl+C 复制节点）粘贴：识别 smm 节点树 JSON → 交给库命令（与原库行为一致）
      let handled = false;
      try {
        const t = text.trim();
        if (t.startsWith('{') || t.startsWith('[')) {
          const parsed = JSON.parse(t);
          const arr = Array.isArray(parsed) ? parsed : (parsed && parsed.data && parsed.children ? [parsed] : null);
          if (arr) {
            mind.execCommand('INSERT_MULTI_CHILD_NODE', [], arr);
            handled = true;
          }
        }
      } catch (eJ) {}
      if (!handled) insertTextAsNode(text.trim().slice(0, 200));
    }
  }, true);

  // 双击播放附件 / 打开附件
  setTimeout(() => {
    if (mind && mind.on) {
      mind.on('node_dblclick', (node) => {
        try {
          // 我们接管双击（预览/打开附件）前，强制隐藏库的文本编辑框：
          // 库默认双击会启动 textEditNode（编辑框），若与其后的 CodeMirror/预览弹窗并存，
          // 保存后编辑框不隐藏会残留在画布原点（视口左下角出现"无框文件名"文字）
          forceHideTextEdit();
          setTimeout(forceHideTextEdit, 120); // 库可能晚于监听启动编辑，延迟补发
          const d = node.nodeData || node;
          const img = d.image || (d.data && d.data.image);
          if (img) {
            $('imgViewerImg').src = img;
            $('imgViewer').style.display = 'flex';
            return;
          }
          if (nodeAttachData(node)) { openNodeAttachment(node); return; }
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
  $('docPreviewClose').addEventListener('click', () => {
    $('docPreviewBody').innerHTML = '';
    $('docPreview').style.display = 'none';
    const hb = $('mdHeadEditBtn'); if (hb) hb.style.display = 'none';
    exitFullscreen(); // 全屏残留清理：防磨砂 ✕ 覆盖自绘标题栏最大化按钮
  });
  $('imgFileInput').addEventListener('change', async (e) => {
    const file = e.target.files[0];
    if (!file) return;
    let imgUrl = '';
    if (hasNative) {
      const rel = await storeFileExternal(file);
      if (rel) imgUrl = resolveMediaUrl(rel);
    }
    if (!imgUrl) {
      const reader = new FileReader();
      reader.onload = (ev) => { insertImageNode(String(ev.target.result)); };
      reader.readAsDataURL(file);
    } else {
      insertImageNode(imgUrl);
    }
    e.target.value = '';
  });

  // 图片节点插入（image 字段：URL 或 dataUrl）
  function insertImageNode(imgUrl) {
    try {
      const img = new Image();
      img.onload = () => {
        try {
          const w = Math.min(img.width, 280);
          const h = img.height * (w / img.width);
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
            data: { text: '', image: imgUrl, imageSize: { width: w, height: h } },
            children: []
          });
          mind.render();
          markDirty();
        } catch (e2) {}
      };
      img.src = imgUrl;
    } catch (e) {}
  }

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
  $('btnSettings').addEventListener('click', () => openPanel('settingsPanel'));
  const btnFs = $('btnFullscreen');
  if (btnFs) btnFs.addEventListener('click', () => { try { callNative('toggleMaximize').catch(() => {}); } catch (e) {} });
  $('overlay').addEventListener('click', closeAllPanels);
  document.querySelectorAll('.panel-close').forEach(b => b.addEventListener('click', () => closePanel(b.dataset.close)));
  $('btnPickHistoryDir').addEventListener('click', pickProjectDir);
  $('btnRegAssoc').addEventListener('click', async () => {
    const r = await callNative('registerAssoc');
    if (r && r.ok) { toast('文件关联已注册'); fillSettings(); }
  });
  // 修改即保存：设置里任何控件变化立即生效
  ['setRainbow', 'setAutoSave', 'setAutoSaveSec', 'setMaxAttach', 'setAllowTypes'].forEach(id => {
    const el = $(id);
    if (el) el.addEventListener('change', () => saveSettingsFromUi());
  });
  $('setAutoSaveSec').addEventListener('input', () => saveSettingsFromUi());
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
  $('btnZoomIn').addEventListener('click', () => { try { mind.view.enlarge(); } catch (e) {} setTimeout(updateZoom, 50); });
  $('btnZoomOut').addEventListener('click', () => { try { mind.view.narrow(); } catch (e) {} setTimeout(updateZoom, 50); });
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
    // 诊断浮层：Ctrl+Shift+D 显示渲染树各节点真实坐标（排查附件左移/框文分离）
    if ((e.ctrlKey || e.metaKey) && e.shiftKey && (e.key === 'D' || e.key === 'd')) {
      e.preventDefault();
      toggleDiagPanel();
    }
  });
}

// 诊断浮层：渲染树节点 uid/text/内部坐标/DOM坐标/父节点（仅调试用，不影响业务）
function toggleDiagPanel() {
  let dg = document.getElementById('diagPanel');
  if (dg) { dg.remove(); return; }
  const lines = [];
  try {
    const walk = (t, dep) => {
      if (!t) return;
      const no = t._node || t;
      const nd = t.data || {};
      let info = '';
      try {
        const L = no._left, T = no._top;
        const el = no.shapeNode || no.node;
        let g = '';
        if (el) { try { const r = el.getBoundingClientRect(); g = ' g:' + Math.round(r.left) + ',' + Math.round(r.top); } catch (e2) {} }
        info = ' L:' + L + ' T:' + T + g + ' shape:' + (!!el) + ' par:' + (no.parent ? String(no.parent.getData('uid')).slice(0, 6) : '-');
      } catch (e) { info = ' ERR:' + (e.message || e); }
      lines.push(' '.repeat(dep * 2) + String(nd.uid || '').slice(0, 6) + ' ' + String(nd.text || '').slice(0, 12).replace(/\s+/g, ' ') + (nd.attachmentUrl || nd.attachUrl ? ' [ATT]' : '') + ' ' + info);
      (t.children || []).forEach(c => walk(c, dep + 1));
    };
    walk(mind.renderer.renderTree, 0);
  } catch (e) { lines.push('WALK_ERR: ' + (e.message || e)); }
  dg = document.createElement('div');
  dg.id = 'diagPanel';
  dg.style.cssText = 'position:fixed;top:8px;right:8px;z-index:99999;background:rgba(20,20,30,.94);color:#8ff;font:11px/1.5 Consolas,monospace;padding:10px 12px;border-radius:10px;max-height:70vh;overflow:auto;white-space:pre;box-shadow:0 4px 24px rgba(0,0,0,.4);user-select:text;';
  dg.textContent = lines.join('\n') || '(empty)';
  document.body.appendChild(dg);
}

// ================= 启动 =================
async function boot() {
  try {
    // 启动特效阶段：隐藏主界面，仅显示透明悬浮特效
    document.body.classList.add('splash-mode');
    // 启动特效：小窗展示 2 秒后通知原生恢复主窗口大小，再淡出移除
    setTimeout(() => {
      callNative('restoreMainWindow').catch(() => {});
      document.body.classList.remove('splash-mode');
      const sp = document.getElementById('bootSplash');
      if (sp) { sp.classList.add('hide'); setTimeout(() => sp.remove(), 650); }
    }, 2100);
    // 原生 title 提示会显示黑色小条且截图截不到：统一转为浅色自定义提示
    function convertTitles(root) {
      (root || document).querySelectorAll('[title]:not([data-tip])').forEach(el => {
        el.setAttribute('data-tip', el.getAttribute('title'));
        el.removeAttribute('title');
      });
    }
    convertTitles(document);
    if (!window._tipObs) {
      window._tipObs = new MutationObserver(muts => {
        for (const m of muts) {
          if (m.type === 'attributes' && m.attributeName === 'title' && m.target.hasAttribute && m.target.hasAttribute('title') && !m.target.hasAttribute('data-tip')) {
            m.target.setAttribute('data-tip', m.target.getAttribute('title'));
            m.target.removeAttribute('title');
            continue;
          }
          for (const n of m.addedNodes) {
            if (n.nodeType !== 1 || !n.querySelectorAll) continue;
            if (n.hasAttribute && n.hasAttribute('title') && !n.hasAttribute('data-tip')) {
              n.setAttribute('data-tip', n.getAttribute('title'));
              n.removeAttribute('title');
            }
            n.querySelectorAll('[title]:not([data-tip])').forEach(el => {
              el.setAttribute('data-tip', el.getAttribute('title'));
              el.removeAttribute('title');
            });
          }
        }
      });
      window._tipObs.observe(document.body, { childList: true, subtree: true, attributes: true, attributeFilter: ['title'] });
    }
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

// 浏览器（无原生桥）环境暴露测试钩子；exe 内不暴露
if (!hasNative && typeof window !== 'undefined') {
  window.__winTest = { openNodeAttachment: openNodeAttachment, openTextPreview: openTextPreview, setNodeAttachment: setNodeAttachment, nodeAttachData: nodeAttachData, insertFileToNode: insertFileToNode, openDocPreview: openDocPreview, openMarkdownPreview: openMarkdownPreview, mediaObjectUrl: mediaObjectUrl, resolveMediaUrl: resolveMediaUrl, storeFileExternal: storeFileExternal, exportSelfContained: exportSelfContained, importSelfContained: importSelfContained, patchTreeMediaSrc: patchTreeMediaSrc, restoreTreeMediaSrc: restoreTreeMediaSrc, serializeDoc: serializeDoc, get mind() { return mind; } };
}

// ---- 附件偏移终极兜底 ----
// 1) fixAttachNodeTexts（移植自原项目）：附件节点文字统一为文件名（纯文本），
//    并在每次渲染后做 DOM 对齐——文字元素若因样式重建与节点框分离（飘出框外/停旧坐标），
//    按"文字中心→框中心"差值用 transform 平移回来。
// 2) 库可能在任意时机把文本编辑框（textEditNode）show 出来（双击附件时它与 CodeMirror 并存，
//    保存后若未隐藏会残留在画布原点=视口左下角，表现为"附件文字偏移/无框文件名"）。
//    定时器只针对"编辑对象是附件节点"（含 currentNode 为 null 但内容匹配附件文件名）强制隐藏，
//    普通节点文字编辑完全不受影响。
function fixAttachNodeTexts() {
  if (!mind || !mind.renderer) return;
  try {
    // 自动重建"未渲染"的公式节点（旧数据/KaTeX 失败时 text 存的是 LaTeX 源码）：用 mathLatex 重渲染为内联公式
    rebuildRawMathNodes();
  } catch (e) {}
  try { injectMathIntoFo(); } catch (e) {}
  try {
    const tree = mind.getData();
    let needRender = false;
    const walk = (t) => {
      if (t && t.data) {
        const d = t.data;
        if (d.attachmentName && (d.text !== d.attachmentName || d.richText)) {
          const inst = d.uid ? mind.renderer.findNodeByUid(d.uid) : null;
          if (inst) mind.renderer.setNodeDataRender(inst, { text: d.attachmentName, richText: false });
        }
        // 图片节点（text 含 <img>）：强制背景透明+零内边距+无边框，图片即节点，不留背景露边
        // 必须"渲染树实例+数据树"双写（只写 getData 树不生效，render 读渲染树实例数据）
        if (typeof d.text === 'string' && d.text.indexOf('<img') !== -1) {
          if (d.fillColor !== 'transparent' || d.paddingX || d.paddingY || d.borderWidth) {
            const patch = { fillColor: 'transparent', paddingX: 0, paddingY: 0, borderWidth: 0, borderColor: 'transparent' };
            const inst = d.uid ? mind.renderer.findNodeByUid(d.uid) : null;
            if (inst) setNodeDataPersist(inst, patch, true);
            else { for (const k in patch) d[k] = patch[k]; }
            needRender = true;
          }
        }
        // 清理旧版本残留的库 emoji 📎 图标（统一由 injectAttachIcons 注入 SVG 别针）
        if (Array.isArray(d.icon)) {
          const rest = d.icon.filter(i => i !== 'emoji_📎');
          if (rest.length !== d.icon.length) d.icon = rest;
        }
      }
      if (t && t.children) t.children.forEach(walk);
    };
    walk(tree);
    if (needRender) { try { mind.render(); } catch (eR) {} }
  } catch (e) {}
  try {
    const c = document.querySelector('.smm-container');
    if (c) {
      // 图片节点：图片 div 强制撑满节点 foreignObject（图片即节点，不留空隙/不露白边）。
      // 库对含 <img> 的富文本节点测量可能比图片 div 略大（含 1px 取整/边距），
      // 直接把 div 拉成与节点区域一致，img 的 object-fit:cover 会跟随铺满。
      c.querySelectorAll('foreignObject').forEach(fo => {
        const html = fo.innerHTML || '';
        if (html.indexOf('<img') === -1) return;
        const div = fo.querySelector('div');
        if (!div) return;
        const fr = fo.getBoundingClientRect();
        const dr = div.getBoundingClientRect();
        if (!fr.width || !dr.width) return;
        if (Math.abs(fr.width - dr.width) > 2 || Math.abs(fr.height - dr.height) > 2) {
          div.style.width = fr.width + 'px';
          div.style.height = fr.height + 'px';
        }
      });
      c.querySelectorAll('text.smm-text-node-wrap').forEach(tx => {
        const tr = tx.getBoundingClientRect();
        if (!tr || !tr.width) return;
        let g = tx.parentElement, shape = null, ng = g;
        for (let i = 0; i < 8 && ng; i++) {
          const s2 = ng.querySelector(':scope > rect, :scope > [class*=shape]');
          if (s2 && s2 !== tx) { shape = s2; break; }
          ng = ng.parentElement;
        }
        if (!shape) return;
        const sr = shape.getBoundingClientRect();
        if (!sr || !sr.width) return;
        const tc = tr.x + tr.width / 2, sc = sr.x + sr.width / 2;
        if (Math.abs(tc - sc) > 2) {
          const ctm = tx.getScreenCTM && tx.getScreenCTM();
          const k = ctm && ctm.a ? ctm.a : 1;
          const dx = (sc - tc) / k;
          tx.setAttribute('transform', 'translate(' + dx.toFixed(2) + ' 0)');
        }
      });
    }
  } catch (e) {}
  try { injectAttachIcons(); } catch (e) {}
}

// 注入附件别针图标（移植自原项目）：SVG foreignObject 回形针，节点右上角，磨砂底，点击打开附件
function injectAttachIcons() {
  if (!mind || !mind.renderer) return;
  const NS = 'http://www.w3.org/2000/svg';
  let uidMap = null;
  const nameMap = {};
  try {
    const tree = mind.getData();
    uidMap = {};
    const walk = (t) => {
      if (t && t.data) {
        if (t.data.uid) uidMap[t.data.uid] = t.data;
        if ((t.data.attachmentUrl || t.data.attachUrl) && t.data.attachmentName) nameMap[t.data.attachmentName] = t.data;
      }
      if (t && t.children) t.children.forEach(walk);
    };
    walk(tree);
  } catch (e) {}
  const openAtt = (d) => {
    try { openNodeAttachment({ getData: (k) => (d ? d[k] : undefined) }); } catch (e) {
      try { openTextPreview({ getData: (k) => (d ? d[k] : undefined) }, d); } catch (e2) {}
    }
  };
  const injectIntoGroup = (gEl, d) => {
    if (!gEl || gEl.querySelector('.smm-att-icon')) return;
    let shape = gEl.querySelector('.smm-text-node-wrap, [class*=text]');
    let bb = null;
    if (shape) { try { bb = shape.getBBox(); } catch (e) {} }
    if (!bb && gEl.getBBox) { try { bb = gEl.getBBox(); } catch (e) {} }
    const fo = document.createElementNS(NS, 'foreignObject');
    fo.setAttribute('class', 'smm-att-icon');
    const x = bb ? Math.max(0, bb.x + bb.width - 26) : 0;
    const y = bb ? Math.max(0, bb.y + 2) : 0;
    fo.setAttribute('x', x);
    fo.setAttribute('y', y);
    fo.setAttribute('width', '24');
    fo.setAttribute('height', '24');
    const div = document.createElement('div');
    div.style.cssText = 'width:22px;height:22px;display:flex;align-items:center;justify-content:center;cursor:pointer;font-size:12px;background:rgba(20,22,26,.55);backdrop-filter:blur(4px);-webkit-backdrop-filter:blur(4px);border-radius:7px;color:#fff;line-height:1;transition:transform .15s ease,background .15s ease';
    div.title = d.attachmentName || d.attachName || '附件';
    div.innerHTML = '<svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21.44 11.05l-9.19 9.19a6 6 0 0 1-8.49-8.49l9.19-9.19a4 4 0 0 1 5.66 5.66l-9.2 9.19a2 2 0 0 1-2.83-2.83l8.49-8.48"/></svg>';
    div.addEventListener('mouseenter', () => { div.style.transform = 'scale(1.15)'; div.style.background = 'rgba(14,124,134,.85)'; });
    div.addEventListener('mouseleave', () => { div.style.transform = 'scale(1)'; div.style.background = 'rgba(20,22,26,.55)'; });
    div.addEventListener('click', (e) => {
      e.preventDefault(); e.stopPropagation();
      openAtt(d);
    });
    fo.appendChild(div);
    gEl.appendChild(fo);
  };
  try {
    const cache = mind.renderer.nodeCache || {};
    Object.keys(cache).forEach(uid => {
      const n = cache[uid];
      const gEl = (n && n.group && n.group.node) ? n.group.node : (n && n.group ? n.group : null);
      if (!gEl || gEl.querySelector('.smm-att-icon')) return;
      const d = (uidMap && uidMap[uid]) || (n && n.data) || {};
      if (!(d.attachmentUrl || d.attachUrl)) return;
      injectIntoGroup(gEl, d);
    });
  } catch (e) {}
  try {
    const svg = document.querySelector('.smm-container svg');
    if (!svg) return;
    svg.querySelectorAll('title').forEach(t => {
      const name = (t.textContent || '').trim();
      if (!name || !nameMap[name]) return;
      const gEl = t.closest('g.smm-node') || t.closest('g');
      if (!gEl || gEl.querySelector('.smm-att-icon')) return;
      injectIntoGroup(gEl, nameMap[name]);
    });
  } catch (e) {}
}
function startAttEditGuard() {
  if (_attEditGuardTimer) return;
  _attEditGuardTimer = setInterval(() => {
    try {
      const te = mind && mind.renderer && mind.renderer.textEdit;
      if (!te || !te.textEditNode) return;
      if (te.textEditNode.style.display === 'none') return;
      const cn = te.currentNode;
      const isAtt = !!(cn && cn.getData && (cn.getData('attachmentUrl') || cn.getData('attachUrl') || cn.getData('attachmentName')));
      if (isAtt) { forceHideTextEdit(); return; }
      // currentNode 为 null 时的兜底：编辑框残留内容若匹配任一附件节点文件名 → 强制隐藏
      if (!cn) {
        try {
          const content = String((te.textEditNode.textContent || '').trim());
          if (!content) return;
          let hit = false;
          const walk = (t) => {
            if (hit || !t) return;
            if (t.data && t.data.attachmentName === content) hit = true;
            (t.children || []).forEach(walk);
          };
          walk(mind.getData());
          if (hit) forceHideTextEdit();
        } catch (e2) {}
      }
    } catch (e) {}
  }, 200);
}
try { startAttEditGuard(); } catch (e) {}
// 轮询对齐附件节点文字（不依赖 render_end 时序，同原项目；800ms 避免频繁 DOM 开销）
let _attFixTimer = null;
function startAttFixLoop() {
  if (_attFixTimer) return;
  _attFixTimer = setInterval(() => { try { fixAttachNodeTexts(); } catch (e) {} }, 800);
}
try { startAttFixLoop(); } catch (e) {}

// ---- 自动化自测钩子（?autotest=1，headless/测试页用）----
if (typeof window !== 'undefined' && location.search.indexOf('autotest=1') >= 0) {
  setTimeout(() => {
    try {
      // 插桩：记录 insertFileToNode 与 execCommand 调用
      window.__diag = { ins: 0, ec: {} };
      try {
        const origIns = insertFileToNode;
        insertFileToNode = function (file) { window.__diag.ins++; return origIns.call(this, file); };
      } catch (e) {}
      try {
        const origEC = mind.execCommand.bind(mind);
        mind.execCommand = function (name, ...args) {
          window.__diag.ec[name] = (window.__diag.ec[name] || 0) + 1;
          return origEC(name, ...args);
        };
      } catch (e) {}
      const fakeFile = { name: '测试附件.txt', size: 10, type: 'text/plain', lastModified: Date.now(), dataUrl: 'data:text/plain;base64,MTIzNDU2' };
      try {
        const l = mind.renderer.activeNodeList;
        if (l && l.length) l.forEach(n => { try { mind.renderer.removeActiveNode(n); } catch (e) {} });
        // 选中根节点
        try { mind.renderer.activeNodeList = [mind.renderer.renderTree._node]; mind.renderer.drawActiveNode(); } catch (e) {}
      } catch (e) {}
      try { insertFileToNode(fakeFile); } catch (e) { document.title = 'AUTOTEST_INSERT_ERR:' + e.message; }
      setTimeout(() => {
        try {
          const d = mind.getData();
          const diag = { dataTree: [], renderTree: [], textEdit: null };
          const walkD = (n, dep, path) => {
            if (!n) return;
            const nd = n.data || n;
            diag.dataTree.push({ dep, path, uid: nd.uid || '', text: (nd.text || '').slice(0, 24), att: !!(nd.attachmentUrl || nd.attachUrl || nd.attachmentName), icon: (nd.icon || []).length });
            (n.children || []).forEach((c, i) => walkD(c, dep + 1, path + '.' + i));
          };
          walkD(d.root || d, 0, 'r');
          const walkR = (t, dep, path) => {
            if (!t) return;
            const nd = t.data || {};
            let L = '', T = '';
            try {
              const no = t._node || t;
              if (no) {
                try { if (no.getLeft) { L = Math.round(no.getLeft()); T = Math.round(no.getTop()); } } catch (e) {}
                try {
                  const el = no.shapeNode || no.node;
                  if (el) { const r = el.getBoundingClientRect(); L = (L === '' ? '' : L + '/') + 'g' + Math.round(r.left) + ',' + Math.round(r.top); }
                } catch (e2) {}
                try { if (no.parent) L = 'P:' + (no.parent.getData('uid') || '').slice(0, 6) + ' ' + L; } catch (e3) {}
              }
            } catch (e) {}
            diag.renderTree.push({ dep, path, uid: nd.uid || '', text: (nd.text || '').slice(0, 24), att: !!(nd.attachmentUrl || nd.attachUrl || nd.attachmentName), rich: !!nd.richText, L, T });
            (t.children || []).forEach((c, i) => walkR(c, dep + 1, path + '.' + i));
          };
          try { walkR(mind.renderer.renderTree, 0, 'r'); } catch (e) {}
          try {
            const te = mind.renderer.textEdit;
            diag.textEdit = { show: te.showTextEdit, nodeUid: te.currentNode ? te.currentNode.getData('uid') : null, disp: te.textEditNode ? te.textEditNode.style.display : 'n/a', content: te.textEditNode ? te.textEditNode.textContent.slice(0, 24) : '' };
          } catch (e) {}
          const out = JSON.stringify({ diag: window.__diag || null, tree: diag }, null, 1);
          document.title = 'AUTOTEST_OK';
          const div = document.createElement('div');
          div.id = 'autotest-out';
          div.style.cssText = 'position:fixed;top:0;left:0;z-index:99999;background:#fff;color:#222;font:11px/1.4 monospace;max-height:90vh;overflow:auto;white-space:pre-wrap;padding:8px;';
          div.textContent = out;
          document.body.appendChild(div);
        } catch (e) {
          document.title = 'AUTOTEST_DIAG_ERR:' + e.message;
        }
      }, 7000);
    } catch (e) {
      document.title = 'AUTOTEST_ERR:' + e.message;
    }
  }, 2000);
}
// ---- 自动化导出测试（?autotest=export，仅测试用）----
if (typeof window !== 'undefined' && location.search.indexOf('autotest=export') >= 0) {
  setTimeout(async () => {
    try {
      const mock = {
        data: { text: '中心主题', richText: false, uid: 'r1' },
        children: [
          { data: { text: '分支主题', richText: false, uid: 'n1' }, children: [] },
          { data: { text: '公式 $f(x)=\\begin{cases}x^2, & x\\geq 0 \\\\ -x, & x<0\\end{cases}$', richText: false, mathLatex: 'f(x)=\\begin{cases}x^2, & x\\geq 0 \\\\ -x, & x<0\\end{cases}', uid: 'n2' }, children: [] },
          { data: { text: '公式 $\\lim_{x \\to 0} \\frac{\\sin x}{x} = 1$', richText: false, mathLatex: '\\lim_{x \\to 0} \\frac{\\sin x}{x} = 1', uid: 'n3' }, children: [] },
        ]
      };
      mind.setData(mock);
      await new Promise(r => setTimeout(r, 1200));
      rebuildRawMathNodes();
      await new Promise(r => setTimeout(r, 800));
      try { await document.fonts.ready; } catch (e) {}
      clearSelectionForExport();
      try { mind.render(); await new Promise(r => setTimeout(r, 500)); } catch (e) {}
      const { str: strRaw } = await mind.doExport.getSvgData();
      window.__exportTestSvg = strRaw;
      const { W, H, X, Y } = parseSvgSize(strRaw);
      if (!W || !H) { document.title = 'EXPORT_TEST_ERR:SIZE'; return; }
      const canvas = await renderChunkByHtml(strRaw, W, H, 0, X, Y, '#ffffff');
      window.__exportTestData = canvas.toDataURL('image/png');
      window.__exportTestMeta = JSON.stringify({ W, H, X, Y });
      // SVG 自导出测试（同 exportSvgSelf 逻辑，不弹保存框）
      try {
        let sout = strRaw;
        const kc2 = await getKatexCssForSvg();
        if (kc2) sout = sout.replace('</svg>', '<style>' + kc2 + '</style></svg>');
        window.__exportTestSvgSelf = sout;
      } catch (e) { window.__exportTestSvgSelf = ''; }
      document.title = 'EXPORT_TEST_OK';
    } catch (e) { document.title = 'EXPORT_TEST_ERR:' + String(e && e.message || e).slice(0, 200); }
  }, 2500);
}