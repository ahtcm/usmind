package com.usmind.controller;

import com.usmind.common.Result;
import com.usmind.service.FileService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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
    public Result<Void> deleteFile(@PathVariable Long id,
                                   @RequestParam(defaultValue = "false") boolean force) {
        fileService.deleteFile(id, force);
        return Result.ok();
    }

    @PostMapping("/files/batch-delete")
    public Result<Integer> batchDeleteFiles(@RequestBody List<Long> ids,
                                            @RequestParam(defaultValue = "false") boolean force) {
        fileService.deleteBatch(ids, force);
        return Result.ok(ids == null ? 0 : ids.size());
    }

    /** 孤儿资源：不再被任何导图/头像引用的文件记录 */
    @GetMapping("/files/orphans")
    public Result<Map<String, Object>> orphanStats() {
        List<com.usmind.entity.FileResource> all = fileService.pageAll();
        int orphan = 0, kept = 0;
        for (com.usmind.entity.FileResource r : all) {
            if (fileService.countRefs(r.getUrl()) > 0) kept++;
            else orphan++;
        }
        Map<String, Object> out = new java.util.HashMap<>();
        out.put("total", all.size());
        out.put("orphan", orphan);
        out.put("kept", kept);
        return Result.ok(out);
    }

    /** 清理孤儿资源（物理删除文件+记录） */
    @PostMapping("/files/orphans/cleanup")
    public Result<Map<String, Object>> cleanupOrphans() {
        return Result.ok(fileService.orphanCleanup());
    }
}
