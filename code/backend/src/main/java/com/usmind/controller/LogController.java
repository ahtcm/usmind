package com.usmind.controller;

import com.usmind.common.Result;
import com.usmind.common.JwtUtil;
import com.usmind.entity.RequestLog;
import com.usmind.mapper.RequestLogMapper;
import io.jsonwebtoken.Claims;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 前端日志上报接口：浏览器端 window.onerror / unhandledrejection 等错误信息上报到后端日志库
 */
@RestController
@RequestMapping("/api/log")
public class LogController {

    private final RequestLogMapper requestLogMapper;
    private final JwtUtil jwtUtil;
    private final com.usmind.service.SettingsService settingsService;

    public LogController(RequestLogMapper requestLogMapper, JwtUtil jwtUtil,
                         com.usmind.service.SettingsService settingsService) {
        this.requestLogMapper = requestLogMapper;
        this.jwtUtil = jwtUtil;
        this.settingsService = settingsService;
    }

    @PostMapping("/report")
    public Result<Void> report(@RequestBody Map<String, Object> body) {
        Long userId = null;
        String username = "";
        try {
            jakarta.servlet.http.HttpServletRequest req =
                    ((ServletRequestAttributes) RequestContextHolder
                            .getRequestAttributes()).getRequest();
            String auth = req.getHeader("Authorization");
            if (auth != null && auth.startsWith("Bearer ")) {
                Claims c = jwtUtil.parse(auth.substring(7));
                Object uid = c.get("uid");
                userId = uid == null ? null : Long.valueOf(String.valueOf(uid));
                username = c.getSubject();
            }
        } catch (Exception ignored) {
        }
        try {
            RequestLog r = new RequestLog();
            r.setUserId(userId);
            r.setUsername(username == null ? "" : username);
            r.setMethod("FRONTEND");
            String path = body.get("path") == null ? "" : String.valueOf(body.get("path"));
            r.setPath(path.length() > 255 ? path.substring(0, 255) : path);
            r.setQuery("");
            r.setStatusCode(0);
            r.setSource("frontend");
            String level = body.get("level") == null ? "info" : String.valueOf(body.get("level"));
            r.setLevel("error".equals(level) || "warn".equals(level) ? level : "info");
            // 前端日志级别过滤：后台配置记录哪些级别（多选），不含该级别则不落库
            try {
                String cfg = settingsService.get("logRecordLevels", "info,warn,error");
                if (cfg == null || cfg.isBlank()) return Result.ok();
                boolean hit = false;
                for (String c : cfg.split(",")) {
                    if (c.trim().equalsIgnoreCase(r.getLevel())) { hit = true; break; }
                }
                if (!hit) return Result.ok();
            } catch (Exception ignored) { }
            r.setSuccess("error".equals(level) ? 0 : 1);
            r.setCostMs(0);
            String msg = body.get("message") == null ? "" : String.valueOf(body.get("message"));
            r.setErrorMsg(msg.length() > 1000 ? msg.substring(0, 1000) : msg);
            String detail = body.get("detail") == null ? "" : String.valueOf(body.get("detail"));
            r.setDetail(detail.length() > 3000 ? detail.substring(0, 3000) : detail);
            r.setRequestAt(LocalDateTime.now());
            requestLogMapper.insert(r);
        } catch (Exception e) {
            // 上报失败不影响业务
        }
        return Result.ok();
    }
}
