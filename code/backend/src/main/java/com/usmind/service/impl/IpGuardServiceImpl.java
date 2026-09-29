package com.usmind.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.usmind.common.BusinessException;
import com.usmind.entity.IpRule;
import com.usmind.mapper.IpRuleMapper;
import com.usmind.service.IpGuardService;
import com.usmind.service.SettingsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class IpGuardServiceImpl implements IpGuardService {

    private final IpRuleMapper ipRuleMapper;
    private final StringRedisTemplate redisTemplate;
    private final SettingsService settingsService;

    public IpGuardServiceImpl(IpRuleMapper ipRuleMapper, StringRedisTemplate redisTemplate,
                              SettingsService settingsService) {
        this.ipRuleMapper = ipRuleMapper;
        this.redisTemplate = redisTemplate;
        this.settingsService = settingsService;
    }

    @Override
    public void check(String ip, Long userId) {
        if (settingsService.getInt("ipGuardEnabled", 1) != 1) return;
        if (ip == null || ip.isBlank()) return;
        // 白名单优先
        if (match("white", "ip", ip) || (userId != null && match("white", "user_id", String.valueOf(userId)))) return;
        // 黑名单拒绝
        if (match("black", "ip", ip) || (userId != null && match("black", "user_id", String.valueOf(userId)))) {
            throw new BusinessException(403, "访问已被限制（黑名单）");
        }
        // 频率限制：按 ip + 当前时间窗口计数，超限自动拉黑
        int limit = settingsService.getInt("ipRateLimit", 60);
        int window = settingsService.getInt("ipRateWindow", 60);
        String key = "rate:" + ip + ":" + (System.currentTimeMillis() / (window * 1000L));
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, window, TimeUnit.SECONDS);
        }
        if (count != null && count > limit) {
            autoBlack(ip);
            throw new BusinessException(403, "请求过于频繁，已临时限制访问");
        }
    }

    private void autoBlack(String ip) {
        int blackSeconds = settingsService.getInt("ipAutoBlackSeconds", 3600);
        try {
            // 避免重复拉黑
            Long exists = ipRuleMapper.selectCount(new LambdaQueryWrapper<IpRule>()
                    .eq(IpRule::getRuleType, "black").eq(IpRule::getTargetType, "ip")
                    .eq(IpRule::getTargetValue, ip).eq(IpRule::getEnabled, 1));
            if (exists != null && exists > 0) return;
            IpRule rule = new IpRule();
            rule.setRuleType("black");
            rule.setTargetType("ip");
            rule.setTargetValue(ip);
            rule.setRemark("超频自动拉黑");
            rule.setEnabled(1);
            rule.setCreatedAt(LocalDateTime.now());
            ipRuleMapper.insert(rule);
            log.warn("IP 超频自动拉黑: {}", ip);
        } catch (Exception e) {
            log.warn("自动拉黑失败: {}", e.getMessage());
        }
        // 拉黑期间放 Redis 快速生效；到期后清理
        redisTemplate.opsForValue().set("ipblack:" + ip, "1", blackSeconds, TimeUnit.SECONDS);
        redisTemplate.delete("rate:" + ip + ":*");
    }

    private boolean match(String ruleType, String targetType, String value) {
        // Redis 快速缓存优先（自动拉黑）
        if ("black".equals(ruleType) && "ip".equals(targetType)) {
            Boolean b = redisTemplate.hasKey("ipblack:" + value);
            if (Boolean.TRUE.equals(b)) return true;
        }
        Long cnt = ipRuleMapper.selectCount(new LambdaQueryWrapper<IpRule>()
                .eq(IpRule::getRuleType, ruleType)
                .eq(IpRule::getTargetType, targetType)
                .eq(IpRule::getTargetValue, value)
                .eq(IpRule::getEnabled, 1));
        return cnt != null && cnt > 0;
    }

    @Override
    public boolean ipInWhite(String ip) {
        return match("white", "ip", ip);
    }

    @Override
    public boolean userInWhite(Long userId) {
        return userId != null && match("white", "user_id", String.valueOf(userId));
    }
}
