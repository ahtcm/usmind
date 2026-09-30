package com.usmind.controller;

import com.usmind.common.Result;
import com.usmind.service.StatsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/stats")
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping
    public Result<Map<String, Object>> dashboard() {
        return Result.ok(statsService.dashboard());
    }

    /** 经营统计（注册/VIP/收入/趋势/分布），start/end 可选 */
    @GetMapping("/dashboard/business")
    public Result<Map<String, Object>> business(@RequestParam(value = "start", required = false) String start,
                                                @RequestParam(value = "end", required = false) String end) {
        return Result.ok(statsService.business(start, end));
    }
}
