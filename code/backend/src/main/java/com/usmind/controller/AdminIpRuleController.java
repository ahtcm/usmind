package com.usmind.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.usmind.common.Result;
import com.usmind.entity.IpRule;
import com.usmind.mapper.IpRuleMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/admin/ip-rules")
public class AdminIpRuleController {

    private final IpRuleMapper ipRuleMapper;
    private final StringRedisTemplate redisTemplate;

    public AdminIpRuleController(IpRuleMapper ipRuleMapper, StringRedisTemplate redisTemplate) {
        this.ipRuleMapper = ipRuleMapper;
        this.redisTemplate = redisTemplate;
    }

    /** 删除/禁用黑名单规则时，同步清除 Redis 快速生效缓存，否则限制会残留到 TTL 到期 */
    private void clearRedisCache(IpRule r) {
        if (r == null) return;
        try {
            if ("black".equals(r.getRuleType()) && "ip".equals(r.getTargetType()) && r.getTargetValue() != null) {
                redisTemplate.delete("ipblack:" + r.getTargetValue());
                Set<String> keys = redisTemplate.keys("rate:" + r.getTargetValue() + ":*");
                if (keys != null && !keys.isEmpty()) redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            // Redis 不可用时不影响数据库操作
        }
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
        // 改为禁用或修改为白名单时，清除旧的拉黑缓存
        if (Integer.valueOf(0).equals(exist.getEnabled()) || !"black".equals(exist.getRuleType())) {
            clearRedisCache(exist);
        }
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        IpRule exist = ipRuleMapper.selectById(id);
        ipRuleMapper.deleteById(id);
        clearRedisCache(exist);
        return Result.ok();
    }

    @DeleteMapping("/batch")
    public Result<Void> batchDelete(@RequestBody List<Long> ids) {
        if (ids != null && !ids.isEmpty()) {
            for (Long id : ids) {
                IpRule exist = ipRuleMapper.selectById(id);
                ipRuleMapper.deleteById(id);
                clearRedisCache(exist);
            }
        }
        return Result.ok();
    }
}
