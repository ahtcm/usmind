# USMIND 代码文件与函数指南

> 本文档逐一说明 `code/` 下每个文件的作用及其内部主要函数/方法的作用，帮助快速定位代码。

---

## 一、后端（`code/backend`，Spring Boot）

### 1.1 入口与启动
| 文件 | 作用 |
| --- | --- |
| `UsmindApplication.java` | Spring Boot 启动类（`main` 入口） |

### 1.2 common（通用组件）
| 文件 | 作用 |
| --- | --- |
| `Result.java` | 统一 API 返回包装：`ok()` / `fail(code, msg)`，所有接口返回 `{code, message, data}` |
| `ResultCode.java` | 返回码常量（200 成功、400 参数/业务错误、401 未登录、403 无权限、404 不存在、500 系统错误） |
| `BusinessException.java` | 业务异常（携带 code + message），由全局异常处理器统一转成 JSON |
| `GlobalExceptionHandler.java` | `@RestControllerAdvice`：捕获业务异常、认证异常、参数校验异常、兜底异常，统一返回 `Result` |
| `JwtUtil.java` | 生成 / 解析 JWT（`generateToken(username, userId, role)` / `parseToken`） |
| `SecretCipher.java` | 对称加密工具（用于加密 OSS 的 accessKey / secretKey 落库） |
| `SecurityUtil.java` | 取当前登录用户（`getCurrentUserId()` / `getCurrentUser()`） |
| `RedisKeys.java`（如存在） | Redis key 常量 |

### 1.3 config（配置）
| 文件 | 作用 |
| --- | --- |
| `SecurityConfig.java` | Spring Security 配置：放行 `/api/auth/**`、`/api/share/**`、`/api/themes`、`/api/icons`、`/api/preview-config`、上传静态资源；其余需 JWT |
| `JwtAuthenticationFilter.java`（在 security 包） | 每次请求解析 `Authorization: Bearer xxx`，校验 JWT 并注入登录用户 |
| `RedisConfig.java` | Redis 序列化 / 连接工厂配置 |
| `MybatisPlusConfig.java` | MyBatis-Plus 分页插件 |
| `WebConfig.java` | CORS、静态资源映射（`/uploads/**` → 本地目录） |
| `DataInitializer.java` | 启动时初始化：创建默认管理员 `admin/admin123`、默认主题、内置图标（读取 `emoji-seed.json`） |

### 1.4 security（认证）
| 文件 | 作用 |
| --- | --- |
| `LoginUser.java` | 登录用户模型（userId / username / password / nickname / role / status） |
| `UserDetailsServiceImpl.java` | Spring Security 用户加载（按用户名查库） |
| `JwtAuthenticationFilter.java` | JWT 认证过滤器（见上） |

### 1.5 entity（实体，对应 7 张表）
| 文件 | 对应表 | 主要字段 |
| --- | --- | --- |
| `SysUser.java` | sys_user | id, username, password, nickname, avatar, email, phone, role(ADMIN/USER), status |
| `MindMap.java` | mind_map | id, userId, name, data(JSON), layout, theme, viewData, manualStyles, isShared, shareToken, sharePassword, viewCount |
| `FileResource.java` | file_resource | id, userId, mindMapId, originalName, storedName, storageType, url, size, mimeType, createdAt |
| `OssConfig.java` | oss_config | id, name, type, endpoint, accessKey, secretKey(密文), bucket, region, domain, isActive, configJson |
| `SysConfig.java` | sys_config | id, configKey, configValue, updatedAt |
| `ThemeTemplate.java` | sys_theme | id, name, themeKey, branchColors, configJson, sort, enabled, builtin |
| `IconTemplate.java` | sys_icon | id, name, category, sort, enabled, builtin |

### 1.6 mapper（MyBatis-Plus）
| 文件 | 作用 |
| --- | --- |
| `SysUserMapper` / `MindMapMapper` / `FileResourceMapper` / `OssConfigMapper` / `SysConfigMapper` / `ThemeTemplateMapper` / `IconTemplateMapper` | 对应表的数据访问层（继承 `BaseMapper<T>`，自带 CRUD / selectCount / selectPage 等） |

