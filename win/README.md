# USMIND 本地离线版（win/）

纯本地运行的思维导图桌面版：无需联网、无需后端/数据库，数据存本地文件。保留主题、公式、附件、预览等核心功能；不含账号/会员/协作/分享/评论（那些属于在线版 `code/`）。

## 目录结构

```
win/
├── src/                  C++ 壳程序（main.cpp 窗口+虚拟主机 / bridge.cpp 桥 / native.cpp 文件与安全）
├── web/                  前端（js/app.js、css/app.css、index.html、vendor/ 含 KaTeX/JSZip/CodeMirror/pdf.js 等）
├── build/                编译中间产物（build.ps1 产出 usmind.exe；测试时复制 web 过来）
├── release/              发布目录：usmind.exe + WebView2Loader.dll + web/（整个目录拷走即用）
├── tools/                工具链（w64devkit、webview2-sdk）
└── build.ps1             一键构建
```

## 构建

```
powershell -ExecutionPolicy Bypass -File build.ps1
```

产物输出到 `release/`（绿色便携：复制整个 release 目录即可分发，无需安装）。

## 运行

双击 `release\usmind.exe`。首次启动自动创建 `release\data\attachments\`（外置附件目录）。支持双击 `.usmind` 文件打开（设置 → 文件关联 可注册）。

## 附件双模式（2026-10 引入）

解决大文件（视频等）base64 内嵌导致卡顿的问题：

1. **外置附件模式（默认）**：插入的图片/视频/音频/文件写入 `data\attachments\`，节点仅存 `attachments/xxx` 相对路径，经 `https://media.local/`（WebView2 虚拟主机，映射到 data 目录）流式加载。`.usmind` 保存文件只含结构，小且快。**拷走分享**：需连同 `data\` 一起拷，或改用下面的自包含导出。
2. **zip 自包含模式**：导出菜单 →「自包含包」→ 把结构与所有媒体打成单个 `.usmind`（zip）文件，拷走即开（媒体自动解包回 `data\attachments\`，节点引用改写为相对路径）。导入：直接打开该 `.usmind` 文件。

| 场景 | 推荐 |
| --- | --- |
| 日常编辑、大视频不卡 | 外置模式（默认） |
| 发给别人 / 换机器 | 自包含包导出 |

> 说明：外置模式下 `.usmind` 文件本身不含媒体，只有结构；长期不用的大媒体建议删除附件节点以释放 `data\attachments\` 空间。

## 安全设计

- `safeRelPath`：写入附件路径必须是 `attachments/` 前缀、正斜杠、禁止 `..` 与 `:`（防目录穿越）。
- `media.local` 仅映射到 data 目录，其他路径不可达。
- 文件对话框打开附件：相对路径校验后才交系统打开。

## 已知限制

- 需要 WebView2 运行时（Win10/11 自带，Win7 需装运行时）。
- 附件按外置存储，`.usmind` 单独拷贝不含媒体（除非用自包含导出）。
- 未实现：多端同步、账号、会员、评论、机构协作（属在线版）。
