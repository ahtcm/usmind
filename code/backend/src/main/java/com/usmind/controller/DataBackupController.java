package com.usmind.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.usmind.common.Result;
import com.usmind.service.DataBackupService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 数据库备份/恢复（管理后台）：
 *  GET  /api/admin/data/backup  -> 导出全库 JSON
 *  POST /api/admin/data/restore -> 上传备份 JSON 整体恢复
 */
@RestController
@RequestMapping("/api/admin/data")
public class DataBackupController {

    private final DataBackupService dataBackupService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DataBackupController(DataBackupService dataBackupService) {
        this.dataBackupService = dataBackupService;
    }

    @GetMapping("/backup")
    public Result<Map<String, Object>> backup() {
        return Result.ok(dataBackupService.backup());
    }

    @PostMapping("/restore")
    public Result<Map<String, Object>> restore(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return Result.error(500, "请上传备份文件");
        }
        try {
            String text = new String(file.getBytes(), StandardCharsets.UTF_8);
            Map<String, Object> doc = objectMapper.readValue(text, new TypeReference<Map<String, Object>>() {});
            return Result.ok(dataBackupService.restore(doc));
        } catch (IllegalArgumentException e) {
            return Result.error(500, e.getMessage());
        } catch (Exception e) {
            return Result.error(500, "恢复失败: " + e.getMessage());
        }
    }
}
