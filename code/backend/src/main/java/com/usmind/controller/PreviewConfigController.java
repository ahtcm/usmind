package com.usmind.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.usmind.common.Result;
import com.usmind.service.SettingsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 公开预览配置：前端拉取"可在线预览/高亮"的文件类型（后台 sys_config 可配）
 */
@RestController
public class PreviewConfigController {

    private final SettingsService settingsService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PreviewConfigController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping("/api/preview-config")
    public Result<Map<String, Object>> get() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("previewModes", safeParse(settingsService.get("previewModes", "{}"), new TypeReference<Map<String, String>>() {}));
        m.put("textPreviewExts", safeParse(settingsService.get("textPreviewExts", "[]"), new TypeReference<List<String>>() {}));
        m.put("attachTextColor", settingsService.get("attachTextColor", "#e53935"));
        return Result.ok(m);
    }

    private <T> T safeParse(String json, TypeReference<T> ref) {
        try {
            return objectMapper.readValue(json, ref);
        } catch (Exception e) {
            try {
                return objectMapper.readValue(ref.getType().equals(new TypeReference<Map<String, String>>() {}.getType()) ? "{}" : "[]", ref);
            } catch (Exception e2) {
                return null;
            }
        }
    }
}
