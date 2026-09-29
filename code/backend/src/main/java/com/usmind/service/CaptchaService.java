package com.usmind.service;

import java.util.Map;

/**
 * 图形验证码：生成 / 校验（默认后台开关 authCaptchaEnabled 控制注册/登录是否需要）
 */
public interface CaptchaService {
    Map<String, Object> create();

    boolean verify(String uuid, String code);

    boolean enabled();
}
