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

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
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
