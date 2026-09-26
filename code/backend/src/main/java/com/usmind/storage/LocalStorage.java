package com.usmind.storage;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
public class LocalStorage implements StorageStrategy {

    private final String uploadDir;
    private final String domain;

    public LocalStorage(String uploadDir, String domain) {
        this.uploadDir = uploadDir;
        this.domain = domain == null ? "" : domain;
    }

    @Override
    public String type() {
        return "local";
    }

    @Override
    public String upload(byte[] data, String storedName, String contentType) throws Exception {
        Path target = Paths.get(uploadDir, storedName);
        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }
        Files.write(target, data);
        log.info("本地存储文件: {}", target);
        String base = domain == null || domain.isEmpty() ? "" : domain;
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base + "/uploads/" + storedName;
    }

    @Override
    public void test() throws Exception {
        File dir = new File(uploadDir);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("无法创建上传目录: " + uploadDir);
        }
        if (!dir.canWrite()) {
            throw new IllegalStateException("上传目录不可写: " + uploadDir);
        }
    }

    @Override
    public void delete(String storedName) throws Exception {
        Path target = Paths.get(uploadDir, storedName);
        boolean ok = Files.deleteIfExists(target);
        log.info("本地存储删除: {} -> {}", target, ok ? "已删除" : "不存在");
    }
}
