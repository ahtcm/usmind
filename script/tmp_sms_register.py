# -*- coding: utf-8 -*-
"""AuthServiceImpl.register 补短信验证码校验骨架"""
import io
P = r'C:\Users\25750\Desktop\usmind\code\backend\src\main\java\com\usmind\service\impl\AuthServiceImpl.java'
s = io.open(P, encoding='utf-8').read()
old = '''        // 图形验证码（后台可开关）
        if (captchaService.enabled()) {
            if (!captchaService.verify(req.getCaptchaUuid(), req.getCaptchaCode())) {
                throw new BusinessException(400, "图形验证码错误或已过期");
            }
        }'''
new = '''        // 图形验证码（后台可开关）
        if (captchaService.enabled()) {
            if (!captchaService.verify(req.getCaptchaUuid(), req.getCaptchaCode())) {
                throw new BusinessException(400, "图形验证码错误或已过期");
            }
        }
        // 短信验证码（后台可开关；服务商未接入时 send-code 会拒绝发码，注册校验同样拦截）
        if (settingsService.getInt("authSmsEnabled", 0) == 1) {
            if (req.getPhone() == null || req.getPhone().isBlank()) {
                throw new BusinessException(400, "请输入手机号");
            }
            if (req.getSmsCode() == null || req.getSmsCode().isBlank()) {
                throw new BusinessException(400, "请输入短信验证码");
            }
            String cached = redisTemplate.opsForValue().get("smscode:" + req.getPhone().trim());
            if (cached == null || !cached.equals(req.getSmsCode().trim())) {
                throw new BusinessException(400, "短信验证码错误或已过期");
            }
            redisTemplate.delete("smscode:" + req.getPhone().trim());
        }'''
assert s.count(old) == 1, 'sms-register'
s = s.replace(old, new)
io.open(P, 'w', encoding='utf-8', newline='\n').write(s)
print('短信校验骨架 OK')
