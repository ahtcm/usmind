# -*- coding: utf-8 -*-
import io

p = r"C:\Users\25750\Desktop\usmind\code\backend\src\main\java\com\usmind\config\DataInitializer.java"
with open(p, "r", encoding="utf-8", newline="") as f:
    c = f.read().replace("\r\n", "\n")

# locate the whole broken region: from "// 默认会员套餐" to the closing "}" of the if block (before "\n        } catch (Exception e) {")
start = c.find("            // 默认会员套餐")
if start < 0:
    print("MISS start"); raise SystemExit
end_marker = "\n        } catch (Exception e) {\n            log.warn(\"初始化会员/支付/IP表失败"
end = c.find(end_marker, start)
if end < 0:
    print("MISS end"); raise SystemExit

new_block = """            // 默认会员套餐
            Long planCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM member_plan", Long.class);
            if (planCount == null || planCount == 0) {
                jdbcTemplate.execute("INSERT INTO member_plan (name, level, price, original_price, discount_percent, days, sort, enabled) VALUES ('月度会员', 1, 9.90, 0, 0, 30, 1, 1), ('季度会员', 1, 26.00, 0, 0, 90, 2, 1), ('年度会员', 1, 88.00, 0, 0, 365, 3, 1)");
            }
            // migrate: member_plan add original_price / discount_percent columns (new tables already have them)
            try {
                Integer p1 = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'member_plan' AND column_name = 'original_price'", Integer.class);
                if (p1 == null || p1 == 0) jdbcTemplate.execute("ALTER TABLE member_plan ADD COLUMN original_price DECIMAL(10,2) DEFAULT 0 AFTER price");
                Integer p2 = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'member_plan' AND column_name = 'discount_percent'", Integer.class);
                if (p2 == null || p2 == 0) jdbcTemplate.execute("ALTER TABLE member_plan ADD COLUMN discount_percent INT DEFAULT 0 AFTER original_price");
            } catch (Exception e) { log.warn("member_plan migrate price cols failed: {}", e.getMessage()); }
            // 会员兑换码表
            jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS redeem_code (" +
                " id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                " code VARCHAR(32) NOT NULL UNIQUE," +
                " plan_id BIGINT," +
                " days INT DEFAULT 30," +
                " level INT DEFAULT 1," +
                " status VARCHAR(16) DEFAULT 'active'," +
                " used_by BIGINT," +
                " used_at DATETIME NULL," +
                " remark VARCHAR(255) DEFAULT ''," +
                " created_by BIGINT," +
                " created_at DATETIME DEFAULT CURRENT_TIMESTAMP" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员兑换码表'");"""

c = c[:start] + new_block + c[end:]
with open(p, "w", encoding="utf-8", newline="") as f:
    f.write(c.replace("\n", "\r\n"))
print("DataInitializer region rewritten")
