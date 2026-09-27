# USMIND 接口文档（REST API）

> 基础地址：`http://<host>:8899`（经 nginx 时同路径 `/api/...`）。
> 统一返回格式：`{ "code": 200, "message": "ok", "data": ... }`；`code=200` 成功，否则失败（400 参数/业务、401 未登录、403 无权限、404 不存在）。
> 认证：除公开接口外，请求头需带 `Authorization: Bearer <token>`（登录接口返回）。

---

## 一、认证 Auth（`/api/auth`）

| 方法 | 路径 | 说明 | 参数 | 返回 data |
| --- | --- | --- | --- | --- |
| POST | `/api/auth/login` | 登录（公开） | `{username, password}` | `{user:{id,username,nickname,role,...}, token}` |
| POST | `/api/auth/register` | 注册（公开） | `{username, password, nickname}` | `{id}` |
| GET | `/api/auth/info` | 当前登录用户 | - | `{id, username, nickname, role, ...}` |
| POST | `/api/auth/logout` | 退出登录 | - | - |
| PUT | `/api/auth/password` | 修改密码 | `{oldPassword, newPassword}` | - |

---

## 二、导图 MindMap（`/api/mindmaps`）

| 方法 | 路径 | 说明 | 参数 | 返回 data |
| --- | --- | --- | --- | --- |
| GET | `/api/mindmaps` | 我的导图列表 | `page, size, keyword` | `{list, total, page, size}` |
| POST | `/api/mindmaps` | 新建导图 | `{name}` | `{id}` |
| GET | `/api/mindmaps/{id}` | 导图详情 | - | `{id, name, data, layout, theme, viewData, manualStyles, ...}` |
| PUT | `/api/mindmaps/{id}` | 保存导图 | `{data, layout, theme, viewData, manualStyles}` | - |
| DELETE | `/api/mindmaps/{id}` | 删除导图 | - | - |
| POST | `/api/mindmaps/{id}/share` | 开启分享 | `{password?}` | `{shareToken, sharePassword}` |
| DELETE | `/api/mindmaps/{id}/share` | 取消分享 | - | - |

---

## 三、分享 Share（`/api/share`）

| 方法 | 路径 | 说明 | 参数 | 返回 data |
| --- | --- | --- | --- | --- |
| GET | `/api/share/{token}` | 分享页读取导图（免登录） | `?password=xxx`（分享设密码时必填） | 导图完整数据 |

---

## 四、文件 / 资源（`/api/file`、`/api/admin/files`）

| 方法 | 路径 | 说明 | 参数 | 返回 data |
| --- | --- | --- | --- | --- |
| POST | `/api/file/upload` | 上传文件（multipart） | `file, mindMapId?` | `{id, url, name, size, mimeType}` |
| PUT | `/api/file/content` | 更新文件内容（附件在线编辑） | `{id, content}` | - |
| GET | `/api/file/{id}` | 取文件信息 | - | 文件资源对象 |
| GET | `/api/admin/files` | 资源管理列表 | `page, size, keyword, type?` | `{list, total}` |
| DELETE | `/api/admin/files/{id}` | 删除资源 | - | - |
| POST | `/api/admin/files/batch-delete` | 批量删除资源 | `[ids]` | 删除数量 |

---

## 五、主题 Theme（`/api/themes`、`/api/admin/themes`）

| 方法 | 路径 | 说明 | 参数 | 返回 data |
| --- | --- | --- | --- | --- |
| GET | `/api/themes` | 公开：主题列表 | - | 启用主题数组 |
| GET | `/api/admin/themes` | 后台：主题列表（含禁用） | - | 全部主题 |
| GET | `/api/admin/themes/{id}` | 主题详情 | - | 主题对象 |
| POST | `/api/admin/themes` | 新增主题 | `{name, themeKey, branchColors, configJson, enabled}` | `{id}` |
| PUT | `/api/admin/themes/{id}` | 修改主题 | 同上 | -（改名自动同步存量导图） |
| DELETE | `/api/admin/themes/{id}` | 删除主题 | - | - |
| POST | `/api/admin/themes/batch-delete` | 批量删除主题 | `[ids]` | 数量 |

---

## 六、图标 Icon（`/api/icons`、`/api/admin/icons`）

| 方法 | 路径 | 说明 | 参数 | 返回 data |
| --- | --- | --- | --- | --- |
| GET | `/api/icons` | 公开：启用图标列表 | - | 图标数组（含分类） |
| GET | `/api/admin/icons` | 后台：图标列表 | `category?, keyword?` | 图标数组 |
| POST | `/api/admin/icons` | 新增图标 | `{name, category, sort}` | `{id}` |
| POST | `/api/admin/icons/batch` | 批量导入图标 | `[ {name, category} ]` | 数量 |
| DELETE | `/api/admin/icons/{id}` | 删除图标 | - | - |
| POST | `/api/admin/icons/batch-delete` | 批量删除 | `[ids]` | 数量 |

---

## 七、OSS 配置（`/api/admin/oss`）

