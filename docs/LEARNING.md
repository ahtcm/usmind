# 从零学习 USMIND 项目：路线与 Demo 实践

> 目标读者：想通过本项目学习「全栈 Web 系统开发」的人。建议按阶段推进，每阶段配一个可运行、可验证的 Demo，亲手做一遍后再进入下一阶段。

---

## 阶段零：先把系统跑起来（半天）

**目标**：能看到界面、能登录、能建导图。

1. 确认 `tools/` 完整（jdk-17 / mysql / redis / nginx / maven / nodejs）。
2. 执行 `script\bat\start-all.bat`（MySQL → Redis → 后端 8899 → nginx）。
3. 打开 http://127.0.0.1:8866（用户端）、http://127.0.0.1:8867（后台，`admin/admin123`）。
4. 数据库已存在（`sql\init.sql` 或已导入的备份）时直接可用；新机器先 `script\bat\init-db.bat`。

**验证**：后台能看到统计数据；用户端能新建导图、刷新不丢。

**要弄懂**：`script/bat/start-all.bat` 里每个服务的启动顺序；nginx 四个 server 分别干什么（见 README）。

---

## 阶段一：后端入门（2~3 天）

**目标**：理解一个 REST 接口从请求到响应的完整链路。

1. 读 `AuthController` → `AuthServiceImpl` → `SysUserMapper`：登录是怎么校验、怎么签发 JWT 的。
2. 读 `Result.java` + `GlobalExceptionHandler.java`：统一返回与异常是怎么收敛的。
3. 读 `MindMapController` + `MindMapServiceImpl`：导图的增删改查、分享 token 逻辑。
4. 读 `FileController` + `FileServiceImpl` + `LocalStorage`：上传文件怎么落盘、怎么记 file_resource。
5. 读 `security/JwtAuthenticationFilter`：每个请求怎么被鉴权。

**Demo 1：写一个新接口**
- 在 `StatsController` 旁边加一个 `GET /api/admin/stats/ping` 返回 `{pong:true}`（带 ADMIN 鉴权）。
- 用 Postman / curl 验证：未带 token 401、带 token 200。
- 观察 `DataInitializer` 如何初始化默认数据。

**Demo 2：存储策略模式**
- 读 `StorageFactory` + 各 `*Storage` 实现，说明为什么新增一种存储只需要加一个实现类。
- 在管理后台「OSS 配置」新增一个本地配置并启用，上传一张图，看 `file_resource.url` 与 `uploads/` 文件。

---

## 阶段二：用户端前端入门（3~5 天）

**目标**：理解脑图编辑器是怎么与 simple-mind-map 结合的。

1. 读 `Editor.vue` 的 `createMind`：实例参数（容器、主题、布局、事件）。
2. 读 `bindMindEvents`：`node_dblclick` 分发逻辑（图片/附件/公式）。
3. 读 `utils/mathRenderer.js`：LaTeX → SVG 的转换。
4. 读 `utils/mediastore.js`：媒体节点的引用与还原。
5. 读 `utils/modal.js`：为什么不用浏览器原生 alert。

**Demo 3：跑通一次完整交互**
- 新建导图 → 双击根节点输入文字 → Tab 建子节点 → 右键设置颜色 → 保存 → 刷新（数据还在）。
- 打开 DevTools Network，观察 `PUT /api/mindmaps/{id}` 的请求体（`data` 是节点树 JSON）。

**Demo 4：插入一个数学公式**
- 双击节点 → 点 ∑ → 输入 `A=\begin{pmatrix}1&2\\3&4\end{pmatrix}` → 插入。
- 观察节点宽度/高度如何按公式自适应（`setTimeout(mind.render(), 30)` 的作用）。
- 双击公式节点 → 弹出编辑框（SVG 反向还原 LaTeX）。

**Demo 5：图片/视频节点**
- 复制一张图片，在节点内 `Ctrl+V` → 图片平铺为节点。
- 双击图片节点 → 全屏浏览 → 点 × 返回。
- 看 `uploads/` 目录新增文件、`file_resource` 新增记录。

---

## 阶段三：管理后台（2~3 天）

**目标**：掌握一个 Element Plus 后台的完整套路。

1. 读 `AdminLayout.vue`（布局）与 `router/index.js`（守卫）。
2. 读 `Users.vue`：列表 + 弹窗表单 + 批量删除；再读后端 `UserServiceImpl.delete` 的**管理员保底**。
3. 读 `Themes.vue`：可视化主题配置 + 实时预览是怎么组织的（`buildConfig`）。
4. 读 `ProjectConfig.vue`：配置文件可视化编辑与导出。

**Demo 6：后台完整走一遍**
- 建一个普通用户 → 用它登录用户端（只能看自己的导图）。
- 后台改主题（各层字号、背景图）→ 用户端刷新生效。
- 上传一个 .py 文件当附件 → 后台资源管理里按后缀过滤、在线预览（代码高亮）。
- 数据备份页导出 JSON / SQL → 文件出现在下载目录。

---

## 阶段四：系统集成与迁移（2~3 天）

**目标**：理解多机部署与数据迁移。

1. 读 `nginx.conf` 四个 server：80 外网分流原理。
2. 读 `application.yml` / `application-dev.yml`：配置项与 secrets。
3. 跑一遍迁移：
   - 用 `sql\usmind_db_backup_*.sql` 在另一台机器恢复数据库；
   - 拷贝 `uploads/`；
   - 起服务，数据完整可见。

**Demo 7：OSS 热插拔**
- 后台配置阿里云/腾讯/七牛/MinIO 任一（有账号时）→ 测试连通 → 启用。
- 新上传资源 URL 变成 OSS 域名；关掉后回落到本地存储。

**Demo 8：.usmind 资源包迁移**
- 导出某个含图片/附件的导图为 `.usmind` → 换一个系统（或清空后）导入。
- 观察资源被自动上传入库、节点 URL 被改写、图片正常显示 —— 理解"脑图+资源整体迁移"。

---

## 阶段五：深入进阶（按兴趣选）

- **公式渲染**：MathJax 动态加载、SVG 尺寸测量、防塌陷重渲染。
- **画布交互**：双指捏合缩放、滑动条联动、懒加载（`applyLazyView`）、背景三层结构（底色/图/脑图）。
- **权限体系**：Spring Security 过滤器链、JWT、ROLE 划分、管理端/用户端隔离。
- **存储抽象**：策略模式 + 工厂 + 密钥加密。
- **前端工程**：Vite 构建、base 路径（`/admin/`）、nginx 强缓存策略、`Ctrl+F5` 发版。
- **数据安全**：管理员保底、批量删除事务、备份/恢复。

---

## 学习建议

1. **先跑通，再读码**：每个阶段先动手跑 Demo，再看代码，印象最深。
2. **断点调试**：后端用 IDEA 断点看 `AuthServiceImpl.login`；前端用 DevTools 断点看 `Editor.vue` 的 `onPaste`。
3. **接口驱动**：随时对照 `docs/API.md` 和 `docs/FILE_GUIDE.md` 定位代码。
4. **改一行验证**：如把 `application-dev.yml` 的数据库密码改错，观察启动报错 → 学会看日志（`logs/` 与 Maven 输出）。
5. **从"加功能"切入**：给自己定小任务（如"给用户加一个备注字段"），打通 表→实体→Mapper→Service→Controller→前端 全链路。
