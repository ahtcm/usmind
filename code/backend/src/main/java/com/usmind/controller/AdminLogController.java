package com.usmind.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.usmind.common.Result;
import com.usmind.entity.RequestLog;
import com.usmind.mapper.RequestLogMapper;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理后台：请求日志查询（受 /api/admin/** ADMIN 权限保护）
 */
@RestController
@RequestMapping("/api/admin/logs")
public class AdminLogController {

    private final RequestLogMapper requestLogMapper;

    public AdminLogController(RequestLogMapper requestLogMapper) {
        this.requestLogMapper = requestLogMapper;
    }

    @GetMapping
    public Result<Map<String, Object>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String path,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) Integer success,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        LambdaQueryWrapper<RequestLog> w = new LambdaQueryWrapper<>();
        if (username != null && !username.isBlank()) w.like(RequestLog::getUsername, username.trim());
        if (path != null && !path.isBlank()) w.like(RequestLog::getPath, path.trim());
        if (level != null && !level.isBlank()) w.eq(RequestLog::getLevel, level.trim());
        if (success != null) w.eq(RequestLog::getSuccess, success);
        if (source != null && !source.isBlank()) w.eq(RequestLog::getSource, source.trim());
        if (startDate != null && !startDate.isBlank()) {
            try { w.ge(RequestLog::getRequestAt, LocalDate.parse(startDate.trim()).atStartOfDay()); } catch (Exception ignored) {}
        }
        if (endDate != null && !endDate.isBlank()) {
            try { w.le(RequestLog::getRequestAt, LocalDate.parse(endDate.trim()).plusDays(1).atStartOfDay()); } catch (Exception ignored) {}
        }
        w.orderByDesc(RequestLog::getId);
        Page<RequestLog> p = requestLogMapper.selectPage(new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 100)), w);
        Map<String, Object> out = new HashMap<>();
        out.put("list", p.getRecords());
        out.put("total", p.getTotal());
        return Result.ok(out);
    }

    /** 接口名称下拉：去重后的 path 列表（不含前端上报） */
    @GetMapping("/interface-list")
    public Result<List<String>> interfaceList(@RequestParam(required = false) String keyword) {
        List<RequestLog> all = requestLogMapper.selectList(
                new LambdaQueryWrapper<RequestLog>()
                        .eq(RequestLog::getSource, "backend")
                        .select(RequestLog::getPath));
        List<String> paths = all.stream().map(RequestLog::getPath).filter(p -> p != null && !p.isBlank()).distinct().sorted().collect(java.util.stream.Collectors.toList());
        if (keyword != null && !keyword.isBlank()) {
            paths = paths.stream().filter(p -> p.contains(keyword)).collect(java.util.stream.Collectors.toList());
        }
        return Result.ok(paths);
    }

    /** 清空全部请求日志（危险操作，前端二次确认） */
    @DeleteMapping
    public Result<Void> clearAll() {
        requestLogMapper.delete(new LambdaQueryWrapper<>());
        return Result.ok(null);
    }

    /** 日志详情：该条记录 + 同 path 最近的前端上报记录（前后端日志对照） */
    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        RequestLog r = requestLogMapper.selectById(id);
        if (r == null) return Result.error(404, "日志不存在");
        Map<String, Object> out = new HashMap<>();
        out.put("log", r);
        List<RequestLog> front = requestLogMapper.selectList(
                new LambdaQueryWrapper<RequestLog>()
                        .eq(RequestLog::getSource, "frontend")
                        .eq(RequestLog::getPath, r.getPath() == null ? "" : r.getPath())
                        .orderByDesc(RequestLog::getId)
                        .last("LIMIT 5"));
        out.put("frontendLogs", front);
        return Result.ok(out);
    }
}