### 1.7 dto（请求对象）
| 文件 | 作用 |
| --- | --- |
| `LoginRequest` | 登录（username, password） |
| `RegisterRequest` | 注册（username, password, nickname） |
| `PasswordChangeRequest` | 修改密码（oldPassword, newPassword） |
| `ResetPasswordRequest` | 管理员重置密码（password） |
| `MindMapSaveRequest` | 保存导图（data, layout, theme, viewData, manualStyles） |
| `ShareRequest` | 分享（password 可空） |
| `UserCreateRequest` / `UserUpdateRequest` | 用户创建 / 更新 |
| `OssConfigRequest` | OSS 配置增改 |
| `AiRequest` | AI 请求（prompt, model 等） |

### 1.8 controller（接口层，全部映射见 docs/API.md）
| 文件 | 作用 |
| --- | --- |
| `AuthController` | `/api/auth/**`：登录 / 注册 / 当前用户 / 退出 / 改密码 |
| `UserController` | `/api/admin/users/**`：用户管理（含删除保底一个管理员） |
| `MindMapController` | `/api/mindmaps/**`：我的导图列表 / 详情 / 保存 / 删除 / 分享 / 取消分享 |
| `AdminMindMapController` | `/api/admin/mindmaps`：管理后台导图列表 / 批量删除 |
| `ShareController` | `/api/share/{token}`：分享页免登录读取导图 |
| `FileController` | `/api/file/**`：上传 / 改内容 / 取文件 |
| `AdminFileController` | `/api/admin/files`：资源管理（列表 / 删除 / 批量删除） |
| `ThemeController` | `/api/themes`（公开）、`/api/admin/themes/**`（管理：增删改、批量删） |
| `IconController` | `/api/icons`（公开）、`/api/admin/icons/**`（管理：增删、批量导入） |
| `OssController` | `/api/admin/oss/**`：OSS 配置 CRUD / 启用 / 测试连通 / 批量删 |
| `SettingsController` | `/api/admin/settings`：系统 KV 配置（登录页声明、缓存时长等） |
| `PreviewConfigController` | `/api/preview-config`：前端拉取全局配置（预览服务、主题、图标等） |
| `PreviewController` | `/api/preview/**`：附件在线预览（pdf.js 代理、office 服务跳转、代码高亮内容） |
| `DataBackupController` | `/api/admin/data/**`：数据库备份（导出 JSON/SQL）、上传恢复 |
| `StatsController` | `/api/admin/stats`：仪表盘统计 |
| `ProjectConfigController` | `/api/admin/project-config/**`：项目配置（后端 / nginx 配置的读取、保存、导出） |
| `AiController` | `/api/ai/generate`：AI 生成（代理 OpenAI 兼容接口） |

### 1.9 service（业务层）
| 文件 | 作用 |
| --- | --- |
| `AuthServiceImpl` | 登录（JWT 签发）、注册、改密码、当前用户信息 |
| `UserServiceImpl` | 用户 CRUD、**删除管理员保底校验**、重置密码、批量删除 |
| `MindMapServiceImpl` | 导图 CRUD、默认主题（"浅色"）、主题改名同步存量导图、分享（token 生成 / 密码校验） |
| `FileServiceImpl` | 上传（按日期分目录、随机名）、写入本地存储、记录 file_resource |
| `ThemeTemplateServiceImpl` | 主题模板 CRUD、内置主题、改名同步 |
| `IconTemplateServiceImpl` | 图标 CRUD、批量导入、内置图标种子 |
| `OssConfigServiceImpl` | OSS 配置 CRUD、激活唯一、测试连通 |
| `SettingsServiceImpl` | KV 配置读写 |
| `StatsServiceImpl` | 仪表盘统计（用户 / 导图 / 资源 / 访问量） |
| `DataBackupService` | 数据库全量导出（JSON / SQL）、恢复导入 |

