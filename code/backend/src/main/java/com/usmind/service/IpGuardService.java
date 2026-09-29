package com.usmind.service;

/**
 * IP / 用户安全防护：白名单优先放行、黑名单拒绝、超频自动拉黑
 */
public interface IpGuardService {
    /** 校验请求，通过返回 true；拒绝抛出 BusinessException(403) */
    void check(String ip, Long userId);

    boolean ipInWhite(String ip);

    boolean userInWhite(Long userId);

    /** 手动拉黑 IP（如评论超频），立即写入 Redis 快速生效 + ip_rule 表持久化 */
    void banIp(String ip);
}
