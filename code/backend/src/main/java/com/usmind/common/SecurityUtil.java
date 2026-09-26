package com.usmind.common;

import com.usmind.security.LoginUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class SecurityUtil {

    public static Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof LoginUser lu) {
            return lu.getUserId();
        }
        throw new BusinessException(ResultCode.UNAUTHORIZED);
    }

    public static String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return null;
        return auth.getName();
    }

    public static String currentRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return null;
        Object principal = auth.getPrincipal();
        if (principal instanceof LoginUser lu) {
            return lu.getRole();
        }
        return null;
    }
}
