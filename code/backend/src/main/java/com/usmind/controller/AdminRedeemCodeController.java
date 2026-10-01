package com.usmind.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.usmind.common.BusinessException;
import com.usmind.common.Result;
import com.usmind.common.SecurityUtil;
import com.usmind.entity.MemberPlan;
import com.usmind.entity.RedeemCode;
import com.usmind.entity.SysUser;
import com.usmind.mapper.MemberPlanMapper;
import com.usmind.mapper.RedeemCodeMapper;
import com.usmind.mapper.SysUserMapper;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletResponse;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 后台：会员兑换码管理。
 * 创建一次性兑换码（可批量），支持按状态筛选（active/used），用户端 POST /api/member/redeem 兑换。
 */
@RestController
@RequestMapping("/api/admin/redeem-codes")
public class AdminRedeemCodeController {

    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final RedeemCodeMapper redeemCodeMapper;
    private final MemberPlanMapper planMapper;
    private final SysUserMapper userMapper;
    private final SecureRandom random = new SecureRandom();

    public AdminRedeemCodeController(RedeemCodeMapper redeemCodeMapper,
                                     MemberPlanMapper planMapper, SysUserMapper userMapper) {
        this.redeemCodeMapper = redeemCodeMapper;
        this.planMapper = planMapper;
        this.userMapper = userMapper;
    }

