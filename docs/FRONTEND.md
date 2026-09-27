# USMIND 前端架构与技术细节

> 前端分两个独立工程：`code/web-user`（用户端，脑图）与 `code/web-admin`（管理后台）。两者均为 Vue 3 + Vite + Pinia + Vue Router。

---

## 一、用户端（web-user）

### 1.1 技术选型
- **Vue 3**（Composition API，`<script setup>`）+ **Vite**
- **simple-mind-map**（脑图内核，本地 npm 包）：负责节点渲染、连线、布局、缩放、编辑框
- **MathJax**（tex-svg）：公式渲染（动态加载，按需）
- **pdf.js**：PDF 附件在线预览
- **Pinia**：用户状态
- **Vue Router**：路由
- 无 UI 组件库（脑图端全部自研组件 / 样式，保证与工具条统一风格）

### 1.2 路由
```
/               → 重定向 /maps
/login          → 登录 / 注册
/maps           → 我的导图列表
/editor/:id     → 脑图编辑（/:id 缺省时新建）
/share/:token   → 分享只读页（免登录）
```

### 1.3 目录职责
| 目录 | 职责 |
| --- | --- |
| `views/Editor.vue` | 脑图编辑器（核心，约 2000+ 行，功能见 FILE_GUIDE） |
| `views/Login.vue` / `MindMapList.vue` / `ShareView.vue` | 登录 / 列表 / 分享页 |
| `utils/mathRenderer.js` | LaTeX ↔ MathJax SVG HTML 转换 |
| `utils/mediastore.js` | 媒体节点（图片/视频/音频/文件）引用与还原 |
| `utils/modal.js` | 自研 toast / 确认框 / 输入框（不依赖浏览器原生 alert/confirm，风格统一） |
| `api/` | axios 封装（自动带 token、401 跳登录） |
| `assets/style.css` | 全局样式（含 768px 移动端断点） |

### 1.4 关键机制

#### ① 脑图内核（simple-mind-map）
- 实例在 `createMind()` 创建，`bindMindEvents()` 绑定事件：
  - `node_dblclick`：双击节点 → 图片全屏 / 附件编辑预览 / 公式编辑（按节点类型分发）
  - `node_attachmentClick`：单击附件图标 → 附件预览
  - `scale` / `drag` 等：联动缩放百分比、滑动条、懒加载
- 渲染：节点文字支持 HTML（`richText: true`），公式/图片/媒体都以 HTML 片段注入节点。

#### ② 公式渲染（mathRenderer.js）
- `tex2svgHtml(tex, display)`：调 MathJax 把 LaTeX 转成 SVG HTML。
- 节点数据里存的是**SVG HTML**（不是 LaTeX），保证渲染即时、导出所见即所得。
- 双击公式节点 → `extractLatexFromHtml` 从 SVG 反向还原 LaTeX 进编辑弹窗。
- 插入 / 编辑确认后 `setTimeout(() => mind.render(), 30)` 重渲染画布，**按公式实际尺寸重算节点宽高，避免公式节点塌陷**（不用整页刷新，不丢编辑态）。
- 兼容旧数据：`migrateKatexToSvg` 把历史 KaTeX 标记迁移为 MathJax SVG。

#### ③ 媒体 / 附件节点（mediastore.js）
- 粘贴（`onPaste`）或上传图片 / 视频 / 音频 / 文件到节点：
  - 图片：压缩后上传（`compressImage`），节点内 `<img>` 平铺（超出节点部分 `overflow:hidden` 裁剪），图片作为节点主体时**节点无边框，看起来就是一张图**。
  - 视频：`<video autoplay loop muted playsinline>` 平铺，无控制条，缓存复用。
  - 文件：附件图标 + 附件名（默认红色）显示在节点上，`injectAttachIcons` 按类型注入图标。
- 存储引用：节点数据存后端返回的 URL（`/uploads/...` 或 OSS 地址）；序列化/反序列化由 `materializeNodeData` / `dematerializeNodeData` 处理。
- 预览：`openAttachmentPreview` 按扩展名分发（图片 / PDF(pdf.js) / 音视频 / 代码高亮 / Office 跳转）；`openImageLightbox` 图片全屏（点 ×、遮罩或 Esc 返回）。
- 双击规则：**图片节点双击 → 全屏浏览；附件节点双击 → 编辑与浏览；公式节点双击 → 公式编辑**。

#### ④ Emoji 图标
- 图标面板按分类折叠（`toggleIconGroup`），滚动懒加载（`loadMoreIcons`），分类数据来自后端 `/api/icons`（管理后台可配置）。
- 插入：`insertEmoji` 在节点文字光标处插入 Emoji 字符（与文字同流）。

#### ⑤ 主题与样式
- 主题模板来自后端 `/api/themes`：节点配色、分支配色、各层字号/字体、画布背景（色/图/视频 + 透明度）。
- `applyTheme` 应用主题；`applyCanvasBackground` 处理画布背景：
  - 三层结构：**底色（最底）→ 背景图片/视频（中间，等比放大铺满、允许溢出裁剪、不畸变）→ 脑图（最上）**。
  - 背景视频 `autoplay loop muted` 无控制条，`Cache-Control: immutable` 强缓存。
- 单节点样式：右键菜单设置字体大小 / 颜色 / 背景色 / 圆角 / 边框，存 `manualStyles`。