### 1.10 storage（存储策略）
| 文件 | 作用 |
| --- | --- |
| `StorageStrategy.java` | 存储接口（upload / delete / getUrl） |
| `LocalStorage.java` | 本地文件系统存储 |
| `AliyunOssStorage.java` | 阿里云 OSS |
| `TencentCosStorage.java` | 腾讯云 COS |
| `QiniuStorage.java` | 七牛云 |
| `MinioStorage.java` | MinIO（自建对象存储） |
| `StorageFactory.java` | 策略工厂：按 OSS 配置 type 返回对应实现；未配置回落本地 |

---

## 二、用户端前端（`code/web-user`）

### 2.1 入口与全局
| 文件 | 作用 |
| --- | --- |
| `main.js` | Vue 入口，注册 Pinia / Router，初始化主题 |
| `App.vue` | 根组件 |
| `router/index.js` | 路由：`/login`、`/maps`（导图列表）、`/editor/:id`（编辑）、`/share/:token`（分享只读）、`/` 重定向 |
| `stores/user.js` | Pinia 用户状态：`setToken` / `setUser` / `logout`（localStorage 持久化） |
| `assets/style.css` | 全局样式（含移动端 768px 断点适配、工具条 / 滑动条 / 弹窗 / 全屏查看器等） |

### 2.2 api（接口封装，基于 axios）
| 文件 | 作用 |
| --- | --- |
| `request.js` | axios 实例：baseURL `/api`、请求带 JWT、响应统一拦截（401 跳登录） |
| `auth.js` | `login` / `register` / `info` / `logout` / `changePassword` |
| `mindmap.js` | `list` / `get` / `create` / `save` / `remove` / `share` / `stopShare` |
| `file.js` | `upload` / `updateContent` / `getFile` |
| `icons.js` | `fetchIcons()`（分类图标列表） |
| `themes.js` | `fetchThemes()` |
| `previewConfig.js` | `fetchPreviewConfig()`（预览服务 / 全局配置） |
| `ai.js` | `generate(prompt)` |

### 2.3 utils（工具）
| 文件 | 作用 / 函数 |
| --- | --- |
| `mathRenderer.js` | **公式渲染核心**：`loadMathJax()` 动态加载 MathJax；`tex2svgHtml(tex, display)` LaTeX→SVG HTML；`extractLatexFromHtml(html)` 从节点富文本还原 LaTeX；`isMathHtml`；`migrateKatexToSvg(html)` 旧 KaTeX→MathJax 迁移 |
| `mediastore.js` | **媒体节点核心**：`uploadMedia(file)` 上传到后端；`saveMedia(file)` 保存并生成媒体引用；`mediaToUrl(ref)` 引用→URL；`isMediaRef`；`materializeNodeData(node)` 把媒体引用还原成可渲染数据；`dematerializeNodeData(node)` 序列化前把 blob/媒体转引用；`blobUrlToMediaRef`；`replaceMedia`；`imageSizeFor(url)` 探测图片尺寸 |
| `modal.js` | **统一弹窗 / 提示**（非浏览器原生）：`toast(msg, type)` 轻提示；`showConfirm(opts)` 确认框；`showPrompt(opts)` 输入框 |
| `theme.js` | 前端内置主题定义 `THEMES` 与主题列表渲染 |
| `ai.js` | AI 请求封装 |

### 2.4 views（页面）
| 文件 | 作用 / 主要函数 |
| --- | --- |
| `Login.vue` | 登录 / 注册页（移动端适配） |
| `MindMapList.vue` | 导图列表页：`loadList` / `onNew`（新建）/ `openMap` / `onDelete` / `onShare` / `onStopShare` / `onLogout`；移动端头部优化（隐藏"思维"与用户名、按钮同行） |
| `ShareView.vue` | 分享页（免登录只读渲染导图，密码校验） |
| `Editor.vue` | **脑图编辑器主页面（功能最集中，见下）** |

