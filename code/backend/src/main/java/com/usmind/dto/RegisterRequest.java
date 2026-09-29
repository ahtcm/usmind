package com.usmind.dto;

import lombok.Data;

@Data
public class RegisterRequest {
    private String username;
    private String password;
    private String nickname;
    private String email;
    private String phone;
    private String captchaUuid;
    private String captchaCode;
    private String smsCode;
    private String avatar;
}
