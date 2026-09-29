package com.usmind.security;

import com.usmind.common.BusinessException;
import com.usmind.common.SecurityUtil;
import com.usmind.service.IpGuardService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * IP / 用户安全防护过滤器：白名单优先放行、黑名单拒绝、超频自动拉黑。
 * 注册在 JWT 过滤器之后（可拿到当前用户）。
 */
@Component
public class IpGuardFilter extends OncePerRequestFilter {

    private final IpGuardService ipGuardService;

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

    private String clientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
            int i = ip.indexOf(',');
            return (i > 0 ? ip.substring(0, i) : ip).trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) return ip.trim();
        return request.getRemoteAddr() == null ? "" : request.getRemoteAddr();
    }
}
