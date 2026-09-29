package com.usmind.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.usmind.common.BusinessException;
import com.usmind.entity.FileResource;
import com.usmind.entity.MindMap;
import com.usmind.entity.SysUser;
import com.usmind.mapper.FileResourceMapper;
import com.usmind.mapper.SysUserMapper;
import com.usmind.service.FileService;
import com.usmind.service.SettingsService;
import com.usmind.storage.StorageFactory;
import com.usmind.storage.StorageStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class FileServiceImpl implements FileService {

    private final FileResourceMapper fileResourceMapper;
    private final SysUserMapper sysUserMapper;
    private final com.usmind.mapper.MindMapMapper mindMapMapper;
    private final StorageFactory storageFactory;
    private final SettingsService settingsService;

    @org.springframework.beans.factory.annotation.Value("${file.upload-dir:C:/Users/25750/Desktop/usmind/uploads}")
    private String uploadDir;

    public FileServiceImpl(FileResourceMapper fileResourceMapper, SysUserMapper sysUserMapper,
                           com.usmind.mapper.MindMapMapper mindMapMapper,
                           StorageFactory storageFactory, SettingsService settingsService) {
        this.fileResourceMapper = fileResourceMapper;
        this.sysUserMapper = sysUserMapper;
        this.mindMapMapper = mindMapMapper;
        this.storageFactory = storageFactory;
        this.settingsService = settingsService;
    }

    private boolean isVip(Long userId) {
        SysUser u = sysUserMapper.selectById(userId);
        if (u == null || u.getMemberLevel() == null || u.getMemberLevel() <= 0) return false;
        if (u.getMemberExpire() == null) return false;
        return u.getMemberExpire().isAfter(java.time.LocalDateTime.now());
    }

    /** 危险文件类型黑名单：无论后台如何配置（含 *），一律拒绝，防止 SVG/HTML/JS 存储型 XSS */
    private static final Set<String> DANGEROUS_EXT = Set.of(
            "svg", "html", "htm", "xhtml", "shtml", "js", "mjs", "mhtml", "htc", "vbs", "php", "jsp", "asp", "aspx");

    /** 是否按图片处理（扩展名或 Content-Type 命中其一） */
    private boolean isImage(String ext, String contentType) {
        if (ext != null && Set.of("png", "jpg", "jpeg", "gif", "webp", "bmp", "ico").contains(ext)) return true;
        if (contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("image/")) return true;
        return false;
    }

    /** 图片魔数校验：比对文件头与声明的格式 */
    private boolean magicMatches(byte[] head, String ext) {
        if (head == null || head.length < 12) return false;
        int b0 = head[0] & 0xFF, b1 = head[1] & 0xFF;
        switch (ext == null ? "" : ext.toLowerCase(Locale.ROOT)) {
            case "png":
                return b0 == 0x89 && b1 == 0x50 && (head[2] & 0xFF) == 0x4E && (head[3] & 0xFF) == 0x47;
            case "jpg":
            case "jpeg":
                return b0 == 0xFF && b1 == 0xD8;
            case "gif":
                return b0 == 0x47 && b1 == 0x49 && (head[2] & 0xFF) == 0x46; // GIF
            case "webp":
                return b0 == 0x52 && b1 == 0x49 && (head[2] & 0xFF) == 0x46 && (head[3] & 0xFF) == 0x46; // RIFF
            case "bmp":
                return b0 == 0x42 && b1 == 0x4D; // BM
            case "ico":
                return b0 == 0x00 && b1 == 0x00 && (head[2] & 0xFF) == 0x01 && (head[3] & 0xFF) == 0x00;
            default:
                return true; // 非图片格式不做内容校验
        }
    }

    @Override
    public Map<String, Object> upload(long userId, MultipartFile file, Long mindMapId) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "文件为空");
        }
        String original = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();

        // 大小软校验（MB）
        // 会员大小限制：非会员 uploadFreeMaxMb / 会员 uploadVipMaxMb（默认值一致时等价于 uploadMaxSizeMb）
        boolean vip = isVip(userId);
        int maxMb = settingsService.getInt(vip ? "uploadVipMaxMb" : "uploadFreeMaxMb",
                settingsService.getInt("uploadMaxSizeMb", 100));
        if (file.getSize() > maxMb * 1024L * 1024L) {
            throw new BusinessException(400, "文件大小超过限制(" + maxMb + "MB)" + (vip ? "" : "，升级会员可提升上限"));
        }
        // 上传资源数量限制
        Long count = fileResourceMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.usmind.entity.FileResource>()
                .eq(com.usmind.entity.FileResource::getUserId, userId));
        int maxCount = settingsService.getInt(vip ? "uploadVipCount" : "uploadFreeCount", 200);
        if (count != null && count >= maxCount) {
            throw new BusinessException(400, "上传资源数量已达上限(" + maxCount + "个)" + (vip ? "" : "，升级会员可提升上限"));
        }
        // 类型软校验
        String allowed = settingsService.get("allowedFileTypes", "");
        int dotIdx = original.lastIndexOf('.');
        String ext = dotIdx >= 0 ? original.substring(dotIdx) : "";
        String extLower = dotIdx >= 0 ? original.substring(dotIdx + 1).toLowerCase(Locale.ROOT) : "";
        if (allowed != null && !allowed.isBlank() && !extLower.isEmpty()) {
            boolean ok = allowed.trim().equals("*") || Arrays.stream(allowed.split(","))
                    .map(String::trim)
                    .anyMatch(t -> t.equalsIgnoreCase(extLower));
            if (!ok) throw new BusinessException(400, "不允许的文件类型: " + extLower);
        }
        // 内置危险类型黑名单：无论后台如何配置（含 *），一律拒绝，防止 SVG/HTML/JS 存储型 XSS
        if (!extLower.isEmpty() && DANGEROUS_EXT.contains(extLower)) {
            throw new BusinessException(400, "不允许上传危险文件类型: " + extLower);
        }
        // 图片魔数校验：声明/扩展名为图片时，校验真实文件头，防止伪装文件（如把脚本改名 .png）
        if (isImage(extLower, file.getContentType())) {
            byte[] head = file.getBytes();
            if (!magicMatches(head, extLower)) {
                throw new BusinessException(400, "文件内容与图片格式不符: " + extLower);
            }
        }

        String storedName = LocalDate.now() + "/" + UUID.randomUUID().toString().replace("-", "") + ext;

        StorageStrategy storage = storageFactory.get();
        String url;
        try {
            url = storage.upload(file.getBytes(), storedName, file.getContentType());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            // 云端失败时兜底本地存储
            try {
                url = storageFactory.buildForTest(localConfig()).upload(file.getBytes(), storedName, file.getContentType());
            } catch (Exception ex) {
                throw new BusinessException(500, "文件上传失败: " + e.getMessage());
            }
        }

        FileResource r = new FileResource();
        r.setUserId(userId);
        r.setMindMapId(mindMapId == null ? 0 : mindMapId);
        r.setOriginalName(original);
        r.setStoredName(storedName);
        r.setStorageType(storage.type());
        r.setUrl(url);
        r.setSize(file.getSize());
        r.setMimeType(file.getContentType() == null ? "" : file.getContentType());
        fileResourceMapper.insert(r);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", r.getId());
        data.put("url", r.getUrl());
        data.put("originalName", r.getOriginalName());
        data.put("size", r.getSize());
        data.put("mimeType", r.getMimeType());
        return data;
    }

    private com.usmind.entity.OssConfig localConfig() {
        com.usmind.entity.OssConfig c = new com.usmind.entity.OssConfig();
        c.setType("local");
        return c;
    }

    @Override
    public FileResource getById(Long id) {
        FileResource r = fileResourceMapper.selectById(id);
        if (r == null) throw new BusinessException(404, "文件不存在");
        return r;
    }

    @Override
    public java.util.List<FileResource> pageAll() {
        return fileResourceMapper.selectList(null);
    }

    @Override
    public void updateContentByUrl(String url, String content) {
        if (url == null || url.isBlank()) throw new BusinessException(400, "资源地址为空");
        FileResource r = fileResourceMapper.selectOne(new LambdaQueryWrapper<FileResource>()
                .eq(FileResource::getUrl, url).last("LIMIT 1"));
        if (r == null) throw new BusinessException(404, "文件不存在");
        if (url.startsWith("http")) throw new BusinessException(400, "OSS 远程文件暂不支持在线编辑");
        java.nio.file.Path path = java.nio.file.Paths.get(uploadDir, r.getStoredName().replace("/", File.separator));
        try {
            java.nio.file.Files.write(path, (content == null ? "" : content).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new BusinessException(500, "写入文件失败: " + e.getMessage());
        }
    }
    @Override
    public Map<String, Object> pageFiles(int page, int size, String keyword) {
        LambdaQueryWrapper<FileResource> qw = new LambdaQueryWrapper<FileResource>()
                .orderByDesc(FileResource::getCreatedAt);
        if (keyword != null && !keyword.isBlank()) {
            qw.like(FileResource::getOriginalName, keyword.trim());
        }
        Page<FileResource> p = fileResourceMapper.selectPage(new Page<>(page, size), qw);
        List<FileResource> list = p.getRecords();

        // 批量查用户名
        Set<Long> uids = list.stream().map(FileResource::getUserId).collect(Collectors.toSet());
        Map<Long, String> nameMap = new HashMap<>();
        if (!uids.isEmpty()) {
            sysUserMapper.selectBatchIds(uids).forEach(u -> nameMap.put(u.getId(), u.getNickname() == null || u.getNickname().isBlank() ? u.getUsername() : u.getNickname()));
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (FileResource r : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("userId", r.getUserId());
            m.put("username", nameMap.getOrDefault(r.getUserId(), "未知用户"));
            m.put("mindMapId", r.getMindMapId());
            m.put("originalName", r.getOriginalName());
            m.put("storageType", r.getStorageType());
            m.put("url", r.getUrl());
            m.put("size", r.getSize());
            m.put("mimeType", r.getMimeType());
            m.put("createdAt", r.getCreatedAt());
            rows.add(m);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", rows);
        data.put("total", p.getTotal());
        return data;
    }

    @Override
    public void deleteFile(Long id, boolean force) {
        FileResource r = fileResourceMapper.selectById(id);
        if (r == null) throw new BusinessException(404, "文件不存在");
        if (!force) {
            int refs = countRefs(r.getUrl());
            if (refs > 0) {
                throw new BusinessException(400, "该资源仍被 " + refs + " 处引用（导图/头像），删除后引用处将无法显示；如需强制删除请再次确认");
            }
        }
        // 删除存储对象（失败仅记日志，不阻断记录删除）
        try {
            storageFactory.get().delete(r.getStoredName());
        } catch (Exception e) {
            log.warn("删除存储对象失败 id={} name={}: {}", id, r.getStoredName(), e.getMessage());
        }
        // 本地兜底：若为本地文件且仍存在则一并删除
        try {
            java.nio.file.Files.deleteIfExists(
                    java.nio.file.Paths.get(uploadDir == null ? "" : uploadDir, r.getStoredName().replace("/", java.io.File.separator)));
        } catch (Exception ignored) {
        }
        fileResourceMapper.deleteById(id);
        log.info("删除文件记录 id={} name={}", id, r.getOriginalName());
    }

    @Override
    public void deleteBatch(List<Long> ids, boolean force) {
        if (ids == null || ids.isEmpty()) return;
        for (Long id : ids) {
            deleteFile(id, force);
        }
    }

    @Override
    public Map<String, Object> deleteByUrl(String url) {
        Map<String, Object> out = new HashMap<>();
        if (url == null || url.isBlank()) {
            out.put("deleted", false);
            out.put("reason", "url-empty");
            return out;
        }
        FileResource r = fileResourceMapper.selectOne(new LambdaQueryWrapper<FileResource>()
                .eq(FileResource::getUrl, url).last("LIMIT 1"));
        if (r == null) {
            out.put("deleted", false);
            out.put("reason", "not-exists");
            return out;
        }
        int refs = countRefs(url);
        if (refs > 0) {
            out.put("deleted", false);
            out.put("refs", refs);
            return out;
        }
        try {
            storageFactory.get().delete(r.getStoredName());
        } catch (Exception e) {
            log.warn("按URL删除存储对象失败 {}: {}", r.getStoredName(), e.getMessage());
        }
        try {
            java.nio.file.Files.deleteIfExists(
                    java.nio.file.Paths.get(uploadDir == null ? "" : uploadDir, r.getStoredName().replace("/", java.io.File.separator)));
        } catch (Exception ignored) {
        }
        fileResourceMapper.deleteById(r.getId());
        out.put("deleted", true);
        log.info("按URL删除资源: {}", url);
        return out;
    }

    @Override
    public int countRefs(String url) {
        if (url == null || url.isBlank()) return 0;
        String rel = url;
        int idx = url.indexOf("/uploads/");
        if (idx >= 0) rel = url.substring(idx);
        int c = 0;
        try {
            Long maps = mindMapMapper.selectCount(new LambdaQueryWrapper<MindMap>()
                    .like(MindMap::getData, rel));
            c += maps == null ? 0 : maps.intValue();
        } catch (Exception e) {
            log.warn("countRefs导图查询失败: {}", e.getMessage());
        }
        try {
            Long av = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getAvatar, url).or().eq(SysUser::getAvatar, rel));
            c += av == null ? 0 : av.intValue();
        } catch (Exception e) {
            log.warn("countRefs头像查询失败: {}", e.getMessage());
        }
        return c;
    }

    @Override
    public Map<String, Object> orphanCleanup() {
        List<FileResource> all = fileResourceMapper.selectList(null);
        int deleted = 0, kept = 0;
        List<String> errors = new ArrayList<>();
        for (FileResource r : all) {
            if (countRefs(r.getUrl()) > 0) {
                kept++;
                continue;
            }
            try {
                storageFactory.get().delete(r.getStoredName());
            } catch (Exception e) {
                log.warn("孤儿清理-删除存储对象失败 {}: {}", r.getStoredName(), e.getMessage());
            }
            try {
                java.nio.file.Files.deleteIfExists(
                        java.nio.file.Paths.get(uploadDir == null ? "" : uploadDir, r.getStoredName().replace("/", java.io.File.separator)));
            } catch (Exception e) {
                errors.add(r.getStoredName() + ": " + e.getMessage());
            }
            fileResourceMapper.deleteById(r.getId());
            deleted++;
        }
        Map<String, Object> out = new HashMap<>();
        out.put("deleted", deleted);
        out.put("kept", kept);
        out.put("errors", errors);
        log.info("孤儿资源清理完成: 删除={} 保留(仍被引用)={}", deleted, kept);
        return out;
    }
}
