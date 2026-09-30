# USMIND API 契约（前后端共享）

## 通用约定
- Base URL: `/api`
- 响应格式: `{ code: 200, message: "ok", data: {...} }`
- 认证: Header `Authorization: Bearer <token>`
- 分页: `?page=1&size=10`，返回 `{ list: [], total: N, page: 1, size: 10 }`
- 时间格式: ISO 8601 字符串

## 1. 认证 Auth

### POST /api/auth/login
请求: `{ username: "admin", password: "admin123" }`
响应: `{ token: "jwt...", user: { id, username, nickname, avatar, role } }`

### POST /api/auth/register
请求: `{ username, password, nickname? }`
响应: `{ id, username }`

### GET /api/auth/info
Header: Bearer
响应: `{ id, username, nickname, avatar, email, phone, role }`

### POST /api/auth/logout
Header: Bearer
响应: `{}`

### PUT /api/auth/password
Header: Bearer
请求: `{ oldPassword, newPassword }`
响应: `{}`

## 2. 用户管理（仅 ADMIN）

### GET /api/admin/users?page=1&size=10&keyword=
响应: 分页 `{ list: [{id, username, nickname, avatar, email, phone, role, status, createdAt}], total }`

### POST /api/admin/users
请求: `{ username, password, nickname?, email?, phone?, role? }`
响应: `{ id }`

### PUT /api/admin/users/{id}
请求: `{ nickname?, email?, phone?, role?, status? }`
响应: `{}`

### DELETE /api/admin/users/{id}
响应: `{}`

### PUT /api/admin/users/{id}/password
请求: `{ password }`
响应: `{}`

## 3. 导图 MindMap（需登录）

### GET /api/mindmaps?page=1&size=20&keyword=
响应: 分页 `{ list: [{id, name, layout, theme, isShared, updatedAt, createdAt}], total }`

### POST /api/mindmaps
请求: `{ name?, data?, layout?, theme? }`
响应: `{ id }`

### GET /api/mindmaps/{id}
响应: `{ id, userId, name, data, layout, theme, viewData, isShared, shareToken, createdAt, updatedAt }`

### PUT /api/mindmaps/{id}
请求: `{ name?, data?, layout?, theme?, viewData? }`
响应: `{}`

### DELETE /api/mindmaps/{id}
响应: `{}`

### POST /api/mindmaps/{id}/share
请求: `{ password? }`
响应: `{ shareToken, shareUrl }`

### DELETE /api/mindmaps/{id}/share
响应: `{}`

## 4. 分享（免登录）

### GET /api/share/{token}
可选 query: `?password=xxx`
响应: `{ id, name, data, layout, theme, viewData }`（无密码或密码正确）
错误: `{ code: 403, message: "需要分享密码" }` 或 `{ code: 404 }`

## 5. 文件上传

### POST /api/file/upload
Header: Bearer
Content-Type: multipart/form-data
字段: `file` (文件), `mindMapId` (可选)
响应: `{ id, url, originalName, size, mimeType }`

### GET /api/file/{id}
响应: 302 重定向到实际文件URL 或 直接返回文件流

## 6. OSS 配置（仅 ADMIN）

### GET /api/admin/oss/configs
响应: `{ list: [{id, name, type, endpoint, bucket, region, domain, isActive, createdAt}] }`（不含 secret_key）

### POST /api/admin/oss/configs
请求: `{ name, type, endpoint, accessKey, secretKey, bucket, region, domain?, configJson? }`
响应: `{ id }`

### PUT /api/admin/oss/configs/{id}
请求: 同上（字段可选，secretKey 为空则不修改）
响应: `{}`

### DELETE /api/admin/oss/configs/{id}
响应: `{}`

### PUT /api/admin/oss/configs/{id}/activate
响应: `{}`（热切换当前启用的OSS，其他置为非激活）

### GET /api/admin/oss/test/{id}
响应: `{ success: true, message: "连接成功" }`

## 7. 文档预览

### GET /api/preview/{fileId}
根据文件类型返回预览:
- pdf: 直接返回文件流（浏览器内置预览）
- 图片: 直接返回
- doc/docx/xlsx/pptx: 后端转换为PDF或HTML返回（使用onlyoffice在线预览URL或kkFileView）
- 其他: 返回下载

### GET /api/preview/url?url=xxx
通过URL预览（用于导图节点中引用的外部文件）

## 8. AI 助手（透传）

### POST /api/ai/generate
Header: Bearer
请求: `{ mode: "create|summary|chat", prompt, mindMapData? }`
响应: `{ result: "..." }`（后端可配置AI API密钥，未配置时返回模拟数据）

## 2. 验证码与安全契约（新增）

### GET /api/auth/captcha
响应: `{ uuid: "xxx", image: "data:image/png;base64,..." }`（5 分钟有效）

### POST /api/auth/login（登录验证码开关开启时）
请求: `{ username, password, captchaUuid, captchaCode }`
> 开关 `authLoginCaptchaEnabled=1` 时必填且校验，错误返回 400"图形验证码错误或已过期"；后台走 `/api/auth/admin-login` 不校验。

### POST /api/auth/register
请求: `{ username, password, nickname?, phone?, email?, captchaUuid?, captchaCode?, smsCode? }`

### POST /api/auth/send-code（短信验证码，默认关闭）
请求: `{ phone, captchaUuid, captchaCode }`（同一 IP 一分钟一次）

## 3. 新模块契约（机构 / 会员 / 评论 / 日志 / 黑白名单 / WS）

- 机构：`POST /api/orgs`（创建，需会员可关）/ `POST /api/orgs/join`（邀请码）/ `GET /api/orgs`（我的机构）/ `GET /api/orgs/{id}`（含成员）/ `POST /api/orgs/{id}/members/{userId}/kick` / `POST /api/orgs/{id}/leave` / `DELETE /api/orgs/{id}`（解散，仅创建者）/ `POST /api/orgs/{id}/mindmaps/{mapId}/invite`
- 会员：`GET /api/member/plans` / `GET /api/member/me` / `POST /api/member/purchase`（`{planId}` → `{payParams}`）/ `POST /api/pay/wechat/notify`（回调验签）/ `GET /api/pay/wechat/status/{orderNo}`
- 评论：`GET|POST /api/share/{token}/comments`（免登录，`{nodeId, content, parentId?, name}`）/ `GET|POST /api/mindmaps/{id}/comments`（机构内，登录）
- 日志：`GET /api/admin/logs`（`page,size,userId?,path?,level?,source?,timeRange?`）/ `GET /api/admin/logs/{id}`（详情含请求头/请求体/响应/异常栈）/ `POST /api/log/report`（前端上报）
- 黑白名单：`POST /api/admin/ip-rules`（`{ruleType, targetType(ip|user_id), targetValue}`，正则校验）/ `DELETE /api/admin/ip-rules/{id}`
- WebSocket：`/ws/mindmap/{id}?token=` 消息 `{type: doc_update|struct_update|lock|kick|user_join|user_leave}`；`/ws/comment/{mapId}?token=` 消息 `{type: comment_new, data: {...}}`