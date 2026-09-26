package com.usmind.service;

import com.usmind.dto.LoginRequest;

import java.util.Map;

public interface AuthService {
    Map<String, Object> login(LoginRequest req);
    Long register(String username, String password, String nickname);
    Map<String, Object> info(Long userId);
    void logout(String token);
    void changePassword(Long userId, String oldPassword, String newPassword);
}
