# USMIND 接口文档（REST API + WebSocket）

> 基础地址：`http://<host>:8899`（经 nginx 时同路径 `/api/...`，80 / 8866 / 8867 均可访问）。
> 统一返回格式：`{ "code": 200, "message": "ok", "data": ... }`；`code=200` 成功，否则失败（400 参数/业务、401 未登录、403 无权限、404 不存在、429 限频）。
> 认证：除公开接口外，请求头需带 `Authorization: Bearer <token>`（登录接口返回）。
> 图形验证码：登录/注册共用 `/api/auth/captcha`（返回 `{uuid, image}`，image 为 base64 图片），提交时携带 `captchaUuid` + `captchaCode`；开关见 `authCaptchaEnabled`（注册）与 `authLoginCaptchaEnabled`（登录，均后台可配、前端同步）。

---

## 一、认证 Auth（`/api/auth`）

| 方法 | 路径 | 说明 | 参数 | 返回 data |
| --- | --- | --- | --- | --- |
| GET | `/api/auth/captcha` | 获取图形验证码（公开） | - | `{uuid, image}` |
| POST | `/api/auth/login` | 登录（公开） | `{username, password, captchaUuid?, captchaCode?}` | `{user:{id,username,nickname,avatar,role,...}, token}` |
| POST | `/api/auth/admin-login` | 管理后台登录（公开，不校验图形验证码） | `{username, password}` | 同上（ADMIN 角色 token） |
| POST | `/api/auth/register` | 注册（公开） | `{username, password, nickname?, phone?, email?, captchaUuid?, captchaCode?, smsCode?}` | `{id, username}` |
| POST | `/api/auth/send-code` | 发送短信验证码（默认关闭，需后台开启+接入服务商） | `{phone, captchaUuid, captchaCode}` | - |
| GET | `/api/auth/wechat/url` | 微信扫码登录授权地址（默认关闭） | - | `{url}` |
| GET | `/api/auth/wechat/callback` | 微信登录回调 | `code, state` | `{user, token}` |
| GET | `/api/auth/info` | 当前登录用户 | - | 用户信息 |
| POST | `/api/auth/logout` | 退出登录 | - | - |
| PUT | `/api/auth/password` | 修改密码 | `{oldPassword, newPassword}` | - |

> 登录失败会校验图形验证码（`authLoginCaptchaEnabled=1` 时），验证码错误提示"图形验证码错误或已过期"，前端应刷新验证码。

---

## 二、客户端公共配置（`/api/config`）

| 方法 | 路径 | 说明 | 返回 data 关键字段 |
| --- | --- | --- | --- |
| GET | `/api/config/client` | 登录前即可获取的全局开关（公开） | `feature`(会员功能开关 map)、`authCaptchaEnabled`、`authLoginCaptchaEnabled`、`authSmsEnabled`、`wechatLoginEnabled`、`memberEnabled`、`orgCreateNeedMember`、`orgInviteNeedMember`、`allowedFileTypes`、`uploadMaxSizeMb` |

> 前端据此决定登录/注册页是否显示图形验证码、微信登录按钮、会员相关 UI 等。

---

## 三、导图 MindMap（`/api/mindmaps`）

| 方法 | 路径 | 说明 | 参数 | 返回 data |
| --- | --- | --- | --- | --- |
| GET | `/api/mindmaps` | 我的导图列表 | `page, size, keyword` | `{list, total, page, size}` |
| POST | `/api/mindmaps` | 新建导图 | `{name}` | `{id}` |
| GET | `/api/mindmaps/{id}` | 导图详情（校验归属：仅本人/机构协作成员可读） | - | `{id, name, data, layout, theme, viewData, manualStyles, collabConfig, ...}` |
| PUT | `/api/mindmaps/{id}` | 保存导图（服务端合并：保留对方新增节点，同节点冲突回传 diff 信息） | `{data, layout, theme, viewData, manualStyles, baseVersion?}` | 保存结果 / 冲突详情 |
| DELETE | `/api/mindmaps/{id}` | 删除导图 | - | - |
| POST | `/api/mindmaps/{id}/share` | 开启/复制分享（已分享则返回原链接与密码） | `{password?}` | `{shareToken, sharePassword}` |
| DELETE | `/api/mindmaps/{id}/share` | 取消分享 | - | - |
| PUT | `/api/mindmaps/{id}/collab-config` | 导图级协作设置（节点所有权开关、审批超时等） | `{...}` | - |
| GET | `/api/mindmaps/{id}/comments` | 导图评论列表（组织内协作评论） | `afterId, source?` | `{list, total}` |
| POST | `/api/mindmaps/{id}/comments` | 发表评论（组织内） | `{nodeId, content, parentId?}` | 评论对象 |
| GET | `/api/mindmaps/{id}/comments/{commentId}/replies` | 评论回复列表 | - | 回复数组 |

