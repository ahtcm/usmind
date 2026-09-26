package com.usmind.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.usmind.common.Result;
import com.usmind.common.SecurityUtil;
import com.usmind.dto.MindMapSaveRequest;
import com.usmind.dto.ShareRequest;
import com.usmind.entity.MindMap;
import com.usmind.service.MindMapService;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/mindmaps")
public class MindMapController {

    private final MindMapService mindMapService;

    public MindMapController(MindMapService mindMapService) {
        this.mindMapService = mindMapService;
    }

    @GetMapping
    public Result<Map<String, Object>> page(@RequestParam(defaultValue = "1") long page,
                                            @RequestParam(defaultValue = "20") long size,
                                            @RequestParam(required = false) String keyword) {
        long uid = SecurityUtil.currentUserId();
        IPage<MindMap> p = mindMapService.page(uid, page, size, keyword);
        Map<String, Object> data = new HashMap<>();
        data.put("list", p.getRecords());
        data.put("total", p.getTotal());
        data.put("page", page);
        data.put("size", size);
        return Result.ok(data);
    }

    @PostMapping
    public Result<Map<String, Object>> create(@RequestBody MindMapSaveRequest req) {
        Long id = mindMapService.create(SecurityUtil.currentUserId(), req);
        return Result.ok(Map.of("id", id));
    }

    @GetMapping("/{id}")
    public Result<MindMap> get(@PathVariable Long id) {
        return Result.ok(mindMapService.get(SecurityUtil.currentUserId(), id));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody MindMapSaveRequest req) {
        mindMapService.update(SecurityUtil.currentUserId(), id, req);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        mindMapService.delete(SecurityUtil.currentUserId(), id);
        return Result.ok();
    }

    @PostMapping("/{id}/share")
    public Result<Map<String, Object>> share(@PathVariable Long id, @RequestBody(required = false) ShareRequest req) {
        String pwd = req == null ? null : req.getPassword();
        return Result.ok(mindMapService.share(SecurityUtil.currentUserId(), id, pwd));
    }

    @DeleteMapping("/{id}/share")
    public Result<Void> unshare(@PathVariable Long id) {
        mindMapService.unshare(SecurityUtil.currentUserId(), id);
        return Result.ok();
    }
}
