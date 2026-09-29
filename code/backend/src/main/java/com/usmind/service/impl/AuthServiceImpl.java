package com.usmind.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.usmind.common.BusinessException;
import com.usmind.common.JwtUtil;
import com.usmind.common.ResultCode;
import com.usmind.dto.LoginRequest;
import com.usmind.entity.SysUser;
import com.usmind.mapper.SysUserMapper;
import com.usmind.service.AuthService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.concurrent.TimeUnit;

import com.usmind.service.CaptchaService;
import com.usmind.service.MemberService;
import com.usmind.service.SettingsService;

@Service
public class AuthServiceImpl implements AuthService {

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redisTemplate;
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
    }

    @Override
    public Map<String, Object> login(LoginRequest req) {
        SysUser u = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, req.getUsername()));
        if (u == null || !passwordEncoder.matches(req.getPassword(), u.getPassword())) {
            throw new BusinessException(401, "用户名或密码错误");
        }
        if (u.getStatus() != null && u.getStatus() == 0) {
            throw new BusinessException(403, "账号已被禁用");
        }
        u.setLastLoginAt(LocalDateTime.now());
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
    }

    @Override
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
        if (password.length() < 6 || !password.matches(".*[A-Za-z].*") || !password.matches(".*\\d.*")) {
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
        u.setNickname(req.getNickname() == null || req.getNickname().isBlank() ? username : req.getNickname());
        u.setEmail(req.getEmail());
        u.setPhone(req.getPhone());
        u.setRole("USER");
        u.setStatus(1);
        u.setMemberLevel(0);
        u.setSource("local");
        if (req.getAvatar() != null && !req.getAvatar().isBlank()) u.setAvatar(req.getAvatar());
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
    }

        @Override
    public Map<String, Object> info(Long userId) {
        SysUser u = userMapper.selectById(userId);
        if (u == null) throw new BusinessException(401, "用户不存在");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("user", userMap(u));
        m.put("member", memberService.memberInfo(userId));
        return m;
    }

    @Override
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
    public void logout(String token) {
        if (token == null || token.isBlank()) return;
        long ttl = jwtUtil.getExpireMs();
        redisTemplate.opsForValue().set("jwt:blacklist:" + token, "1", ttl, TimeUnit.MILLISECONDS);
    }

    @Override
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        SysUser u = userMapper.selectById(userId);
        if (u == null) throw new BusinessException(ResultCode.UNAUTHORIZED);
        if (!passwordEncoder.matches(oldPassword, u.getPassword())) {
            throw new BusinessException(400, "原密码错误");
        }
        if (newPassword == null || newPassword.length() < 6) {
            throw new BusinessException(400, "新密码长度至少6位");
        }
        u.setPassword(passwordEncoder.encode(newPassword));
        userMapper.updateById(u);
    }
}
