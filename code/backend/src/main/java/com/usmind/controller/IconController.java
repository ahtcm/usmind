package com.usmind.controller;

import com.usmind.common.Result;
import com.usmind.entity.IconTemplate;
import com.usmind.service.IconTemplateService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class IconController {

    private final IconTemplateService iconTemplateService;

    public IconController(IconTemplateService iconTemplateService) {
        this.iconTemplateService = iconTemplateService;
    }

    /** 用户端读取启用图标（公开），按分类分组 */
    @GetMapping("/api/icons")
    public Result<List<Map<String, Object>>> listEnabled() {
        return Result.ok(iconTemplateService.listEnabledGrouped());
    }

    /** 管理后台：全部图标 */
    @GetMapping("/api/admin/icons")
    public Result<List<IconTemplate>> listAll() {
        return Result.ok(iconTemplateService.listAll());
    }

    @PostMapping("/api/admin/icons")
    public Result<Long> create(@RequestBody IconTemplate icon) {
        return Result.ok(iconTemplateService.create(icon));
    }

    /** 批量导入 */
    @PostMapping("/api/admin/icons/batch")
    public Result<Integer> batch(@RequestBody Map<String, String> body) {
        return Result.ok(iconTemplateService.batchCreate(body.get("category"), body.get("emojiText")));
    }

    @DeleteMapping("/api/admin/icons/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        iconTemplateService.delete(id);
        return Result.ok(null);
    }
}
