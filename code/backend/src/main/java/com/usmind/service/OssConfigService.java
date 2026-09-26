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
}
