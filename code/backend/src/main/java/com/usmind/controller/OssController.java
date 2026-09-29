package com.usmind.controller;

import com.usmind.common.Result;
import com.usmind.dto.OssConfigRequest;
import com.usmind.entity.OssConfig;
import com.usmind.service.OssConfigService;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/oss")
public class OssController {

    private final OssConfigService ossConfigService;

    public OssController(OssConfigService ossConfigService) {
        this.ossConfigService = ossConfigService;
    }

    @GetMapping("/configs")
    public Result<Map<String, Object>> list() {
        List<OssConfig> list = ossConfigService.list();
        Map<String, Object> data = new HashMap<>();
        data.put("list", list);
        return Result.ok(data);
    }

    @PostMapping("/configs")
    public Result<Map<String, Object>> create(@RequestBody OssConfigRequest req) {
        Long id = ossConfigService.create(req);
        return Result.ok(Map.of("id", id));
    }

    @PutMapping("/configs/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody OssConfigRequest req) {
        ossConfigService.update(id, req);
        return Result.ok();
    }

    @DeleteMapping("/configs/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        ossConfigService.delete(id);
        return Result.ok();
    }

    @PostMapping("/configs/batch-delete")
    public Result<Integer> batchDelete(@RequestBody List<Long> ids) {
        ossConfigService.deleteBatch(ids);
        return Result.ok(ids == null ? 0 : ids.size());
    }

    @PutMapping("/configs/{id}/activate")
    public Result<Void> activate(@PathVariable Long id) {
        ossConfigService.activate(id);
        return Result.ok();
    }

    @GetMapping("/test/{id}")
    public Result<Map<String, Object>> test(@PathVariable Long id) {
        return Result.ok(ossConfigService.test(id));
    }

    @PostMapping("/migrate")
    public Result<Map<String, Object>> migrate(@RequestBody(required = false) Map<String, Object> body) {
        boolean deleteAfter = body != null && body.get("deleteAfter") != null
                && Boolean.parseBoolean(String.valueOf(body.get("deleteAfter")));
        return Result.ok(ossConfigService.migrate(deleteAfter));
    }
}
