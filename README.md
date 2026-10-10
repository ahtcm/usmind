# USMIND 思维导图系统

一款自托管的**在线思维导图 + 知识管理**系统：脑图编辑支持富文本、数学公式、Emoji 图标、图片/视频/音频/文件附件与媒体节点，配套独立的**管理后台**（用户 / 资源 / OSS / 主题 / 图标 / 项目配置 / 数据备份），支持分享链接、多种格式导入导出、移动端适配，并可在任意电脑直接运行（绿色免安装）。

> 另有 **USMIND 本地离线版**（`win/`）：纯本地 Windows 桌面版，无需联网、无需后端/数据库/账号，保留脑图编辑、数学公式、附件媒体、主题系统、导入导出与多画布标签页等核心能力，数据保存在本机，绿色免安装。详见 [win/README.md](win/README.md)。

---

## 一、功能特性

### 用户端（脑图）
- **节点编辑**：双击编辑文字；`Ctrl+Enter` 强制换行（不按键则节点自动延伸变宽）；右键菜单（字体颜色 / 背景颜色 / 复制 / 剪切 / 粘贴 / 删除 / 折叠 / 展开 / 插入数学公式 / 附件）；快捷键 `Ctrl+C / Ctrl+X / Ctrl+V / Delete`。
- **数学公式**：节点内可插入 LaTeX 公式（MathJax SVG 渲染），支持矩阵、多行公式；双击公式节点进入公式编辑弹窗；节点高度按公式实际尺寸自动计算，不塌陷。
- **Emoji 图标**：图标面板按分类折叠展示、懒加载、搜索；图标可插入到节点文字光标处（跟随光标插入）。
- **附件与媒体节点**：粘贴 / 上传图片、视频、音频、PDF、Office、代码文件等到节点；图片、视频可作为节点主体平铺显示（超出部分裁剪）；单击附件图标预览，双击附件节点进入编辑与浏览；图片节点双击进入全屏浏览（点 × / Esc 返回脑图）；附件文件名默认红色。
- **文件在线预览**：PDF（pdf.js 渲染）、图片、音视频、代码高亮、Office 文档（可配置 Office 预览服务）等。
- **主题与样式**：全局主题模板（管理后台可配置：节点配色 / 字体 / 字号（根 / 第一层 / 其它层）/ 画布背景色 / 背景图 / 背景视频 + 透明度）；单个节点可覆盖字体大小、颜色、背景色、圆角、边框；分支一键套用配色 / 随机彩虹色。
- **画布交互**：Ctrl+滚轮缩放；双指捏合缩放（移动端，仅缩放脑图）；底部 / 右侧细滑动条控制视口（透明、悬停加宽、与工具栏同配色）；按住根节点可拖动视口；标签页拖拽排序、双击重命名、`<>` 按钮滚动；画布背景图按视口等比放大铺满（允许溢出裁剪）。
- **多画布（标签页）**：一个窗口打开多个导图，Tab 切换、新建、删除、重命名、拖拽排序。
- **分享**：导图分享为公开链接（可设密码），分享页免登录只读浏览。
- **导入导出**：导出 JSON / PNG / JPG / JPEG / SVG / PDF / Markdown；`.usmind` 资源包（JSON + 资源文件夹打包，可整体迁移到其它系统）；JSON 导入。PNG / JPG / PDF 导出采用 html2canvas 混合 DOM 方案（完整 SVG 提取 foreignObject 为 HTML 层再逐元素捕获），公式 / 图片等富内容完整显示、超大画布自动分块打包并带进度遮罩提示。
- **AI 辅助**：节点内容 AI 生成（后端代理 OpenAI 兼容接口，可配置）。
- **自动保存**：编辑即保存（防抖），刷新不丢数据。
- **机构协作**：邀请码加入机构、机构内多人实时协作编辑同一导图（WebSocket 广播，结构变更 / 文本编辑秒级同步）；节点所有权机制（默认关闭，可在管理后台全局开关 / 导图级协作设置中配置）：历史节点归导图创建者，节点创建者与导图创建者拥有编辑权，他人编辑需申请（审批优先级：节点创建者 → 导图创建者，超时转派 / 自动拒绝时长可配置）；他人修改未编辑节点时静默 diff 局部应用（不全局刷新、不闪屏、不丢自己的编辑内容），自己正在编辑的节点被他人修改时弹增强冲突框（显示对方内容，可加载最新 / 以我的为准）。
- **移动端适配**：完整响应式布局，双指缩放 / 单指平移，标签页与工具栏移动端优化。

