package com.usmind.service.impl;

import com.usmind.common.BusinessException;
import com.usmind.service.CaptchaService;
import com.usmind.service.SettingsService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class CaptchaServiceImpl implements CaptchaService {

    private final StringRedisTemplate redisTemplate;
    private final SettingsService settingsService;
    private static final char[] CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    public CaptchaServiceImpl(StringRedisTemplate redisTemplate, SettingsService settingsService) {
        this.redisTemplate = redisTemplate;
        this.settingsService = settingsService;
    }

    @Override
    public boolean enabled() {
        return settingsService.getInt("authCaptchaEnabled", 1) == 1;
    }

    @Override
    public Map<String, Object> create() {
        int w = 130, h = 44;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(248, 249, 252));
        g.fillRect(0, 0, w, h);
        Random r = new Random();
        // 干扰线
        for (int i = 0; i < 6; i++) {
            g.setColor(new Color(160 + r.nextInt(90), 160 + r.nextInt(90), 160 + r.nextInt(90)));
            g.drawLine(r.nextInt(w), r.nextInt(h), r.nextInt(w), r.nextInt(h));
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            char c = CHARS[r.nextInt(CHARS.length)];
            sb.append(c);
            g.setColor(new Color(60 + r.nextInt(120), 60 + r.nextInt(120), 60 + r.nextInt(120)));
            g.setFont(new Font("Arial", Font.BOLD, 26 + r.nextInt(6)));
            g.drawString(String.valueOf(c), 16 + i * 28, 30 + r.nextInt(8));
        }
        // 噪点
        for (int i = 0; i < 40; i++) {
            g.setColor(new Color(180 + r.nextInt(75), 180 + r.nextInt(75), 180 + r.nextInt(75)));
            g.fillRect(r.nextInt(w), r.nextInt(h), 2, 2);
        }
        g.dispose();
        String uuid = UUID.randomUUID().toString().replace("-", "");
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", bos);
            String base64 = Base64.getEncoder().encodeToString(bos.toByteArray());
            // 图形验证码有效期 5 分钟
            redisTemplate.opsForValue().set("captcha:" + uuid, sb.toString(), 5, TimeUnit.MINUTES);
            Map<String, Object> m = new HashMap<>();
            m.put("uuid", uuid);
            m.put("image", "data:image/png;base64," + base64);
            m.put("enabled", enabled());
            return m;
        } catch (Exception e) {
            throw new BusinessException(500, "验证码生成失败");
        }
    }

    @Override
    public boolean verify(String uuid, String code) {
        if (uuid == null || code == null) return false;
        String key = "captcha:" + uuid;
        String saved = redisTemplate.opsForValue().get(key);
        if (saved == null) return false;
        redisTemplate.delete(key); // 一次性
        return saved.equalsIgnoreCase(code.trim());
    }
}
