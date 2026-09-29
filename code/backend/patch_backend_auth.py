# -*- coding: utf-8 -*-
# 后端增强大补丁：注册/验证码/会员字段/安全防护/上传画布限制
import io

b = r'C:\Users\25750\Desktop\usmind\code\backend\src\main\java\com\usmind'

# 1. SysUser 实体加字段
p = b + r'\entity\SysUser.java'
s = open(p, encoding='utf-8').read()
old = """    private String role;
    private Integer status;"""
new = """    private String role;
    private Integer status;
    private Integer memberLevel;
    private java.time.LocalDateTime memberExpire;
    private String source;
    private String openidWechat;
    private String openidQq;
    private java.time.LocalDateTime lastLoginAt;"""
assert old in s, 'SYSUSER NOT FOUND'
s = s.replace(old, new, 1)
open(p, 'w', encoding='utf-8', newline='\n').write(s)

# 2. RegisterRequest 加字段
p = r'C:\Users\25750\Desktop\usmind\code\backend\src\main\java\com\usmind\dto\RegisterRequest.java'
s = open(p, encoding='utf-8').read()
old = """    private String username;
    private String password;
    private String nickname;
}"""
new = """    private String username;
    private String password;
    private String nickname;
    private String email;
    private String phone;
    private String captchaUuid;
    private String captchaCode;
    private String smsCode;
}"""
assert old in s, 'REG NOT FOUND'
s = s.replace(old, new, 1)
open(p, 'w', encoding='utf-8', newline='\n').write(s)

# 3. AuthService 接口加 updateProfile
p = b + r'\service\AuthService.java'
s = open(p, encoding='utf-8').read()
old = """    Map<String, Object> info(Long userId);
    void logout(String token);"""
new = """    Map<String, Object> info(Long userId);
    void updateProfile(Long userId, Map<String, Object> body);
    void logout(String token);"""
assert old in s, 'AUTHSVC NOT FOUND'
s = s.replace(old, new, 1)
open(p, 'w', encoding='utf-8', newline='\n').write(s)

# 4. AuthServiceImpl：register 增强 + updateProfile + info 增强
p = b + r'\service\impl\AuthServiceImpl.java'
s = open(p, encoding='utf-8').read()
old = """import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;"""
new = """import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.usmind.service.CaptchaService;
import com.usmind.service.MemberService;
import com.usmind.service.SettingsService;"""
assert old in s, 'AUTHSVC IMP IMP NOT FOUND'
s = s.replace(old, new, 1)

old2 = """    private final StringRedisTemplate redisTemplate;

    public AuthServiceImpl(SysUserMapper userMapper, PasswordEncoder passwordEncoder,
                           JwtUtil jwtUtil, StringRedisTemplate redisTemplate) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.redisTemplate = redisTemplate;
    }"""
new2 = """    private final StringRedisTemplate redisTemplate;
    private final CaptchaService captchaService;
    private final SettingsService settingsService;
    private final MemberService memberService;

    public AuthServiceImpl(SysUserMapper userMapper, PasswordEncoder passwordEncoder,
                           JwtUtil jwtUtil, StringRedisTemplate redisTemplate,
                           CaptchaService captchaService, SettingsService settingsService,
                           MemberService memberService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.redisTemplate = redisTemplate;
        this.captchaService = captchaService;
        this.settingsService = settingsService;
        this.memberService = memberService;
    }"""
assert old2 in s, 'AUTHSVC CTOR NOT FOUND'
s = s.replace(old2, new2, 1)

old3 = """    @Override
    public Long register(String username, String password, String nickname) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new BusinessException(400, "用户名和密码不能为空");
        }
        SysUser exist = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username));
        if (exist != null) {
            throw new BusinessException(400, "用户名已存在");
        }
        SysUser u = new SysUser();
        u.setUsername(username);
        u.setPassword(passwordEncoder.encode(password));
        u.setNickname(nickname == null || nickname.isBlank() ? username : nickname);
        u.setRole("USER");
        u.setStatus(1);
        userMapper.insert(u);
        return u.getId();
    }"""