- **账号与安全**：注册（图形验证码，后台可关）/ 登录（独立图形验证码开关，前台同步隐藏）；登录后头像下拉菜单（个人信息 / 会员中心 / 退出）；用户中心可改昵称 / 邮箱 / 手机 / 头像；短信验证码注册预留（默认关闭，可后台配置接入服务商）；微信登录预留（默认关闭，后台可配 AppID/Secret）。
- **会员体系**：套餐购买（微信支付，默认关闭，后台可开）；**套餐价格管理**（套餐增删改、现价 / 原价 / 折扣百分比，改价客户端实时生效）；**一次性兑换码**（批量生成、状态筛选、按筛选批量导出 Excel/CSV）；会员功能（特定主题 👑、导出格式、上传大小与数量、画布数等）后台统一配置，客户端非会员显示皇冠图标且不可用；会员体系可整体关闭（用户无感知）。
- **评论体系**：分享页与机构内均可对节点评论 / 回复（128 字 / 条，IP 限频）；作者端实时收到评论提醒（呼吸灯电流特效沿连线流淌 + 角标 +1 + 音效，可关闭），点开阅读后不再重复提醒；指定人回复只提醒被回复人。
### 管理后台（/admin/）
- **登录鉴权**：管理员登录（JWT），登录页版权 / 入网声明可在后台配置（本地缓存时长可配置）。
- **仪表盘**：注册用户 / VIP 会员 / 付费转化率 / 区间收入 / 今日收入 / 区间新增 / 30 天内到期 / 兑换码会员 / 累计收入 / 累计支付订单 / 平台导图 共 12 项指标卡；注册 / 付费 / 收入趋势折线图（按近 7 / 30 / 90 天快捷筛选）；会员来源、付费转化、主题使用分布饼图；订单 / 兑换码 / 最新动态表格。
- **用户管理**：增删改查、启用禁用、重置密码、批量删除；**至少保留一个管理员**（删到只剩一个时禁止删除）。
- **导图管理**：全部导图列表、查看详情、取消分享、删除、批量删除。
- **资源管理**：全部上传文件列表（图片 / 视频 / 音频 / 文件分类）、在线预览、删除、批量删除、按后缀过滤。
- **OSS 配置**：热插拔存储（本地 / 阿里云 OSS / 腾讯 COS / 七牛 / MinIO），可测试连通、切换启用；支持将已有本地文件一键迁移到新存储；未配置 OSS 时自动回落到本地存储。
- **主题模板管理**：可视化配置主题（节点配色、分支配色、各层字号字体、画布背景图 / 视频 / 背景色 + 透明度），实时预览，启用 / 禁用。
- **图标管理**：Emoji 图标库管理（分类、启用、增删、批量导入）。
- **项目配置**：后端配置（数据库 / Redis / 端口 / 上传目录 / JWT / OSS 密钥 / AI 接口等）与 nginx 配置的可视化编辑、导出配置文件，便于迁移到不同环境。
- **数据备份**：一键备份当前数据库（JSON / SQL），上传备份文件恢复数据。
- **移动端适配**：管理后台所有页面适配手机。

