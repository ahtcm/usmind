package com.usmind.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.usmind.common.Result;
import com.usmind.entity.Org;
import com.usmind.entity.OrgMember;
import com.usmind.mapper.OrgMapper;
import com.usmind.mapper.OrgMemberMapper;
import com.usmind.mapper.SysUserMapper;
import com.usmind.service.OrgService;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/orgs")
public class AdminOrgController {

    private final OrgMapper orgMapper;
    private final OrgMemberMapper orgMemberMapper;
    private final SysUserMapper sysUserMapper;
    private final OrgService orgService;

    public AdminOrgController(OrgMapper orgMapper, OrgMemberMapper orgMemberMapper,
                              SysUserMapper sysUserMapper, OrgService orgService) {
        this.orgMapper = orgMapper;
        this.orgMemberMapper = orgMemberMapper;
        this.sysUserMapper = sysUserMapper;
        this.orgService = orgService;
    }

    @GetMapping
    public Result<Map<String, Object>> page(@RequestParam(defaultValue = "1") long page,
                                            @RequestParam(defaultValue = "10") long size,
                                            @RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<Org> qw = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            qw.like(Org::getName, keyword).or().like(Org::getInviteCode, keyword);
        }
        qw.orderByDesc(Org::getId);
        IPage<Org> p = orgMapper.selectPage(new Page<>(page, size), qw);
        List<Map<String, Object>> list = new ArrayList<>();
        for (Org o : p.getRecords()) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", o.getId());
            item.put("name", o.getName());
            item.put("inviteCode", o.getInviteCode());
            item.put("memberCount", o.getMemberCount());
            item.put("status", o.getStatus());
            com.usmind.entity.SysUser u = sysUserMapper.selectById(o.getOwnerId());
            item.put("ownerNick", u == null ? "已注销" : (u.getNickname() == null || u.getNickname().isBlank() ? u.getUsername() : u.getNickname()));
            item.put("createdAt", o.getCreatedAt());
            list.add(item);
        }
        Map<String, Object> data = new HashMap<>();
        data.put("list", list);
        data.put("total", p.getTotal());
        return Result.ok(data);
    }

    @GetMapping("/{id}/members")
    public Result<List<Map<String, Object>>> members(@PathVariable Long id) {
        List<Map<String, Object>> list = new ArrayList<>();
        List<OrgMember> ms = orgMemberMapper.selectList(new LambdaQueryWrapper<OrgMember>()
                .eq(OrgMember::getOrgId, id).orderByAsc(OrgMember::getJoinedAt));
        for (OrgMember m : ms) {
            com.usmind.entity.SysUser u = sysUserMapper.selectById(m.getUserId());
            Map<String, Object> mi = new HashMap<>();
            mi.put("userId", m.getUserId());
            mi.put("role", m.getRole());
            mi.put("nickname", u == null ? "已注销" : (u.getNickname() == null || u.getNickname().isBlank() ? u.getUsername() : u.getNickname()));
            list.add(mi);
        }
        return Result.ok(list);
    }

    @DeleteMapping("/{id}")
    public Result<Void> disband(@PathVariable Long id) {
        orgService.disband(id);
        return Result.ok();
    }
}
