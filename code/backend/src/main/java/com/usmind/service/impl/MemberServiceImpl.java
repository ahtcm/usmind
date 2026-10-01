package com.usmind.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.usmind.common.BusinessException;
import com.usmind.entity.MemberPlan;
import com.usmind.entity.SysUser;
import com.usmind.mapper.MemberPlanMapper;
import com.usmind.mapper.SysUserMapper;
import com.usmind.service.MemberService;
import com.usmind.service.SettingsService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MemberServiceImpl implements MemberService {

    private final SysUserMapper userMapper;
    private final MemberPlanMapper planMapper;
    private final com.usmind.mapper.RedeemCodeMapper redeemCodeMapper;
    private final SettingsService settingsService;

    public MemberServiceImpl(SysUserMapper userMapper, MemberPlanMapper planMapper,
                             com.usmind.mapper.RedeemCodeMapper redeemCodeMapper,
                             SettingsService settingsService) {
        this.userMapper = userMapper;
        this.planMapper = planMapper;
        this.redeemCodeMapper = redeemCodeMapper;
        this.settingsService = settingsService;
    }

    @Override
    public boolean isVip(Long userId) {
        if (userId == null) return false;
        SysUser u = userMapper.selectById(userId);
        return isVip(u);
    }

    private boolean isVip(SysUser u) {
        if (u == null || u.getMemberLevel() == null || u.getMemberLevel() <= 0) return false;
        if (u.getMemberExpire() == null) return false;
        return u.getMemberExpire().isAfter(LocalDateTime.now());
    }

    @Override
    public Map<String, Object> memberInfo(Long userId) {
        SysUser u = userMapper.selectById(userId);
        Map<String, Object> m = new LinkedHashMap<>();
        boolean vip = isVip(u);
        m.put("isVip", vip);
        m.put("level", u == null ? 0 : (u.getMemberLevel() == null ? 0 : u.getMemberLevel()));
        m.put("expire", u == null || u.getMemberExpire() == null ? null : u.getMemberExpire().toString());
        m.put("source", u == null || u.getMemberSource() == null ? "" : u.getMemberSource());
        m.put("feature", featureConfig());
        return m;
    }

    @Override
    public List<Map<String, Object>> plans() {
        List<MemberPlan> list = planMapper.selectList(new LambdaQueryWrapper<MemberPlan>()
                .eq(MemberPlan::getEnabled, 1).orderByAsc(MemberPlan::getSort));
        List<Map<String, Object>> out = new ArrayList<>();
        for (MemberPlan p : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("name", p.getName());
            m.put("level", p.getLevel());
            m.put("price", p.getPrice());
            m.put("originalPrice", p.getOriginalPrice());
            m.put("discountPercent", p.getDiscountPercent());
            m.put("days", p.getDays());
            out.add(m);
        }
        return out;
    }

    @Override
    public Map<String, Object> featureConfig() {
        Map<String, Object> m = new LinkedHashMap<>();
        // 会员专属主题 / 导出格式（逗号分隔 id / 格式名）
        m.put("memberThemeIds", settingsService.get("memberThemeIds", ""));
        m.put("memberExportFormats", settingsService.get("memberExportFormats", ""));
        // 上传资源数量与单文件大小限制（MB）
        m.put("uploadFreeCount", settingsService.getInt("uploadFreeCount", 20));
        m.put("uploadFreeMaxMb", settingsService.getInt("uploadFreeMaxMb", 20));
        m.put("uploadVipCount", settingsService.getInt("uploadVipCount", 200));
        m.put("uploadVipMaxMb", settingsService.getInt("uploadVipMaxMb", 200));
        // 画布个数限制
        m.put("mindmapFreeCount", settingsService.getInt("mindmapFreeCount", 5));
        m.put("mindmapVipCount", settingsService.getInt("mindmapVipCount", 200));
        // 微信支付开关（客户端据此决定是否展示充值入口）
        m.put("wechatPayEnabled", settingsService.getInt("wechatPayEnabled", 0) == 1);
        return m;
    }

    @Override
    public void grant(Long userId, Integer level, int days) {
        SysUser u = userMapper.selectById(userId);
        if (u == null) throw new BusinessException(400, "用户不存在");
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime base = u.getMemberExpire() != null && u.getMemberExpire().isAfter(now)
                ? u.getMemberExpire() : now;
        u.setMemberLevel(level);
        u.setMemberExpire(base.plusDays(days));
        u.setMemberSource("pay");
        userMapper.updateById(u);
    }

    @Override
    public void cancel(Long userId) {
        SysUser u = userMapper.selectById(userId);
        if (u == null) return;
        u.setMemberLevel(0);
        u.setMemberExpire(null);
        u.setMemberSource("");
        userMapper.updateById(u);
    }

    @Override
    public Map<String, Object> redeem(Long userId, String code) {
        if (code == null || code.isBlank()) throw new BusinessException(400, "请输入兑换码");
        com.usmind.entity.RedeemCode target = redeemCodeMapper.selectOne(
                new LambdaQueryWrapper<com.usmind.entity.RedeemCode>()
                        .eq(com.usmind.entity.RedeemCode::getCode, code.trim().toUpperCase())
                        .eq(com.usmind.entity.RedeemCode::getStatus, "active")
                        .last("LIMIT 1"));
        if (target == null) throw new BusinessException(400, "兑换码不存在或已失效");
        SysUser u = userMapper.selectById(userId);
        if (u == null) throw new BusinessException(400, "用户不存在");
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime base = u.getMemberExpire() != null && u.getMemberExpire().isAfter(now)
                ? u.getMemberExpire() : now;
        int level = target.getLevel() == null ? 1 : target.getLevel();
        int days = target.getDays() == null ? 30 : target.getDays();
        u.setMemberLevel(level);
        u.setMemberExpire(base.plusDays(days));
        u.setMemberSource("redeem");
        userMapper.updateById(u);
        target.setStatus("used");
        target.setUsedBy(userId);
        target.setUsedAt(now);
        redeemCodeMapper.updateById(target);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("isVip", true);
        out.put("level", level);
        out.put("expire", u.getMemberExpire().toString());
        out.put("source", "redeem");
        out.put("days", days);
        return out;
    }
}
