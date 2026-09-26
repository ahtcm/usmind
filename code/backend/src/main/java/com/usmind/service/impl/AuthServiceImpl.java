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

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class AuthServiceImpl implements AuthService {

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redisTemplate;

    public AuthServiceImpl(SysUserMapper userMapper, PasswordEncoder passwordEncoder,
                           JwtUtil jwtUtil, StringRedisTemplate redisTemplate) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.redisTemplate = redisTemplate;
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
        String token = jwtUtil.generateToken(u.getUsername(), u.getId(), u.getRole());
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
    }

    @Override
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
    }

    @Override
    public Map<String, Object> info(Long userId) {
        SysUser u = userMapper.selectById(userId);
        if (u == null) throw new BusinessException(ResultCode.UNAUTHORIZED);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("username", u.getUsername());
        m.put("nickname", u.getNickname());
        m.put("avatar", u.getAvatar());
        m.put("email", u.getEmail());
        m.put("phone", u.getPhone());
        m.put("role", u.getRole());
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
