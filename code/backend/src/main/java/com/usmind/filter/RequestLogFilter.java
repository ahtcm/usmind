package com.usmind.filter;

import com.usmind.common.JwtUtil;
import com.usmind.entity.RequestLog;
import com.usmind.mapper.RequestLogMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 请求日志过滤器：记录 /api/** 请求的 用户、接口、状态、耗时、IP、请求头、请求体、响应体、异常堆栈。
 * 关键：所有依赖 request/response 对象的数据在请求线程内同步收集完成，异步线程只负责落库
 * （容器会在请求结束后回收 request，异步读会得到空值）。
 * 安全约束：不记录 Authorization/Cookie，敏感接口与文件上传不记录 body。
 * 注册方式：@Component 提供 bean 供 SecurityConfig 注入，同时用 FilterRegistrationBean 禁用
 * Spring Boot 的容器级自动注册，只保留 Spring Security 链内 addFilterAfter 的唯一注册，
 * 避免双重包装导致认证失败响应体丢失。
 */
@Slf4j
@Component
public class RequestLogFilter extends OncePerRequestFilter {

    /** 禁用容器级自动注册：仅由 SecurityConfig.addFilterAfter 在 Security 链内注册一次 */
    @Bean
    public FilterRegistrationBean<RequestLogFilter> requestLogFilterRegistration(RequestLogFilter filter) {
        FilterRegistrationBean<RequestLogFilter> reg = new FilterRegistrationBean<>(filter);
        reg.setEnabled(false);
        return reg;
    }

    private final JwtUtil jwtUtil;
    private final RequestLogMapper requestLogMapper;

    @org.springframework.beans.factory.annotation.Value("${ipguard.trusted-proxy:false}")
    private boolean trustedProxy;

    private static final ThreadPoolExecutor EXEC = new ThreadPoolExecutor(
            1, 2, 60, TimeUnit.SECONDS, new ArrayBlockingQueue<>(500),
            new ThreadPoolExecutor.DiscardOldestPolicy());

    /** 记录进日志的请求头白名单（绝不含 Authorization/Cookie） */
    private static final String[] HEADER_WHITELIST = {
            "content-type", "content-length", "accept", "accept-language",
            "origin", "referer", "user-agent", "x-forwarded-for", "x-request-id"
    };

