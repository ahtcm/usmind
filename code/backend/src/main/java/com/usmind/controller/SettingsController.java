package com.usmind.controller;

import com.usmind.common.Result;
import com.usmind.service.SettingsService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/settings")
public class SettingsController {

    private final SettingsService settingsService;

    public SettingsController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping
    public Result<Map<String, Object>> get() {
        return Result.ok(settingsService.getSettings());
    }

    @PutMapping
    public Result<Void> update(@RequestBody Map<String, Object> body) {
        settingsService.updateSettings(body);
        return Result.ok();
    }
}
