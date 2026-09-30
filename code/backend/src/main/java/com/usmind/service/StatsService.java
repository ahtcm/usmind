package com.usmind.service;

import java.util.Map;

public interface StatsService {
    Map<String, Object> dashboard();

    /** 经营统计：注册/VIP/收入/转化/趋势/分布（start/end 为 yyyy-MM-dd，可为空） */
    Map<String, Object> business(String start, String end);
}
