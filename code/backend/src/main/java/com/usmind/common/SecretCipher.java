package com.usmind.common;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * OSS secret_key 轻量加密（AES），至少满足 base64 编码存储。
 */
@Component
public class SecretCipher {

    @Value("${oss.secret-cipher-key:usmind-oss-secret-key-2024}")
    private String cipherKey;

    private SecretKeySpec keySpec;

    @PostConstruct
    public void init() {
        byte[] raw = new byte[16];
        byte[] b = cipherKey.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(b, 0, raw, 0, Math.min(16, b.length));
        this.keySpec = new SecretKeySpec(raw, "AES");
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
        try {
            Cipher c = Cipher.getInstance("AES");
            c.init(Cipher.DECRYPT_MODE, keySpec);
            return new String(c.doFinal(Base64.getDecoder().decode(stored)), StandardCharsets.UTF_8);
        } catch (Exception e) {
            // 兼容未加密/旧数据：直接 base64 解码尝试
            try {
                return new String(Base64.getDecoder().decode(stored), StandardCharsets.UTF_8);
            } catch (Exception ex) {
                return stored;
            }
        }
    }
}
