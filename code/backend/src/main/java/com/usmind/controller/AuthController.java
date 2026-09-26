package com.usmind.controller;

import com.usmind.common.Result;
import com.usmind.common.SecurityUtil;
import com.usmind.dto.LoginRequest;
import com.usmind.dto.PasswordChangeRequest;
import com.usmind.dto.RegisterRequest;
import com.usmind.service.AuthService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody LoginRequest req) {
        return Result.ok(authService.login(req));
    }

    @PostMapping("/register")
    public Result<Map<String, Object>> register(@RequestBody RegisterRequest req) {
        Long id = authService.register(req.getUsername(), req.getPassword(), req.getNickname());
        return Result.ok(Map.of("id", id, "username", req.getUsername()));
    }

    @GetMapping("/info")
    public Result<Map<String, Object>> info() {
        return Result.ok(authService.info(SecurityUtil.currentUserId()));
    }

    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader(value = "Authorization", required = false) String header) {
        if (header != null && header.startsWith("Bearer ")) {
            authService.logout(header.substring(7));
        }
        return Result.ok();
    }

    @PutMapping("/password")
    public Result<Void> changePassword(@RequestBody PasswordChangeRequest req) {
        authService.changePassword(SecurityUtil.currentUserId(), req.getOldPassword(), req.getNewPassword());
        return Result.ok();
    }
}
