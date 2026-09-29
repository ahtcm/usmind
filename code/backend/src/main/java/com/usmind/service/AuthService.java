package com.usmind.service;

import com.usmind.dto.LoginRequest;

import java.util.Map;

public interface AuthService {
    Map<String, Object> login(LoginRequest req);
    Long register(String username, String password, String nickname);

    Long register(com.usmind.dto.RegisterRequest req);
    Map<String, Object> info(Long userId);
    void updateProfile(Long userId, Map<String, Object> body);
    void logout(String token);
    Map<String, Object> wechatUrl();
    Map<String, Object> wechatCallback(String code, String state);
    void changePassword(Long userId, String oldPassword, String newPassword);
}