| 方法 | 路径 | 说明 | 参数 | 返回 data |
| --- | --- | --- | --- | --- |
| GET | `/api/admin/oss/configs` | 配置列表 | - | 配置数组 |
| POST | `/api/admin/oss/configs` | 新增配置 | `{name, type, endpoint, accessKey, secretKey, bucket, region, domain}` | `{id}` |
| PUT | `/api/admin/oss/configs/{id}` | 修改配置 | 同上 | - |
| DELETE | `/api/admin/oss/configs/{id}` | 删除配置 | - | - |
| POST | `/api/admin/oss/configs/batch-delete` | 批量删除 | `[ids]` | 数量 |
| PUT | `/api/admin/oss/configs/{id}/activate` | 启用为当前存储（唯一激活） | - | - |
| GET | `/api/admin/oss/test/{id}` | 连通测试 | - | `{ok, message}` |

---

## 八、用户管理（`/api/admin/users`）

| 方法 | 路径 | 说明 | 参数 | 返回 data |
| --- | --- | --- | --- | --- |
| GET | `/api/admin/users` | 用户列表 | `page, size, keyword` | `{list, total}` |
| POST | `/api/admin/users` | 新增用户 | `{username, password, nickname, role, email, phone}` | `{id}` |
| PUT | `/api/admin/users/{id}` | 修改用户 | `{nickname, email, phone, role, status}` | - |
| DELETE | `/api/admin/users/{id}` | 删除用户 | - | -（**管理员保底：只剩一个管理员时拒绝**） |
| POST | `/api/admin/users/batch-delete` | 批量删除 | `[ids]` | 数量（同样受保底限制） |
| PUT | `/api/admin/users/{id}/password` | 重置密码 | `{password}` | - |

---

## 九、导图管理（`/api/admin/mindmaps`）

| 方法 | 路径 | 说明 | 参数 | 返回 data |
| --- | --- | --- | --- | --- |
| GET | `/api/admin/mindmaps` | 全部导图列表 | `page, size, keyword` | `{list, total}` |
| GET | `/api/admin/mindmaps/{id}` | 导图详情 | - | 导图对象 |
| POST | `/api/admin/mindmaps/batch-delete` | 批量删除导图 | `[ids]` | 数量 |

---

## 十、数据备份（`/api/admin/data`）

| 方法 | 路径 | 说明 | 参数 | 返回 data |
| --- | --- | --- | --- | --- |
| GET | `/api/admin/data/backup` | 导出数据库备份（JSON） | `?type=json\|sql` | 备份文件内容 / 下载 |
| POST | `/api/admin/data/restore` | 上传备份文件恢复数据库 | multipart `file` | `{ok, message}` |

---

## 十一、统计（`/api/admin/stats`）

| 方法 | 路径 | 说明 | 参数 | 返回 data |
| --- | --- | --- | --- | --- |
| GET | `/api/admin/stats` | 仪表盘统计 | - | `{userCount, mindMapCount, fileCount, viewCount, recentMindMaps}` |

---

## 十二、系统设置（`/api/admin/settings`）

| 方法 | 路径 | 说明 | 参数 | 返回 data |
| --- | --- | --- | --- | --- |
| GET | `/api/admin/settings` | 读取 KV 配置 | - | 配置 map |
| PUT | `/api/admin/settings` | 保存 KV 配置 | `{key: value, ...}` | - |

常用 key：登录页版权声明、入网声明、登录缓存时长（小时）、默认主题等。

---

## 十三、项目配置（`/api/admin/project-config`）

| 方法 | 路径 | 说明 | 参数 | 返回 data |
| --- | --- | --- | --- | --- |
| GET | `/api/admin/project-config` | 读取项目配置（后端 / nginx 配置内容） | - | `{backendConfig, nginxConfig, ...}` |
| PUT | `/api/admin/project-config` | 保存项目配置 | `{backendConfig?, nginxConfig?}` | - |
| GET | `/api/admin/project-config/export` | 导出配置文件 | `?type=backend\|nginx` | 配置文件内容 |

---

## 十四、预览（`/api/preview`、`/api/preview-config`）

| 方法 | 路径 | 说明 | 参数 | 返回 data |
| --- | --- | --- | --- | --- |
| GET | `/api/preview-config` | 前端全局配置（公开） | - | `{officeServer, previewConfig, ...}` |
| GET | `/api/preview/{fileId}` | 附件在线预览（按文件类型返回渲染内容 / Office 跳转地址） | - | 预览 HTML / 跳转链接 |
| GET | `/api/preview/url` | 预览地址生成 | `?fileId=` | `{url}` |

---

## 十五、AI（`/api/ai`）

| 方法 | 路径 | 说明 | 参数 | 返回 data |
| --- | --- | --- | --- | --- |
| POST | `/api/ai/generate` | AI 生成节点内容 | `{prompt, model?}` | `{content}` |

> 需在 `application.yml` 配置 `ai.api-url` 与 `ai.api-key`（OpenAI 兼容接口），未配置返回明确错误。

---

## 附：接口鉴权一览

| 类别 | 是否需登录 |
| --- | --- |
| `/api/auth/login`、`/api/auth/register` | 公开 |
| `/api/share/**`、`/api/themes`、`/api/icons`、`/api/preview-config` | 公开（只读） |
| `/api/mindmaps/**`、`/api/file/**`、`/api/ai/**` | 用户登录（USER / ADMIN） |
| `/api/admin/**` | 仅管理员（ADMIN） |
| `/uploads/**` | 公开静态资源（nginx 直接服务） |