- **支付与验证码**：微信支付开关（默认关，无商户资质不可用）/ 注册图形验证码开关 / 登录图形验证码开关（前端同步隐藏）/ 短信验证码开关（预留）/ IP 限频与自动拉黑参数（超频自动入黑名单，到期自动解封，后台删除黑名单立即生效）。
- **会员配置**：会员体系总开关、会员主题（👑）、会员导出格式、上传大小与数量限制、画布数等；订单管理（联查用户名 / 删除 / 批量删）。
- **评论管理**：全站评论列表（分享 + 机构内，按来源筛选），支持评论内容 / 评论者模糊查询、脑图下拉筛选、组织下拉筛选（自动覆盖该组织全部导图）、批量删除。
- **IP 黑白名单**：按 IP / 用户 ID 拉黑或加白（正则校验，用户 ID 不存在时提示），删除即解除。
- **请求日志**：全量接口日志（用户 / 接口下拉 / 级别 / 前后端来源 / 时间筛选），详情含请求头、请求体、响应、异常栈；记录级别（多选）、保留天数与定时清理时间在系统设置配置；支持一键删除全部日志。
- **机构管理**：全站机构列表、解散（在线成员被 WS 踢出并提示）。
- **仪表盘**：12 项业务指标卡 + 注册 / 付费 / 收入趋势折线 + 会员来源 / 付费转化 / 主题分布饼图 + 订单 / 兑换码 / 最新动态表格；统计区间支持近 7 / 30 / 90 天快捷筛选。
---

## 二、系统架构

```mermaid
graph TB
    subgraph 浏览器
        U[用户端 web-user<br/>Vue3 8866]
        A[管理后台 web-admin<br/>Vue3+ElementPlus 8867]
        S[分享页 /share<br/>免登录只读]
    end

    subgraph nginx（80 外网入口 / 8866 / 8867 内网入口）
        N[nginx 反向代理 + 静态托管]
    end

    subgraph 后端
        B[Spring Boot 8899<br/>REST API]
        C[Storage 策略<br/>本地 / 阿里云 / 腾讯 / 七牛 / MinIO]
        P[在线预览<br/>pdf.js / 代码高亮 / Office 服务]
        AI[AI 代理<br/>OpenAI 兼容]
    end

    subgraph 存储
        DB[(MySQL 8<br/>usmind_db)]
        RD[(Redis)]
        FS[uploads/ 本地文件]
        OSS[(对象存储)]
    end

    U --> N
    A --> N
    S --> N
    N --> B
    B --> DB
    B --> RD
    B --> C
    C --> FS
    C --> OSS
    B --> P
    B --> AI
```

### 端口与路由
| 入口 | 说明 |
| --- | --- |
| `http://127.0.0.1:8866` | 用户端（脑图），静态由 `release/usmind` 托管，`/api/`、`/uploads/` 反代到 8899 |
| `http://127.0.0.1:8867` | 管理后台，静态由 `release/admin` 托管（base `/admin/`），`/api/` 反代到 8899 |
| `http://127.0.0.1:8899` | Spring Boot 后端 REST API |
| `http://127.0.0.1:80` | 外网穿透唯一入口（仅开放 80）：`/` → 8866，`/admin/` → 8867，`/api/`、`/uploads/` → 8899 |

---

## 三、目录结构

