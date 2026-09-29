package com.usmind.common;

import com.usmind.service.SettingsService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class JwtUtil {

    /** 从 application.yml 注入；若为空白或已知公开默认值，将自动生成随机密钥并持久化 */
    @Value("${jwt.secret:}")
    private String secret;

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    private final SettingsService settingsService;

    private SecretKey key;

    public JwtUtil(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @PostConstruct
    public void init() {
        this.key = Keys.hmacShaKeyFor(resolveSecret().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 密钥解析优先级：
     * 1. 环境变量 USMIND_JWT_SECRET（生产推荐，固定密钥便于多实例部署）
     * 2. application.yml jwt.secret（已配置且非公开默认值）
     * 3. 自动生成 48 字节随机密钥，持久化到 {upload-dir 上级}/data/jwt-secret.key（重启不失效）
     */
    private String resolveSecret() {
        String env = System.getenv("USMIND_JWT_SECRET");
        if (env != null && !env.isBlank()) return env;
        if (secret != null && !secret.isBlank() && !isKnownDefault(secret)) return secret;
        try {
            java.nio.file.Path dataDir = java.nio.file.Paths.get(uploadDir).toAbsolutePath().getParent().resolve("data");
            java.nio.file.Files.createDirectories(dataDir);
            java.nio.file.Path keyFile = dataDir.resolve("jwt-secret.key");
            if (java.nio.file.Files.exists(keyFile)) {
                String saved = new String(java.nio.file.Files.readAllBytes(keyFile), StandardCharsets.UTF_8).trim();
                if (!saved.isEmpty() && !isKnownDefault(saved)) return saved;
            }
            byte[] bytes = new byte[48];
            new SecureRandom().nextBytes(bytes);
            String gen = Base64.getEncoder().encodeToString(bytes);
            java.nio.file.Files.write(keyFile, gen.getBytes(StandardCharsets.UTF_8));
            log.warn("jwt.secret 未配置或仍为公开默认值，已自动生成随机密钥并持久化到 {}（固定密钥请设环境变量 USMIND_JWT_SECRET）", keyFile);
            return gen;
        } catch (Exception e) {
            byte[] bytes = new byte[48];
            new SecureRandom().nextBytes(bytes);
            return Base64.getEncoder().encodeToString(bytes);
        }
    }

    private boolean isKnownDefault(String s) {
        return "usmind-secret-key-2024".equals(s)
                || "usmind-jwt-secret-key-2024-very-long-and-secure-enough-for-hs256".equals(s);
    }

    public String generateToken(String username, Long userId, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("uid", userId);
        claims.put("role", role);
        Date now = new Date();
        long expireMs = settingsService.getJwtExpireMs();
        return Jwts.builder()
                .claims(claims)
                .subject(username)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expireMs))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public long getExpireMs() {
        return settingsService.getJwtExpireMs();
    }
}