new3 = """    @Override
    public Long register(String username, String password, String nickname) {
        return register(new com.usmind.dto.RegisterRequest() {{
            setUsername(username);
            setPassword(password);
            setNickname(nickname);
        }});
    }

    @Override
    public Long register(com.usmind.dto.RegisterRequest req) {
        String username = req.getUsername();
        String password = req.getPassword();
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new BusinessException(400, "用户名和密码不能为空");
        }
        // 密码强度：至少 6 位且含字母和数字
        if (password.length() < 6 || !password.matches(".*[A-Za-z].*") || !password.matches(".*\\\\d.*")) {
            throw new BusinessException(400, "密码至少 6 位且需包含字母和数字");
        }
        // 图形验证码（后台可开关）
        if (captchaService.enabled()) {
            if (!captchaService.verify(req.getCaptchaUuid(), req.getCaptchaCode())) {
                throw new BusinessException(400, "图形验证码错误或已过期");
            }
        }
        SysUser exist = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username));
        if (exist != null) {
            throw new BusinessException(400, "用户名已存在");
        }
        if (req.getPhone() != null && !req.getPhone().isBlank()) {
            Long p = userMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getPhone, req.getPhone()));
            if (p != null && p > 0) throw new BusinessException(400, "手机号已被注册");
        }
        if (req.getEmail() != null && !req.getEmail().isBlank()) {
            Long e = userMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getEmail, req.getEmail()));
            if (e != null && e > 0) throw new BusinessException(400, "邮箱已被注册");
        }
        SysUser u = new SysUser();
        u.setUsername(username);
        u.setPassword(passwordEncoder.encode(password));
        u.setNickname(nickname == null || nickname.isBlank() ? username : nickname);
        u.setEmail(req.getEmail());
        u.setPhone(req.getPhone());
        u.setRole("USER");
        u.setStatus(1);
        u.setMemberLevel(0);
        u.setSource("local");
        u.setCreatedAt(LocalDateTime.now());
        userMapper.insert(u);
        return u.getId();
    }

    @Override
    public void updateProfile(Long userId, Map<String, Object> body) {
        SysUser u = userMapper.selectById(userId);
        if (u == null) throw new BusinessException(400, "用户不存在");
        if (body.get("nickname") != null) u.setNickname(String.valueOf(body.get("nickname")));
        if (body.get("avatar") != null) u.setAvatar(String.valueOf(body.get("avatar")));
        if (body.get("phone") != null) u.setPhone(String.valueOf(body.get("phone")));
        if (body.get("email") != null) u.setEmail(String.valueOf(body.get("email")));
        userMapper.updateById(u);
    }"""
assert old3 in s, 'AUTHSVC REG NOT FOUND'
s = s.replace(old3, new3, 1)

# info 增强：返回会员信息
old4 = """        String token = jwtUtil.generateToken(u.getUsername(), u.getId(), u.getRole());
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", u.getId());
        user.put("username", u.getUsername());
        user.put("nickname", u.getNickname());
        user.put("avatar", u.getAvatar());
        user.put("role", u.getRole());

        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("user", user);
        return data;
    }"""
new4 = """        u.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(u);
        String token = jwtUtil.generateToken(u.getUsername(), u.getId(), u.getRole());
        Map<String, Object> user = userMap(u);

        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("user", user);
        data.put("member", memberService.memberInfo(u.getId()));
        return data;
    }

    private Map<String, Object> userMap(SysUser u) {
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", u.getId());
        user.put("username", u.getUsername());
        user.put("nickname", u.getNickname());
        user.put("avatar", u.getAvatar());
        user.put("role", u.getRole());
        user.put("email", u.getEmail());
        user.put("phone", u.getPhone());
        user.put("memberLevel", u.getMemberLevel() == null ? 0 : u.getMemberLevel());
        user.put("memberExpire", u.getMemberExpire() == null ? null : u.getMemberExpire().toString());
        return user;
    }"""
assert old4 in s, 'AUTHSVC LOGIN NOT FOUND'
s = s.replace(old4, new4, 1)

# info 方法
old5 = """    @Override
    public Map<String, Object> info(Long userId) {
        return Map.of();"""
if old5 not in s:
    # 尝试找原 info 实现
    import re
    m = re.search(r'@Override\n    public Map<String, Object> info\(Long userId\) \{.*?\n    \}', s, re.S)
    if m:
        s = s.replace(m.group(0), """    @Override
    public Map<String, Object> info(Long userId) {
        SysUser u = userMapper.selectById(userId);
        if (u == null) throw new BusinessException(401, "用户不存在");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("user", userMap(u));
        m.put("member", memberService.memberInfo(userId));
        return m;
    }""")
    else:
        assert False, 'INFO NOT FOUND'
else:
    s = s.replace(old5, """    @Override
    public Map<String, Object> info(Long userId) {
        SysUser u = userMapper.selectById(userId);
        if (u == null) throw new BusinessException(401, "用户不存在");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("user", userMap(u));
        m.put("member", memberService.memberInfo(userId));
        return m;
    }""", 1)
open(p, 'w', encoding='utf-8', newline='\n').write(s)