---

## 四、分享 Share（`/api/share`）

| 方法 | 路径 | 说明 | 参数 | 返回 data |
| --- | --- | --- | --- | --- |
| GET | `/api/share/{token}` | 分享页读取导图（免登录） | `?password=xxx`（设密码时必填） | 导图完整数据 |
| GET | `/api/share/{token}/comments` | 分享页评论列表 | `afterId, name?` | `{list, total}` |
| POST | `/api/share/{token}/comments` | 分享页评论（免登录，需输入昵称 name，限频+128 字/条） | `{nodeId, content, parentId?, name}` | 评论对象 |

> 评论均通过 WebSocket 实时推送（作者端呼吸灯/电流特效+角标提醒，见 WebSocket 章节）。

---

## 五、文件 / 资源（`/api/file`、`/api/admin`）

| 方法 | 路径 | 说明 | 参数 | 返回 data |
| --- | --- | --- | --- | --- |
| POST | `/api/file/upload` | 上传文件（multipart，白名单后缀） | `file, mindMapId?` | `{id, url, name, size, mimeType}` |
| PUT | `/api/file/content` | 更新文件内容（附件在线编辑） | `{id, content}` | - |
| GET | `/api/file/{id}` | 取文件信息 | - | 文件资源对象 |
| POST | `/api/file/upload-avatar` | 上传头像（multipart） | `file` | `{url}` |
| GET | `/api/admin/files` | 资源管理列表 | `page, size, keyword, type?` | `{list, total}` |
| DELETE | `/api/admin/files/{id}` | 删除资源（同步删除 uploads 文件） | - | - |
| POST | `/api/admin/files/batch-delete` | 批量删除资源 | `[ids]` | 删除数量 |

---

## 六、主题 Theme（`/api/themes`、`/api/admin/themes`）

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

## 七、图标 Icon（`/api/icons`、`/api/admin/icons`）

| 方法 | 路径 | 说明 | 参数 | 返回 data |
| --- | --- | --- | --- | --- |
| GET | `/api/icons` | 公开：启用图标列表 | - | 图标数组（含分类） |
| GET | `/api/admin/icons` | 后台：图标列表 | `category?, keyword?` | 图标数组 |
| POST | `/api/admin/icons` | 新增图标 | `{name, category, sort}` | `{id}` |
| POST | `/api/admin/icons/batch` | 批量导入图标 | `[ {name, category} ]` | 数量 |
| DELETE | `/api/admin/icons/{id}` | 删除图标 | - | - |
| POST | `/api/admin/icons/batch-delete` | 批量删除 | `[ids]` | 数量 |

---

## 八、OSS 存储配置（`/api/admin/oss`）

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

## 九、用户（`/api/admin/users`、`/api/user`）

