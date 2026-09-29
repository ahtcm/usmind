package com.usmind.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.usmind.common.BusinessException;
import com.usmind.common.SecretCipher;
import com.usmind.dto.OssConfigRequest;
import com.usmind.entity.FileResource;
import com.usmind.entity.MindMap;
import com.usmind.entity.OssConfig;
import com.usmind.entity.SysUser;
import com.usmind.mapper.FileResourceMapper;
import com.usmind.mapper.MindMapMapper;
import com.usmind.mapper.OssConfigMapper;
import com.usmind.mapper.SysUserMapper;
import com.usmind.service.OssConfigService;
import com.usmind.storage.StorageFactory;
import com.usmind.storage.StorageStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Slf4j
@Service
public class OssConfigServiceImpl implements OssConfigService {

    private final OssConfigMapper ossConfigMapper;
    private final SecretCipher secretCipher;
    private final StorageFactory storageFactory;
    private final StringRedisTemplate redisTemplate;
    private final FileResourceMapper fileResourceMapper;
    private final MindMapMapper mindMapMapper;
    private final SysUserMapper sysUserMapper;

    @Value("${file.upload-dir:C:/Users/25750/Desktop/usmind/uploads}")
    private String uploadDir;

    public OssConfigServiceImpl(OssConfigMapper ossConfigMapper, SecretCipher secretCipher,
                                StorageFactory storageFactory, StringRedisTemplate redisTemplate,
                                FileResourceMapper fileResourceMapper, MindMapMapper mindMapMapper,
                                SysUserMapper sysUserMapper) {
        this.ossConfigMapper = ossConfigMapper;
        this.secretCipher = secretCipher;
        this.storageFactory = storageFactory;
        this.redisTemplate = redisTemplate;
        this.fileResourceMapper = fileResourceMapper;
        this.mindMapMapper = mindMapMapper;
        this.sysUserMapper = sysUserMapper;
    }

    @Override
    public List<OssConfig> list() {
        List<OssConfig> list = ossConfigMapper.selectList(new LambdaQueryWrapper<OssConfig>()
                .orderByDesc(OssConfig::getIsActive)
                .orderByAsc(OssConfig::getId));
        // 不返回密钥
        list.forEach(c -> c.setSecretKey(null));
        return list;
    }

    @Override
    public Long create(OssConfigRequest req) {
        if (!StringUtils.hasText(req.getName()) || !StringUtils.hasText(req.getType())) {
            throw new BusinessException(400, "名称和类型不能为空");
        }
        OssConfig c = new OssConfig();
        fill(c, req, true);
        c.setIsActive(0);
        ossConfigMapper.insert(c);
        return c.getId();
    }

    @Override
    public void update(Long id, OssConfigRequest req) {
        OssConfig c = ossConfigMapper.selectById(id);
        if (c == null) throw new BusinessException(404, "配置不存在");
        fill(c, req, false);
        ossConfigMapper.updateById(c);
        if (c.getIsActive() != null && c.getIsActive() == 1) {
            storageFactory.refresh();
            clearCache();
        }
    }

    private void fill(OssConfig c, OssConfigRequest req, boolean isCreate) {
        if (req.getName() != null) c.setName(req.getName());
        if (req.getType() != null) c.setType(req.getType());
        if (req.getEndpoint() != null) c.setEndpoint(req.getEndpoint());
        if (req.getAccessKey() != null) c.setAccessKey(req.getAccessKey());
        if (StringUtils.hasText(req.getSecretKey())) {
            c.setSecretKey(secretCipher.encrypt(req.getSecretKey()));
        } else if (isCreate) {
            c.setSecretKey("");
        }
        if (req.getBucket() != null) c.setBucket(req.getBucket());
        if (req.getRegion() != null) c.setRegion(req.getRegion());
        if (req.getDomain() != null) c.setDomain(req.getDomain());
        if (req.getConfigJson() != null) c.setConfigJson(req.getConfigJson());
    }

    @Override
    public void delete(Long id) {
        OssConfig c = ossConfigMapper.selectById(id);
        if (c == null) throw new BusinessException(404, "配置不存在");
        if (c.getIsActive() != null && c.getIsActive() == 1) {
            throw new BusinessException(400, "不能删除当前激活的配置");
        }
        ossConfigMapper.deleteById(id);
    }

    @Override
    public void deleteBatch(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return;
        for (Long id : ids) {
            delete(id);
        }
    }

    @Override
    public void activate(Long id) {
        OssConfig c = ossConfigMapper.selectById(id);
        if (c == null) throw new BusinessException(404, "配置不存在");
        // 全部置为非激活
        OssConfig reset = new OssConfig();
        reset.setIsActive(0);
        ossConfigMapper.update(reset, new LambdaQueryWrapper<>());
        c.setIsActive(1);
        ossConfigMapper.updateById(c);
        // 运行时热切换
        storageFactory.refresh();
        clearCache();
    }

