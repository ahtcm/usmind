package com.usmind.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.usmind.common.Result;
import com.usmind.entity.MemberPlan;
import com.usmind.entity.PayOrder;
import com.usmind.entity.SysUser;
import com.usmind.mapper.MemberPlanMapper;
import com.usmind.mapper.PayOrderMapper;
import com.usmind.mapper.SysUserMapper;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 后台：支付订单管理（列表 / 状态筛选 / 删除） */
@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderController {

    private final PayOrderMapper orderMapper;
    private final SysUserMapper userMapper;
    private final MemberPlanMapper planMapper;

    public AdminOrderController(PayOrderMapper orderMapper, SysUserMapper userMapper, MemberPlanMapper planMapper) {
        this.orderMapper = orderMapper;
        this.userMapper = userMapper;
        this.planMapper = planMapper;
    }

    @GetMapping
    public Result<Map<String, Object>> page(@RequestParam(defaultValue = "1") long page,
                                            @RequestParam(defaultValue = "10") long size,
                                            @RequestParam(required = false) String status,
                                            @RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<PayOrder> qw = new LambdaQueryWrapper<PayOrder>()
                .orderByDesc(PayOrder::getCreatedAt);
        if (status != null && !status.isEmpty()) {
            qw.eq(PayOrder::getStatus, status);
        }
        if (keyword != null && !keyword.isEmpty()) {
            qw.and(w -> w.like(PayOrder::getOutTradeNo, keyword)
                    .or().like(PayOrder::getTransactionId, keyword));
        }
        Page<PayOrder> p = orderMapper.selectPage(new Page<>(page, size), qw);
        // 组装用户名与套餐名
        List<Map<String, Object>> list = new ArrayList<>();
        for (PayOrder o : p.getRecords()) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", o.getId());
            m.put("outTradeNo", o.getOutTradeNo());
            m.put("userId", o.getUserId());
            m.put("amount", o.getAmount());
            m.put("channel", o.getChannel());
            m.put("status", o.getStatus());
            m.put("transactionId", o.getTransactionId());
            m.put("createdAt", o.getCreatedAt());
            m.put("paidAt", o.getPaidAt());
            if (o.getUserId() != null) {
                SysUser u = userMapper.selectById(o.getUserId());
                m.put("username", u == null ? "" : u.getUsername());
            } else {
                m.put("username", "");
            }
            if (o.getPlanId() != null) {
                MemberPlan mp = planMapper.selectById(o.getPlanId());
                m.put("planName", mp == null ? "" : mp.getName());
            } else {
                m.put("planName", "");
            }
            list.add(m);
        }
        Map<String, Object> data = new HashMap<>();
        data.put("list", list);
        data.put("total", p.getTotal());
        return Result.ok(data);
    }

    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        orderMapper.deleteById(id);
        return Result.ok();
    }
}
