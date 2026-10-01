# -*- coding: utf-8 -*-
import io

base = r"C:\Users\25750\Desktop\usmind\code\backend\src\main\java\com\usmind"
nl = "\r\n"

def patch(path, pairs, must=True):
    p = base + "\\" + path
    with open(p, "r", encoding="utf-8", newline="") as f:
        c = f.read().replace("\r\n", "\n")
    for a, r in pairs:
        if a in c:
            c = c.replace(a, r)
        elif must:
            print("MISS:", path, "->", a[:80].replace("\r", "\\r").replace("\n", "\\n"))
            return False
    with open(p, "w", encoding="utf-8", newline="") as f:
        f.write(c.replace("\n", "\r\n"))
    print("OK:", path)
    return True

# ============ 1. MemberService interface: add redeem ============
patch(r"service\MemberService.java", [
    ('    /** 开通会员（支付回调或后台手动开通调用） */\n    void grant(Long userId, Integer level, int days);\n\n    void cancel(Long userId);\n}',
     '    /** 开通会员（支付回调或后台手动开通调用） */\n    void grant(Long userId, Integer level, int days);\n\n    void cancel(Long userId);\n\n    /** 兑换码兑换会员：校验码并开通，成功返回用户会员信息 */\n    Map<String, Object> redeem(Long userId, String code);\n}'),
])

# ============ 2. MemberServiceImpl ============
p = base + r"\service\impl\MemberServiceImpl.java"
with open(p, "r", encoding="utf-8", newline="") as f:
    c = f.read().replace("\r\n", "\n")

# 2a. inject RedeemCodeMapper field + constructor param
a = '    private final SysUserMapper userMapper;\n    private final MemberPlanMapper planMapper;\n    private final SettingsService settingsService;\n\n    public MemberServiceImpl(SysUserMapper userMapper, MemberPlanMapper planMapper,\n                             SettingsService settingsService) {\n        this.userMapper = userMapper;\n        this.planMapper = planMapper;\n        this.settingsService = settingsService;\n    }'
r = '    private final SysUserMapper userMapper;\n    private final MemberPlanMapper planMapper;\n    private final com.usmind.mapper.RedeemCodeMapper redeemCodeMapper;\n    private final SettingsService settingsService;\n\n    public MemberServiceImpl(SysUserMapper userMapper, MemberPlanMapper planMapper,\n                             com.usmind.mapper.RedeemCodeMapper redeemCodeMapper,\n                             SettingsService settingsService) {\n        this.userMapper = userMapper;\n        this.planMapper = planMapper;\n        this.redeemCodeMapper = redeemCodeMapper;\n        this.settingsService = settingsService;\n    }'
assert a in c, "ctor"
c = c.replace(a, r)

# 2b. plans add fields
a = '            m.put("price", p.getPrice());\n            m.put("days", p.getDays());\n            out.add(m);'
r = '            m.put("price", p.getPrice());\n            m.put("originalPrice", p.getOriginalPrice());\n            m.put("discountPercent", p.getDiscountPercent());\n            m.put("days", p.getDays());\n            out.add(m);'
assert a in c, "plans"
c = c.replace(a, r)

# 2c. memberInfo add source
a = '        m.put("expire", u == null || u.getMemberExpire() == null ? null : u.getMemberExpire().toString());\n        m.put("feature", featureConfig());'
r = '        m.put("expire", u == null || u.getMemberExpire() == null ? null : u.getMemberExpire().toString());\n        m.put("source", u == null || u.getMemberSource() == null ? "" : u.getMemberSource());\n        m.put("feature", featureConfig());'
assert a in c, "memberInfo"
c = c.replace(a, r)

# 2d. grant set source=pay
a = '        u.setMemberLevel(level);\n        u.setMemberExpire(base.plusDays(days));\n        userMapper.updateById(u);\n    }\n\n    @Override\n    public void cancel(Long userId) {'
r = '        u.setMemberLevel(level);\n        u.setMemberExpire(base.plusDays(days));\n        u.setMemberSource("pay");\n        userMapper.updateById(u);\n    }\n\n    @Override\n    public void cancel(Long userId) {'
assert a in c, "grant"
c = c.replace(a, r)

# 2e. cancel clear source + append redeem method
a = '        u.setMemberLevel(0);\n        u.setMemberExpire(null);\n        userMapper.updateById(u);\n    }\n}'
r = '''        u.setMemberLevel(0);
        u.setMemberExpire(null);
        u.setMemberSource("");
        userMapper.updateById(u);
    }

    @Override
    public Map<String, Object> redeem(Long userId, String code) {
        if (code == null || code.isBlank()) throw new BusinessException(400, "请输入兑换码");
        com.usmind.entity.RedeemCode target = redeemCodeMapper.selectOne(
                new LambdaQueryWrapper<com.usmind.entity.RedeemCode>()
                        .eq(com.usmind.entity.RedeemCode::getCode, code.trim().toUpperCase())
                        .eq(com.usmind.entity.RedeemCode::getStatus, "active")
                        .last("LIMIT 1"));
        if (target == null) throw new BusinessException(400, "兑换码不存在或已失效");
        SysUser u = userMapper.selectById(userId);
        if (u == null) throw new BusinessException(400, "用户不存在");
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime base = u.getMemberExpire() != null && u.getMemberExpire().isAfter(now)
                ? u.getMemberExpire() : now;
        int level = target.getLevel() == null ? 1 : target.getLevel();
        int days = target.getDays() == null ? 30 : target.getDays();
        u.setMemberLevel(level);
        u.setMemberExpire(base.plusDays(days));
        u.setMemberSource("redeem");
        userMapper.updateById(u);
        target.setStatus("used");
        target.setUsedBy(userId);
        target.setUsedAt(now);
        redeemCodeMapper.updateById(target);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("isVip", true);
        out.put("level", level);
        out.put("expire", u.getMemberExpire().toString());
        out.put("source", "redeem");
        out.put("days", days);
        return out;
    }
}'''
assert a in c, "redeem-place"
c = c.replace(a, r)

with open(p, "w", encoding="utf-8", newline="") as f:
    f.write(c.replace("\n", "\r\n"))
print("OK: MemberServiceImpl")

# ============ 3. MemberController: add redeem endpoint ============
patch(r"controller\MemberController.java", [
    ('    @GetMapping("/info")',
     '    @PostMapping("/redeem")\n    public Result<Map<String, Object>> redeem(@RequestBody Map<String, String> body) {\n        return Result.ok(memberService.redeem(SecurityUtil.currentUserId(), body.get("code")));\n    }\n\n    @GetMapping("/info")'),
])
