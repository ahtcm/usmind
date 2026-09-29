# -*- coding: utf-8 -*-
# 综合补丁：头像上传(免登录) + 微信扫码登录(默认关闭,后台可配)
import io, sys

base = r'C:\Users\25750\Desktop\usmind\code\backend\src\main\java\com\usmind'

def patch(path, pairs):
    p = base + path
    s = io.open(p, encoding='utf-8').read()
    for old, new in pairs:
        if old not in s:
            print('NOT FOUND in', path, '::', old[:80]); sys.exit(1)
        s = s.replace(old, new, 1)
    io.open(p, 'w', encoding='utf-8', newline='\n').write(s)
    print('OK', path)

# 1) RegisterRequest 加 avatar
patch(r'\dto\RegisterRequest.java', [(
"""    private String captchaCode;
    private String smsCode;
}""",
"""    private String captchaCode;
    private String smsCode;
    private String avatar;
}""")])

# 2) AuthService 接口加方法
patch(r'\service\AuthService.java', [(
"""    void logout(String token);""",
"""    void logout(String token);
    Map<String, Object> wechatUrl();
    Map<String, Object> wechatCallback(String code, String state);""")])

# 3) AuthServiceImpl：imports + register avatar + 微信登录方法
patch(r'\service\impl\AuthServiceImpl.java', [
(
"""import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;""",
"""import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;"""),
(
"""        u.setSource("local");
        u.setCreatedAt(LocalDateTime.now());""",
"""        u.setSource("local");
        if (req.getAvatar() != null && !req.getAvatar().isBlank()) u.setAvatar(req.getAvatar());
        u.setCreatedAt(LocalDateTime.now());"""),
(
"""    @Override
    public void logout(String token) {""",
"""    @Override
    public Map<String, Object> wechatUrl() {
        if (settingsService.getInt("wechatLoginEnabled", 0) != 1) {
            throw new BusinessException(400, "微信登录未开启");
        }
        String appid = settingsService.get("wechatLoginAppId", "");
        String redirect = settingsService.get("wechatLoginRedirectUri", "");
        if (appid.isBlank() || redirect.isBlank()) {
            throw new BusinessException(400, "微信登录未配置 AppID 或回调地址");
        }
        String state = UUID.randomUUID().toString().replace("-", "");
        redisTemplate.opsForValue().set("wxlogin:state:" + state, "1", 10, TimeUnit.MINUTES);
        String url = "https://open.weixin.qq.com/connect/qrconnect?appid=" + appid
                + "&redirect_uri=" + URLEncoder.encode(redirect, StandardCharsets.UTF_8)
                + "&response_type=code&scope=snsapi_login&state=" + state + "#wechat_redirect";
        Map<String, Object> m = new HashMap<>();
        m.put("url", url);
        m.put("state", state);
        return m;
    }

    @Override
    public Map<String, Object> wechatCallback(String code, String state) {
        if (settingsService.getInt("wechatLoginEnabled", 0) != 1) {
            throw new BusinessException(400, "微信登录未开启");
        }
        if (code == null || code.isBlank() || state == null || state.isBlank()) {
            throw new BusinessException(400, "微信登录参数缺失");
        }
        String skey = "wxlogin:state:" + state;
        String saved = redisTemplate.opsForValue().get(skey);
        if (saved == null) throw new BusinessException(400, "登录状态已过期，请重新扫码");
        redisTemplate.delete(skey);
        String appid = settingsService.get("wechatLoginAppId", "");
        String secret = settingsService.get("wechatLoginSecret", "");
        if (appid.isBlank() || secret.isBlank()) throw new BusinessException(400, "微信登录未配置");
        String tokenUrl = "https://api.weixin.qq.com/sns/oauth2/access_token?appid=" + appid
                + "&secret=" + secret + "&code=" + code + "&grant_type=authorization_code";
        String json = httpGet(tokenUrl);
        String openid = jsonField(json, "openid");
        if (openid == null || openid.isBlank()) {
            String em = jsonField(json, "errmsg");
            throw new BusinessException(400, "微信授权失败" + (em.isBlank() ? "" : "：" + em));
        }
        SysUser u = userMapper.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getOpenidWechat, openid));
        boolean created = false;
        if (u == null) {
            u = new SysUser();
            u.setUsername("wx_" + openid.substring(0, Math.min(8, openid.length())));
            u.setPassword(passwordEncoder.encode(UUID.randomUUID().toString().replace("-", "").substring(0, 12)));
            u.setNickname("微信用户" + openid.substring(Math.max(0, openid.length() - 4)));
            u.setRole("USER");
            u.setStatus(1);
            u.setMemberLevel(0);
            u.setSource("wechat");
            u.setOpenidWechat(openid);
            try {
                String userInfoUrl = "https://api.weixin.qq.com/sns/userinfo?access_token=" + jsonField(json, "access_token")
                        + "&openid=" + openid;
                String uj = httpGet(userInfoUrl);
                String nick = jsonField(uj, "nickname");
                if (nick != null && !nick.isBlank()) u.setNickname(nick);
                String head = jsonField(uj, "headimgurl");
                if (head != null && !head.isBlank()) u.setAvatar(head);
            } catch (Exception ignored) { }
            u.setCreatedAt(LocalDateTime.now());
            userMapper.insert(u);
            created = true;
        }
        String token = jwtUtil.generateToken(u.getUsername(), u.getId(), u.getRole());
        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("user", userMap(u));
        data.put("member", memberService.memberInfo(u.getId()));
        data.put("created", created);
        return data;
    }

    private String httpGet(String url) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest req = HttpRequest.newBuilder(URI.create(url)).GET().build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            return resp.body();
        } catch (Exception e) {
            throw new BusinessException(500, "微信接口调用失败");
        }
    }

    private String jsonField(String json, String key) {
        try {
            return new ObjectMapper().readTree(json).path(key).asText();
        } catch (Exception e) { return null; }
    }

    @Override
    public void logout(String token) {""")])

