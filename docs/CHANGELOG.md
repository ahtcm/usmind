# USMIND 变更记录

## 2026-10-01 · 后台评论筛选/批量删除 + 会员价格管理与兑换码体系

- **后台评论管理增强**：评论分页新增脑图下拉（mindMapId）与组织下拉（orgId，自动查该组织全部导图）筛选、评论内容/评论者模糊查询；新增批量删除（`POST /api/admin/comments/batch-delete`），删除后前端刷新列表。
- **后台侧边栏滚动**：AdminLayout 左侧菜单 `max-height + overflow-y:auto` 且隐藏滚动条（`scrollbar-width:none` / `::-webkit-scrollbar{display:none}`），移动端抽屉内菜单同样处理。
- **会员价格管理**：member_plan 表新增 `original_price` / `discount_percent` 列（DataInitializer 自动迁移补列）；后台「会员价格管理」卡片支持新增/编辑/删除套餐，改价后客户端 `/api/member/plans` 实时生效（前端显示原价划线 + 折扣标签）。
- **一次性兑换码**：新增 `redeem_code` 表 + 后台生成（选套餐或自定义天数，1-100 个，16 位去混淆字符集 `ABCDEFGHJKLMNPQRSTUVWXYZ23456789`）、状态筛选（可用/已兑换/已停用）、列表删除；用户端会员中心新增兑换输入框（`POST /api/member/redeem`），兑换后会员时长累加、`sys_user.member_source=redeem` 标注来源。
- **会员来源标注**：sys_user 新增 `member_source` 列（pay 支付 / redeem 兑换码 / 空=后台开通）；后台会员列表新增「会员来源」列（支付蓝 / 兑换码绿 / 后台灰）；仪表盘新增「兑换码会员」统计卡与会员来源分布。
- **实测**：改价实时生效（月度 8.8 元 / 原价 19.8 / 55% 折扣）；兑换链路通过（expire 累加 30 天、usedByName=系统管理员、redeemVipCount=1）；评论三种筛选与批量删除接口通过；用户端会员中心下拉筛选 / 划线价 / 折扣标签 / 兑换入口渲染正常。

## 2026-10-01 · 登录图形验证码 + 注册页移除头像 + 后台独立登录接口（`d63ac11` / `b4e7cb5`）

- **登录图形验证码**：登录页用户名/密码后新增图形验证码输入框 + 点击刷新（复用注册验证码组件）；后端 `AuthController.login` 在 `authLoginCaptchaEnabled=1` 时校验 `captchaUuid` + `captchaCode`；`LoginRequest` 增加两字段。
- **后台独立登录接口**：新增 `/api/auth/admin-login` 不校验图形验证码（后台管理员走此接口，避免验证码不可用/超时误锁后台）；`SecurityConfig` permitAll 加入该路径；web-admin `api/auth.js` 登录改走 admin-login。
- **独立开关**：`SettingsServiceImpl` 新增 `authLoginCaptchaEnabled`（默认 1）；`/api/config/client` 输出该开关；后台「支付与验证码」页注册验证码卡片后新增「登录图形验证码」开关；关闭后前端登录页同步隐藏验证码、后端登录不校验。
- **注册页移除头像上传**：删除 Login.vue 注册模板头像上传块（注册字段 = 用户名/密码/确认密码/昵称/手机号/邮箱），与"登录页头像直接上传"设计保持一致。
- **实测**：默认开显示验证码 → 关闭后前端隐藏且无验证码登录成功 → 恢复默认开；后台 admin-login 正常进 dashboard。
- **提交**：`8dae620`（注册头像移除）/ `1c03a42`（master）/ `d63ac11`（登录验证码）/ `b4e7cb5`（master）。

## 2026-09-30 · 全量测试问题修复 P1/P2/P3（`06cf31e` / `de0af9e`）

- **P1 IP 自动拉黑无逃生通道**：`ip_rule` 表新增 `expire_at`（LocalDateTime，DataInitializer 建表含该列+旧表 information_schema 迁移补列）；`IpGuardServiceImpl.autoBlack` 写入到期时间、`match` 改 selectList 遍历并对过期记录惰性 deleteById；`IpGuardFilter` 对 `/api/admin/**` 直接放行（后台保留 ADMIN 角色 JWT 保护，管理员删黑名单后用户立即恢复）。实测：触发限频 → 拉黑 → 后台删除黑名单 → 立即恢复可用。
- **P2 主题会员 👑 图标不显示**：`Editor.vue isMemberTheme` 由索引匹配改为按 `t.name` / `t.key` 匹配，后台配置的会员主题正确显示皇冠。
- **P2 公式插入不即时渲染**：`insertMathToNode` 改 async，取 raw 后 `await loadMathJax()` 再渲染，插入后即时显示（防节点塌陷）。
- **P2 后台新增订单/评论管理页**：后端 `AdminOrderController` / `AdminCommentController`（列表/删除/批量删，联查用户名/导图名）；前端 `Orders.vue` / `Comments.vue` + `api/orders.js` / `api/comments.js` + 路由 + AdminLayout 菜单（Tickets / ChatDotRound 图标）。
- **P3 清理测试遗留数据**：删除临时测试附件（script/tmp_attach 等）。
- **验证遗留**：后台会员配置写入的测试值 `memberThemeIds=晨曦白` 未还原（供用户验证 👑）。
## 2026-09-29 · 导出重构：html2canvas 混合 DOM 方案（公式/图片完整导出 + 进度遮罩）

