# -*- coding: utf-8 -*-
import io

def patch(path, pairs):
    with open(path, "r", encoding="utf-8", newline="") as f:
        c = f.read().replace("\r\n", "\n")
    for a, r in pairs:
        if a in c:
            c = c.replace(a, r)
        else:
            print("MISS:", path, "->", a[:60].replace("\n", "\\n"))
            return
    with open(path, "w", encoding="utf-8", newline="") as f:
        f.write(c.replace("\n", "\r\n"))
    print("OK:", path)

# ===== Members.vue: add 会员来源 column =====
patch(r"C:\Users\25750\Desktop\usmind\code\web-admin\src\views\Members.vue", [
    ('        <el-table-column prop="source" label="注册来源" width="100" />',
     '''        <el-table-column label="会员来源" width="110">
          <template #default="{ row }">
            <template v-if="row.memberLevel > 0">
              <el-tag v-if="row.memberSource === 'redeem'" type="success" size="small">兑换码</el-tag>
              <el-tag v-else-if="row.memberSource === 'pay'" type="primary" size="small">支付</el-tag>
              <el-tag v-else type="info" size="small">后台</el-tag>
            </template>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column prop="source" label="注册来源" width="100" />'''),
])

# ===== Dashboard.vue: add redeem vip card =====
patch(r"C:\Users\25750\Desktop\usmind\code\web-admin\src\views\Dashboard.vue", [
    ('stats.expireSoonVips = d.expireSoonVips ?? 0',
     'stats.expireSoonVips = d.expireSoonVips ?? 0\n      stats.redeemVipCount = d.redeemVipCount ?? 0\n      stats.vipSource = d.vipSource || []'),
    ("stats.expireSoonVips = d.expireSoonVips ?? 0",
     "stats.expireSoonVips = d.expireSoonVips ?? 0\n    stats.redeemVipCount = d.redeemVipCount ?? 0\n    stats.vipSource = d.vipSource || []"),
])
