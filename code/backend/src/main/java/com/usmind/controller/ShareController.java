package com.usmind.controller;

import com.usmind.common.Result;
import com.usmind.entity.MindMap;
import com.usmind.service.MindMapService;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/share")
public class ShareController {

    private final MindMapService mindMapService;

    public ShareController(MindMapService mindMapService) {
        this.mindMapService = mindMapService;
    }

    @GetMapping("/{token}")
    public Result<Map<String, Object>> get(@PathVariable String token,
                                           @RequestParam(required = false) String password) {
        MindMap m = mindMapService.getByShareToken(token, password);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", m.getId());
        data.put("name", m.getName());
        data.put("data", m.getData());
        data.put("layout", m.getLayout());
        data.put("theme", m.getTheme());
        data.put("viewData", m.getViewData());
        return Result.ok(data);
    }
}
