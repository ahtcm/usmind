# USMIND 思维导图系统

一个**自托管、可私有部署**的在线思维导图与知识管理平台：富文本脑图编辑 + 公式 / 图片 / 音视频 / 文件附件 + 机构多人实时协作 + 分享评论 + 会员商业化体系，前后端分离、开箱即用（绿色免安装，Windows / Linux 一键启动）。

## 核心特性

**脑图编辑**
- 富文本节点、LaTeX 数学公式（MathJax SVG，矩阵 / 多行公式，节点高度自适应不塌陷）
- Emoji 图标面板（分类折叠 / 懒加载 / 光标插入）、图片 / 视频 / 音频 / PDF / Office / 代码附件与媒体节点
- 全局主题模板（后台可视化配置：配色 / 字体 / 背景图 / 背景视频）、单节点样式覆盖、分支一键配色
- 快捷键（复制 / 剪切 / 粘贴 / 删除 / 撤销重做）、多画布标签页、画布平移缩放、移动端双指缩放
- 导入导出：JSON / PNG / JPG / SVG / PDF / Markdown / `.usmind` 资源包（超大画布自动分块 + 进度提示）

**协作与分享**
- 机构体系：邀请码加入、创建 / 解散机构、踢出成员、邀请成员协作脑图
- 多人实时协作：WebSocket 秒级同步（结构变更增量局部应用，不闪屏不丢编辑）、节点编辑锁（持锁者实时广播，他人只读）
- 服务端智能合并：保存时保留对方新增、同节点冲突弹 diff 框（加载对方 / 以我为准）
- 分享链接（可设密码）+ 节点评论 / 回复（分享页与机构内均可，实时推送 + 电流特效提醒 + 音效 + 已读持久化）

**账号与商业化**
- 注册 / 登录（图形验证码后台可开关）、头像、个人中心、微信登录预留（默认关）
- 会员体系：微信支付购买（默认关）、套餐价格管理（现价 / 原价 / 折扣）、一次性兑换码（批量生成 / 状态筛选 / 批量导出）、会员专属主题 / 导出格式 / 上传限制后台统一配置（可整体关闭）
- 安全防护：IP 限频自动拉黑（到期解封）、IP / 用户黑白名单（正则校验）、请求日志全量记录（前后端 / 级别筛选 + 详情）

**管理后台**
- 仪表盘（12 项指标 + 趋势折线 + 分布饼图 + 订单 / 兑换码表格）、用户 / 导图 / 资源 / 评论 / 订单 / 机构 / 兑换码管理
- OSS 热插拔（本地 / 阿里云 / 腾讯 / 七牛 / MinIO，一键迁移本地文件）、主题模板 / 图标库管理
- 项目配置可视化编辑、数据备份恢复、请求日志清理策略配置

## 技术栈

| 层 | 技术 |
| --- | --- |
| 后端 | Java 17 · Spring Boot 3 · Spring Security + JWT · MyBatis-Plus · WebSocket |
| 存储 | MySQL 8 · Redis 5 · 本地文件 / 对象存储（策略模式） |
| 用户端 | Vue 3 · Vite · simple-mind-map · MathJax · pdf.js |
| 管理后台 | Vue 3 · Vite · Element Plus · ECharts |
| 网关 | nginx（80 / 8866 / 8867 多入口分流） |

## 快速开始

```bash
# Windows（绿色免安装，环境已内置 tools/）
script\bat\start-all.bat      # 一键启动 MySQL → Redis → 后端 8899 → nginx

# Linux
bash script/shell/start-all.sh
```

- 用户端：http://127.0.0.1:8866
- 管理后台：http://127.0.0.1:8867（默认 `admin / admin123`）
- 数据库：`sql/init.sql`（或后台「数据备份」页导入备份）

## 文档

- [README.md](README.md) — 功能 / 架构 / 部署 / 迁移 / 发布
- [docs/API.md](docs/API.md) — REST + WebSocket 接口文档
- [docs/FILE_GUIDE.md](docs/FILE_GUIDE.md) — 代码文件与函数说明
- [docs/FRONTEND.md](docs/FRONTEND.md) — 前端架构
- [docs/LEARNING.md](docs/LEARNING.md) — 从零学习路线
- [docs/CHANGELOG.md](docs/CHANGELOG.md) — 变更记录

## License

MIT
