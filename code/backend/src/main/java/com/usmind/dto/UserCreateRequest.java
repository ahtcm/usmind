package com.usmind.dto;

import lombok.Data;

@Data
public class UserCreateRequest {
    private String username;
    private String password;
    private String nickname;
    private String email;
    private String phone;
    private String role;
}
