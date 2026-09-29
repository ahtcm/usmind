package com.usmind.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.usmind.common.Result;
import com.usmind.entity.SysUser;
import com.usmind.service.UserService;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/members")
public class AdminMemberController {

    private final UserService userService;

    public AdminMemberController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public Result<Map<String, Object>> page(@RequestParam(defaultValue = "1") long page,
                                            @RequestParam(defaultValue = "10") long size,
                                            @RequestParam(required = false) String keyword) {
        IPage<SysUser> p = userService.page(page, size, keyword);
        Map<String, Object> data = new HashMap<>();
        data.put("list", p.getRecords());
        data.put("total", p.getTotal());
        return Result.ok(data);
    }

    /** 开通 / 调整会员：body { level, days }；level=0 取消会员 */
    @PutMapping("/{id}/member")
    public Result<Void> setMember(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        int level = body.get("level") == null ? 1 : Integer.parseInt(String.valueOf(body.get("level")));
        if (level <= 0) {
            userService.cancelMember(id);
        } else {
            int days = body.get("days") == null ? 30 : Integer.parseInt(String.valueOf(body.get("days")));
            userService.grantMember(id, level, days);
        }
        return Result.ok();
    }
}
