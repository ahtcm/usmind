package com.usmind.controller;

import com.usmind.common.Result;
import com.usmind.common.SecurityUtil;
import com.usmind.dto.LoginRequest;
import com.usmind.dto.PasswordChangeRequest;
import com.usmind.dto.RegisterRequest;
import com.usmind.service.AuthService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
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
    }

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody LoginRequest req) {
        return Result.ok(authService.login(req));
    }

    @PostMapping("/register")
    public Result<Map<String, Object>> register(@RequestBody RegisterRequest req) {
        Long id = authService.register(req);
        return Result.ok(Map.of("id", id, "username", req.getUsername()));
    }

    @GetMapping("/wechat/url")
    public Result<Map<String, Object>> wechatUrl() {
        return Result.ok(authService.wechatUrl());
    }

    @GetMapping("/wechat/callback")
    public Result<Map<String, Object>> wechatCallback(@RequestParam("code") String code,
                                                      @RequestParam("state") String state) {
        return Result.ok(authService.wechatCallback(code, state));
    }

    @GetMapping("/info")
    public Result<Map<String, Object>> info() {
        return Result.ok(authService.info(SecurityUtil.currentUserId()));
    }

    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader(value = "Authorization", required = false) String header) {
        if (header != null && header.startsWith("Bearer ")) {
            authService.logout(header.substring(7));
        }
        return Result.ok();
    }

    @PutMapping("/password")
    public Result<Void> changePassword(@RequestBody PasswordChangeRequest req) {
        authService.changePassword(SecurityUtil.currentUserId(), req.getOldPassword(), req.getNewPassword());
        return Result.ok();
    }
}
