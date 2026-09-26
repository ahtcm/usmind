package com.usmind.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.usmind.common.BusinessException;
import com.usmind.dto.MindMapSaveRequest;
import com.usmind.entity.MindMap;
import com.usmind.mapper.MindMapMapper;
import com.usmind.service.MindMapService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class MindMapServiceImpl implements MindMapService {

    private final MindMapMapper mindMapMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MindMapServiceImpl(MindMapMapper mindMapMapper) {
        this.mindMapMapper = mindMapMapper;
    }

    /** 导图数据统一序列化为 JSON 字符串存储 */
    private String toJson(Object data) {
        if (data == null) return null;
        if (data instanceof String s) return s;
        try {
            return objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            return String.valueOf(data);
        }
    }

    @Override
    public IPage<MindMap> page(long userId, long page, long size, String keyword) {
        Page<MindMap> p = new Page<>(page, size);
        LambdaQueryWrapper<MindMap> qw = new LambdaQueryWrapper<>();
        qw.eq(MindMap::getUserId, userId);
        if (StringUtils.hasText(keyword)) {
            qw.like(MindMap::getName, keyword);
        }
        qw.orderByDesc(MindMap::getUpdatedAt);
        // 列表不返回大字段 data
        Page<MindMap> result = mindMapMapper.selectPage(p, qw.select(MindMap.class, f ->
                !f.getColumn().equals("data")));
        return result;
    }

    @Override
    public Long create(long userId, MindMapSaveRequest req) {
        MindMap m = new MindMap();
        m.setUserId(userId);
        m.setName(StringUtils.hasText(req.getName()) ? req.getName() : "未命名");
        m.setData(toJson(req.getData()));
        m.setLayout(StringUtils.hasText(req.getLayout()) ? req.getLayout() : "logicalStructure");
        m.setTheme(StringUtils.hasText(req.getTheme()) ? req.getTheme() : "XMind 浅色");
        m.setViewData(toJson(req.getViewData()));
        m.setManualStyles(req.getManualStyles() != null ? toJson(req.getManualStyles()) : null);
        m.setIsShared(0);
        mindMapMapper.insert(m);
        return m.getId();
    }

    @Override
    public MindMap get(long userId, Long id) {
        MindMap m = mindMapMapper.selectById(id);
        if (m == null || !m.getUserId().equals(userId)) {
            throw new BusinessException(404, "导图不存在");
        }
        return m;
    }

    @Override
    public void update(long userId, Long id, MindMapSaveRequest req) {
        MindMap m = mindMapMapper.selectById(id);
        if (m == null || !m.getUserId().equals(userId)) {
            throw new BusinessException(404, "导图不存在");
        }
        if (req.getName() != null) m.setName(req.getName());
        if (req.getData() != null) m.setData(toJson(req.getData()));
        if (req.getLayout() != null) m.setLayout(req.getLayout());
        if (req.getTheme() != null) m.setTheme(req.getTheme());
        if (req.getViewData() != null) m.setViewData(toJson(req.getViewData()));
        if (req.getManualStyles() != null) m.setManualStyles(toJson(req.getManualStyles()));
        mindMapMapper.updateById(m);
    }

    @Override
    public void delete(long userId, Long id) {
        MindMap m = mindMapMapper.selectById(id);
        if (m == null || !m.getUserId().equals(userId)) {
            throw new BusinessException(404, "导图不存在");
        }
        mindMapMapper.deleteById(id);
    }

    @Override
    public void deleteBatch(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return;
        for (Long id : ids) {
            MindMap m = mindMapMapper.selectById(id);
            if (m != null) mindMapMapper.deleteById(id);
        }
    }

    @Override
    public Map<String, Object> share(long userId, Long id, String password) {
        MindMap m = mindMapMapper.selectById(id);
        if (m == null || !m.getUserId().equals(userId)) {
            throw new BusinessException(404, "导图不存在");
        }
        if (!StringUtils.hasText(m.getShareToken())) {
            m.setShareToken(UUID.randomUUID().toString().replace("-", ""));
        }
        m.setIsShared(1);
        m.setSharePassword(password == null ? "" : password);
        mindMapMapper.updateById(m);
        Map<String, Object> data = new HashMap<>();
        data.put("shareToken", m.getShareToken());
        data.put("shareUrl", "/share/" + m.getShareToken());
        return data;
    }

    @Override
    public void unshare(long userId, Long id) {
        MindMap m = mindMapMapper.selectById(id);
        if (m == null || !m.getUserId().equals(userId)) {
            throw new BusinessException(404, "导图不存在");
        }
        m.setIsShared(0);
        m.setSharePassword("");
        mindMapMapper.updateById(m);
    }

    @Override
    public MindMap getByShareToken(String token, String password) {
        MindMap m = mindMapMapper.selectOne(new LambdaQueryWrapper<MindMap>()
                .eq(MindMap::getShareToken, token)
                .eq(MindMap::getIsShared, 1)
                .last("LIMIT 1"));
        if (m == null) throw new BusinessException(404, "分享不存在或已取消");
        if (StringUtils.hasText(m.getSharePassword())) {
            if (!m.getSharePassword().equals(password)) {
                throw new BusinessException(403, "需要分享密码");
            }
        }
        m.setViewCount(m.getViewCount() == null ? 1 : m.getViewCount() + 1);
        mindMapMapper.updateById(m);
        return m;
    }
}