```
usmind/
├── code/                  # 全部源码
│   ├── backend/           # Spring Boot 后端（Java 17 + Maven）
│   │   └── src/main/
│   │       ├── java/com/usmind/
│   │       │   ├── controller/   # REST 接口层（Auth/User/MindMap/Share/File/Theme/Icon/Oss/...）
│   │       │   ├── service/      # 业务逻辑接口 + impl 实现
│   │       │   ├── entity/       # MyBatis-Plus 实体（17 张表：用户/导图/资源/主题/图标/配置/OSS/会员套餐/订单/兑换码/机构/成员/协作/评论/已读/IP规则/请求日志）
│   │       │   ├── mapper/       # MyBatis-Plus Mapper
│   │       │   ├── dto/          # 请求/响应对象
│   │       │   ├── config/       # 安全/Redis/MyBatis/初始化配置
│   │       │   ├── security/     # JWT 认证过滤器与登录用户
│   │       │   ├── storage/      # 存储策略（本地/阿里云/腾讯/七牛/MinIO）
│   │       │   └── common/       # 统一返回/异常/JWT 工具/加密等
│   │       └── resources/        # application.yml、emoji-seed.json 等
│   ├── web-user/          # 用户端（Vue3 + Vite + simple-mind-map）
│   │   └── src/
│   │       ├── views/     # Editor.vue（脑图）/ Login / MindMapList / ShareView / Org / MemberView / ProfileView
│   │       ├── utils/     # mathRenderer(公式)/ mediastore(媒体) / modal / theme / ai
│   │       ├── api/       # 接口封装（auth/file/icons/mindmap/themes/previewConfig/ai/comment/member/org）
│   │       ├── stores/    # Pinia 用户状态
│   │       └── router/ assets/
│   └── web-admin/         # 管理后台（Vue3 + Vite + Element Plus）
│       └── src/
│           ├── views/     # Dashboard/Users/MindMaps/Resources/OssConfig/Themes/Icons/ProjectConfig/DataBackup/Settings/PayConfig/MemberConfig/Orders/Comments/IpRules/Logs/Orgs/Members/Login
│           ├── api/       # 接口封装
│           ├── layouts/   # AdminLayout（侧边栏+顶栏）
│           └── router/ stores/ styles/
├── release/               # 构建产物与发布形态
│   ├── usmind/            # 用户端 dist（nginx 8866 托管）
│   ├── admin/             # 管理后台 dist（nginx 8867 /admin/ 托管）
│   ├── USMIND-setup.exe   # ① 单文件自解压版（zip 内嵌 exe）
│   ├── USMIND-online.exe  # ② 在线安装器（16KB，CDN 实时拉取环境）
│   ├── online-dist/       # 在线版环境分片（22 片 + manifest.json，推送 GitHub @v1 经 jsDelivr 分发）
│   （release 为构建产物，git 忽略；launcher 构建源码在 code\launcher-src\）
├── script/                  # 一键脚本（启动/停止/构建/初始化数据库等，全部相对路径）
│   ├── bat/                 # Windows 批处理（start-all.bat / stop-all.bat / build-*.bat 等 13 个）
│   │   ├── start-all.bat    # 一键启动（MySQL → Redis → 后端 8899 → 构建两个前端）
│   │   ├── stop-all.bat     # 全部停止
│   │   └── ...              # 其余见 docs\FILE_GUIDE.md「脚本体系」
│   └── shell/               # Linux shell 等价物（作用同 bat，环境放 tools/ 命名一致）
│       ├── start-all.sh     # 一键启动（Linux）
│       ├── stop-all.sh      # 全部停止（Linux）
│       ├── build-all.sh     # 一键构建（后端 jar + 两个前端部署到 nginx html）
│       ├── init-db.sh       # 初始化数据库（Linux）
│       └── ...              # start-mysql / start-redis / start-nginx / start-backend 等
├── sql/                   # 数据库脚本
│   ├── init.sql           # 建库建表（含种子数据）
│   └── usmind_db_backup_*.sql / usmind-backup-*.json  # 备份文件
├── tools/                 # 绿色运行环境（免安装）
│   ├── jdk-17.0.2/        # JDK 17
│   ├── apache-maven-3.9.6/
│   ├── mysql-8.0.36-winx64/
│   ├── Redis-x64-5.0.14.1/
│   ├── nginx-1.30.5/
│   └── nodejs/
├── uploads/               # 上传资源（本地存储落地目录，按日期分目录）
├── logs/                  # nginx / 后端日志
├── docs/                  # 项目文档（本说明系列）
└── .gitignore             # git 忽略规则（/uploads/、node_modules 等）
```

---

## 四、技术栈

| 层 | 技术 |
| --- | --- |
| 后端 | Java 17、Spring Boot 3、Spring Security + JWT、MyBatis-Plus、Lombok |
| 数据库 | MySQL 8.0（`usmind_db`，17 张表）、Redis 5（token / 配置缓存） |
| 存储 | 本地文件系统 + 策略模式（阿里云 OSS / 腾讯 COS / 七牛 / MinIO 热插拔） |
| 用户端 | Vue 3（Composition API）、Vite、Pinia、Vue Router、simple-mind-map（脑图内核）、MathJax（公式 SVG）、pdf.js（PDF 预览） |
| 管理后台 | Vue 3、Vite、Element Plus、Pinia、ECharts（仪表盘） |
| 网关 | nginx（静态托管 + 反向代理 + 外网 80 端口分流） |
| 其它 | pdf.js / highlight.js / CodeMirror（附件预览）、Office 在线预览服务（可配置）、AI 接口代理 |

