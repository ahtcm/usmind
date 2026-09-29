package com.usmind.controller;

import com.usmind.common.Result;
import com.usmind.common.SecurityUtil;
import com.usmind.service.OrgService;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orgs")
public class OrgController {

    private final OrgService orgService;

    public OrgController(OrgService orgService) {
        this.orgService = orgService;
    }

    @PostMapping
    public Result<Map<String, Object>> create(@RequestBody Map<String, String> body) {
        return Result.ok(orgService.create(SecurityUtil.currentUserId(), body.get("name")));
    }

    @GetMapping("/my")
    public Result<List<Map<String, Object>>> my() {
        return Result.ok(orgService.myOrgs(SecurityUtil.currentUserId()));
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.ok(orgService.detail(SecurityUtil.currentUserId(), id));
    }

    @PostMapping("/join")
    public Result<Map<String, Object>> join(@RequestBody Map<String, String> body) {
        return Result.ok(orgService.join(SecurityUtil.currentUserId(), body.get("inviteCode")));
    }

    @DeleteMapping("/{id}/members/{userId}")
    public Result<Void> kick(@PathVariable Long id, @PathVariable Long userId) {
        orgService.kick(SecurityUtil.currentUserId(), id, userId);
        return Result.ok();
    }

    @DeleteMapping("/{id}/exit")
    public Result<Void> exit(@PathVariable Long id) {
        orgService.exit(SecurityUtil.currentUserId(), id);
        return Result.ok();
    }

    @PostMapping("/{id}/collab")
    public Result<Void> addCollab(@PathVariable Long id, @RequestBody Map<String, Long> body) {
        orgService.addCollab(SecurityUtil.currentUserId(), id, body.get("mindMapId"));
        return Result.ok();
    }

    @GetMapping("/{id}/collab")
    public Result<List<Map<String, Object>>> collabList(@PathVariable Long id) {
        return Result.ok(orgService.collabList(SecurityUtil.currentUserId(), id));
    }

    @DeleteMapping("/{id}/collab/{mindMapId}")
    public Result<Void> removeCollab(@PathVariable Long id, @PathVariable Long mindMapId) {
        orgService.removeCollab(SecurityUtil.currentUserId(), id, mindMapId);
        return Result.ok();
    }
}
