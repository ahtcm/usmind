package com.usmind.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.usmind.dto.MindMapSaveRequest;
import com.usmind.entity.MindMap;

import java.util.List;
import java.util.Map;

public interface MindMapService {
    IPage<MindMap> page(long userId, long page, long size, String keyword);
    Long create(long userId, MindMapSaveRequest req);
    MindMap get(long userId, Long id);
    void update(long userId, Long id, MindMapSaveRequest req);
    void delete(long userId, Long id);
    /** 管理后台批量删除（不校验归属） */
    void deleteBatch(List<Long> ids);
    Map<String, Object> share(long userId, Long id, String password);
    void unshare(long userId, Long id);
    MindMap getByShareToken(String token, String password);
}