- **背景**：43 号"数学公式"导图导出 PNG/PDF 出现黑块（MathJax 大公式外层 svg + 内层嵌套 svg 被旧 `liftFormulaSvg` 同时提升到根级，同位置叠加渲染成黑块）；50 号"图片"导图导出失败（simple-mind-map 输出 SVG 中空元素未自闭合导致 XML 解析失败，Image 加载失败回退到 SVG blob）。
- **方案（用户拍板：先导出完整 SVG，再用图片/PDF 装这个 SVG）**：SVG 作为 `<img>` 加载时 foreignObject 一律不渲染（浏览器安全限制），html2canvas 直接捕获内联 SVG 时也会把 svg 序列化成图片（foreignObject 照样丢）。最终实现为**混合 DOM 捕获**：
  - `collectFoItems`：DOMParser 解析完整 SVG，收集所有 `foreignObject`（按祖先 matrix 变换链内→外累积到画布绝对坐标），移除后得到纯 SVG（连线 / 节点形状）。
  - `renderChunkByHtml`：离屏容器 = SVG 层（innerHTML 纯块 SVG，viewBox 裁剪到分块视口）+ HTML 富文本层（原 foreignObject 内容绝对定位到画布坐标，浏览器原生渲染文字 / 公式 / 图片）；MathJax 公式 svg 的 `ex` 单位按 foreignObject 的 font-size 换算为 px（html2canvas 序列化 svg 为图片时无字体上下文）；视频 / 音频按用户方案贴「▶视频 / ♪音频：文件名」占位；`html2canvas(wrap, { useCORS, scale:1 })` 逐块捕获。
  - `exportChunkedImages` / `exportChunkedPdf`：不再走 `loadImg(blobURL)+drawImage` 大 SVG 渲染，逐块调 `renderChunkByHtml`（CHUNK=5000，超大画布分块 PNG 打包 zip / PDF 逐页）。
- **进度遮罩**：导出点击后**立即**显示全屏遮罩（`export-loading`，与页面加载同风格：spinner + 当前步骤文案 + 百分比进度条），先 `nextTick + 60ms` 让出主线程再开始 `getSvgData`（大导图 SVG 序列化是同步操作会阻塞 Vue 渲染）；导出中 `exporting` 标志防重复点击，失败 / 异常兜底自关（10 分钟超时）。
- **实测**：50 图片导图 PNG 584×524（彩色图片正常显示）、PDF 合法；43 公式导图 PNG 分块 zip 4 块全部公式完整、无黑块、PDF 1.4MB；进度遮罩点击后 2ms 出现、25%→50%→75%→完成自动关闭；控制台无报错。
- **依赖**：新增 `html2canvas`（npm 国内镜像安装）。
- **提交**：`d29e335`（方案落地）/ `8c51113`（进度遮罩）/ `4ac6a18`（立即显示+防重复）/ `53d0f1e`（先让出主线程，遮罩 2ms 渲染）。

## 2026-09-28 · 移除便携绿色版（按需求）

- **删除便携版全部内容**：
elease\usmind-portable\（产物目录此前已清理）；构建脚本 script\bat\build-portable.bat / uild-all.bat（便携+自解压+在线一键构建，随便携产物一并移除）；启动器源码 code\launcher-src\launcher\Main.java、logo.ico、min.svg、x.svg、
ginx-portable.conf、launcher.jar、classes\。
- **保留自解压 / 在线版源码**：code\launcher-src\selfextract.cs / Installer.cs / uild_online.ps1 / 两个 stub exe（用户未要求删除；二者基于便携产物组装，如需重新启用需先重建便携产物）。
- **文档同步**：README 目录树 / 发布形态章节（三种→两种，重编号）/ 构建表格 / FAQ；FILE_GUIDE「发布与构建体系」章节与脚本表格同步更新。

## 2026-09-28 · 脚本体系重组：bat 移入 script\bat + 新增 Linux shell

