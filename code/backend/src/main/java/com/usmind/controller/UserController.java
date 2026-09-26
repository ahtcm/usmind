package com.usmind.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.usmind.common.Result;
import com.usmind.dto.ResetPasswordRequest;
import com.usmind.dto.UserCreateRequest;
import com.usmind.dto.UserUpdateRequest;
import com.usmind.entity.SysUser;
import com.usmind.service.UserService;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
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
        data.put("page", page);
        data.put("size", size);
        return Result.ok(data);
    }

    @PostMapping
    public Result<Map<String, Object>> create(@RequestBody UserCreateRequest req) {
        Long id = userService.create(req);
        return Result.ok(Map.of("id", id));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody UserUpdateRequest req) {
        userService.update(id, req);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return Result.ok();
    }

    @PostMapping("/batch-delete")
    public Result<Integer> batchDelete(@RequestBody List<Long> ids) {
        userService.deleteBatch(ids);
        return Result.ok(ids == null ? 0 : ids.size());
    }

    @PutMapping("/{id}/password")
    public Result<Void> resetPassword(@PathVariable Long id, @RequestBody ResetPasswordRequest req) {
        userService.resetPassword(id, req.getPassword());
        return Result.ok();
    }
}
