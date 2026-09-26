package com.usmind.service;

import java.util.Map;

public interface SettingsService {

    String get(String key, String defaultValue);

    int getInt(String key, int defaultValue);

    void set(String key, String value);

    Map<String, Object> getSettings();

    void updateSettings(Map<String, Object> body);

    /** 供 JwtUtil 动态读取过期毫秒 */
    long getJwtExpireMs();
}
