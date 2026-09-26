package com.usmind.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.usmind.common.BusinessException;
import com.usmind.entity.FileResource;
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
    private final StorageFactory storageFactory;
    private final SettingsService settingsService;

    @org.springframework.beans.factory.annotation.Value("${file.upload-dir:C:/Users/25750/Desktop/usmind/uploads}")
    private String uploadDir;

    public FileServiceImpl(FileResourceMapper fileResourceMapper, SysUserMapper sysUserMapper,
                           StorageFactory storageFactory, SettingsService settingsService) {
        this.fileResourceMapper = fileResourceMapper;
        this.sysUserMapper = sysUserMapper;
        this.storageFactory = storageFactory;
        this.settingsService = settingsService;
    }

    @Override
    public Map<String, Object> upload(long userId, MultipartFile file, Long mindMapId) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "文件为空");
        }
        String original = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();

        // 大小软校验（MB）
        int maxMb = settingsService.getInt("uploadMaxSizeMb", 100);
        if (file.getSize() > maxMb * 1024L * 1024L) {
            throw new BusinessException(400, "文件大小超过限制(" + maxMb + "MB)");
        }
        // 类型软校验
        String allowed = settingsService.get("allowedFileTypes", "");
        int dotIdx = original.lastIndexOf('.');
        String ext = dotIdx >= 0 ? original.substring(dotIdx) : "";
        String extLower = dotIdx >= 0 ? original.substring(dotIdx + 1).toLowerCase(Locale.ROOT) : "";
        if (allowed != null && !allowed.isBlank() && !extLower.isEmpty()) {
            boolean ok = Arrays.stream(allowed.split(","))
                    .map(String::trim)
                    .anyMatch(t -> t.equalsIgnoreCase(extLower));
            if (!ok) throw new BusinessException(400, "不允许的文件类型: " + extLower);
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
    public void deleteFile(Long id) {
        FileResource r = fileResourceMapper.selectById(id);
        if (r == null) throw new BusinessException(404, "文件不存在");
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
    public void deleteBatch(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return;
        for (Long id : ids) {
            deleteFile(id);
        }
    }
}