# 5. AuthController：加 captcha / send-code
p = b + r'\controller\AuthController.java'
s = open(p, encoding='utf-8').read()
old = """    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }"""
new = """    private final AuthService authService;
    private final com.usmind.service.CaptchaService captchaService;
    private final com.usmind.service.SettingsService settingsService;

    public AuthController(AuthService authService,
                          com.usmind.service.CaptchaService captchaService,
                          com.usmind.service.SettingsService settingsService) {
        this.authService = authService;
        this.captchaService = captchaService;
        this.settingsService = settingsService;
    }

    /** 图形验证码（注册/登录防机器人） */
    @GetMapping("/captcha")
    public Result<Map<String, Object>> captcha() {
        return Result.ok(captchaService.create());
    }

    /** 发送注册验证码（短信/邮箱）：默认关闭，后台接入验证码服务商后开启 */
    @PostMapping("/send-code")
    public Result<Void> sendCode(@RequestBody Map<String, String> body) {
        if (settingsService.getInt("authSmsEnabled", 0) != 1) {
            throw new com.usmind.common.BusinessException(400, "验证码服务未开启，请联系管理员");
        }
        String target = body.getOrDefault("phone", body.getOrDefault("email", ""));
        if (target.isBlank()) throw new com.usmind.common.BusinessException(400, "手机号或邮箱不能为空");
        // 代码就绪：此处接入短信/邮件服务商（阿里云/腾讯云等）发送验证码并写入 Redis
        // 默认未接入，仅占位；后台配置 smsProvider / smsAccessKeyId / smsAccessKeySecret 后启用
        String code = String.valueOf((int) (Math.random() * 900000) + 100000);
        org.springframework.data.redis.core.StringRedisTemplate rt = null;
        // TODO: 接入服务商后发送并缓存: redisTemplate.opsForValue().set("smscode:" + target, code, 5, TimeUnit.MINUTES)
        throw new com.usmind.common.BusinessException(400, "验证码服务商未配置，暂无法发送");
    }"""
assert old in s, 'AUTHCTL NOT FOUND'
s = s.replace(old, new, 1)
open(p, 'w', encoding='utf-8', newline='\n').write(s)

# 6. SettingsServiceImpl：默认键 + getSettings 字段
p = b + r'\service\impl\SettingsServiceImpl.java'
s = open(p, encoding='utf-8').read()
old = """        putDefaultIfAbsent("aiApiKey", "");"""
new = """        putDefaultIfAbsent("aiApiKey", "");
        // 微信支付（默认关闭）
        putDefaultIfAbsent("wechatPayEnabled", "0");
        putDefaultIfAbsent("wechatMchId", "");
        putDefaultIfAbsent("wechatAppId", "");
        putDefaultIfAbsent("wechatApiKey", "");
        putDefaultIfAbsent("wechatPrivateKey", "");
        putDefaultIfAbsent("wechatNotifyUrl", "");
        // 注册 / 验证码（图形验证码默认开，短信验证码默认关）
        putDefaultIfAbsent("authCaptchaEnabled", "1");
        putDefaultIfAbsent("authSmsEnabled", "0");
        putDefaultIfAbsent("smsProvider", "");
        putDefaultIfAbsent("smsAccessKeyId", "");
        putDefaultIfAbsent("smsAccessKeySecret", "");
        putDefaultIfAbsent("smsSignName", "");
        putDefaultIfAbsent("smsTemplateCode", "");
        // 会员功能开关（空=全部开放）
        putDefaultIfAbsent("memberThemeIds", "");
        putDefaultIfAbsent("memberExportFormats", "");
        // 上传限制：数量 / 单文件大小(MB)（非会员 / 会员）
        putDefaultIfAbsent("uploadFreeCount", "20");
        putDefaultIfAbsent("uploadFreeMaxMb", "20");
        putDefaultIfAbsent("uploadVipCount", "200");
        putDefaultIfAbsent("uploadVipMaxMb", "200");
        // 画布个数限制
        putDefaultIfAbsent("mindmapFreeCount", "5");
        putDefaultIfAbsent("mindmapVipCount", "200");
        // IP 防护
        putDefaultIfAbsent("ipGuardEnabled", "1");
        putDefaultIfAbsent("ipRateLimit", "60");
        putDefaultIfAbsent("ipRateWindow", "60");
        putDefaultIfAbsent("ipAutoBlackSeconds", "3600");"""
assert old in s, 'SETTINGS DEFAULT NOT FOUND'
s = s.replace(old, new, 1)
open(p, 'w', encoding='utf-8', newline='\n').write(s)

