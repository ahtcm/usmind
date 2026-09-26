package com.usmind.storage;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.usmind.common.SecretCipher;
import com.usmind.entity.OssConfig;
import com.usmind.mapper.OssConfigMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class StorageFactory {

    private final OssConfigMapper ossConfigMapper;
    private final SecretCipher secretCipher;
    private final StringRedisTemplate redisTemplate;

    @Value("${file.upload-dir:C:/Users/25750/Desktop/usmind/uploads}")
    private String uploadDir;

    private static final String ACTIVE_CACHE_KEY = "oss:active-config";

    private volatile StorageStrategy current;

    public StorageFactory(OssConfigMapper ossConfigMapper,
                          SecretCipher secretCipher,
                          StringRedisTemplate redisTemplate) {
        this.ossConfigMapper = ossConfigMapper;
        this.secretCipher = secretCipher;
        this.redisTemplate = redisTemplate;
    }

    @PostConstruct
    public void init() {
        refresh();
    }

    public StorageStrategy get() {
        if (current == null) {
            refresh();
        }
        return current;
    }

    /** 重新从数据库加载激活配置并构建存储策略（运行时热切换） */
    public synchronized void refresh() {
        OssConfig cfg = loadActive();
        this.current = build(cfg);
        try {
            redisTemplate.opsForValue().set(ACTIVE_CACHE_KEY,
                    cfg == null ? "local" : cfg.getType(), 1, TimeUnit.HOURS);
        } catch (Exception ignored) {}
        log.info("存储策略已切换为: {}", this.current.type());
    }

    public OssConfig loadActive() {
        try {
            OssConfig cfg = ossConfigMapper.selectOne(new LambdaQueryWrapper<OssConfig>()
                    .eq(OssConfig::getIsActive, 1)
                    .last("LIMIT 1"));
            if (cfg != null) return cfg;
        } catch (Exception e) {
            log.warn("加载激活OSS配置失败: {}", e.getMessage());
        }
        // 兜底：本地存储
        OssConfig local = new OssConfig();
        local.setType("local");
        return local;
    }

    public StorageStrategy build(OssConfig cfg) {
        if (cfg == null || cfg.getType() == null || "local".equals(cfg.getType())) {
            return new LocalStorage(uploadDir, cfg == null ? "" : cfg.getDomain());
        }
        String type = cfg.getType();
        String secret = secretCipher.decrypt(cfg.getSecretKey());
        switch (type) {
            case "aliyun":
                return new AliyunOssStorage(cfg.getEndpoint(), cfg.getAccessKey(), secret,
                        cfg.getBucket(), cfg.getDomain());
            case "minio":
                return new MinioStorage(cfg.getEndpoint(), cfg.getAccessKey(), secret,
                        cfg.getBucket(), cfg.getDomain());
            case "tencent":
                return new TencentCosStorage(cfg.getAccessKey(), secret, cfg.getBucket(),
                        cfg.getRegion(), cfg.getDomain());
            case "qiniu":
                return new QiniuStorage(cfg.getAccessKey(), secret, cfg.getBucket(),
                        cfg.getRegion(), cfg.getDomain());
            default:
                return new LocalStorage(uploadDir, cfg.getDomain());
        }
    }

    /** 为指定配置构建一次性存储策略（用于连接测试，不切换当前） */
    public StorageStrategy buildForTest(OssConfig cfg) {
        return build(cfg);
    }
}
