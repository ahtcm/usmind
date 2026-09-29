package com.usmind.controller;

import com.usmind.common.Result;
import com.usmind.common.SecurityUtil;
import com.usmind.service.AuthService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserCenterController {

    private final AuthService authService;

    public UserCenterController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/profile")
    public Result<Map<String, Object>> profile() {
        return Result.ok(authService.info(SecurityUtil.currentUserId()));
    }

    @PutMapping("/profile")
    public Result<Void> updateProfile(@RequestBody Map<String, Object> body) {
        authService.updateProfile(SecurityUtil.currentUserId(), body);
        return Result.ok();
    }
}