---

## 五、快速启动

### 直接运行版（无需构建，分享给任何人）

三种发布形态（详见下文「十、发布形态与分发」），拿到手直接双击即可运行，**无需安装任何环境**：

| 版本 | 产物 | 体积 | 首次运行 |
| --- | --- | --- | --- |
| 单文件自解压版 | `release\USMIND-setup.exe` | ~371MB | 自动解压到 `%LOCALAPPDATA%\USMIND` 并启动 |
| 在线安装版 | `release\USMIND-online.exe` | 16KB | 从国内 CDN 实时下载环境（可视化进度条），装好后自动启动 |

> 在线版原理：安装器内置分片清单，从 `cdn.jsdelivr.net/gh/ahtcm/usmind@v1/release/online-dist`（GitHub 公开仓库 v1 标签，jsDelivr 国内加速节点）下载 22 个分片 → 拼接解压 → 组装为便携版目录 → 启动。已安装（目标目录存在 `USMIND.exe`）则直接启动，跳过下载。可用环境变量 `USMIND_BASE_URL` 覆盖下载源；可用 `--dir <目录>` 指定安装目录（默认 `%LOCALAPPDATA%\USMIND-online`）。
>
> ⚠️ **当前状态**：分片已按需求从 GitHub 移除（不再托管 `online-dist`）。`code\launcher-src\build_online.ps1` 仍可随时重新生成分片；若需继续使用在线版，请自行托管 `release\online-dist`（如推回 GitHub `@v1` 或私有对象存储）后重新构建安装器。

### 一键启动

### 依赖（已随项目打包在 `tools/`，绿色免安装）
- JDK 17、Maven 3.9、MySQL 8、Redis 5、nginx 1.30、Node.js（构建时才需要）

### 一键启动
```bat
script\bat\start-all.bat        :: 依次启动 MySQL → Redis → 后端(8899) → nginx(80/8866/8867)
script\bat\stop-all.bat         :: 全部停止
script\bat\delete-all.bat       :: 清理数据与日志（慎用）
```

各服务也可单独启停：`start-mysql / start-redis / start-backend / start-nginx / start-web-user / start-web-admin`。

### Linux 部署（`script\shell\`，作用与 bat 一致）
```bash
bash script/shell/start-all.sh        # 依次启动 MySQL → Redis → nginx → 后端(8899) → 构建前端
bash script/shell/stop-all.sh         # 全部停止
bash script/shell/delete-all.sh       # 清理数据与日志（慎用）
bash script/shell/init-db.sh          # 首次初始化数据库（sql/init.sql）
bash script/shell/build-all.sh        # 一键构建：后端 jar + 两个前端部署到 nginx html
```

> Linux 环境放项目 `tools/` 下，命名与 Windows 一致：`jdk-17.0.2` / `apache-maven-3.9.6` / `nodejs` / `mysql-*` / `redis-*` / `nginx-*`（mysql/redis/nginx 版本目录自动探测）。全部为 bash 脚本（`bash -n` 语法通过），项目根由 `$(dirname "$0")/../..` 定位。

启动后访问：
- 用户端：http://127.0.0.1:8866
- 管理后台：http://127.0.0.1:8867（默认账号 `admin / admin123`）

### 首次初始化数据库
```bat
script\bat\init-db.bat          :: 执行 sql\init.sql 建库建表（已有数据可跳过）
bash script/shell/init-db.sh    :: 同上（Linux）
```

### 构建
```bat
script\bat\build-backend.bat    :: 构建后端 jar（mvn clean package -DskipTests，产物 code\backend\target\usmind-backend.jar）
script\bat\build-web-user.bat   :: 构建用户端并部署到 release\usmind
script\bat\build-web-admin.bat  :: 构建管理后台并部署到 release\admin

```

Linux 对应：`bash script/shell/build-backend.sh` / `build-web-user.sh` / `build-web-admin.sh` / `build-all.sh`（前端 dist 部署到 nginx `html/{usmind,admin}`）。