- **Windows 批处理全部移入 `script\bat\`**：15 个 bat（start-all / stop-all / delete-all / init-db / start-{mysql,redis,nginx,backend,web-user,web-admin} / build-{backend,web-user,web-admin,portable,all}）从 `script\` 根移入 `script\bat\`；所有脚本的项目根定位从 `cd /d "%SCRIPT_DIR%.."` 改为 `cd /d "%SCRIPT_DIR%..\.."`（bat 在 script\bat，上两级即项目根），同目录兄弟调用（start-all → start-*.bat）不受影响。
- **新增 Linux shell 等价物 `script\shell\`**：14 个 .sh（start-all / stop-all / delete-all / init-db / start-{mysql,redis,nginx,backend,web-user,web-admin} / build-{backend,web-user,web-admin,all}），功能与 bat 一一对应；环境目录约定与 Windows 一致放 `tools\`（jdk-17.0.2 / apache-maven-3.9.6 / nodejs / mysql-* / redis-* / nginx-*，版本目录名自动探测），后端 `java -jar target/usmind-backend.jar --spring.profiles.active=dev --file.upload-dir=<ROOT>/uploads`，前端 dist 构建后部署到 `tools/nginx-*/html/{usmind,admin}`；`build-all.sh` 不含便携版（服务器部署不需要 jpackage 便携包）。
- **README / docs 脚本路径同步更新**：`script\xxx.bat` → `script\bat\xxx.bat`；目录树补充 `script\bat` / `script\shell` 结构。

## 2026-09-28 · 启动器可靠性修复 + 构建脚本统一入 script\（6d16030）

- **修复启动器"自杀 bug"**：`killUsmindFamily` 会把启动器自身的 JVM（路径含 `usmind-portable` 的 java.exe）当作残留 taskkill，进程树强杀导致启动器中途死亡、停止流程中断、服务残留。现改用 Java `ProcessHandle.allProcesses()` 原生枚举并排除自身进程，stopAll 末尾兜底清理重试 3 次；停止后端口/进程最终全部释放（MySQL/nginx 优雅关闭约 30s 属正常延迟）。
- **修复数据库建表被跳过**：`ensureMysqlData` 仅判断"库存在"就跳过导入，导致业务表缺失、后端 `Table doesn't exist` 启动失败。现改为每次启动幂等导入 `init.sql`（全部 `CREATE TABLE IF NOT EXISTS`），后端可正常启动。
- **构建脚本统一移入 `script\` 目录**：`build-portable.bat` / `build-all.bat` 从项目根移入 `script\`，路径定位与其他脚本统一（`SCRIPT_DIR` 相对定位）；修复移动引入的 `ROOT=%CD%\` 尾斜杠问题（否则 `%ROOT%release` 拼接缺分隔符）。
- **清理误跟踪**：`release/usmind-portable/components/nginx/conf/nginx.conf` 取消 git 跟踪（release 整体忽略，构建时由模板 `code\launcher-src\nginx-portable.conf` 重新生成，含 `pid` 指令保证 `nginx -s stop` 生效）。
- **提交 `code/logo.ico`**：网页与 exe 统一 logo（与 `code\launcher-src\logo.ico` 相同）。
- **在线版分片从 GitHub 移除**：`release/online-dist` 不再托管（commit a620e31），`code\launcher-src\build_online.ps1` 保留，可随时重新生成；如需继续使用在线版需自行托管分片后重新构建安装器。
- **验证**：`script\build-portable.bat -s` 全流程构建 exit 0；干净环境下 `USMIND.exe --smoke` = `[SMOKE] OK` + 停止后无任何残留。


## 2026-09-28 · v1 正式发布（三种发布形态 + 一键构建 + CDN 在线分发）

- **新增 `build-all.bat`**：一键重建三种发布形态，全部相对路径（`%~dp0` 定位项目根，csc 用 `%WINDIR%`）。
- **① 便携绿色版** `release\usmind-portable\`：jlink 精简 JRE + jpackage 启动器 + 全组件；双击即用，首次自动初始化 MySQL。
- **② 单文件自解压版** `release\USMIND-setup.exe`：C# stub + Optimal zip 拼接，解压到 `%LOCALAPPDATA%\USMIND` 后启动。
- **③ 在线安装版** `release\USMIND-online.exe`：WinForms 下载器 + 内嵌分片清单，从 jsDelivr CDN（GitHub `@v1` 标签，国内加速）下载 22 片 371MB → 拼接解压组装 → 启动；可视化进度条 + 可取消；`USMIND_BASE_URL` 可覆盖下载源。
- **分片体系**：`release\online-dist\`（6 组件 zip 按 20MB 切分 + manifest.json），`build_online.ps1` 生成；分片已入库并推 GitHub `@v1`。
- **git**：构建源码 `release\launcher-src\` 入库（`-f` 强加，release 在 .gitignore）；`v1` 标签创建并同步 github + gitee；develop 双远程同步至 `cc24fb1`。
- **验证**：在线版真实 CDN 端到端通过（jsDelivr 下载 → 组装 1GB → 8866 200 → 登录 200）；自解压版 `--extract-only` 与完整流程通过。

## 2026-09-27 · 全栈重构：Vue3 用户端 + 管理后台 + Spring Boot 后端

- **后端**：Java 17 + Spring Boot 3 + MyBatis-Plus + JWT（`code/backend`），接口分层 controller/service/entity/mapper；7 张表（sys_user / mind_map / file_resource / oss_config / sys_config / sys_theme / sys_icon）；存储策略模式（本地 / 阿里云 / 腾讯 / 七牛 / MinIO 热插拔）。
- **用户端重构**：原脑图用 Vue3 + Vite + simple-mind-map 重构（`code/web-user`），样式与功能保持；新增登录、公式节点（MathJax SVG）、Emoji 图标面板（分类折叠/懒加载/光标插入）、附件/媒体节点（图片视频平铺、单击预览、双击编辑）、画布背景图/视频、视口滑动条、标签页拖拽、移动端适配、导入导出（JSON/PNG/JPG/SVG/PDF/Markdown）与 `.usmind` 资源包迁移。
- **管理后台**：Vue3 + Element Plus（`code/web-admin`，`/admin/`），用户 / 导图 / 资源 / OSS / 主题模板 / 图标 / 项目配置 / 数据备份 / 登录页配置；移动端适配；至少保留一个管理员。
- **部署形态变化**：由"EXE + git 双推 + Netlify"（早期 usmind_server.py 方案，`tools/` 旧脚本已删）改为**标准前后端分离**：nginx（80/8866/8867）+ Spring Boot 8899 + MySQL/Redis；一键脚本 `script/start-all.bat` 等（相对路径）。


## 2026-09-25 · 渲染底层整体替换为 simple-mind-map（wanglin2/mind-map）

### 背景
用户要求对标 XMind 全功能（含图片/公式/音视频/iframe 等富内容嵌入），mind-elixir 无法原生满足，经用户确认整体替换为
`simple-mind-map@0.14.0-fix.3`（注意：npm 上的 `mind-map@0.3.0` 是 jsMind 移植版，非同一项目；`@mind-map/core` 不存在）。

### 已完成的适配（源码在 code/web/src）
- **main.js**：保留原 UI 壳（悬浮顶/底栏、四标签侧栏、右键菜单、AI 面板、多画布），全部交互改调 mind-map API：
  - 数据迁移：旧 mind-elixir `nodeData/topic/icons/expanded` → `{ root: { data: {text, expand, icon}, children } }`，并清洗历史污染数据
  - 命令：Tab=INSERT_CHILD_NODE、Enter=INSERT_NODE、Ctrl+C/V/X=copy/paste/cut、Ctrl+Z/Y=BACK/FORWARD、F2=编辑
  - 右键菜单 9 项（插入子/同级、编辑、复制、剪切、粘贴、折叠、展开、删除）
  - 折叠/展开、全部收起/展开、布局切换（右/两侧/左）、撤销重做、自动保存（5s）、多画布切换
  - 主题 6 套（XMind 浅色=默认/深空黑/极简白/暮色紫/森林绿/暖阳橙）+ 样式滑块（圆角/字号/线宽/边框）+ 自定义背景色
  - 图标 42 个：优先级（内置 priority_1-4）、序号（自定义 number_1-10）、表情包（自定义 emoji_xx，SVG 文本渲染）
  - 彩虹分支线：RainbowLines 插件 + XMind 风格色板
  - 交互：左键画布平移 + 右键拖拽框选（Select 插件 useLeftKeySelectionRightKeyDrag:false）、滚轮缩放
- **theme.js**：6 套 mind-map 主题（lineStyle:'curve' 圆滑曲线，rootLineKeepSameInCurve）
- **ai.js**：AI 生成结果 `toMindMapData` → `{root}` → `setData(root)` + fit
- 旧代码备份：`src/backup-mind-elixir/`（main.js/style.css/theme.js/ai.js）

### 关键踩坑（换底必读）
1. `mind.setData(data)` 接收的是**根节点数据**（`{data, children}`），不是 `{root}` 包裹；完整格式用 `setFullData`
2. `renderer.setData` 只赋值不重绘；`mind.setData` 内部会 reRender，不要在之后手动 `renderer.setData`
3. 主题名必须是字符串且已 `MindMap.defineTheme` 注册；旧 doc.theme 若为对象（mind-elixir 版 {name,palette,cssVar}）需规范化
4. 图标是 `type_name` 命名（priority_1 / number_1 / emoji_😀），emoji 需自定义 iconList（SVG text 包裹）
5. 构造 opt 需在实例化前注册插件与主题（`MindMap.usePlugin` / `MindMap.defineTheme`）

### 部署状态
- 构建：`cd code/web && npm run build`（产物 389.63 kB）
- 部署：`python script/build_site.py` → nginx 8866（相对路径校验通过）
- git：已推送 gitee.com:carwang/usmind.git master（8f6a332..981e1ab）

### 待办（后续迭代）
- 内容嵌入增强：图片/GIF 原生支持已具备，音视频/iframe/LaTeX 公式待接入
- 加密分享（btn-share 为占位，build_site.py 已留 --password 参数）
- EXE 打包验证（script/build.bat 占位）
- OSS 上传、git 自动同步入 EXE
- AI 双源实测（需 Ollama 地址 / API Key）

## 2026-09-25 · 彩虹分支着色（对齐 XMind 分支配色）

- 每套主题新增 `branchColors` 分支色板（XMind 浅色/极简白：绿-青-黄-橙-粉-紫-蓝-橘；深色主题用高亮分支色）
- 实现 `applyBranchRainbow()`：按第二层分支序号分配颜色——一级分支节点实色填充+白字+同色边框+同色线条；该分支子树用分支色 14% 的浅色填充+浅色边框+同色线条
- 触发时机：初始化 / 画布切换 / 主题切换 / 数据变化（350ms 节流），新建节点自动继承父分支色
- 主题切换同步更新 RainbowLines 插件色板，线条与节点配色统一
- 已推送 gitee（981e1ab..de8121d）

## 2026-09-25 · 布局系统（XMind 风格布局选择）

- 侧栏"布局"页重做为 8 种布局卡片网格，每项带 SVG 示意图标 + 名称 + 选中高亮（对齐 XMind 布局选择器）：
  思维导图（两侧）/ 逻辑结构图（右）/ 逻辑结构图（左）/ 组织结构图 / 目录组织图 / 时间轴 / 竖向时间轴 / 鱼骨头图
- 全部布局可实时创建切换（simple-mind-map 布局引擎），底栏布局下拉同步 8 项
- 已实测：组织结构图（垂直树状）、时间轴（水平主线）、鱼骨头图（斜向鱼骨）、竖向时间轴（垂直主线）切换正常，彩虹分支配色在各类布局下保持
- 修复图标页：优先级按钮 P0-P3、序号 01-10、表情包按钮结构复原（此前被批量脚本弄坏为 priority_1..4 文本）
- 已推送 gitee（de8121d..04f923c）

## 2026-09-25 · 新增黑曜石主题

- 新增第 7 套主题"黑曜石"（对齐 XMind 黑曜石/用户参考图）：黑色背景 + 白色矩形中心主题 + 分支按白/红/黄/蓝/绿/紫/橙/青彩虹分色
- 修复彩虹分支文字色逻辑：浅色分支（白/黄底）自动使用深色文字，深色分支保持白字，全主题受益
- 已推送 gitee（04f923c..83de389）

## 2026-09-25 · 主题模板卡片升级

- 主题模板从纯文字改为「彩虹色块条 + 名称」卡片（对齐 XMind 配色面板：晨曦/彩虹/活力式色块条），色块条为各主题 8 段分支色
- 选中态：主题色高亮边框 + 阴影
- 已推送 gitee（83de389..ee0250c）

## 2026-09-25 · 节点大气化升级（对齐 XMind 大节点样式）

- 全部 7 套主题统一放大：中心主题内边距 24×14、分支 20×12、子节点 16×10
- 字号提升：中心 18px、分支 16px、子节点 15px；分支 600 / 子节点 500 半粗字重
- 圆角加大（中心 16、分支 12、子节点 10），分支间距拉大（横向 70 / 纵向 52）
- 已推送 gitee（ee0250c..198603f）

## 2026-09-25 · 节点输入实时伸长

- 开启 simple-mind-map `openRealtimeRenderOnNodeTextEdit`：编辑文字时节点外框随输入实时适配大小（100ms 防抖），所见即所得，不再等失焦才伸长
- 已推送 gitee（198603f..d63c4d6）

## 2026-09-25 · 修复 Tab/Enter 一次创建两个节点

- 根因：simple-mind-map 内置已注册 Tab→创建子节点 / Enter→创建同级节点，我方 `addShortcut` 为数组追加导致双重执行
- 修复：移除与内置重复的 Tab/Enter/Ctrl+C/V/X/F2 注册，仅保留内置未注册的 Ctrl+Z 撤销 / Ctrl+Y 重做
- 实测：Tab、Enter 各一次按键仅创建一个节点
- 已推送 gitee（eac49ca..a4932ad）

## 2026-09-25 · 画布标签右键菜单

- 底部画布标签支持右键菜单（对齐 XMind）：复制链接 / 重命名 / 复制 / 删除 / 将画布保存为思维导图
- 复制链接：复制 `?canvas=N` 直链，打开后自动定位对应画布
- 重命名：修改画布名，标签栏实时显示（原固定"画布N"）
- 复制：深拷贝当前画布为新画布（名称带"副本"）
- 删除：二次确认，至少保留一个画布
- 保存为思维导图：导出 `.smm.json` 完整数据文件
- 已实测复制（6→7）、删除（7→6）
- 已推送 gitee（a4932ad..fc1166f）

## 2026-09-25 · 画布标签改版（按用户新设计）

- 移除画布弹窗菜单（三角按钮/右键菜单），改为：
  - 每个标签右侧**圆×按钮**：点击删除画布（hover 显示、红色悬停、二次确认、至少保留一个）
  - **双击标签名字**：行内编辑改名（Enter 保存 / Esc 取消 / 失焦保存），改名后顶栏文件名同步
- 已实测：双击改名"未命名→学习笔记"、圆×删除 7→6
- 已推送 gitee（fc1166f..c0856d4）

## 2026-09-25 · 右键菜单新增节点样式设置

- 节点右键菜单新增 4 项（编辑节点之后）：
  - **字体**：二级子菜单（默认/微软雅黑/宋体/黑体/楷体/等线）
  - **字号**：二级子菜单（12-36 共 9 档）
  - **字体颜色**：点击弹出系统取色器，实时预览
  - **背景颜色**：点击弹出系统取色器，实时预览
- 实现：SET_NODE_STYLE 命令设置 fontFamily/fontSize/color/fillColor
- 已实测字号 24 生效；菜单定位适配加高后的菜单（预留子菜单宽度）
- 已推送 gitee（998bbca..0751d12）

## 2026-09-25 · 修复节点样式两个问题

1. **字号子菜单默认收起**：此前是测试脚本残留的展开状态，代码本身 hover 才展开，刷新即正常
2. **字体颜色/背景颜色设置后无效果**：根因是彩虹分支着色在编辑后 350ms 会用分支色覆盖节点的 color/fillColor
   - 新增**手动样式锁定**机制：通过菜单设置过样式的节点加入锁定集合，彩虹着色跳过这些节点（保留自定义颜色/背景）
   - 锁定集合随画布持久化（doc.manualStyles），换画布/刷新不丢
   - 字号/字体设置同样触发锁定（防止后续被其他编辑重置）
- 已实测：设置字号28+红色文字+浅红背景后，触发任意编辑彩虹重着色，三者均保留
- 已推送 gitee（0751d12..5aa8b75）

## 2026-09-25 · 保存链路加固(样式不再丢失)

- 排查确认：保存本身正常（样式写入 nodeData 并随 getData 持久化，实测刷新后保留）
- 加固三点，杜绝任何时序丢失：
  1. **菜单设置样式（字号/字体/颜色/背景）后立即 saveDoc 落盘**，无需等 5s 自动保存或手动点保存
  2. **刷新/关闭页面前强制保存**（beforeunload）
  3. 切画布时原逻辑已保存当前画布
- 已实测：设置字号 32 后不点保存，localStorage 立即落盘；刷新页面样式保留
- 已推送 gitee（5aa8b75..8bacd16）

## 2026-09-25 · 修复颜色保存不住 + 线条加粗

**1. 节点背景色/字体颜色刷新后丢失（字号保留）**
- 根因：页面初始化走 createMind（直接 new MindMap），未从画布数据恢复手动样式锁定标记 manualStyled → 彩虹分支着色初始化时覆盖自定义颜色，且后续保存把锁定标记清空（恶性循环）
- 修复：createMind 初始化即恢复 `manualStyled = new Set(doc.manualStyles || [])`
- 已实测：设置背景色 #1122aa → 保存 → 刷新 → 分支一显示深蓝背景、字号 32 保留、锁定标记保留

**2. 线条默认粗 1.5 倍**
- 所有主题 lineWidth 由 2 统一加粗为 3（×1.5）
- 已推送 gitee（8bacd16..9ec5ebf）

## 2026-09-25 · 线条加粗到4 + 根节点连线起点贴合

- 所有主题 lineWidth 3 → 4
- 新增 `rootLineStartPositionKeepSameInCurve: true`（7 套主题）：根节点连线起点从框**右边缘中点**接出（此前从框中间穿出），与分支节点连线风格一致（"结合到一起"）
- 已实测：根→分支线与分支→子线起点一致贴合框边缘，线条明显加粗
- 已推送 gitee（9ec5ebf..c9b62f2）

## 2026-09-25 · 修复"调节生效但保存不了"的根因(初始化中断)

- **根因**：applyBranchRainbow 内颜色处理收到 null → hexRgb 抛 `null.replace` → main.js 执行中断（window.usmind 未赋值、状态一直显示"未保存"、部分逻辑未跑）
- **修复**：
  1. hexRgb 空值容错（null/undefined 返回黑色兜底）
  2. getBranchColors 分支色数组 filter(Boolean) + 内置兜底色板，杜绝空数组/空元素
- **结果**：main.js 完整执行（usmind 恢复、保存状态正常显示）
- **保存闭环实测**：设置字号/字体颜色/背景颜色 → 保存 → 刷新 → 全部保留（24px 保留、深蓝背景保留、锁定标记保留）
- 已推送 gitee（c9b62f2..541955e）

## 2026-09-25 · 节点插入媒体(图片/音频/视频/文件) + Ctrl+V 粘贴

- **右键菜单新增「插入」子菜单**：图片、音频、视频、文件(附件)、链接(超链接)、备注
- **Ctrl+V 直接粘贴到选中节点**：
  - 图片/动图：粘贴即插入节点内展示(静态图压缩到最长边1280, GIF/WebP保留动画)
  - 音频：节点内嵌播放器 `<audio controls>`
  - 视频：节点内嵌播放器 `<video controls>`
  - 文件：节点附件图标(点击打开/下载)
- **存储方案(IndexedDB + 引用)**：新增 `src/mediastore.js`，媒体文件统一存 IndexedDB，
  节点数据只保存小引用 `media://<id>`(localStorage 不爆容量)；
  渲染前物化为 blob URL、保存前还原为引用、刷新后自动恢复
