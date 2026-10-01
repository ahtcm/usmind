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

# ===== Dashboard.vue: redeem vip card + stat =====
patch(r"C:\Users\25750\Desktop\usmind\code\web-admin\src\views\Dashboard.vue", [
    ("import { User, Medal, TrendCharts, Wallet, Coin, AlarmClock, Money, UserFilled, Refresh } from '@element-plus/icons-vue'",
     "import { User, Medal, TrendCharts, Wallet, Coin, AlarmClock, Money, UserFilled, Refresh, Ticket } from '@element-plus/icons-vue'"),
    ("const stats = reactive({ totalUsers: 0, newUsers: 0, totalVips: 0, newVips: 0, vipRate: 0, totalIncome: 0, todayIncome: 0, paidOrders: 0, expireSoonVips: 0 })",
     "const stats = reactive({ totalUsers: 0, newUsers: 0, totalVips: 0, newVips: 0, vipRate: 0, totalIncome: 0, todayIncome: 0, paidOrders: 0, expireSoonVips: 0, redeemVipCount: 0 })"),
    ("  { label: '30天内到期', value: 0, sub: '', icon: AlarmClock, color: '#f56c6c' }\n])",
     "  { label: '30天内到期', value: 0, sub: '', icon: AlarmClock, color: '#f56c6c' },\n  { label: '兑换码会员', value: 0, sub: '', icon: Ticket, color: '#07c160' }\n])"),
    ("    stats.expireSoonVips = d.expireSoonVips ?? 0\n    recentOrders.value = d.recentOrders || []",
     "    stats.expireSoonVips = d.expireSoonVips ?? 0\n    stats.redeemVipCount = d.redeemVipCount ?? 0\n    recentOrders.value = d.recentOrders || []"),
    ("    statCards.value[7].value = stats.expireSoonVips\n    statCards.value[7].sub = '30天内到期提醒'",
     "    statCards.value[7].value = stats.expireSoonVips\n    statCards.value[7].sub = '30天内到期提醒'\n    statCards.value[8].value = stats.redeemVipCount\n    statCards.value[8].sub = '兑换码开通会员'"),
])