> 说明：构建脚本全部使用**相对路径**（基于脚本所在目录自动定位项目根、JDK、Maven、Node），整个项目拷到任意位置都能直接构建运行。

---

## 六、配置说明

### 后端配置（`code/backend/src/main/resources/application.yml`）
| 配置项 | 说明 |
| --- | --- |
| `server.port` | 后端端口，默认 `8899` |
| `spring.profiles.active` | `dev` / `prod`（`application-dev.yml` 配本地 MySQL/Redis 连接） |
| `spring.servlet.multipart` | 上传大小上限（默认 100MB） |
| `jwt.secret / expire` | JWT 密钥与过期时间（毫秒） |
| `file.upload-dir` | 本地文件存储目录（默认 `usmind/uploads`） |
| `oss.secret-cipher-key` | OSS 密钥加密密钥 |
| `preview.office-server` | Office 在线预览服务地址（可空） |
| `ai.api-url / api-key` | AI 接口地址与密钥（可空） |

数据库 / Redis 连接在 `application-dev.yml`（或 `-prod.yml`）中：
```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/usmind_db?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai
    username: root
    password: root
  data:
    redis:
      host: 127.0.0.1
      port: 6379
```

### nginx（`tools/nginx-1.30.5/conf/nginx.conf`）
四个 server：8766（旧项目保留）、8866（用户端）、8867（管理后台）、80（外网入口）。路径均为相对 nginx 安装目录（`../../release/usmind`、`../../uploads/` 等），整个项目移动后无需改 nginx 绝对路径。

---

## 七、迁移 / 部署到新机器

### 方式一：整机搬移（绿色版）
1. 拷贝整个 `usmind` 目录到新机器任意位置（如 `D:\usmind`）。
2. 确保无环境依赖（JDK / MySQL / Redis / nginx 都在 `tools/` 内）。
3. 运行 `script\bat\start-all.bat`。
4. 如数据不随目录搬移，先 `script\bat\init-db.bat` 建库，再恢复备份（见下）。

### 方式二：数据迁移（数据库 + 资源）
1. **数据库**：使用 `sql/usmind_db_backup_*.sql` 全量备份（含建库、7 张表结构与数据）：
   ```bat
   mysql -u root -p < sql\usmind_db_backup_20260927.sql
   ```
   也可在管理后台「数据备份」页一键导出 / 上传恢复（JSON / SQL）。
2. **资源文件**：将 `uploads/` 目录整体拷贝到新机器对应位置（本地存储模式），或改配 OSS 后无需搬资源。
3. **后端配置**：核对 `application.yml` 的 `file.upload-dir`、数据库连接。
4. **nginx**：无需改动（相对路径自动生效）。

### 方式三：云服务器 / 外网部署（仅开放 80）
1. 域名或穿透服务指向服务器 80 端口。
2. 无需改后端端口：nginx 80 已按路径分流 `/`→8866、`/admin/`→8867、`/api/`、`/uploads/`→8899。
3. 如部署到 Linux：可参照 `nginx.conf` 的 server 块改写，后端用 `--spring.profiles.active=prod` 启动（提供 `application-prod.yml` 模板）。
4. 配置 OSS（管理后台）后，新上传资源直传对象存储，多机共享无需同步 `uploads/`。

### 方式四：全分离部署（前端 / 后端 / MySQL / Redis 各自独立）

适用：前后端、数据库、缓存分别部署在不同机器 / 云服务（如：前端静态托管到 Netlify/CDN，后端一台服务器，MySQL / Redis 用云数据库）。

```
浏览器
  │
  ├─ 前端（任意静态托管：Netlify / CDN / 静态站）
  │
  └─ /api /uploads ──▶ 后端服务器（Spring Boot 8899）
                          │
                          ├─ MySQL（独立机器 / 云数据库）
                          └─ Redis（独立机器 / 云 Redis）
```

