# -*- coding: utf-8 -*-
import io, sys

p = r"C:\Users\25750\Desktop\usmind\code\backend\src\main\java\com\usmind\config\DataInitializer.java"
with open(p, "r", encoding="utf-8", newline="") as f:
    c = f.read()
nl = "\r\n"
ok = []

# 1) sys_user alters add member_source
a1 = ('                "ALTER TABLE sys_user ADD COLUMN last_login_at DATETIME NULL",' + nl +
      '                "ALTER TABLE sys_user ADD COLUMN avatar VARCHAR(512) DEFAULT \'\'"')
r1 = ('                "ALTER TABLE sys_user ADD COLUMN last_login_at DATETIME NULL",' + nl +
      '                "ALTER TABLE sys_user ADD COLUMN avatar VARCHAR(512) DEFAULT \'\'",' + nl +
      '                "ALTER TABLE sys_user ADD COLUMN member_source VARCHAR(16) DEFAULT \'\'"')
if a1 in c:
    c = c.replace(a1, r1); ok.append("user-source")
else:
    print("MISS1")

# 2) member_plan create table add columns
a2 = ('                " price DECIMAL(10,2) DEFAULT 0," +' + nl +
      '                " days INT DEFAULT 30," +')
r2 = ('                " price DECIMAL(10,2) DEFAULT 0," +' + nl +
      '                " original_price DECIMAL(10,2) DEFAULT 0," +' + nl +
      '                " discount_percent INT DEFAULT 0," +' + nl +
      '                " days INT DEFAULT 30," +')
if a2 in c:
    c = c.replace(a2, r2); ok.append("plan-cols")
else:
    print("MISS2")

# 3) after plan seed: migrate + redeem_code table
a3 = ('                jdbcTemplate.execute("INSERT INTO member_plan (name, level, price, days, sort, enabled) VALUES '
      "('月度会员', 1, 9.90, 30, 1, 1), ('季度会员', 1, 26.00, 90, 2, 1), ('年度会员', 1, 88.00, 365, 3, 1)\");")
r3 = (a3 + nl +
      '            // migrate: member_plan add original_price / discount_percent columns (new tables already have them)' + nl +
      '            try {' + nl +
      '                Integer p1 = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = \'member_plan\' AND column_name = \'original_price\'", Integer.class);' + nl +
      '                if (p1 == null || p1 == 0) jdbcTemplate.execute("ALTER TABLE member_plan ADD COLUMN original_price DECIMAL(10,2) DEFAULT 0 AFTER price");' + nl +
      '                Integer p2 = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = \'member_plan\' AND column_name = \'discount_percent\'", Integer.class);' + nl +
      '                if (p2 == null || p2 == 0) jdbcTemplate.execute("ALTER TABLE member_plan ADD COLUMN discount_percent INT DEFAULT 0 AFTER original_price");' + nl +
      '            } catch (Exception e) { log.warn("member_plan migrate price cols failed: {}", e.getMessage()); }' + nl +
      '            // 会员兑换码表' + nl +
      '            jdbcTemplate.execute(' + nl +
      '                "CREATE TABLE IF NOT EXISTS redeem_code (" +' + nl +
      '                " id BIGINT AUTO_INCREMENT PRIMARY KEY," +' + nl +
      '                " code VARCHAR(32) NOT NULL UNIQUE," +' + nl +
      '                " plan_id BIGINT," +' + nl +
      '                " days INT DEFAULT 30," +' + nl +
      '                " level INT DEFAULT 1," +' + nl +
      '                " status VARCHAR(16) DEFAULT \'active\'," +' + nl +
      '                " used_by BIGINT," +' + nl +
      '                " used_at DATETIME NULL," +' + nl +
      '                " remark VARCHAR(255) DEFAULT \'\'," +' + nl +
      '                " created_by BIGINT," +' + nl +
      '                " created_at DATETIME DEFAULT CURRENT_TIMESTAMP" +' + nl +
      '                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT=\'会员兑换码表\'");')
if a3 in c:
    c = c.replace(a3, r3); ok.append("redeem-table")
else:
    print("MISS3")

with open(p, "w", encoding="utf-8", newline="") as f:
    f.write(c)
print("done: " + ", ".join(ok))
