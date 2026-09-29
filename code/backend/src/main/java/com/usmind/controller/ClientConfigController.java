package com.usmind.controller;

import com.usmind.common.Result;
import com.usmind.service.MemberService;
import com.usmind.service.SettingsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 客户端公共配置：登录前即可获取（会员开关 / 功能限制 / 登录相关开关）
 */
@RestController
@RequestMapping("/api/config")
public class ClientConfigController {

    private final MemberService memberService;
    private final SettingsService settingsService;

    public ClientConfigController(MemberService memberService, SettingsService settingsService) {
        this.memberService = memberService;
        this.settingsService = settingsService;
    }

    @GetMapping("/client")
    public Result<Map<String, Object>> client() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("feature", memberService.featureConfig());
        // 登录相关开关（客户端据此决定是否显示图形验证码 / 验证码输入）
        m.put("authCaptchaEnabled", settingsService.getInt("authCaptchaEnabled", 1) == 1);
        m.put("authSmsEnabled", settingsService.getInt("authSmsEnabled", 0) == 1);
        m.put("wechatLoginEnabled", settingsService.getInt("wechatLoginEnabled", 0) == 1);
        m.put("memberEnabled", settingsService.getInt("memberEnabled", 1) == 1);
        // 上传类型白名单（后台可配）
        m.put("allowedFileTypes", settingsService.get("allowedFileTypes", ""));
        m.put("uploadMaxSizeMb", settingsService.getInt("uploadMaxSizeMb", 100));
        return Result.ok(m);
    }
}
