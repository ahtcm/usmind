package com.usmind.service;

import com.usmind.entity.IconTemplate;

import java.util.List;
import java.util.Map;

public interface IconTemplateService {
    /** 用户端读取启用的图标（公开），按分类分组 */
    List<Map<String, Object>> listEnabledGrouped();

    /** 管理后台：全部图标 */
    List<IconTemplate> listAll();

    /** 新增单个图标 */
    Long create(IconTemplate icon);

    /** 批量导入（emoji 文本，按行/空格/逗号分隔） */
    int batchCreate(String category, String emojiText);

    /** 删除 */
    void delete(Long id);

    /** 批量删除 */
    void deleteBatch(List<Long> ids);
}
