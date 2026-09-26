package com.usmind.controller;

import com.usmind.common.Result;
import com.usmind.dto.AiRequest;
import com.usmind.service.SettingsService;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final SettingsService settingsService;

    public AiController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @PostMapping("/generate")
    public Result<Map<String, Object>> generate(@RequestBody AiRequest req) {
        String apiKey = settingsService.get("aiApiKey", "");
        String endpoint = settingsService.get("aiEndpoint", "");
        String model = settingsService.get("aiModel", "gpt-3.5-turbo");
        String result;
        if (apiKey == null || apiKey.isBlank() || endpoint == null || endpoint.isBlank()) {
            result = mockResult(req);
        } else {
            try {
                result = callRemote(endpoint, apiKey, model, req);
            } catch (Exception e) {
                result = mockResult(req);
            }
        }
        return Result.ok(Map.of("result", result));
    }

    private String callRemote(String endpoint, String apiKey, String model, AiRequest req) throws Exception {
        String body = "{\"model\":" + quote(model) + ",\"messages\":[{\"role\":\"user\",\"content\":"
                + quote(req.getPrompt() == null ? "" : req.getPrompt()) + "}]}";
        HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setDoOutput(true);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }

    private String mockResult(AiRequest req) {
        String mode = req.getMode() == null ? "chat" : req.getMode();
        return switch (mode) {
            case "create" -> "【AI生成】已根据你的提示「" + safe(req.getPrompt())
                    + "」生成一个初始思维导图中心主题与3个一级分支。（提示：在管理后台配置 AI Endpoint/Key 后可获得真实AI结果）";
            case "summary" -> "【AI总结】已对导图内容生成摘要：核心要点已归纳为3条。（提示：配置 AI 后可获得真实结果）";
            default -> "【AI助手】你好！我已收到：" + safe(req.getPrompt())
                    + "。当前未配置 AI API Key，这是模拟回复。";
        };
    }

    private String safe(String s) { return s == null ? "" : s; }

    private String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