#### ⑥ 画布交互（视口 / 缩放 / 滑动条）
- **缩放**：`Ctrl+滚轮`（库自带）、按钮 − / +（`enlarge` / `narrow`）、移动端**双指捏合**（自定义 touch 处理：`onMapTouchStart/Move` 双指时按距离比例 `view.setScale`，以双指中点为缩放中心；`preventDefault` 阻止浏览器整页缩放；viewport `user-scalable=no`）→ **只缩放脑图，背景与工具条不动**。
- **平移**：空白拖拽、按住根节点拖拽、滑动条拖拽。
- **滑动条**：底部横向 + 右侧纵向细条，`updateScrollbars` 与视口联动；透明样式、悬停加宽、与工具栏同配色；移动端直接拖动滑动条。
- **懒加载渲染**：`applyLazyView` 只渲染视口内节点，加载期显示 loading 遮罩（`page-loading`），渲染完成后才放行交互。

#### ⑦ 多画布（标签页）
- `canvasTabs` 数组管理多个导图；标签可切换 / 新建 / 删除 / 双击重命名 / 拖拽排序 / `<>` 按钮横向滚动（超出时显示）。

#### ⑧ 导入导出
- **导出**：`runExport` —— JSON（当前数据）、PNG / JPG / JPEG / SVG / PDF（`html2canvas` 画布截图，PDF 逐页拼图）、Markdown（节点树转 md 列表）。
- **`.usmind` 资源包**（`exportUsmind`）：JSZip 收集节点内全部资源文件 + `data.json` 打包为 `.usmind` 压缩包；导入（`onImportUsmindFile`）解包后**自动上传资源入库并改写节点 URL**——相当于脑图连同资源从一个系统整体迁移到另一个系统。
- **JSON 导入**：`onImportFile` 读取 JSON 重建导图。

#### ⑨ 自动保存
- 编辑防抖（`scheduleAutoSave`）→ `saveDoc` PUT `/api/mindmaps/{id}`；顶部显示"已保存 / 最近保存时间"。

#### ⑩ 移动端适配
- 768px 断点：工具栏折叠、样式面板全屏抽屉、滑动条可用、双指缩放、标签页横滚。
- 列表页头部：标题简化为"我的导图"、隐藏用户名、退出/新建按钮与标题同行。

---

## 二、管理后台（web-admin）

### 2.1 技术选型
- Vue 3 + Vite + **Element Plus**（表格 / 表单 / 弹窗 / 分页 / 上传）+ Pinia + Vue Router + ECharts（仪表盘）。
- 全局样式 `styles/main.css`：下拉框、弹窗、提示与用户端工具条同配色；移动端抽屉菜单适配。

### 2.2 路由与布局
```
/login                    → 后台登录
/                         → Dashboard（仪表盘）
/users /mindmaps /resources /oss /themes /icons /project-config /data-backup /settings
```
`AdminLayout.vue` 提供侧边菜单 + 顶栏（含收起、退出）；路由守卫校验登录态。

### 2.3 页面要点
- **Users**：角色（ADMIN/USER）、状态启停、重置密码、批量删除；删除唯一管理员由**后端**拦截并提示。
- **Resources**：按类型（图片/视频/音频/文件）与后缀过滤；在线预览（图片/PDF/音视频/代码）。
- **OssConfig**：类型下拉（本地/阿里云/腾讯/七牛/MinIO）、激活唯一、连通测试；密钥加密存储（SecretCipher）。
- **Themes**：可视化编辑 —— 节点配色列表（增删行）、分支配色、**各层字号字体**（根/第一层/其它层）、画布背景色 + 背景图/视频上传 + 透明度；右侧**实时预览**（自绘 mini 脑图示意）；启用/禁用；改名自动同步存量导图。
- **Icons**：分类管理、批量导入、启停、批量删。
- **ProjectConfig**：后端配置与 nginx 配置可视化编辑（带字段说明提示）、一键导出配置文件（迁移部署用）。
- **DataBackup**：导出数据库（JSON / SQL 下载）、上传备份文件恢复。
- **Settings**：登录页版权 / 入网声明、登录缓存时长（小时）等 KV 配置。

### 2.4 构建部署
- 构建：`npm run build` → 产物 `dist/`（base 为 `/admin/`）→ 拷贝到 `release/admin`。
- nginx 8867 的 `/admin/` location 用 `alias ../../release/admin/` 托管；`/api/`、`/uploads/` 反代 8899。

---

## 三、前后端交互约定

1. **API 地址**：统一 `/api` 前缀，经 nginx 反代到 8899；`api/request.js` 的 axios baseURL 为 `/api`。
2. **认证**：登录后 token 存 localStorage（Pinia user store），请求头 `Authorization: Bearer <token>`；401 统一跳登录。
3. **上传**：`multipart/form-data` 到 `/api/file/upload`，返回资源对象；图片前端可先压缩。
4. **静态资源**：`/uploads/**` 由 nginx 直接服务（本地模式）或直连 OSS 域名。
5. **导出图片**：用 `html2canvas` 把脑图容器截图（等比放大导出可调清晰度）。
6. **强缓存策略**：nginx 对 `js/css/img` 设 30 天 immutable，`index.html` no-cache —— 发版后需 `Ctrl+F5`。
