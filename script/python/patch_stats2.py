# -*- coding: utf-8 -*-
import io
p = r"C:\Users\25750\Desktop\usmind\code\backend\src\main\java\com\usmind\service\impl\StatsServiceImpl.java"
with open(p, "r", encoding="utf-8", newline="") as f:
    c = f.read().replace("\r\n", "\n")
a = '        data.put("expireSoonVips", expireSoon);\n\n        // ---- 按天趋势 ----'
r = '''        data.put("expireSoonVips", expireSoon);

        // 兑换码会员数（会员来源标注：pay 支付 / redeem 兑换码 / admin 后台开通）
        long redeemVips = userMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SysUser>()
                .gt("member_level", 0).gt("member_expire", now).eq("member_source", "redeem"));
        java.util.List<java.util.Map<String, Object>> vipSource = jdbcTemplate.queryForList(
                "SELECT IFNULL(member_source,'') AS src, COUNT(*) AS cnt FROM sys_user WHERE member_level > 0 AND member_expire > NOW() GROUP BY member_source");
        data.put("redeemVipCount", redeemVips);
        data.put("vipSource", vipSource);

        // ---- 按天趋势 ----'''
assert a in c, "anchor"
c = c.replace(a, r)
with open(p, "w", encoding="utf-8", newline="") as f:
    f.write(c.replace("\n", "\r\n"))
print("business() patched")
