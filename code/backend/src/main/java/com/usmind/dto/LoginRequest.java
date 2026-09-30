package com.usmind.dto;

import lombok.Data;

@Data
public class LoginRequest {
    private String username;
    private String password;
    private String captchaUuid;
    private String captchaCode;
}