    public RequestLogFilter(JwtUtil jwtUtil, RequestLogMapper requestLogMapper) {
        this.jwtUtil = jwtUtil;
        this.requestLogMapper = requestLogMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null || !uri.startsWith("/api/")) return true;
        if (uri.startsWith("/api/file/upload") || uri.startsWith("/api/file/upload-avatar")) return true;
        if (uri.startsWith("/api/log/report")) return true;
        return false;
    }

    /** 敏感接口（登录/注册/验证码/支付）不记录请求体与响应体，避免泄露密码与支付凭据 */
    private boolean sensitive(String path) {
        return path != null && (path.startsWith("/api/auth/") || path.startsWith("/api/pay/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long start = System.currentTimeMillis();
        Long userId = null;
        String username = null;
        try {
            String auth = request.getHeader("Authorization");
            if (auth != null && auth.startsWith("Bearer ")) {
                Claims c = jwtUtil.parse(auth.substring(7));
                Object uid = c.get("uid");
                userId = uid == null ? null : Long.valueOf(String.valueOf(uid));
                username = c.getSubject();
            }
        } catch (Exception ignored) {
        }

        String ct = request.getContentType() == null ? "" : request.getContentType().toLowerCase();
        boolean isMultipart = ct.contains("multipart/");
        boolean isSensitive = sensitive(request.getRequestURI());

        CappedBodyRequestWrapper reqWrapper = (!isMultipart && !isSensitive)
                ? new CappedBodyRequestWrapper(request) : null;
        CappedBodyResponseWrapper respWrapper = (isMultipart || isSensitive)
                ? null : new CappedBodyResponseWrapper(response);
        HttpServletRequest req = reqWrapper != null ? reqWrapper : request;
        HttpServletResponse resp = respWrapper != null ? respWrapper : response;

        Throwable ex = null;
        try {
            chain.doFilter(req, resp);
        } catch (Throwable t) {
            ex = t;
            throw t;
        } finally {
            // ===== 请求线程内同步收集全部数据（此后不再访问 request/response 对象） =====
            final long cost = System.currentTimeMillis() - start;
            final int status = resp.getStatus();
            final String path = req.getRequestURI() == null ? "" : req.getRequestURI();
            final String method = req.getMethod();
            String q = req.getQueryString();
            final String query = q == null || q.length() > 500 ? (q == null ? "" : q.substring(0, 500)) : q;
            final String ip = clientIp(req);
            String ua = req.getHeader("User-Agent");
            final String userAgent = ua == null || ua.length() > 300 ? (ua == null ? "" : ua.substring(0, 300)) : ua;
            final String reqHeaders = collectHeaders(req);
            final String reqBody = reqWrapper != null && reqWrapper.cachedBody() != null
                    ? truncate(reqWrapper.cachedBody(), 3000) : null;
            String respBody = null;
            if (respWrapper != null) {
                String respCt = respWrapper.getContentType();
                if (respCt != null && (respCt.contains("json") || respCt.contains("text") || respCt.contains("xml"))) {
                    respBody = truncate(respWrapper.cachedBody(), 3000);
                }
            }

            // 计算 异常/级别/错误信息
            final String fErrMsg;
            final String fDetail;
            final int fSuccess;
            final String fLevel;
            if (ex != null) {
                fSuccess = 0;
                fLevel = "error";
                String m = ex.getMessage();
                fErrMsg = m == null || m.length() > 1000 ? (m == null ? ex.getClass().getSimpleName() : m.substring(0, 1000)) : m;
                fDetail = stackTrace(ex);
            } else if (status >= 500) {
                fSuccess = 0;
                fLevel = "error";
                fErrMsg = "HTTP " + status;
                fDetail = null;
            } else if (status >= 400) {
                fSuccess = 0;
                fLevel = "warn";
                fErrMsg = "HTTP " + status;
                fDetail = null;
            } else {
                fSuccess = 1;
                fLevel = "info";
                fErrMsg = null;
                fDetail = null;
            }

            // 业务错误识别：HTTP 200 但响应体 code != 200
            final String fRespBody = respBody;
            int bizCode = -1;
            if (fRespBody != null) {
                java.util.regex.Matcher mt = java.util.regex.Pattern.compile("\"code\"\\s*:\\s*(\\d+)").matcher(fRespBody);
                if (mt.find()) {
                    try { bizCode = Integer.parseInt(mt.group(1)); } catch (Exception ignored) {}
                }
            }
            final int fBiz = bizCode;

            // lambda 只能引用 effectively-final 变量，统一转为 final 副本
            final Long fUserId = userId;
            final String fUsername = username;
            final int fStatus = status;
            final long fCost = cost;
            final String fMethod = method;
            final String fPath = path;
            final String fQuery = query;
            final String fIp = ip;
            final String fUa = userAgent;

            try {
                EXEC.execute(() -> insertLog(fUserId, fUsername, fMethod, fPath, fQuery, fStatus, fCost, fIp, fUa,
                        reqHeaders, reqBody, fRespBody, fErrMsg, fDetail, fSuccess, fLevel, fBiz));
            } catch (Exception e) {
                log.warn("请求日志入队失败: {}", e.getMessage());
            }
        }
    }

    private void insertLog(Long userId, String username, String method, String path, String query,
                           int status, long cost, String ip, String ua,
                           String reqHeaders, String reqBody, String respBody,
                           String errMsg, String detail, int success, String level, int bizCode) {
        try {
            RequestLog r = new RequestLog();
            r.setUserId(userId);
            r.setUsername(username == null || username.isBlank() ? "" : username);
            r.setMethod(method);
            r.setPath(path);
            r.setQuery(query);
            r.setStatusCode(status);
            r.setSource("backend");
            r.setIp(ip == null ? "" : ip);
            r.setUa(ua);
            r.setCostMs((int) cost);
            r.setRequestAt(LocalDateTime.now());
            r.setReqHeaders(reqHeaders);
            r.setReqBody(reqBody);
            r.setRespBody(respBody);
            if (bizCode != -1 && bizCode != 200 && success == 1) {
                r.setSuccess(0);
                r.setLevel(bizCode >= 500 ? "error" : "warn");
                r.setErrorMsg("业务错误 code=" + bizCode);
            } else {
                r.setSuccess(success);
                r.setLevel(level);
                r.setErrorMsg(errMsg);
                r.setDetail(detail);
            }
            requestLogMapper.insert(r);
        } catch (Exception e) {
            log.warn("写入请求日志失败: {}", e.getMessage());
        }
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }

    private String collectHeaders(HttpServletRequest request) {
        try {
            Map<String, String> m = new LinkedHashMap<>();
            for (String h : HEADER_WHITELIST) {
                String v = request.getHeader(h);
                if (v != null && !v.isBlank()) m.put(h, v);
            }
            return m.isEmpty() ? null : m.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private String stackTrace(Throwable ex) {
        try {
            StringWriter sw = new StringWriter();
            ex.printStackTrace(new PrintWriter(sw));
            return truncate(sw.toString(), 3000);
        } catch (Exception e) {
            return ex == null ? "" : ex.getClass().getSimpleName();
        }
    }

    private String clientIp(HttpServletRequest request) {
        // 默认只信直连地址；trusted-proxy 开启（nginx 场景）时才信任代理头（与 IpGuardFilter 保持一致）
        String remote = request.getRemoteAddr() == null ? "" : request.getRemoteAddr();
        if (!trustedProxy) return remote;
        String real = request.getHeader("X-Real-IP");
        if (real != null && !real.isBlank()) return real.trim();
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            String first = xff.split(",")[0].trim();
            if (!first.isBlank()) return first;
        }
        return remote;
    }
}
