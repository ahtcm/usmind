package com.usmind.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.usmind.entity.FileResource;
import com.usmind.entity.MindMap;
import com.usmind.entity.OssConfig;
import com.usmind.entity.PayOrder;
import com.usmind.entity.SysUser;
import com.usmind.mapper.FileResourceMapper;
import com.usmind.mapper.MindMapMapper;
import com.usmind.mapper.OssConfigMapper;
import com.usmind.mapper.PayOrderMapper;
import com.usmind.mapper.MemberPlanMapper;
import com.usmind.mapper.SysUserMapper;
import com.usmind.service.StatsService;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class StatsServiceImpl implements StatsService {

    private final SysUserMapper userMapper;
    private final MindMapMapper mindMapMapper;
    private final FileResourceMapper fileResourceMapper;
    private final OssConfigMapper ossConfigMapper;
    private final com.usmind.mapper.PayOrderMapper payOrderMapper;
    private final com.usmind.mapper.MemberPlanMapper planMapper;

    public StatsServiceImpl(SysUserMapper userMapper, MindMapMapper mindMapMapper,
                            FileResourceMapper fileResourceMapper, OssConfigMapper ossConfigMapper,
                            com.usmind.mapper.PayOrderMapper payOrderMapper,
                            com.usmind.mapper.MemberPlanMapper planMapper) {
        this.userMapper = userMapper;
        this.mindMapMapper = mindMapMapper;
        this.fileResourceMapper = fileResourceMapper;
        this.ossConfigMapper = ossConfigMapper;
        this.payOrderMapper = payOrderMapper;
        this.planMapper = planMapper;
    }

    @Override
    public Map<String, Object> dashboard() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userCount", userMapper.selectCount(null));
        data.put("mindMapCount", mindMapMapper.selectCount(null));
        data.put("shareCount", mindMapMapper.selectCount(new LambdaQueryWrapper<MindMap>()
                .eq(MindMap::getIsShared, 1)));
        data.put("fileCount", fileResourceMapper.selectCount(null));

        // 最近5个注册用户
        Page<SysUser> up = new Page<>(1, 5);
        Page<SysUser> uPage = userMapper.selectPage(up,
                new LambdaQueryWrapper<SysUser>().orderByDesc(SysUser::getId));
        List<Map<String, Object>> recentUsers = uPage.getRecords().stream().map(u -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", u.getId());
            m.put("username", u.getUsername());
            m.put("nickname", u.getNickname());
            m.put("createdAt", u.getCreatedAt());
            return m;
        }).toList();
        data.put("recentUsers", recentUsers);

        // 最近5个更新的导图
        Page<MindMap> mp = new Page<>(1, 5);
        Page<MindMap> mPage = mindMapMapper.selectPage(mp,
                new LambdaQueryWrapper<MindMap>().orderByDesc(MindMap::getUpdatedAt));
        List<Map<String, Object>> recentMindMaps = mPage.getRecords().stream().map(m -> {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("id", m.getId());
            x.put("name", m.getName());
            x.put("userId", m.getUserId());
            x.put("updatedAt", m.getUpdatedAt());
            return x;
        }).toList();
        data.put("recentMindMaps", recentMindMaps);

        // 存储信息
        OssConfig active = ossConfigMapper.selectOne(new LambdaQueryWrapper<OssConfig>()
                .eq(OssConfig::getIsActive, 1).last("LIMIT 1"));
        Map<String, Object> storage = new HashMap<>();
        storage.put("type", active == null ? "local" : active.getType());
        storage.put("bucket", active == null ? "" : active.getBucket());
        Long totalSize = fileResourceMapper.sumSize();
        storage.put("totalSize", totalSize == null ? 0 : totalSize);
        data.put("storage", storage);

        return data;
    }

    @Override
    public Map<String, Object> business(String start, String end) {
        Map<String, Object> data = new LinkedHashMap<>();
        String s0 = (start == null || start.isBlank()) ? null : start.trim();
        String e0 = (end == null || end.isBlank()) ? null : end.trim();
        LocalDateTime now = LocalDateTime.now();
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SysUser> uw;
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<PayOrder> ow;

        // ---- 总览 ----
        long totalUsers = userMapper.selectCount(null);
        long totalVips = userMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SysUser>()
                .gt("member_level", 0).gt("member_expire", now));
        uw = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        if (s0 != null) uw.ge("created_at", s0 + " 00:00:00");
        if (e0 != null) uw.le("created_at", e0 + " 23:59:59");
        long newUsers = userMapper.selectCount(uw);

        ow = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        ow.eq("status", "paid");
        if (s0 != null) ow.ge("paid_at", s0 + " 00:00:00");
        if (e0 != null) ow.le("paid_at", e0 + " 23:59:59");
        ow.select("COALESCE(SUM(amount),0) as income", "COUNT(*) as cnt", "COUNT(DISTINCT user_id) as payUsers");
        List<Map<String, Object>> paidAgg = payOrderMapper.selectMaps(ow);
        double totalIncome = paidAgg.isEmpty() ? 0 : Double.parseDouble(String.valueOf(paidAgg.get(0).get("income")));
        long paidCnt = paidAgg.isEmpty() ? 0 : Long.parseLong(String.valueOf(paidAgg.get(0).get("cnt")));
        long newVips = paidAgg.isEmpty() ? 0 : Long.parseLong(String.valueOf(paidAgg.get(0).get("payUsers")));

        ow = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        ow.eq("status", "paid").ge("paid_at", now.toLocalDate().toString() + " 00:00:00");
        ow.select("COALESCE(SUM(amount),0) as income");
        List<Map<String, Object>> todayAgg = payOrderMapper.selectMaps(ow);
        double todayIncome = todayAgg.isEmpty() ? 0 : Double.parseDouble(String.valueOf(todayAgg.get(0).get("income")));

        long expireSoon = userMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SysUser>()
                .gt("member_level", 0).gt("member_expire", now).le("member_expire", now.plusDays(30)));
        double vipRate = totalUsers > 0 ? Math.round(totalVips * 1000.0 / totalUsers) / 10.0 : 0;

        data.put("totalUsers", totalUsers);
        data.put("newUsers", newUsers);
        data.put("totalVips", totalVips);
        data.put("newVips", newVips);
        data.put("vipRate", vipRate);
        data.put("totalIncome", Math.round(totalIncome * 100) / 100.0);
        data.put("todayIncome", Math.round(todayIncome * 100) / 100.0);
        data.put("paidOrders", paidCnt);
        data.put("expireSoonVips", expireSoon);

        // ---- 按天趋势 ----
        java.util.List<Map<String, Object>> trend = new java.util.ArrayList<>();
        if (s0 != null) {
            LocalDate from = LocalDate.parse(s0);
            LocalDate to = e0 == null ? LocalDate.now() : LocalDate.parse(e0);
            if (to.isBefore(from)) to = from;
            java.util.Map<String, long[]> dayMap = new java.util.TreeMap<>();
            com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SysUser> uq = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
            uq.select("DATE_FORMAT(created_at,'%Y-%m-%d') as d", "COUNT(*) as c").groupBy("d")
                    .ge("created_at", from.toString() + " 00:00:00").le("created_at", to.toString() + " 23:59:59");
            for (Map<String, Object> r : userMapper.selectMaps(uq)) {
                dayMap.computeIfAbsent(String.valueOf(r.get("d")), k -> new long[3])[0] += Long.parseLong(String.valueOf(r.get("c")));
            }
            com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<PayOrder> pq = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
            pq.select("DATE_FORMAT(paid_at,'%Y-%m-%d') as d", "COUNT(DISTINCT user_id) as c").eq("status", "paid")
                    .groupBy("d").ge("paid_at", from.toString() + " 00:00:00").le("paid_at", to.toString() + " 23:59:59");
            for (Map<String, Object> r : payOrderMapper.selectMaps(pq)) {
                dayMap.computeIfAbsent(String.valueOf(r.get("d")), k -> new long[3])[1] += Long.parseLong(String.valueOf(r.get("c")));
            }
            com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<PayOrder> iq = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
            iq.select("DATE_FORMAT(paid_at,'%Y-%m-%d') as d", "COALESCE(SUM(amount),0) as c").eq("status", "paid")
                    .groupBy("d").ge("paid_at", from.toString() + " 00:00:00").le("paid_at", to.toString() + " 23:59:59");
            for (Map<String, Object> r : payOrderMapper.selectMaps(iq)) {
                dayMap.computeIfAbsent(String.valueOf(r.get("d")), k -> new long[3])[2] += Math.round(Double.parseDouble(String.valueOf(r.get("c"))));
            }
            for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
                long[] v = dayMap.getOrDefault(d.toString(), new long[3]);
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("date", d.toString());
                m.put("users", v[0]);
                m.put("vips", v[1]);
                m.put("income", v[2]);
                trend.add(m);
            }
        }
        data.put("trend", trend);

        // ---- 分布 ----
        java.util.List<Map<String, Object>> levelDist = new java.util.ArrayList<>();
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SysUser> lq = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        lq.select("member_level as lv", "COUNT(*) as c").gt("member_expire", now).groupBy("member_level");
        for (Map<String, Object> r : userMapper.selectMaps(lq)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", "LV" + String.valueOf(r.get("lv")));
            m.put("value", Long.parseLong(String.valueOf(r.get("c"))));
            levelDist.add(m);
        }
        data.put("levelDist", levelDist);

        java.util.List<Map<String, Object>> planDist = new java.util.ArrayList<>();
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<PayOrder> plq = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        plq.select("plan_id as pid", "COUNT(*) as c").eq("status", "paid").groupBy("plan_id");
        java.util.List<Map<String, Object>> plRows = payOrderMapper.selectMaps(plq);
        for (Map<String, Object> r : plRows) {
            Long pid = Long.valueOf(String.valueOf(r.get("pid")));
            String name = "套餐#" + pid;
            try {
                com.usmind.entity.MemberPlan p = planMapper.selectById(pid);
                if (p != null && p.getName() != null) name = p.getName();
            } catch (Exception ignored) { }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", name);
            m.put("value", Long.parseLong(String.valueOf(r.get("c"))));
            planDist.add(m);
        }
        data.put("planDist", planDist);

        java.util.List<Map<String, Object>> sourceDist = new java.util.ArrayList<>();
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SysUser> sq = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        sq.select("source", "COUNT(*) as c").groupBy("source");
        for (Map<String, Object> r : userMapper.selectMaps(sq)) {
            String src = String.valueOf(r.get("source"));
            if (src == null || src.isBlank()) src = "local";
            String label = "local".equals(src) ? "本地注册" : ("wechat".equals(src) ? "微信登录" : src);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", label);
            m.put("value", Long.parseLong(String.valueOf(r.get("c"))));
            sourceDist.add(m);
        }
        data.put("sourceDist", sourceDist);

        // 最近支付订单
        Page<PayOrder> op = new Page<>(1, 6);
        Page<PayOrder> oPage = payOrderMapper.selectPage(op,
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PayOrder>()
                        .eq(PayOrder::getStatus, "paid").orderByDesc(PayOrder::getPaidAt));
        java.util.List<Map<String, Object>> recentOrders = oPage.getRecords().stream().map(o -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", o.getId());
            m.put("userId", o.getUserId());
            m.put("amount", o.getAmount());
            m.put("planId", o.getPlanId());
            m.put("paidAt", o.getPaidAt());
            return m;
        }).toList();
        data.put("recentOrders", recentOrders);

        return data;
    }
}
