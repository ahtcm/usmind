package com.usmind.controller;

import com.usmind.common.Result;
import com.usmind.service.FileService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 管理后台：资源管理接口（受 /api/admin/** ADMIN 权限保护）
 */
@RestController
@RequestMapping("/api/admin")
public class AdminFileController {

    private final FileService fileService;

    public AdminFileController(FileService fileService) {
        this.fileService = fileService;
    }

    @GetMapping("/files")
    public Result<Map<String, Object>> pageFiles(@RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "10") int size,
                                                 @RequestParam(required = false) String keyword) {
        return Result.ok(fileService.pageFiles(page, Math.min(size, 100), keyword));
    }

    @DeleteMapping("/files/{id}")
    public Result<Void> deleteFile(@PathVariable Long id) {
        fileService.deleteFile(id);
        return Result.ok();
    }
}