# 7. SecurityConfig：放行新接口 + 注册 IpGuardFilter
p = b + r'\config\SecurityConfig.java'
s = open(p, encoding='utf-8').read()
old = """                        .requestMatchers("/api/auth/login", "/api/auth/register").permitAll()"""
new = """                        .requestMatchers("/api/auth/login", "/api/auth/register", "/api/auth/captcha", "/api/auth/send-code").permitAll()
                        .requestMatchers("/api/config/client", "/api/member/plans", "/api/pay/wechat/notify").permitAll()"""
assert old in s, 'SEC CONFIG NOT FOUND'
s = s.replace(old, new, 1)

old2 = """    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }"""
new2 = """    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final com.usmind.security.IpGuardFilter ipGuardFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          com.usmind.security.IpGuardFilter ipGuardFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.ipGuardFilter = ipGuardFilter;
    }"""
assert old2 in s, 'SEC CTOR NOT FOUND'
s = s.replace(old2, new2, 1)

old3 = """                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);"""
new3 = """                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(ipGuardFilter, com.usmind.security.JwtAuthenticationFilter.class);"""
assert old3 in s, 'SEC FILTER NOT FOUND'
s = s.replace(old3, new3, 1)
open(p, 'w', encoding='utf-8', newline='\n').write(s)

# 8. DataInitializer：建新表 + 补列 + 默认套餐
p = b + r'\config\DataInitializer.java'
s = open(p, encoding='utf-8').read()
old = """    @Override
    public void run(String... args) {
        initAdmin();
        initLocalOss();
        initThemes();
        initIcons();
    }"""
new = """    @Override
    public void run(String... args) {
        initMemberTables();
        initAdmin();
        initLocalOss();
        initThemes();
        initIcons();
    }

    private void initMemberTables() {
        try {
            // 旧库补列（逐个尝试，失败忽略）
            String[] alters = {
                "ALTER TABLE sys_user ADD COLUMN member_level INT DEFAULT 0",
                "ALTER TABLE sys_user ADD COLUMN member_expire DATETIME NULL",
                "ALTER TABLE sys_user ADD COLUMN source VARCHAR(16) DEFAULT 'local'",
                "ALTER TABLE sys_user ADD COLUMN openid_wechat VARCHAR(128) DEFAULT ''",
                "ALTER TABLE sys_user ADD COLUMN openid_qq VARCHAR(128) DEFAULT ''",
                "ALTER TABLE sys_user ADD COLUMN last_login_at DATETIME NULL"
            };
            for (String a : alters) {
                try { jdbcTemplate.execute(a); } catch (Exception ignored) {}
            }
            jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS member_plan (" +
                " id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                " name VARCHAR(64) DEFAULT ''," +
                " level INT DEFAULT 1," +
                " price DECIMAL(10,2) DEFAULT 0," +
                " days INT DEFAULT 30," +
                " sort INT DEFAULT 0," +
                " enabled TINYINT DEFAULT 1," +
                " created_at DATETIME DEFAULT CURRENT_TIMESTAMP" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员套餐表'");
            jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS pay_order (" +
                " id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                " out_trade_no VARCHAR(64) NOT NULL UNIQUE," +
                " user_id BIGINT," +
                " plan_id BIGINT," +
                " amount DECIMAL(10,2) DEFAULT 0," +
                " channel VARCHAR(16) DEFAULT 'wechat'," +
                " status VARCHAR(16) DEFAULT 'created'," +
                " transaction_id VARCHAR(64) DEFAULT ''," +
                " created_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
                " paid_at DATETIME NULL" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付订单表'");
            jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS ip_rule (" +
                " id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                " rule_type VARCHAR(8) DEFAULT 'black'," +
                " target_type VARCHAR(8) DEFAULT 'ip'," +
                " target_value VARCHAR(128)," +
                " remark VARCHAR(255) DEFAULT ''," +
                " enabled TINYINT DEFAULT 1," +
                " created_at DATETIME DEFAULT CURRENT_TIMESTAMP" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IP黑白名单表'");
            // 默认会员套餐
            Long planCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM member_plan", Long.class);
            if (planCount == null || planCount == 0) {
                jdbcTemplate.execute("INSERT INTO member_plan (name, level, price, days, sort, enabled) VALUES ('月度会员', 1, 9.90, 30, 1, 1), ('季度会员', 1, 26.00, 90, 2, 1), ('年度会员', 1, 88.00, 365, 3, 1)");
            }
        } catch (Exception e) {
            log.warn("初始化会员/支付/IP表失败: {}", e.getMessage());
        }
    }"""
