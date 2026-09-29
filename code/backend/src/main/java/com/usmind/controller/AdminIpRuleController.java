package com.usmind.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.usmind.common.Result;
import com.usmind.entity.IpRule;
import com.usmind.mapper.IpRuleMapper;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/ip-rules")
public class AdminIpRuleController {

    private final IpRuleMapper ipRuleMapper;

    public AdminIpRuleController(IpRuleMapper ipRuleMapper) {
        this.ipRuleMapper = ipRuleMapper;
    }

    @GetMapping
    public Result<Map<String, Object>> page(@RequestParam(defaultValue = "1") long page,
                                            @RequestParam(defaultValue = "10") long size,
                                            @RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<IpRule> qw = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            qw.like(IpRule::getTargetValue, keyword).or().like(IpRule::getRemark, keyword);
        }
        qw.orderByDesc(IpRule::getId);
        IPage<IpRule> p = ipRuleMapper.selectPage(new Page<>(page, size), qw);
        Map<String, Object> data = new HashMap<>();
        data.put("list", p.getRecords());
        data.put("total", p.getTotal());
        return Result.ok(data);
    }

    @PostMapping
    public Result<Map<String, Object>> create(@RequestBody IpRule rule) {
        if (rule.getRuleType() == null || rule.getTargetType() == null || rule.getTargetValue() == null) {
            return Result.error(400, "规则类型/目标类型/目标值不能为空");
        }
        rule.setEnabled(rule.getEnabled() == null ? 1 : rule.getEnabled());
        rule.setCreatedAt(LocalDateTime.now());
        ipRuleMapper.insert(rule);
        return Result.ok(Map.of("id", rule.getId()));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody IpRule rule) {
        IpRule exist = ipRuleMapper.selectById(id);
        if (exist == null) return Result.error(404, "规则不存在");
        if (rule.getRuleType() != null) exist.setRuleType(rule.getRuleType());
        if (rule.getTargetType() != null) exist.setTargetType(rule.getTargetType());
        if (rule.getTargetValue() != null) exist.setTargetValue(rule.getTargetValue());
        if (rule.getRemark() != null) exist.setRemark(rule.getRemark());
        if (rule.getEnabled() != null) exist.setEnabled(rule.getEnabled());
        ipRuleMapper.updateById(exist);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        ipRuleMapper.deleteById(id);
        return Result.ok();
    }

    @DeleteMapping("/batch")
    public Result<Void> batchDelete(@RequestBody List<Long> ids) {
        if (ids != null && !ids.isEmpty()) ipRuleMapper.deleteBatchIds(ids);
        return Result.ok();
    }
}
