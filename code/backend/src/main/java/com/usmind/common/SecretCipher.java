package com.usmind.common;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * OSS secret_key 轻量加密（AES），至少满足 base64 编码存储。
 * 密钥来源：环境变量 USMIND_OSS_CIPHER_KEY > application.yml > 随机持久化 data/oss-cipher.key。
 */
@Slf4j
@Component
public class SecretCipher {

    @Value("${oss.secret-cipher-key:}")
    private String cipherKey;

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    private SecretKeySpec keySpec;

    @PostConstruct
    public void init() {
        byte[] raw = resolveKey();
        this.keySpec = new SecretKeySpec(raw, "AES");
    }

    private byte[] resolveKey() {
        String env = System.getenv("USMIND_OSS_CIPHER_KEY");
        String cfg = env != null && !env.isBlank() ? env : cipherKey;
        if (cfg != null && !cfg.isBlank() && !"usmind-oss-secret-key-2024".equals(cfg)) {
            return derive(cfg);
        }
        try {
            java.nio.file.Path dataDir = java.nio.file.Paths.get(uploadDir).toAbsolutePath().getParent().resolve("data");
            java.nio.file.Files.createDirectories(dataDir);
            java.nio.file.Path keyFile = dataDir.resolve("oss-cipher.key");
            if (java.nio.file.Files.exists(keyFile)) {
                String saved = new String(java.nio.file.Files.readAllBytes(keyFile), StandardCharsets.UTF_8).trim();
                if (!saved.isEmpty() && !"usmind-oss-secret-key-2024".equals(saved)) return derive(saved);
            }
            byte[] bytes = new byte[32];
            new SecureRandom().nextBytes(bytes);
            String gen = Base64.getEncoder().encodeToString(bytes);
            java.nio.file.Files.write(keyFile, gen.getBytes(StandardCharsets.UTF_8));
            log.warn("oss.secret-cipher-key 未配置或仍为公开默认值，已生成随机密钥并持久化到 {}", keyFile);
            return Base64.getDecoder().decode(gen);
        } catch (Exception e) {
            byte[] bytes = new byte[32];
            new SecureRandom().nextBytes(bytes);
            return bytes;
        }
    }

    private byte[] derive(String s) {
        byte[] raw = new byte[32];
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(b, 0, raw, 0, Math.min(32, b.length));
        return raw;
    }

    public String encrypt(String plain) {
        if (plain == null || plain.isEmpty()) return "";
        try {
            Cipher c = Cipher.getInstance("AES");
            c.init(Cipher.ENCRYPT_MODE, keySpec);
            return Base64.getEncoder().encodeToString(c.doFinal(plain.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return Base64.getEncoder().encodeToString(plain.getBytes(StandardCharsets.UTF_8));
        }
    }

    public String decrypt(String stored) {
        if (stored == null || stored.isEmpty()) return "";
        // 先用当前密钥解（新数据）
        String v = tryDecrypt(stored, keySpec);
        if (v != null) return v;
        // 兼容存量数据：旧默认密钥（取前 16 字节）仅作解密回退，新写入一律用新随机密钥
        byte[] oldBytes = "usmind-oss-secret-key-2024".getBytes(StandardCharsets.UTF_8);
        SecretKeySpec old = new SecretKeySpec(java.util.Arrays.copyOfRange(oldBytes, 0, 16), "AES");
        v = tryDecrypt(stored, old);
        if (v != null) return v;
        return stored;
    }

    private String tryDecrypt(String stored, SecretKeySpec spec) {
        try {
            Cipher c = Cipher.getInstance("AES");
            c.init(Cipher.DECRYPT_MODE, spec);
            return new String(c.doFinal(Base64.getDecoder().decode(stored)), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

}
