# -*- coding: utf-8 -*-
import io

p = r"C:\Users\25750\Desktop\usmind\code\backend\src\main\java\com\usmind\service\impl\StatsServiceImpl.java"
with open(p, "r", encoding="utf-8", newline="") as f:
    c = f.read().replace("\r\n", "\n")
ok = []

# 1) import JdbcTemplate
a = "import org.springframework.stereotype.Service;"
r = "import org.springframework.jdbc.core.JdbcTemplate;\nimport org.springframework.stereotype.Service;"
if a in c:
    c = c.replace(a, r); ok.append("import")
else:
    print("MISS-import")

# 2) field + ctor
a = "    private final com.usmind.mapper.MemberPlanMapper planMapper;\n\n    public StatsServiceImpl(SysUserMapper userMapper, MindMapMapper mindMapMapper,\n                            FileResourceMapper fileResourceMapper, OssConfigMapper ossConfigMapper,\n                            com.usmind.mapper.PayOrderMapper payOrderMapper,\n                            com.usmind.mapper.MemberPlanMapper planMapper) {\n        this.userMapper = userMapper;\n        this.mindMapMapper = mindMapMapper;\n        this.fileResourceMapper = fileResourceMapper;\n        this.ossConfigMapper = ossConfigMapper;\n        this.payOrderMapper = payOrderMapper;\n        this.planMapper = planMapper;\n    }"
r = "    private final com.usmind.mapper.MemberPlanMapper planMapper;\n    private final JdbcTemplate jdbcTemplate;\n\n    public StatsServiceImpl(SysUserMapper userMapper, MindMapMapper mindMapMapper,\n                            FileResourceMapper fileResourceMapper, OssConfigMapper ossConfigMapper,\n                            com.usmind.mapper.PayOrderMapper payOrderMapper,\n                            com.usmind.mapper.MemberPlanMapper planMapper,\n                            JdbcTemplate jdbcTemplate) {\n        this.userMapper = userMapper;\n        this.mindMapMapper = mindMapMapper;\n        this.fileResourceMapper = fileResourceMapper;\n        this.ossConfigMapper = ossConfigMapper;\n        this.payOrderMapper = payOrderMapper;\n        this.planMapper = planMapper;\n        this.jdbcTemplate = jdbcTemplate;\n    }"
if a in c:
    c = c.replace(a, r); ok.append("ctor")
else:
    print("MISS-ctor")

# 3) dashboard add member stats before storage
a = "        // 存储信息\n        OssConfig active = ossConfigMapper.selectOne(new LambdaQueryWrapper<OssConfig>()"
r = """        // 会员统计（有效期内的会员，按来源标注：pay 支付 / redeem 兑换码 / admin 后台）
        data.put("vipCount", userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .gt(SysUser::getMemberLevel, 0)
                .gt(SysUser::getMemberExpire, LocalDateTime.now())));
        data.put("redeemVipCount", userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .gt(SysUser::getMemberLevel, 0)
                .gt(SysUser::getMemberExpire, LocalDateTime.now())
                .eq(SysUser::getMemberSource, "redeem")));
        java.util.List<java.util.Map<String, Object>> vipSource = jdbcTemplate.queryForList(
                "SELECT IFNULL(member_source,'') AS src, COUNT(*) AS cnt FROM sys_user WHERE member_level > 0 AND member_expire > NOW() GROUP BY member_source");
        data.put("vipSource", vipSource);

        // 存储信息
        OssConfig active = ossConfigMapper.selectOne(new LambdaQueryWrapper<OssConfig>()"""
if a in c:
    c = c.replace(a, r); ok.append("dashboard")
else:
    print("MISS-dashboard")

with open(p, "w", encoding="utf-8", newline="") as f:
    f.write(c.replace("\n", "\r\n"))
print("done: " + ", ".join(ok))