    @GetMapping
    public Result<Map<String, Object>> page(@RequestParam(defaultValue = "1") long page,
                                            @RequestParam(defaultValue = "10") long size,
                                            @RequestParam(required = false) String status) {
        LambdaQueryWrapper<RedeemCode> qw = new LambdaQueryWrapper<RedeemCode>()
                .orderByDesc(RedeemCode::getId);
        if (status != null && !status.isEmpty()) qw.eq(RedeemCode::getStatus, status);
        Page<RedeemCode> p = redeemCodeMapper.selectPage(new Page<>(page, size), qw);
        List<Map<String, Object>> list = new ArrayList<>();
        for (RedeemCode rc : p.getRecords()) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", rc.getId());
            m.put("code", rc.getCode());
            m.put("planId", rc.getPlanId());
            String planName = "";
            if (rc.getPlanId() != null) {
                MemberPlan plan = planMapper.selectById(rc.getPlanId());
                planName = plan == null ? "" : plan.getName();
            }
            m.put("planName", planName);
            m.put("days", rc.getDays());
            m.put("level", rc.getLevel());
            m.put("status", rc.getStatus());
            m.put("remark", rc.getRemark());
            m.put("createdAt", rc.getCreatedAt());
            if (rc.getUsedBy() != null) {
                SysUser u = userMapper.selectById(rc.getUsedBy());
                m.put("usedBy", rc.getUsedBy());
                m.put("usedByName", u == null ? "" : (u.getNickname() == null || u.getNickname().isBlank() ? u.getUsername() : u.getNickname()));
                m.put("usedAt", rc.getUsedAt());
            } else {
                m.put("usedBy", null);
                m.put("usedByName", "");
                m.put("usedAt", null);
            }
            list.add(m);
        }
        Map<String, Object> data = new HashMap<>();
        data.put("list", list);
        data.put("total", p.getTotal());
        return Result.ok(data);
    }

    /**
     * 创建兑换码：body { planId?, days?, level?, remark?, count? }
     * planId 存在则取套餐 days/level；否则用自定义 days/level（默认 30 天/1 级）。
     * count 默认 1，最多 100，批量生成。
     */
    @PostMapping
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        int count = body.get("count") == null ? 1 : Math.max(1, Math.min(100, Integer.parseInt(String.valueOf(body.get("count")))));
        Long planId = null;
        Integer days = 30, level = 1;
        if (body.get("planId") != null) {
            try {
                planId = Long.valueOf(String.valueOf(body.get("planId")));
                MemberPlan plan = planMapper.selectById(planId);
                if (plan != null) { days = plan.getDays(); level = plan.getLevel(); }
            } catch (Exception ignored) {}
        } else {
            if (body.get("days") != null) days = Integer.parseInt(String.valueOf(body.get("days")));
            if (body.get("level") != null) level = Integer.parseInt(String.valueOf(body.get("level")));
        }
        String remark = body.get("remark") == null ? "" : String.valueOf(body.get("remark"));
        Long creator = SecurityUtil.currentUserId();
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            RedeemCode rc = new RedeemCode();
            rc.setCode(genCode());
            rc.setPlanId(planId);
            rc.setDays(days);
            rc.setLevel(level);
            rc.setStatus("active");
            rc.setRemark(remark);
            rc.setCreatedBy(creator);
            rc.setCreatedAt(LocalDateTime.now());
            redeemCodeMapper.insert(rc);
            codes.add(rc.getCode());
        }
        Map<String, Object> data = new HashMap<>();
        data.put("created", count);
        data.put("codes", codes);
        data.put("days", days);
        data.put("level", level);
        return Result.ok(data);
    }

    /** 批量导出（筛选后全部，CSV 格式，Excel 可直接打开） */
    @GetMapping("/export")
    public void export(@RequestParam(required = false) String status, HttpServletResponse response) throws Exception {
        LambdaQueryWrapper<RedeemCode> qw = new LambdaQueryWrapper<RedeemCode>()
                .orderByDesc(RedeemCode::getId);
        if (status != null && !status.isEmpty()) qw.eq(RedeemCode::getStatus, status);
        List<RedeemCode> all = redeemCodeMapper.selectList(qw);
        StringBuilder sb = new StringBuilder();
        sb.append("\uFEFF兑换码,套餐,时长(天),级别,状态,使用者,兑换时间,备注\n");
        for (RedeemCode rc : all) {
            String planName = "";
            if (rc.getPlanId() != null) {
                MemberPlan plan = planMapper.selectById(rc.getPlanId());
                planName = plan == null ? "" : plan.getName();
            }
            String usedByName = "";
            String usedAt = "";
            if (rc.getUsedBy() != null) {
                SysUser u = userMapper.selectById(rc.getUsedBy());
                usedByName = u == null ? "" : (u.getNickname() == null || u.getNickname().isBlank() ? u.getUsername() : u.getNickname());
                usedAt = rc.getUsedAt() == null ? "" : rc.getUsedAt().toString().replace("T", " ").substring(0, 19);
            }
            String statusLabel = "active".equals(rc.getStatus()) ? "可用" : "used".equals(rc.getStatus()) ? "已兑换" : "已停用";
            sb.append(rc.getCode()).append(",")
              .append(csv(planName)).append(",")
              .append(rc.getDays() == null ? "" : rc.getDays()).append(",")
              .append(rc.getLevel() == null ? "" : rc.getLevel()).append(",")
              .append(statusLabel).append(",")
              .append(csv(usedByName)).append(",")
              .append(usedAt).append(",")
              .append(csv(rc.getRemark() == null ? "" : rc.getRemark())).append("\n");
        }
        byte[] bytes = sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=redeem-codes-" + System.currentTimeMillis() + ".csv");
        response.getOutputStream().write(bytes);
        response.getOutputStream().flush();
    }

    private static String csv(String s) {
        if (s == null) return "";
        s = s.replace("\"", "\"\"");
        return s.contains(",") || s.contains("\n") ? "\"" + s + "\"" : s;
    }

    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        redeemCodeMapper.deleteById(id);
        return Result.ok();
    }

    private String genCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            StringBuilder sb = new StringBuilder(16);
            for (int i = 0; i < 16; i++) sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
            String code = sb.toString();
            Long cnt = redeemCodeMapper.selectCount(new LambdaQueryWrapper<RedeemCode>()
                    .eq(RedeemCode::getCode, code));
            if (cnt == null || cnt == 0) return code;
        }
        throw new BusinessException(500, "生成兑换码失败，请重试");
    }
}
