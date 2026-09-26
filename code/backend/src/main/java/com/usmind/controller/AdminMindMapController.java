package com.usmind.controller;

import com.usmind.common.Result;
import com.usmind.service.MindMapService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理后台：导图批量管理接口（受 /api/admin/** ADMIN 权限保护）
 */
@RestController
@RequestMapping("/api/admin/mindmaps")
public class AdminMindMapController {

    private final MindMapService mindMapService;

    public AdminMindMapController(MindMapService mindMapService) {
        this.mindMapService = mindMapService;
    }

    @PostMapping("/batch-delete")
    public Result<Integer> batchDelete(@RequestBody List<Long> ids) {
        mindMapService.deleteBatch(ids);
        return Result.ok(ids == null ? 0 : ids.size());
    }
}