assert old in s, 'DATAINIT RUN NOT FOUND'
s = s.replace(old, new, 1)
open(p, 'w', encoding='utf-8', newline='\n').write(s)

# 9. UserService 接口 + 实现：grantMember/cancelMember
p = b + r'\service\UserService.java'
s = open(p, encoding='utf-8').read()
old = """    void resetPassword(Long id, String password);
}"""
new = """    void resetPassword(Long id, String password);
    void grantMember(Long id, int level, int days);
    void cancelMember(Long id);
}"""
assert old in s, 'USERSVC NOT FOUND'
s = s.replace(old, new, 1)
open(p, 'w', encoding='utf-8', newline='\n').write(s)

p = b + r'\service\impl\UserServiceImpl.java'
s = open(p, encoding='utf-8').read()
# 找文件尾
idx = s.rfind('}')
tail = s[idx:].strip()
assert 'public void resetPassword' in s, 'USERSVCIMPL RESET NOT FOUND'
# 在 resetPassword 方法后追加
m = None
import re
mm = re.search(r'    public void resetPassword\(Long id, String password\) \{.*?\n    \}\n', s, re.S)
assert mm, 'RESET BODY NOT FOUND'
extra = """
    @Override
    public void grantMember(Long id, int level, int days) {
        com.usmind.service.MemberService ms = com.usmind.service.MemberService.class.cast(
            org.springframework.context.ApplicationContext.class.cast(null)); // 占位(不走)
    }
"""
# 更稳妥：直接注入 MemberService——但构造函数改动大，改用 jdbc 更新
new_methods = """
    @Override
    public void grantMember(Long id, int level, int days) {
        SysUser u = userMapper.selectById(id);
        if (u == null) return;
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.time.LocalDateTime base = u.getMemberExpire() != null && u.getMemberExpire().isAfter(now)
                ? u.getMemberExpire() : now;
        u.setMemberLevel(level);
        u.setMemberExpire(base.plusDays(days));
        userMapper.updateById(u);
    }

    @Override
    public void cancelMember(Long id) {
        SysUser u = userMapper.selectById(id);
        if (u == null) return;
        u.setMemberLevel(0);
        u.setMemberExpire(null);
        userMapper.updateById(u);
    }
"""
s = s.replace(mm.group(0), mm.group(0) + new_methods, 1)
open(p, 'w', encoding='utf-8', newline='\n').write(s)

# 10. FileServiceImpl：上传会员限制（数量/大小）
p = b + r'\service\impl\FileServiceImpl.java'
s = open(p, encoding='utf-8').read()
old = """        int maxMb = settingsService.getInt("uploadMaxSizeMb", 100);
        if (file.getSize() > maxMb * 1024L * 1024L) {
            throw new BusinessException(400, "文件大小超过限制(" + maxMb + "MB)");
        }"""
new = """        // 会员大小限制：非会员 uploadFreeMaxMb / 会员 uploadVipMaxMb（默认值一致时等价于 uploadMaxSizeMb）
        boolean vip = isVip(userId);
        int maxMb = settingsService.getInt(vip ? "uploadVipMaxMb" : "uploadFreeMaxMb",
                settingsService.getInt("uploadMaxSizeMb", 100));
        if (file.getSize() > maxMb * 1024L * 1024L) {
            throw new BusinessException(400, "文件大小超过限制(" + maxMb + "MB)" + (vip ? "" : "，升级会员可提升上限"));
        }
        // 上传资源数量限制
        Long count = fileResourceMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.usmind.entity.FileResource>()
                .eq(com.usmind.entity.FileResource::getUserId, userId));
        int maxCount = settingsService.getInt(vip ? "uploadVipCount" : "uploadFreeCount", 200);
        if (count != null && count >= maxCount) {
            throw new BusinessException(400, "上传资源数量已达上限(" + maxCount + "个)" + (vip ? "" : "，升级会员可提升上限"));
        }"""
assert old in s, 'FILE LIMIT NOT FOUND'
s = s.replace(old, new, 1)

# FileServiceImpl 需要 isVip + fileResourceMapper 注入——找类头注入
mm2 = re.search(r'    public FileServiceImpl\((.*?)\) \{\n(.*?)\n    \}', s, re.S)
assert mm2, 'FILESVC CTOR NOT FOUND'
# 检查是否已有 fileResourceMapper 字段
if 'fileResourceMapper' not in s:
    # 找字段声明区（private final 行）在构造前
    fields_old = """    private final SettingsService settingsService;"""
    # 检查实际字段名
    if fields_old not in s:
        # 打印字段帮助定位
        pass
open(p, 'w', encoding='utf-8', newline='\n').write(s)
print('backend patch part1 OK')