    @Override
    public Map<String, Object> test(Long id) {
        OssConfig c = ossConfigMapper.selectById(id);
        if (c == null) throw new BusinessException(404, "配置不存在");
        Map<String, Object> result = new HashMap<>();
        try {
            StorageStrategy s = storageFactory.buildForTest(c);
            s.test();
            result.put("success", true);
            result.put("message", "连接成功");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "连接失败: " + e.getMessage());
        }
        return result;
    }

    @Override
    public Map<String, Object> migrate(boolean deleteAfter) {
        StorageStrategy oss = storageFactory.get();
        if (oss == null || "local".equalsIgnoreCase(oss.type())) {
            throw new BusinessException(400, "当前激活的是本地存储，请先激活OSS/MinIO/COS/七牛配置后再迁移");
        }

        // 1. 扫描所有仍为本地存储的文件记录
        List<FileResource> locals = fileResourceMapper.selectList(
                new LambdaQueryWrapper<FileResource>().eq(FileResource::getStorageType, "local"));
        int ok = 0, failed = 0, skipped = 0;
        List<String> errors = new ArrayList<>();
        // 相对路径 /uploads/x -> 新URL
        Map<String, String> urlMap = new HashMap<>();

        for (FileResource r : locals) {
            String storedName = r.getStoredName();
            Path p = Paths.get(uploadDir, storedName.replace("/", File.separator));
            if (!Files.exists(p)) {
                skipped++;
                errors.add(storedName + ": 本地文件不存在，已跳过");
                continue;
            }
            try {
                byte[] data = Files.readAllBytes(p);
                String mime = (r.getMimeType() == null || r.getMimeType().isBlank())
                        ? guessMime(storedName) : r.getMimeType();
                String newUrl = oss.upload(data, storedName, mime);
                r.setUrl(newUrl);
                r.setStorageType(oss.type());
                fileResourceMapper.updateById(r);
                urlMap.put("/uploads/" + storedName, newUrl);
                ok++;
            } catch (Exception e) {
                failed++;
                errors.add(storedName + ": " + e.getMessage());
            }
        }

        // 2. 全库替换导图数据与用户头像中的旧URL引用
        int mapUpdated = 0, userUpdated = 0;
        if (!urlMap.isEmpty()) {
            List<MindMap> maps = mindMapMapper.selectList(null);
            for (MindMap m : maps) {
                String d = m.getData();
                if (d != null && d.contains("/uploads/")) {
                    String nd = replaceAllUrls(d, urlMap);
                    if (!nd.equals(d)) {
                        m.setData(nd);
                        mindMapMapper.updateById(m);
                        mapUpdated++;
                    }
                }
            }
            List<SysUser> users = sysUserMapper.selectList(
                    new LambdaQueryWrapper<SysUser>().like(SysUser::getAvatar, "/uploads/"));
            for (SysUser u : users) {
                String a = replaceAllUrls(u.getAvatar(), urlMap);
                if (!a.equals(u.getAvatar())) {
                    u.setAvatar(a);
                    sysUserMapper.updateById(u);
                    userUpdated++;
                }
            }
        }

        // 3. 可选：删除本地已迁移文件
        int deleted = 0;
        if (deleteAfter) {
            for (FileResource r : locals) {
                try {
                    Path p = Paths.get(uploadDir, r.getStoredName().replace("/", File.separator));
                    if (Files.deleteIfExists(p)) deleted++;
                } catch (Exception ignored) {
                }
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("files", ok);
        result.put("failed", failed);
        result.put("skipped", skipped);
        result.put("mindmaps", mapUpdated);
        result.put("users", userUpdated);
        result.put("deletedLocal", deleted);
        result.put("errors", errors);
        log.info("存储迁移完成: 文件成功={} 失败={} 跳过={} 导图更新={} 头像更新={} 删除本地={}",
                ok, failed, skipped, mapUpdated, userUpdated, deleted);
        return result;
    }

    private String guessMime(String storedName) {
        String n = storedName.toLowerCase(Locale.ROOT);
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".gif")) return "image/gif";
        if (n.endsWith(".webp")) return "image/webp";
        if (n.endsWith(".svg")) return "image/svg+xml";
        if (n.endsWith(".mp4")) return "video/mp4";
        if (n.endsWith(".webm")) return "video/webm";
        if (n.endsWith(".mp3")) return "audio/mpeg";
        if (n.endsWith(".wav")) return "audio/wav";
        if (n.endsWith(".pdf")) return "application/pdf";
        if (n.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (n.endsWith(".xlsx")) return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        if (n.endsWith(".txt") || n.endsWith(".md")) return "text/plain";
        if (n.endsWith(".json")) return "application/json";
        return "application/octet-stream";
    }

    /** 先替换绝对地址形式 http(s)://host/uploads/x，再替换相对形式 /uploads/x */
    private String replaceAllUrls(String text, Map<String, String> urlMap) {
        String out = text;
        for (Map.Entry<String, String> e : urlMap.entrySet()) {
            String rel = e.getKey();
            String neu = e.getValue();
            try {
                out = out.replaceAll("(https?://[^/\"'\\s]+)" + Pattern.quote(rel),
                        java.util.regex.Matcher.quoteReplacement(neu));
            } catch (Exception ignored) {
            }
            out = out.replace(rel, neu);
        }
        return out;
    }

    private void clearCache() {
        try {
            redisTemplate.delete("oss:active-config");
        } catch (Exception ignored) {}
    }
}