| 组件 | 配置 |
| --- | --- |
| 前端 API 地址 | `web-user/src/api/request.js` 的 baseURL 由 `/api` 改为 `https://你的域名/api`，重新构建后部署到任意静态站 |
| nginx（后端那台） | 不再托管前端静态，只保留 `location /api/` 与 `location /uploads/` 反代 8899；开启 CORS（`Access-Control-Allow-Origin: 前端域名`，放行 `OPTIONS` 预检） |
| MySQL | `application-prod.yml` 改 `spring.datasource.url` 为远程地址；MySQL 侧授权远程用户（`GRANT ... TO 'user'@'%'`） |
| Redis | `spring.data.redis.host` 改远程 IP，并配置 `password` |
| 附件存储 ⚠️ | `uploads/` 在本地磁盘，多机部署会不一致：① 推荐后台配置 **OSS**（新上传直传对象存储，多机共享）；② 或将 `uploads/` 保留在后端服务器，nginx `/uploads/` 仍从后端反代 |
| JWT | 无需改动：token 走请求头，不依赖 cookie / 域名，天然跨域 |

注意：
- **不要把 `uploads/` 跟前端一起拆走**——附件 URL 会断；要么用 OSS，要么 uploads 跟后端同机。
- 前后端不同域名时，登录 token 分别存各自 localStorage，互不影响。

推荐组合：前端静态托管（CDN）+ 后端与 `uploads/` 同机 + MySQL / Redis 用云数据库（或独立机器）。

### 配置 OSS（热插拔）
管理后台 →「OSS 配置」：
- 类型：本地 / 阿里云 / 腾讯 / 七牛 / MinIO，填 endpoint、AK/SK、bucket、域名。
- 「测试」验证连通，「启用」切换为当前存储；未配置或未启用时自动回落本地存储。
- 已存资源：切换存储后旧资源 URL 仍指向原存储（数据库记录不变），新上传走新存储。

### 前端部署（可选：静态托管到 Netlify 等）
用户端构建产物为纯静态（`release/usmind`），可部署到任何静态托管（Netlify / Vercel / GitHub Pages），但 `/api/` 与 `/uploads/` 需由你的域名反代到后端（或在 `api/request.js` 配置 API 地址）。

---

## 八、数据库表

| 表 | 说明 | 关键字段 |
| --- | --- | --- |
| `sys_user` | 用户 / 管理员 | username, password(BCrypt), nickname, role(ADMIN/USER), status, memberLevel, memberExpire, memberSource |
| `mind_map` | 导图 | name, data(JSON), layout, theme, viewData, manualStyles, isShared, shareToken, sharePassword, viewCount, userId |
| `file_resource` | 上传资源 | originalName, storedName, storageType, url, size, mimeType, userId, mindMapId |
| `oss_config` | OSS 配置 | type, endpoint, accessKey, secretKey(加密), bucket, domain, isActive, configJson |
| `sys_config` | 系统配置（KV） | configKey, configValue |
| `sys_theme` | 主题模板 | name, themeKey, branchColors, configJson, sort, enabled, builtin |
| `sys_icon` | 图标库 | name, category, sort, enabled, builtin |
| `ip_rule` | IP / 用户黑白名单 | ruleType(white/black), targetType(ip/user_id), targetValue, remark, expireAt |
| `member_plan` | 会员套餐 | name, level, price, originalPrice, discountPercent, days, sort, enabled |
| `pay_order` | 支付订单 | orderNo, userId, planId, amount, status(paid/unpaid/closed), paidAt |
| `redeem_code` | 会员兑换码 | code, planId, days, level, status(active/used/disabled), usedBy, usedAt, remark, createdBy |
| `org` | 机构 | name, inviteCode, ownerId, status |
| `org_member` | 机构成员 | orgId, userId, role, joinedAt |
| `org_collab` | 机构导图协作 | orgId, mindMapId, inviteMemberIds(JSON) |
| `node_comment` | 节点评论 | mindMapId, nodeId, userId, nickname, content(≤128字), parentId, source(share/org) |
| `node_comment_read` | 评论已读 | commentId, userId, readAt |
| `request_log` | 请求日志 | userId, path, method, level(info/warn/error), source(frontend/backend), requestHeaders, requestBody, responseBody, errorStack, createdAt |

