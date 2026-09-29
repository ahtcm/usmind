package com.usmind.security;

import com.usmind.common.BusinessException;
import com.usmind.common.SecurityUtil;
import com.usmind.service.IpGuardService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.InetAddress;
import java.util.regex.Pattern;

/**
 * IP / 用户安全防护过滤器：白名单优先放行、黑名单拒绝、超频自动拉黑。
 * 注册在 JWT 过滤器之后（可拿到当前用户）。
 * 防伪造：默认只信 Socket 直连地址（remoteAddr）。
 * 当部署在 nginx 之后时，设置 ipguard.trusted-proxy=true，
 * 且 nginx 必须用 proxy_set_header 覆盖 X-Real-IP / X-Forwarded-For（见部署文档），
 * 否则攻击者可伪造请求头绕过黑白名单。
 */
@Slf4j
@Component
public class IpGuardFilter extends OncePerRequestFilter {

    private static final Pattern PRIVATE_IP = Pattern.compile(
            "^(127\\.|10\\.|192\\.168\\.|172\\.(1[6-9]|2\\d|3[01])\\.|0\\.|169\\.254\\.)");

    private final IpGuardService ipGuardService;

    @Value("${ipguard.trusted-proxy:false}")
    private boolean trustedProxy;

    public IpGuardFilter(IpGuardService ipGuardService) {
        this.ipGuardService = ipGuardService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/")) {
            filterChain.doFilter(request, response);
            return;
        }
        String ip = clientIp(request);
        Long userId = null;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            try {
                userId = SecurityUtil.currentUserId();
            } catch (Exception ignored) {
            }
        }
        try {
            ipGuardService.check(ip, userId);
        } catch (BusinessException e) {
            response.setStatus(200);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":" + e.getCode() + ",\"message\":\"" + e.getMessage() + "\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    /**
     * 取客户端真实 IP。
     * - 未开启 trusted-proxy：一律使用 remoteAddr（直连后端，请求头不可信）。
     * - 开启 trusted-proxy：信任 X-Real-IP 与 X-Forwarded-For 第一个值，
     *   但只有当代理地址（remoteAddr）是本机/内网地址时才信任；
     *   否则仍回退到 remoteAddr，避免伪造请求头绕过防护。
     */
    private String clientIp(HttpServletRequest request) {
        String remote = request.getRemoteAddr() == null ? "" : request.getRemoteAddr();
        if (!trustedProxy || !isTrustedProxySource(remote)) {
            return remote;
        }
        String real = request.getHeader("X-Real-IP");
        if (real != null && !real.isBlank() && !"unknown".equalsIgnoreCase(real)) {
            return real.trim();
        }
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank() && !"unknown".equalsIgnoreCase(xff)) {
            int i = xff.indexOf(',');
            return (i > 0 ? xff.substring(0, i) : xff).trim();
        }
        return remote;
    }

    /** 代理来源是否可信：本机回环或内网地址（生产建议把 nginx 所在 IP 显式列入 allowlist） */
    private boolean isTrustedProxySource(String remote) {
        if (remote == null || remote.isEmpty()) return false;
        try {
            InetAddress addr = InetAddress.getByName(remote);
            if (addr.isLoopbackAddress() || addr.isAnyLocalAddress()) return true;
            if (addr.isSiteLocalAddress()) return true;
            return PRIVATE_IP.matcher(remote).find();
        } catch (Exception e) {
            return PRIVATE_IP.matcher(remote).find();
        }
    }
}
