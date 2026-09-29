package com.usmind.service;

import com.usmind.dto.OssConfigRequest;
import com.usmind.entity.OssConfig;

import java.util.List;
import java.util.Map;

public interface OssConfigService {
    List<OssConfig> list();
    Long create(OssConfigRequest req);
    void update(Long id, OssConfigRequest req);
    void delete(Long id);
    void deleteBatch(List<Long> ids);
    void activate(Long id);
    Map<String, Object> test(Long id);
    /** 将本地存储文件迁移到当前激活的OSS，并全库替换URL引用；deleteAfter=true 时迁移成功后删除本地文件 */
    Map<String, Object> migrate(boolean deleteAfter);
}
