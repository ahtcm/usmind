package com.usmind.controller;

import com.usmind.common.Result;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 项目配置管理（管理后台）：可视化查看/编辑关键部署配置文件
 * - Java 后端配置（application-dev.yml）
 * - Nginx 配置（nginx.conf）
 * 支持：读取当前内容、保存（自动备份）、导出（下载修改后的文件）
 */
@RestController
@RequestMapping("/api/admin/project-config")
public class ProjectConfigController {

    public static final String JAVA_CONFIG_PATH = "C:/Users/25750/Desktop/usmind/code/backend/src/main/resources/application-dev.yml";
    public static final String NGINX_CONFIG_PATH = "C:/Users/25750/Desktop/usmind/tools/nginx-1.30.5/conf/nginx.conf";

    @GetMapping
    public Result<Map<String, Object>> get() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("java", fileItem(JAVA_CONFIG_PATH, "Java 后端配置（application-dev.yml）",
                "端口、数据库、Redis、JWT、OSS、上传大小等后端运行参数。修改后需重启后端服务生效。"));
        m.put("nginx", fileItem(NGINX_CONFIG_PATH, "Nginx 配置（nginx.conf）",
                "监听端口、站点入口、API 反向代理、上传文件缓存等。修改后需执行 nginx -s reload 生效。"));
        return Result.ok(m);
    }

    @PutMapping
    public Result<Map<String, Object>> update(@RequestBody Map<String, Object> body) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (body.containsKey("java")) {
            saveFile(JAVA_CONFIG_PATH, String.valueOf(body.get("java")));
            m.put("java", "saved");
        }
        if (body.containsKey("nginx")) {
            saveFile(NGINX_CONFIG_PATH, String.valueOf(body.get("nginx")));
            m.put("nginx", "saved");
        }
        return Result.ok(m);
    }

    private Map<String, Object> fileItem(String path, String label, String tip) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("label", label);
        item.put("tip", tip);
        item.put("path", path);
        try {
            item.put("content", Files.readString(Paths.get(path), StandardCharsets.UTF_8));
            item.put("exists", true);
        } catch (IOException e) {
            item.put("content", "");
            item.put("exists", false);
            item.put("error", e.getMessage());
        }
        return item;
    }

    private void saveFile(String path, String content) {
        try {
            Path p = Paths.get(path);
            // 先备份当前文件（带时间戳），再写入新内容
            if (Files.exists(p)) {
                String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                Path backup = p.resolveSibling(p.getFileName() + ".bak_" + stamp);
                Files.copy(p, backup, StandardCopyOption.REPLACE_EXISTING);
            }
            Files.writeString(p, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("保存配置文件失败: " + e.getMessage());
        }
    }
}
