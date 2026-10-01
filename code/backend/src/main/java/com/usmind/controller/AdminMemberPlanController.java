package com.usmind.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.usmind.common.Result;
import com.usmind.entity.MemberPlan;
import com.usmind.mapper.MemberPlanMapper;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 后台：会员套餐价格管理。
 * 修改 price/originalPrice/discountPercent 后，客户端 /api/member/plans 实时生效。
 */
@RestController
@RequestMapping("/api/admin/member-plans")
public class AdminMemberPlanController {

    private final MemberPlanMapper planMapper;

    public AdminMemberPlanController(MemberPlanMapper planMapper) {
        this.planMapper = planMapper;
    }

    @GetMapping
    public Result<List<MemberPlan>> list() {
        return Result.ok(planMapper.selectList(new LambdaQueryWrapper<MemberPlan>()
                .orderByAsc(MemberPlan::getSort).orderByAsc(MemberPlan::getId)));
    }

    /** 新增套餐：body { name, level, price, originalPrice, discountPercent, days, sort, enabled } */
    @PostMapping
    public Result<MemberPlan> create(@RequestBody Map<String, Object> body) {
        MemberPlan p = new MemberPlan();
        p.setName(str(body.get("name")));
        p.setLevel(intv(body.get("level"), 1));
        p.setPrice(dec(body.get("price")));
        p.setOriginalPrice(dec(body.get("originalPrice")));
        p.setDiscountPercent(intv(body.get("discountPercent"), 0));
        p.setDays(intv(body.get("days"), 30));
        p.setSort(intv(body.get("sort"), 0));
        p.setEnabled(intv(body.get("enabled"), 1));
        planMapper.insert(p);
        return Result.ok(p);
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        MemberPlan p = planMapper.selectById(id);
        if (p == null) return Result.error(404, "套餐不存在");
        if (body.get("name") != null) p.setName(str(body.get("name")));
        if (body.get("level") != null) p.setLevel(intv(body.get("level"), 1));
        if (body.get("price") != null) p.setPrice(dec(body.get("price")));
        if (body.get("originalPrice") != null) p.setOriginalPrice(dec(body.get("originalPrice")));
        if (body.get("discountPercent") != null) p.setDiscountPercent(intv(body.get("discountPercent"), 0));
        if (body.get("days") != null) p.setDays(intv(body.get("days"), 30));
        if (body.get("sort") != null) p.setSort(intv(body.get("sort"), 0));
        if (body.get("enabled") != null) p.setEnabled(intv(body.get("enabled"), 1));
        planMapper.updateById(p);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        planMapper.deleteById(id);
        return Result.ok();
    }

    private String str(Object o) { return o == null ? "" : String.valueOf(o); }
    private int intv(Object o, int def) {
        if (o == null) return def;
        try { return Integer.parseInt(String.valueOf(o)); } catch (Exception e) { return def; }
    }
    private BigDecimal dec(Object o) {
        if (o == null) return BigDecimal.ZERO;
        try { return new BigDecimal(String.valueOf(o)); } catch (Exception e) { return BigDecimal.ZERO; }
    }
}
