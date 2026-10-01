# -*- coding: utf-8 -*-
import io

def patch(path, old, new, tag):
    with open(path, "r", encoding="utf-8", newline="") as f:
        c = f.read().replace("\r\n", "\n")
    if old in c:
        c = c.replace(old, new)
        with open(path, "w", encoding="utf-8", newline="") as f:
            f.write(c.replace("\n", "\r\n"))
        print("OK:", tag)
    else:
        print("MISS:", tag)

# ===== API.md：在鉴权一览前插入新章节 =====
api_old = "\n---\n\n## 附：接口鉴权一览"
api_new = """
---

## 二十三、后台评论查询与批量删除（`/api/admin/comments`）

| 方法 | 路径 | 说明 | 参数 | 返回 data |
| --- | --- | --- | --- | --- |
| GET | `/api/admin/comments` | 评论分页查询 | `page, size, keyword?（评论内容/评论者模糊）, mindMapId?（脑图筛选）, orgId?（组织筛选，自动查该组织下全部导图）` | `{total, list}` |
| POST | `/api/admin/comments/batch-delete` | 批量删除评论（删除后前端刷新列表） | `{ids: []}` | - |

---

## 二十四、会员套餐价格管理（`/api/admin/member-plans`）

| 方法 | 路径 | 说明 | 参数 | 返回 data |
| --- | --- | --- | --- | --- |
| GET | `/api/admin/member-plans` | 套餐列表（含 originalPrice / discountPercent） | - | `[{id, name, level, price, originalPrice, discountPercent, days, sort, enabled}]` |
| POST | `/api/admin/member-plans` | 新增套餐 | `{name, level, price, originalPrice, discountPercent, days, sort, enabled}` | - |
| PUT | `/api/admin/member-plans/{id}` | 更新套餐（改价后客户端会员中心实时生效） | 同上（可空） | - |
| DELETE | `/api/admin/member-plans/{id}` | 删除套餐 | - | - |

---

## 二十五、会员兑换码（`/api/admin/redeem-codes`）

| 方法 | 路径 | 说明 | 参数 | 返回 data |
| --- | --- | --- | --- | --- |
| GET | `/api/admin/redeem-codes` | 兑换码分页 | `page, size, status?（active\\|used\\|disabled）` | `{total, list}`（used 行含 usedByName / usedAt） |
| POST | `/api/admin/redeem-codes` | 批量生成（16 位去混淆字符集，选套餐取套餐时长或自定义天数） | `{planId?, days?, count?（1-100）, remark?}` | `{created, codes}` |
| DELETE | `/api/admin/redeem-codes/{id}` | 删除兑换码 | - | - |

---

## 二十六、用户端兑换码兑换（`/api/member/redeem`）

| 方法 | 路径 | 说明 | 参数 | 返回 data |
| --- | --- | --- | --- | --- |
| POST | `/api/member/redeem` | 输入兑换码开通会员（会员时长在原有效期上累加，来源标为 redeem） | `{code}` | `{isVip, level, expire, source, days}` |

> `/api/member/plans` 每条套餐现含 `originalPrice` / `discountPercent`（客户端显示划线价与折扣标签）；`/api/member/info` 增 `source`（pay 支付 / redeem 兑换码 / 空=后台开通）。

---

## 二十七、后台统计（会员来源）（`/api/admin/stats`）

| 方法 | 路径 | 说明 | 参数 | 返回 data（本批新增字段） |
| --- | --- | --- | --- | --- |
| GET | `/api/admin/stats` | 总览统计（仪表盘） | - | `vipCount, redeemVipCount, vipSource`（按 member_source 分布） |
| GET | `/api/admin/stats/dashboard/business` | 业务统计（区间） | `start?, end?` | `redeemVipCount, vipSource` |

---

## 附：接口鉴权一览"""
patch(r"C:\Users\25750\Desktop\usmind\docs\API.md", api_old, api_new, "API.md")

# ===== CHANGELOG.md：头部插入新记录 =====
cl_old = "# USMIND 变更记录\n\n## 2026-10-01 · 登录图形验证码"
cl_new = """# USMIND 变更记录

## 2026-10-01 · 后台评论筛选/批量删除 + 会员价格管理与兑换码体系

- **后台评论管理增强**：评论分页新增脑图下拉（mindMapId）与组织下拉（orgId，自动查该组织全部导图）筛选、评论内容/评论者模糊查询；新增批量删除（`POST /api/admin/comments/batch-delete`），删除后前端刷新列表。
- **后台侧边栏滚动**：AdminLayout 左侧菜单 `max-height + overflow-y:auto` 且隐藏滚动条（`scrollbar-width:none` / `::-webkit-scrollbar{display:none}`），移动端抽屉内菜单同样处理。
- **会员价格管理**：member_plan 表新增 `original_price` / `discount_percent` 列（DataInitializer 自动迁移补列）；后台「会员价格管理」卡片支持新增/编辑/删除套餐，改价后客户端 `/api/member/plans` 实时生效（前端显示原价划线 + 折扣标签）。
- **一次性兑换码**：新增 `redeem_code` 表 + 后台生成（选套餐或自定义天数，1-100 个，16 位去混淆字符集 `ABCDEFGHJKLMNPQRSTUVWXYZ23456789`）、状态筛选（可用/已兑换/已停用）、列表删除；用户端会员中心新增兑换输入框（`POST /api/member/redeem`），兑换后会员时长累加、`sys_user.member_source=redeem` 标注来源。
- **会员来源标注**：sys_user 新增 `member_source` 列（pay 支付 / redeem 兑换码 / 空=后台开通）；后台会员列表新增「会员来源」列（支付蓝 / 兑换码绿 / 后台灰）；仪表盘新增「兑换码会员」统计卡与会员来源分布。
- **实测**：改价实时生效（月度 8.8 元 / 原价 19.8 / 55% 折扣）；兑换链路通过（expire 累加 30 天、usedByName=系统管理员、redeemVipCount=1）；评论三种筛选与批量删除接口通过；用户端会员中心下拉筛选 / 划线价 / 折扣标签 / 兑换入口渲染正常。

## 2026-10-01 · 登录图形验证码"""
patch(r"C:\Users\25750\Desktop\usmind\docs\CHANGELOG.md", cl_old, cl_new, "CHANGELOG.md")
