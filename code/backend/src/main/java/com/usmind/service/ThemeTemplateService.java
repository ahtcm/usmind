package com.usmind.service;

import com.usmind.entity.ThemeTemplate;

import java.util.List;
import java.util.Map;

public interface ThemeTemplateService {
    /** 启用主题列表（用户端读取） */
    List<Map<String, Object>> listEnabled();

    /** 全部主题列表（管理后台） */
    List<ThemeTemplate> listAll();

    ThemeTemplate get(Long id);

    Long create(ThemeTemplate t);

    void update(Long id, ThemeTemplate t);

    void delete(Long id);

    void deleteBatch(List<Long> ids);
}