### 2.5 Editor.vue 主要函数分组
| 分组 | 函数 |
| --- | --- |
| 画布生命周期 | `createMind`（创建 simple-mind-map 实例）、`bindMindEvents`（节点/画布事件绑定）、`loadDoc` / `saveDoc` / `markDirty` / `markSaved` / `scheduleAutoSave`（自动保存防抖）、`migrateDocMath` |
| 多画布标签 | `newTab` / `switchCanvas` / `onNewCanvas` / `delCanvas` / `startRename` / `commitRename` / `onTabDragStart/Enter/Drop/End`（拖拽排序） |
| 视口与缩放 | `zoomIn` / `zoomOut` / `zoomFit` / `centerRoot` / `getViewState` / `setViewPan` / `updateScrollbars` / `sbDown/sbMove/sbUp`（滑动条）/ `onMapMouseDown` / `onMapTouchStart/Move/End`（单指平移 + 双指捏合缩放）/ `rootPanMove/End`（按住根拖动）/ `checkBarOverflow` / `barScroll` |
| 布局与主题 | `applyLayout` / `applyThemeFonts` / `applyTheme` / `applyCanvasBackground`（背景图等比铺满）/ `updateCanvasBgSize` / `applyBgColor` / `applyStylePatch` / `applyStyleState` / `applyMediaCardStyle` / `getBranchColors` / `applyBranchTo` / `applyBranchRainbow`（分支配色 / 彩虹） |
| 公式 | `updateMathPreview` / `openMathEdit` / `openMathQuick` / `insertMathToNode`（插入后自动重渲染防塌陷）/ `extractLatexFromHtml` / `replaceKatexInText` / `richEditConfirm` / `richInsertFormulaAtCursor` / `richMathInsert` |
| 富文本编辑 | `openRichEdit` / `richEditConfirm` / `richEditCancel` / `richToggleMathBar` / `updateRichMathPreview` / `richUploadMedia` / `richInsertMediaAtCursor` |
| Emoji 图标 | `iconShown` / `toggleIconGroup`（分类折叠）/ `loadMoreIcons`（懒加载）/ `onIconScroll` / `insertEmoji`（光标处插入）/ `setIcon` |
| 附件 / 媒体节点 | `insertFileToNode` / `fileToDataUrl` / `compressImage` / `openImageLightbox`（图片全屏浏览，×/Esc 返回）/ `openAttachmentPreview`（附件预览：图片/PDF/音视频/代码/Office）/ `applyAttachDefaultColor`（附件名红色）/ `fixAttachNodeTexts` / `injectAttachIcons`（附件图标）/ `mediaCardColors` |
| 右键菜单 / 快捷键 | `showCtxMenu` / `hideCtxMenu` / `ctxAct`（菜单动作：样式/复制/剪切/粘贴/删除/折叠/展开/公式/附件）/ `onNodeShortcutKeydown`（Ctrl+C/X/V、Delete）/ `getActiveNode` / `cloneNodeData`（字段级克隆）/ `doCopyNode` / `doCutNode` / `doPasteNode` / `doDeleteNode` |
| 导入导出 | `runExport`（JSON/PNG/JPG/JPEG/SVG/PDF/Markdown）/ `exportUsmind`（`.usmind` 资源包：jszip 收集资源 + JSON 打包）/ `collectResourceUrls` / `replaceUrlsInData` / `replaceUrlsInExportData` / `onImportUsmindClick` / `onImportUsmindFile`（导入自动入库资源）/ `onImportClick` / `onImportFile`（JSON 导入） |
| AI | `runAI`（AI 生成节点内容）/ `escapeHtml` |
| 分享 | `onShare` / `confirmShare` / `copyShare` |
| 其它 | `onPaste`（粘贴图片/文件到节点）/ `onDocClick` / `goList` / `downloadDataUrl` / `downloadBlob` / `ensureImageSize` / `numSvg` / `emojiSvg` |

---

## 三、管理后台前端（`code/web-admin`）