| 方法 | 路径 | 说明 | 参数 | 返回 data |
| --- | --- | --- | --- | --- |
| GET | `/api/admin/users` | 用户列表 | `page, size, keyword` | `{list, total}` |
| POST | `/api/admin/users` | 新增用户 | `{username, password, nickname, role, email, phone}` | `{id}` |
| PUT | `/api/admin/users/{id}` | 修改用户 | `{nickname, email, phone, role, status}` | - |
| DELETE | `/api/admin/users/{id}` | 删除用户 | - | -（**管理员保底：只剩一个管理员时拒绝**） |
| POST | `/api/admin/users/batch-delete` | 批量删除 | `[ids]` | 数量（同样受保底限制） |
| PUT | `/api/admin/users/{id}/password` | 重置密码 | `{password}` | - |
| PUT | `/api/user/profile` | 用户中心：修改个人信息（昵称/邮箱/手机/头像） | `{...}` | - |
| PUT | `/api/user/avatar` | 用户中心：上传头像 | multipart `file` | `{url}` |

---

## 十、导图管理（`/api/admin/mindmaps`）

| 方法 | 路径 | 说明 | 参数 | 返回 data |
| --- | --- | --- | --- | --- |
| GET | `/api/admin/mindmaps` | 全部导图列表 | `page, size, keyword` | `{list, total}` |
| GET | `/api/admin/mindmaps/{id}` | 导图详情 | - | 导图对象 |
| POST | `/api/admin/mindmaps/batch-delete` | 批量删除导图 | `[ids]` | 数量 |

---

## 十一、机构 Org（`/api/orgs` 用户端、`/api/admin/orgs` 管理端）

| 方法 | 路径 | 说明 | 参数 | 返回 data |
| --- | --- | --- | --- | --- |
| POST | `/api/orgs` | 创建机构（创建者需会员，后台可关） | `{name, inviteCode?}` | `{id, inviteCode}` |
| POST | `/api/orgs/join` | 邀请码加入机构 | `{inviteCode}` | 机构对象 |
| GET | `/api/orgs` | 我的机构（我创建的 / 我加入的） | - | 机构数组 |
| GET | `/api/orgs/{id}` | 机构详情（含成员列表） | - | `{org, members}` |
| PUT | `/api/orgs/{id}` | 修改机构 | `{name, inviteCode?}` | - |
| DELETE | `/api/orgs/{id}` | 解散机构（仅创建者；在线成员被 WS 踢出） | - | - |
| POST | `/api/orgs/{id}/members/{userId}/kick` | 踢出成员（创建者） | - | - |
| POST | `/api/orgs/{id}/leave` | 退出机构（成员） | - | - |
| POST | `/api/orgs/{id}/mindmaps/{mapId}/invite` | 邀请成员协作脑图 | `{memberIds?}` | - |
| GET | `/api/admin/orgs` | 管理端：机构列表 | `page, size, keyword` | `{list, total}` |
| DELETE | `/api/admin/orgs/{id}` | 管理端：解散机构 | - | - |

---

## 十二、会员与支付（`/api/member`、`/api/pay`、`/api/admin/...`）

| 方法 | 路径 | 说明 | 参数 | 返回 data |
| --- | --- | --- | --- | --- |
| GET | `/api/member/plans` | 会员套餐列表（公开） | - | 套餐数组 |
| GET | `/api/member/me` | 我的会员状态 | - | `{level, expireAt, ...}` |
| POST | `/api/member/purchase` | 购买会员（微信支付下单） | `{planId}` | `{payParams}` |
| POST | `/api/pay/wechat/notify` | 微信支付回调（公开，验签） | 微信回调 XML | - |
| GET | `/api/pay/wechat/status/{orderNo}` | 查询订单支付状态 | - | `{paid, ...}` |
| GET | `/api/admin/members` | 会员配置（开关、主题/导出/上传/画布限制） | - | 配置 map |
| PUT | `/api/admin/members` | 保存会员配置 | `{memberThemeIds, memberExportFormats, uploadFreeCount, ...}` | - |
| GET | `/api/admin/pay-config` | 支付/验证码/防护配置读取 | - | 配置 map |
| PUT | `/api/admin/pay-config` | 保存支付/验证码/防护配置 | `{wechatPayEnabled, authCaptchaEnabled, authLoginCaptchaEnabled, authSmsEnabled, ipRateLimit, ...}` | - |
| GET | `/api/admin/orders` | 订单管理列表 | `page, size, status?, keyword?` | `{list(含用户名/套餐名), total}` |
| DELETE | `/api/admin/orders/{id}` | 删除订单 | - | - |
| POST | `/api/admin/orders/batch-delete` | 批量删除订单 | `[ids]` | 数量 |

