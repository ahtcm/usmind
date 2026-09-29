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

    public LogController(RequestLogMapper requestLogMapper, JwtUtil jwtUtil) {
        this.requestLogMapper = requestLogMapper;
        this.jwtUtil = jwtUtil;
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