### 3.1 入口与全局
| 文件 | 作用 |
| --- | --- |
| `main.js` | 入口（Element Plus + Pinia + Router） |
| `App.vue` | 根组件 |
| `router/index.js` | 路由：`/login`、`/`（Dashboard）、`/users`、`/mindmaps`、`/resources`、`/oss`、`/themes`、`/icons`、`/project-config`、`/data-backup`、`/settings`（带 AdminLayout 与登录守卫） |
| `stores/user.js` | 管理员登录态（token 持久化） |
| `layouts/AdminLayout.vue` | 后台框架：侧边菜单 + 顶栏 + 内容区（移动端抽屉适配） |
| `styles/main.css` | 全局样式（与工具条同配色的下拉框、弹窗提示等） |

### 3.2 api
| 文件 | 作用 |
| --- | --- |
| `auth.js` | 登录 / 信息 / 退出 |
| `users.js` | 用户列表 / 增删改 / 批量删 / 重置密码 |
| `mindmaps.js` | 导图列表 / 详情 / 取消分享 / 删除 / 批量删 |
| `files.js` | 资源列表 / 删除 / 批量删 |
| `oss.js` | OSS 配置 CRUD / 启用 / 测试 |
| `themes.js` | 主题 CRUD / 批量删 / 启用 |
| `icons.js` | 图标 CRUD / 批量导入 / 批量删 |
| `settings.js` | 系统 KV 配置读写 |
| `projectConfig.js` | 项目配置读取 / 保存 / 导出 |
| `request.js` | axios 实例（带 token、统一拦截） |

### 3.3 views（页面与主要函数）
| 文件 | 作用 / 函数 |
| --- | --- |
| `Dashboard.vue` | 仪表盘：`loadStats`（统计卡片）/ `loadRecent`（最近导图）/ `formatTime` |
| `Login.vue` | 后台登录页（含版权 / 入网声明配置渲染，本地缓存时长） |
| `Users.vue` | 用户管理：`loadData` / `handleSearch` / `openCreate` / `openEdit` / `submitForm` / `toggleStatus` / `openReset` / `submitReset` / `handleDelete` / `handleBatchDelete`（保底管理员由后端拦截） |
| `MindMaps.vue` | 导图管理：`loadData` / `openDetail` / `handleCancelShare` / `handleDelete` / `handleBatchDelete` |
| `Resources.vue` | 资源管理：`loadData` / `handleSearch`（按类型/后缀）/ `openPreview`（在线预览）/ `handleDelete` / `handleBatchDelete` |
| `OssConfig.vue` | OSS 配置：`loadData` / `openCreate` / `openEdit` / `submitForm` / `handleActivate`（启用唯一）/ `handleTest`（连通测试）/ `handleDelete` / `handleBatchDelete` |
| `Themes.vue` | 主题模板：`load` / `uploadBgImage` / `uploadBgVideo`（画布背景）/ `parseColors` / `addColor` / `removeColor` / `buildConfig`（节点/分支配色、各层字号字体、背景色透明度）/ `openCreate` / `openEdit` / `submitForm` / `toggle`（启用）/ `handleDelete` / `handleBatchDelete` / `loadGlobal`（全局默认主题） |
| `Icons.vue` | 图标管理：`load` / `applyFilter`（分类筛选）/ `openAdd` / `submitAdd` / `openBatch` / `submitBatch`（批量导入）/ `handleDelete` / `toggleSelect` / `handleBatchDelete` |
| `ProjectConfig.vue` | 项目配置：`loadData` / `resetForm` / `saveUploadCfg`（上传/存储）/ `saveFiles`（nginx / 后端配置文件可视化编辑）/ `exportCfg`（导出配置文件） |
| `DataBackup.vue` | 数据备份：`doBackup`（导出 JSON / SQL）/ `onFileChange` / `onFileRemove` / `confirmRestore`（上传恢复） |
| `Settings.vue` | 系统设置：`loadData` / `handleSave`（登录页声明、缓存时长等 KV） |