> 微信支付/登录默认关闭，无商户资质时不可用；所有会员功能开关后台统一配置，客户端非会员显示 👑 图标、不可用（后台可整体关闭会员体系）。

---

## 十三、评论管理（`/api/admin/comments`）

| 方法 | 路径 | 说明 | 参数 | 返回 data |
| --- | --- | --- | --- | --- |
| GET | `/api/admin/comments` | 全部评论列表（分享+组织内） | `page, size, keyword?, source?` | `{list(含用户名/导图名), total}` |
| DELETE | `/api/admin/comments/{id}` | 删除评论 | - | - |
| POST | `/api/admin/comments/batch-delete` | 批量删除评论 | `[ids]` | 数量 |

---

## 十四、IP 黑白名单与防护（`/api/admin/ip-rules`）

| 方法 | 路径 | 说明 | 参数 | 返回 data |
| --- | --- | --- | --- | --- |
| GET | `/api/admin/ip-rules` | 黑白名单列表 | `page, size, type?` | `{list, total}` |
| POST | `/api/admin/ip-rules` | 新增规则（IP/用户ID，支持正则校验） | `{ruleType(white/black), targetType(ip/user_id), targetValue, remark?}` | `{id}` |
| DELETE | `/api/admin/ip-rules/{id}` | 删除规则（解除封禁/白名单） | - | - |
| POST | `/api/admin/ip-rules/batch-delete` | 批量删除 | `[ids]` | 数量 |

> 防护机制：白名单优先放行 → 黑名单拒绝 → 滑动窗口限频（`ipRateLimit/ipRateWindow`）超限自动拉黑（`ipAutoBlackSeconds` 到期自动解封）。`/api/admin/**` 豁免 IP 防护（后台已有 ADMIN 角色 JWT 保护，保留管理员逃生通道）。频控/评论限频配置见第十二章 pay-config。

---

## 十五、请求日志（`/api/admin/logs`、`/api/log`）

| 方法 | 路径 | 说明 | 参数 | 返回 data |
| --- | --- | --- | --- | --- |
| GET | `/api/admin/logs` | 请求日志列表 | `page, size, userId?, path?, level?(正常/异常/错误/警告), source?(前端/后端), timeRange?` | `{list, total}` |
| GET | `/api/admin/logs/{id}` | 日志详情（请求头/请求体/响应/异常栈） | - | 日志对象 |
| POST | `/api/admin/logs/batch-delete` | 批量删除日志 | `[ids]` | 数量 |
| POST | `/api/log/report` | 前端错误日志上报（公开，自动清理超期日志由后台定时任务执行） | `{message, stack, url, ...}` | - |

> 记录级别（info/warn/error）、保留天数、清理时间在系统设置配置；超期日志定时删除。

---

## 十六、数据备份（`/api/admin/data`）

| 方法 | 路径 | 说明 | 参数 | 返回 data |
| --- | --- | --- | --- | --- |
| GET | `/api/admin/data/backup` | 导出数据库备份（JSON/SQL） | `?type=json\|sql` | 备份文件内容 / 下载 |
| POST | `/api/admin/data/restore` | 上传备份文件恢复数据库 | multipart `file` | `{ok, message}` |

---

## 十七、统计（`/api/admin/stats`）

| 方法 | 路径 | 说明 | 返回 data |
| --- | --- | --- | --- |
| GET | `/api/admin/stats` | 仪表盘统计（用户/导图/资源/访问量/注册趋势/订单/会员） | `{userCount, mindMapCount, fileCount, viewCount, orderCount, vipCount, registerTrend, orderTrend, ...}` |

---

## 十八、系统设置（`/api/admin/settings`）

| 方法 | 路径 | 说明 | 参数 | 返回 data |
| --- | --- | --- | --- | --- |
| GET | `/api/admin/settings` | 读取 KV 配置 | - | 配置 map |
| PUT | `/api/admin/settings` | 保存 KV 配置 | `{key: value, ...}` | - |

