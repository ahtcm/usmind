package com.usmind.controller;

import com.usmind.common.Result;
import com.usmind.entity.ThemeTemplate;
import com.usmind.service.ThemeTemplateService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class ThemeController {

    private final ThemeTemplateService themeTemplateService;

    public ThemeController(ThemeTemplateService themeTemplateService) {
        this.themeTemplateService = themeTemplateService;
    }

    /** 用户端读取启用主题（公开） */
    @GetMapping("/api/themes")
    public Result<List<Map<String, Object>>> listEnabled() {
        return Result.ok(themeTemplateService.listEnabled());
    }

    /** 管理后台：全部主题 */
    @GetMapping("/api/admin/themes")
    public Result<List<ThemeTemplate>> listAll() {
        return Result.ok(themeTemplateService.listAll());
    }

    @GetMapping("/api/admin/themes/{id}")
    public Result<ThemeTemplate> get(@PathVariable Long id) {
        return Result.ok(themeTemplateService.get(id));
    }

    @PostMapping("/api/admin/themes")
    public Result<Long> create(@RequestBody ThemeTemplate t) {
        return Result.ok(themeTemplateService.create(t));
    }

    @PutMapping("/api/admin/themes/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody ThemeTemplate t) {
        themeTemplateService.update(id, t);
        return Result.ok(null);
    }

    @DeleteMapping("/api/admin/themes/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        themeTemplateService.delete(id);
        return Result.ok(null);
    }
}