- **修复启动时序**：createMind 异步化(物化后再渲染)，window.usmind 改 getter 实时取实例
- **富文本语义修正**：引擎富文本 = richText=true 标志 + text 字段存 HTML
- **实测通过**：图片/音频/视频/文件 四类粘贴渲染+保存引用+刷新恢复全链路；菜单插入子菜单出现
- 已推送 gitee

## 2026-09-25 · 媒体节点卡片样式优化

- 图片/音频/视频/文件节点统一为**中性卡片样式**：
  - 形状由默认椭圆改为**圆角矩形**(borderRadius 14、细边框 1)
  - 背景色按**当前主题深浅**自动适配(深色主题→深灰卡片 #2A2F3A, 浅色主题→浅灰卡片 #EEF0F3)
  - 边框中性灰, 与播放器/图片协调, 不再被彩虹分支配色包成彩色框
- 加载时对已有媒体节点**幂等统一应用**该卡片样式(刷新/切画布后保持)
- 插入新媒体时同样应用 + 手动样式锁定(防止彩虹着色覆盖)
- 已推送 gitee

## 2026-09-25 · 媒体尺寸适配与铺满节点框

- 图片节点显示最长边 240 → **210**(适配节点框, 不过大)
- 视频节点固定 **300×170**(16:9 圆角, 深色底), 铺满节点框
- 媒体节点内边距 paddingX/Y = **6**(紧凑, 图片/视频铺满框不越界)
- 已推送 gitee

## 2026-09-25 · 媒体平铺节点(溢出裁剪, 视觉沾满)

- **图片节点**：图片作为节点主体**平铺**——媒体尺寸比节点**略大**(四周各溢出10px)，
  超出节点部分**隐藏不显示**，视觉上图片沾满整个圆角卡片
- **视频节点**：video 100% 填满节点框(object-fit:cover)，铺满圆角卡片
- 媒体节点内边距归零(div 即内容，音频播放器保持紧凑内边距)
- 图片节点保留原节点名小标签(左下角半透明)
- 已推送 gitee

## 2026-09-25 · 修复媒体伸出节点框

- **根因**：旧结构 audio/video 无固定尺寸容器，引擎测量高度小于控件实际渲染高度 → 底部/边缘伸出节点框
- **修复**：加载时自动迁移旧媒体为**固定尺寸 div 容器**(audio 280×40、video 300×170)，
  媒体 100% 填满容器(overflow hidden 裁剪)，测量尺寸准确，不再溢出节点框
- 新插入音频同样改为固定尺寸容器
- 已推送 gitee

## 2026-09-25 · 修复附件打不开

- **根因**：引擎点击附件图标只发 `node_attachmentClick` 事件，未绑定打开行为
- **修复**：监听 `node_attachmentClick`，点击附件图标即**下载对应文件**(attachmentName 命名)
- 已推送 gitee

## 2026-09-25 · 附件预览 + 在线编辑

- 点击附件图标弹出**预览窗口**(毛玻璃悬浮):
  - 图片 → 内联预览
  - PDF → iframe 内联预览
  - 音频/视频 → 内联播放器
  - **文本类(txt/json/md/js/css/html/xml/csv/log 等) → textarea 在线编辑 + 「保存修改」**
  - 其他类型 → 提示点击下载
- **在线编辑保存**：修改内容写回 IndexedDB(覆盖原媒体引用)，节点附件即时刷新，自动保存
- 实测: PDF 预览、JSON 在线编辑 → 保存 → 刷新持久化，全链路通过
- 已推送 gitee

## 2026-09-25 · 右键菜单支持删除附件

- 右键菜单新增**删除附件**：一键移除节点附件(attachmentUrl/attachmentName)
- 仅当节点**有附件**时显示该菜单项(无附件自动隐藏)
- 删除后自动保存
- 已推送 gitee

## 2026-09-25 · 附件节点白色磨砂样式

- **附件节点**(带文件)框子改为**白色磨砂质感**：白底 + 点状虚线边框 + 圆角
  (图片/音视频节点保持无框融入画布)
- **删除附件后节点回归彩虹分支色**：删除附件时清除白色样式并解锁，彩虹自动重新着色
- 已推送 gitee

## 2026-09-25 · 全局前端动画丝滑化

- **节点/连线平滑过渡**：折叠/展开/布局切换/节点移动时, 节点位置 0.22s 缓动、连线路径 0.25s 缓动(仿 XMind)
- **智能禁用**：文字编辑输入与节点拖拽期间自动禁用过渡, 保持跟手流畅与打字无延迟
- **右键菜单**：进入淡入缩放(原有), 新增退出淡出动画
- **子菜单**：展开改为淡入滑出(替代生硬跳变)
- **附件预览弹窗**：毛玻璃弹窗进入缩放淡入动画
- **画布切换**：标签切换时内容淡入淡出
- **工具栏/标签/主题卡片**：hover 过渡统一柔化; 主题切换背景色平滑过渡
- 已推送 gitee

## 2026-09-25 · EXE v1 交付 (Windows 一键运行中枢)

- **release/USMIND.exe** (18.5MB 单文件, PyInstaller onefile, 目标电脑零环境依赖)
- 功能:
  - 内置服务: 托管网页编辑端(默认端口 8899, 避开 nginx 8866), 一键启动/停止/打开编辑页
  - 配置中枢: 仓库同步 / AI(Ollama+第三方) / OSS / 运行参数, 全部可视化配置
  - 配置持久化: EXE 同级 config/config.json, 复制 config 文件夹即迁移
  - 保存自动同步: 网页保存 → EXE 接收 → 编译静态项目 → 生成分享页 share.html(内嵌数据) → git 提交推送
  - 内置 git(dulwich): 目标电脑无需安装 git; 推送需在配置面板填 Gitee/GitHub 访问令牌
  - 日志: EXE 同级 logs/usmind.log
- 网页端配合: 加载 EXE 注入的分享数据(__USMIND_INIT__); 保存时自动推送本地 EXE
- 一键脚本(script/): build_exe.bat(构建+打包) / start_usmind.bat / stop_usmind.bat / start_nginx.bat / stop_nginx.bat
- 已推送 gitee(部署根含 share.html 分享入口)

## 2026-09-25 修复: 新建画布
- 底部画布栏 + 按钮未绑定新建事件(仅顶部新建按钮有效), 点击无反应
- 修复: main.js 为 #canvas-add 补绑新建画布事件(与 btn-new 一致)
- build_site.py 保护 share.html 与 data/ 目录(EXE 保存生成的运行时产物不再被构建清理)
- 恢复误删的 share.html 与 data/usmind.json 并推送 gitee
- EXE 重打包(含修复)


## 2026-09-25 配置: Gitee+GitHub双远程
- EXE 支持多远程推送(远程地址逗号分隔多个URL)
- 推送逻辑升级: 优先系统git按已配置remote推送(SSH), 失败回退内置dulwich(HTTPS+Token)
- 配置双仓库: gitee.com/carwang/usmind + github.com/ahtcm/usmind
- 绑定用户本机SSH公钥至GitHub, 实测EXE保存自动双推成功
- Ollama已启用(ollama_enabled=true, url含/v1)



## 2026-09-25 · 媒体同步方案落地(本地 source/ + 线上 URL 改写 + 双推闭环)

### 背景
用户要求"粘贴资源跟仓库走": 未配 OSS 时资源落盘仓库 source/ 目录、节点存相对路径、保存时改写为线上 URL; EXE 打开自动拉取、关闭/停止自动推送。

### 已实现
- **EXE 服务** (`usmind_server.py`): 新增 `POST /api/upload`(multipart 解析, 资源存 `仓库根/source/`, 返回 `source/xxx` 相对路径) + `GET /source/*` 静态路由(本地预览资源, 带 CORS)
- **URL 改写** (`usmind_sync.py`): `build_repo` 新增 `resource_prefix` 参数, 生成 share.html 时把 `source/` 相对引用改写为 `https://usmind.netlify.app/source/xxx`(配置项 resource_prefix 可改)
- **web 端** (`main.js`): 图片/音视频/文件粘贴 → 优先 `POST http://127.0.0.1:8899/api/upload` 落盘仓库(source/ 相对路径), EXE 未开时回退 IndexedDB
- **EXE 生命周期** (`usmind_app.py`): 启动 1.5s 后自动 `git pull`; 关闭/停止服务 → 隐藏窗口 → 后台 git 双推(github+origin) → 完成后退出(兜底 45s)
- **仓库迁移**: 部署根+git 仓库从 `Desktop\usmind\usmind` 迁移到 **EXE 同级** `release\usmind`(nginx 8866 root 同步改, `build_site.py` DEPLOY 改); `repo_path` 支持相对路径(`.\usmind` = EXE 同级), 换电脑免改配置
- **config**: `resource_prefix` 默认 `https://usmind.netlify.app/`, `repo_path` 默认 EXE 同级

### 实测
- 上传: curl multipart → `{"ok":true,"path":"source/u_...png"}`; `/source/u_...png` 200 image/png
- 保存: tabs 含 source/ 引用 → save 后 share.html 引用全部改写为 `https://usmind.netlify.app/source/...`
- 启动: 日志"启动时已拉取远程仓库"; 关闭: "已推送到 remote github / remote origin", 进程自动退出
- 双推: gitee + github 均推送成功, 远程 HEAD 与本地一致

## 2026-09-25 · 线上首页直接渲染导图(修复"netlify 首页不是渲染页")

### 原因
仓库根 index.html 是纯净编辑入口(未注入数据), Netlify 首页默认 index.html → 访客看到空画布而非已保存导图。

### 修复
- `build_repo` (usmind_sync.py): index.html 与 share.html **同样注入** `__USMIND_INIT__` 数据 → 线上首页即渲染已保存导图
- web 端加载优先级 (main.js): **localStorage 优先**(本地编辑不丢), 无本地数据时用注入数据(新访客/线上首页直接展示)

### 实测
- 清除浏览器 localStorage 后打开 8866 首页 → 直接渲染"媒体同步测试"导图(中心主题+子节点可见)
- 本地编辑用户(有 localStorage)刷新 → 显示自己的编辑数据, 不受注入影响

## 2026-09-25 · 线上页面保存报错修复(cloudSync 探测路径)

### 原因
web 端 cloudSync 用相对路径 `fetch('/api/health')` 探测 EXE 服务, 线上(Netlify)页面会打到 Netlify 域返回 404 → 线上无法保存。

### 修复
main.js cloudSync 改为固定 `http://127.0.0.1:8899/api/health` + `/api/save`(与本机 EXE 中转), 本地/线上页面均可保存; 已重打包+推送。

### 实测
- 8866 页面加载新 JS(index-iHofaWOn.js): 含绝对保存地址, 无相对路径残留
- 跨域保存之前已实测通过(8866/线上 → 8899 EXE → 编译 → 双推)