常用 key：`authCaptchaEnabled` / `authLoginCaptchaEnabled` / `authSmsEnabled` / `memberEnabled` / `orgCreateNeedMember` / `orgInviteNeedMember` / `wechatLoginEnabled` / `ipGuardEnabled` / `ipRateLimit` / `ipRateWindow` / `ipAutoBlackSeconds` / `commentRateLimit` / `commentRateWindow` / `logRecordLevels` / `logRetainDays` / `logCleanTime` / `attachTextColor` 等。

---

## 十九、项目配置（`/api/admin/project-config`）

| 方法 | 路径 | 说明 | 参数 | 返回 data |
| --- | --- | --- | --- | --- |
| GET | `/api/admin/project-config` | 读取项目配置（后端 / nginx 配置内容） | - | `{backendConfig, nginxConfig, ...}` |
| PUT | `/api/admin/project-config` | 保存项目配置 | `{backendConfig?, nginxConfig?}` | - |
| GET | `/api/admin/project-config/export` | 导出配置文件 | `?type=backend\|nginx` | 配置文件内容 |

---

## 二十、预览（`/api/preview`、`/api/preview-config`）

| 方法 | 路径 | 说明 | 参数 | 返回 data |
| --- | --- | --- | --- | --- |
| GET | `/api/preview-config` | 前端全局配置（公开） | - | `{officeServer, previewConfig, ...}` |
| GET | `/api/preview/{fileId}` | 附件在线预览（PDF/图片/音视频/代码高亮/Office 跳转） | - | 预览 HTML / 跳转链接 |
| GET | `/api/preview/url` | 预览地址生成 | `?fileId=` | `{url}` |

---

## 二十一、AI（`/api/ai`）

| 方法 | 路径 | 说明 | 参数 | 返回 data |
| --- | --- | --- | --- | --- |
| POST | `/api/ai/generate` | AI 生成节点内容（后端代理 OpenAI 兼容接口） | `{prompt, model?}` | `{content}` |

> 需在后台配置 `aiApiKey` / `aiModel` / `aiEndpoint`（OpenAI 兼容），未配置返回明确错误。

---

## 二十二、WebSocket（`/ws/**`）

| 路径 | 说明 | 消息类型（`{type, ...}`） |
| --- | --- | --- |
| `/ws/mindmap/{id}` | 导图协作实时同步（机构成员） | `doc_update`（全量）、`struct_update`（节点增删改增量，对端局部应用不闪屏）、`lock`（节点编辑锁：持锁者实时广播编辑内容，他人只读）、`user_join/leave`、`kick`（机构解散/被踢强制下线） |
| `/ws/comment/{mapId}` | 评论实时推送（分享+组织内） | `comment_new`（含评论对象、目标用户、来源 source=share/org，作者端触发电流特效+角标+音效） |

> 评论通知规则：对节点回复 → 导图创建者+节点创建者收到提醒（组织内）/ 仅导图创建者（分享）；对指定人回复 → 仅被回复人收到特效提醒；自己回复自己不触发特效。访问过的评论刷新后不再重复提醒（服务端记录已读）。

---

## 附：接口鉴权一览

| 类别 | 是否需登录 |
| --- | --- |
| `/api/auth/*`（login / admin-login / register / captcha / send-code / wechat） | 公开 |
| `/api/config/client`、`/api/member/plans`、`/api/pay/wechat/notify` | 公开 |
| `/api/share/**`、`/api/themes`、`/api/icons`、`/api/preview-config` | 公开（只读/免登录评论） |
| `/api/mindmaps/**`、`/api/file/**`、`/api/ai/**`、`/api/orgs/**`、`/api/member/**`、`/api/user/**` | 用户登录（USER / ADMIN） |
| `/api/admin/**` | 仅管理员（ADMIN；同时豁免 IP 防护） |
| `/uploads/**`、`/api/preview/**`、`/ws/**` | 公开静态资源 / WebSocket |
