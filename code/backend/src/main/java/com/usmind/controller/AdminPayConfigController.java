package com.usmind.controller;

import com.usmind.common.Result;
import com.usmind.service.SettingsService;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 微信支付 / 验证码 / 会员功能开关配置（管理后台）
 */
@RestController
@RequestMapping("/api/admin/pay-config")
public class AdminPayConfigController {

    private final SettingsService settingsService;

    public AdminPayConfigController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping
    public Result<Map<String, Object>> get() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("wechatPayEnabled", settingsService.getInt("wechatPayEnabled", 0));
        m.put("wechatMchId", settingsService.get("wechatMchId", ""));
        m.put("wechatAppId", settingsService.get("wechatAppId", ""));
        m.put("wechatApiKey", settingsService.get("wechatApiKey", ""));
        m.put("wechatPrivateKey", settingsService.get("wechatPrivateKey", ""));
        m.put("wechatNotifyUrl", settingsService.get("wechatNotifyUrl", ""));
        m.put("authCaptchaEnabled", settingsService.getInt("authCaptchaEnabled", 1));
        m.put("authSmsEnabled", settingsService.getInt("authSmsEnabled", 0));
        m.put("smsProvider", settingsService.get("smsProvider", ""));
        m.put("smsAccessKeyId", settingsService.get("smsAccessKeyId", ""));
        m.put("smsAccessKeySecret", settingsService.get("smsAccessKeySecret", ""));
        m.put("smsSignName", settingsService.get("smsSignName", ""));
        m.put("smsTemplateCode", settingsService.get("smsTemplateCode", ""));
        m.put("ipGuardEnabled", settingsService.getInt("ipGuardEnabled", 1));
        m.put("ipRateLimit", settingsService.getInt("ipRateLimit", 60));
        m.put("ipRateWindow", settingsService.getInt("ipRateWindow", 60));
        m.put("ipAutoBlackSeconds", settingsService.getInt("ipAutoBlackSeconds", 3600));
        return Result.ok(m);
    }

    @PutMapping
    public Result<Void> update(@RequestBody Map<String, Object> body) {
        for (Map.Entry<String, Object> e : body.entrySet()) {
            String v = e.getValue() == null ? "" : String.valueOf(e.getValue());
            settingsService.set(e.getKey(), v);
        }
        return Result.ok();
    }
}