# 4) AuthController 加微信登录接口
patch(r'\controller\AuthController.java', [(
"""    @GetMapping("/info")""",
"""    @GetMapping("/wechat/url")
    public Result<Map<String, Object>> wechatUrl() {
        return Result.ok(authService.wechatUrl());
    }

    @GetMapping("/wechat/callback")
    public Result<Map<String, Object>> wechatCallback(@RequestParam("code") String code,
                                                      @RequestParam("state") String state) {
        return Result.ok(authService.wechatCallback(code, state));
    }

    @GetMapping("/info")""")])

# 5) DataInitializer 补 avatar 列
patch(r'\config\DataInitializer.java', [(
"""                "ALTER TABLE sys_user ADD COLUMN last_login_at DATETIME NULL"
""",
"""                "ALTER TABLE sys_user ADD COLUMN last_login_at DATETIME NULL",
                "ALTER TABLE sys_user ADD COLUMN avatar VARCHAR(512) DEFAULT ''"
""")])

# 6) SecurityConfig 放行微信登录
patch(r'\config\SecurityConfig.java', [(
"""                        .requestMatchers("/api/auth/login", "/api/auth/register", "/api/auth/captcha", "/api/auth/send-code").permitAll()""",
"""                        .requestMatchers("/api/auth/login", "/api/auth/register", "/api/auth/captcha", "/api/auth/send-code", "/api/auth/wechat/**").permitAll()""")])

# 7) SettingsServiceImpl：getSettings 加微信登录键 + updateSettings memberKeys 加键
patch(r'\service\impl\SettingsServiceImpl.java', [
(
"""        m.put("attachTextColor", get("attachTextColor", "#e53935"));
        return m;""",
"""        m.put("attachTextColor", get("attachTextColor", "#e53935"));
        m.put("wechatLoginEnabled", getInt("wechatLoginEnabled", 0));
        m.put("wechatLoginAppId", get("wechatLoginAppId", ""));
        m.put("wechatLoginSecret", get("wechatLoginSecret", ""));
        m.put("wechatLoginRedirectUri", get("wechatLoginRedirectUri", ""));
        return m;"""),
(
"""            "wechatPayEnabled", "authCaptchaEnabled", "authSmsEnabled",
            "ipGuardEnabled", "ipRateLimit", "ipRateWindow", "ipAutoBlackSeconds"
""",
"""            "wechatPayEnabled", "authCaptchaEnabled", "authSmsEnabled",
            "wechatLoginEnabled", "wechatLoginAppId", "wechatLoginSecret", "wechatLoginRedirectUri",
            "ipGuardEnabled", "ipRateLimit", "ipRateWindow", "ipAutoBlackSeconds"
""")])

# 8) ClientConfigController 下发 wechatLoginEnabled
patch(r'\controller\ClientConfigController.java', [(
"""        m.put("authSmsEnabled", settingsService.getInt("authSmsEnabled", 0) == 1);""",
"""        m.put("authSmsEnabled", settingsService.getInt("authSmsEnabled", 0) == 1);
        m.put("wechatLoginEnabled", settingsService.getInt("wechatLoginEnabled", 0) == 1);""")])

# 9) AdminPayConfigController GET 加微信登录配置
patch(r'\controller\AdminPayConfigController.java', [(
"""        m.put("authCaptchaEnabled", settingsService.getInt("authCaptchaEnabled", 1));""",
"""        m.put("wechatLoginEnabled", settingsService.getInt("wechatLoginEnabled", 0));
        m.put("wechatLoginAppId", settingsService.get("wechatLoginAppId", ""));
        m.put("wechatLoginSecret", settingsService.get("wechatLoginSecret", ""));
        m.put("wechatLoginRedirectUri", settingsService.get("wechatLoginRedirectUri", ""));
        m.put("authCaptchaEnabled", settingsService.getInt("authCaptchaEnabled", 1));""")])

# 10) FileController 加免登录头像上传
patch(r'\controller\FileController.java', [(
"""    @PutMapping("/content")""",
"""    @PostMapping("/upload-avatar")
    public Result<Map<String, Object>> uploadAvatar(@RequestParam("file") MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) throw new BusinessException(400, "请选择图片");
        String ct = file.getContentType();
        if (ct == null || !ct.startsWith("image/")) throw new BusinessException(400, "仅支持图片文件");
        if (file.getSize() > 2 * 1024 * 1024) throw new BusinessException(400, "头像图片不能超过 2MB");
        return Result.ok(fileService.upload(0L, file, null));
    }

    @PutMapping("/content")""")])

print('ALL PATCHES OK')
