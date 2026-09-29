# -*- coding: utf-8 -*-
# 修复 SettingsServiceImpl.updateSettings：支持会员/上传/画布/支付/验证码/防护键落库
import io, re, sys

p = r'C:\Users\25750\Desktop\usmind\code\backend\src\main\java\com\usmind\service\impl\SettingsServiceImpl.java'
s = io.open(p, encoding='utf-8').read()

old = """        if (body.containsKey("attachTextColor") && body.get("attachTextColor") != null) {
            set("attachTextColor", String.valueOf(body.get("attachTextColor")));
        }
    }"""

new = """        if (body.containsKey("attachTextColor") && body.get("attachTextColor") != null) {
            set("attachTextColor", String.valueOf(body.get("attachTextColor")));
        }
        // ===== 会员 / 上传 / 画布限制 / 支付 / 验证码 / 防护键（后台可配，统一以 string 落库）=====
        String[] memberKeys = {
            "memberThemeIds", "memberExportFormats",
            "uploadFreeCount", "uploadFreeMaxMb", "uploadVipCount", "uploadVipMaxMb",
            "mindmapFreeCount", "mindmapVipCount",
            "wechatPayEnabled", "authCaptchaEnabled", "authSmsEnabled",
            "ipGuardEnabled", "ipRateLimit", "ipRateWindow", "ipAutoBlackSeconds"
        };
        for (String k : memberKeys) {
            if (body.containsKey(k) && body.get(k) != null) {
                set(k, String.valueOf(body.get(k)));
            }
        }
    }"""

if old not in s:
    print('PATTERN NOT FOUND'); sys.exit(1)
s = s.replace(old, new, 1)
io.open(p, 'w', encoding='utf-8', newline='\n').write(s)
print('PATCH OK')