---

## 九、发布形态与分发

项目提供两种"免装环境、双击即用"的发布形态，适合分享给任意电脑用户（含无开发环境、无网络或弱网用户）。便携绿色版（`release\usmind-portable`）已按需求移除；自解压版与在线版均基于便携产物（zip / 分片源）组装，对应构建源码保留在 `code\launcher-src\`（`selfextract.cs` / `Installer.cs` / `build_online.ps1`），如需重新启用请先重建便携产物。

### 1. 单文件自解压版 `release\USMIND-setup.exe`
- 原理：C# stub（`selfextract.cs`）尾部拼接 Optimal 压缩的便携版 zip；运行时自定位 zip 起点（EOCD 扫描）→ 解压到 `%LOCALAPPDATA%\USMIND` → 启动。
- 已安装则直接二次启动，不再解压。支持 `--extract-only`（仅解压，供验证）。

### 2. 在线安装版 `release\USMIND-online.exe`
- 原理：C# WinForms 下载器（`Installer.cs`，16KB）内嵌 `manifest.json`（组件/目录/分片清单，编译为 Win32 资源）；运行时从 **jsDelivr CDN**（GitHub 公开仓库 `@v1` 标签，国内加速节点）按序下载分片 → 拼接为各组件 zip → 解压组装为运行目录（默认 `%LOCALAPPDATA%\USMIND-online`）→ 启动。
- 分片：`release/online-dist/` 由 `build_online.ps1` 生成（6 个组件 zip 按 20MB 切分，共 22 片 371MB，`mysql` 12 片最多）。分片已推 GitHub `@v1`，jsDelivr 自动缓存分发。
- 进度：总进度条 + 当前组件/分片 + 已下载量，可取消；下载失败提示重试（已下载分片会重新开始，断点续传留待后续）。
- 覆盖下载源：环境变量 `USMIND_BASE_URL`（本地模拟验证用）。

### 更新在线版分片（重新发布）
> 现状：分片已从 GitHub 移除，不再托管。以下为**如需重新启用在线版**的操作步骤：
1. `code\launcher-src\build_online.ps1` 重新生成 `release\online-dist`；
2. 托管分片（二选一）：
   - 推回 GitHub：`git add -f release/online-dist` + 提交（`release/` 被 .gitignore 忽略，需 `-f`）→ 推送 develop → 更新 `v1` 标签（`git tag -f v1 && git push github v1 -f`）；
   - 或用 `USMIND_BASE_URL` 指向自建对象存储 / 静态托管；
3. 用 csc 重新编译 `code\launcher-src\Installer.cs`（内嵌新 manifest）生成 `USMIND-online.exe`；
4. 注意 jsDelivr 对新文件/更新有缓存延迟（几分钟），旧 URL 立即生效、更新内容需等缓存刷新。

---

## 十、常见问题

- **后端构建失败「jar 被锁」**：后端运行中会锁住 `target/*.jar`，先 `script\bat\stop-all.bat` 再构建（构建脚本失败时也会提示）。
- **前端改了不生效**：浏览器 `Ctrl+F5` 强刷（nginx 对 js/css 强缓存 30 天，`index.html` 不缓存）。
- **分享页打不开 / 只显示 JSON**：确认走的是 `/share/{token}` 路由且后端在线；分享页免登录只读。
- **上传的文件打不开**：确认 nginx `/uploads/` 反代/别名正确；附件在线预览依赖预览服务配置。
- **删除管理员被拒**：系统要求至少保留一个管理员账号。
- **换机器后登录失败**：确认 MySQL 与 Redis 已启动、`init-db` 已执行、`application-dev.yml` 连接正确。
- **在线版首次下载慢 / 卡住**：首次需下载约 371MB（国内 CDN，一般 5~15 分钟）；卡住多为网络波动，关闭后重开安装器会重新下载；也可改用自解压版。
- **在线版更新后下载到旧环境**：jsDelivr 对分片有缓存延迟，刚推新分片后 5~30 分钟内可能拉到旧版，属正常现象。

