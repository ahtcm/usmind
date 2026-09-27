package com.usmind.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.usmind.common.BusinessException;
import com.usmind.entity.MindMap;
import com.usmind.entity.ThemeTemplate;
import com.usmind.mapper.MindMapMapper;
import com.usmind.mapper.ThemeTemplateMapper;
import com.usmind.service.ThemeTemplateService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ThemeTemplateServiceImpl implements ThemeTemplateService {

    private final ThemeTemplateMapper themeTemplateMapper;
    private final MindMapMapper mindMapMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ThemeTemplateServiceImpl(ThemeTemplateMapper themeTemplateMapper, MindMapMapper mindMapMapper) {
        this.themeTemplateMapper = themeTemplateMapper;
        this.mindMapMapper = mindMapMapper;
    }

    @Override
    public List<Map<String, Object>> listEnabled() {
        List<ThemeTemplate> list = themeTemplateMapper.selectList(new LambdaQueryWrapper<ThemeTemplate>()
                .eq(ThemeTemplate::getEnabled, 1)
                .orderByAsc(ThemeTemplate::getSort)
                .orderByAsc(ThemeTemplate::getId));
        List<Map<String, Object>> result = new ArrayList<>();
        for (ThemeTemplate t : list) {
            result.add(toMap(t));
        }
        return result;
    }

    @Override
    public List<ThemeTemplate> listAll() {
        return themeTemplateMapper.selectList(new LambdaQueryWrapper<ThemeTemplate>()
                .orderByAsc(ThemeTemplate::getSort)
                .orderByAsc(ThemeTemplate::getId));
    }

    @Override
    public ThemeTemplate get(Long id) {
        ThemeTemplate t = themeTemplateMapper.selectById(id);
        if (t == null) throw new BusinessException(404, "主题不存在");
        return t;
    }

    @Override
    public Long create(ThemeTemplate t) {
        if (t.getName() == null || t.getName().isEmpty()) throw new BusinessException(400, "主题名称不能为空");
        if (t.getThemeKey() == null || t.getThemeKey().isEmpty()) {
            t.setThemeKey("theme-" + System.currentTimeMillis());
        }
        if (t.getSort() == null) t.setSort(99);
        if (t.getEnabled() == null) t.setEnabled(1);
        if (t.getBuiltin() == null) t.setBuiltin(0);
        t.setCreatedAt(LocalDateTime.now());
        t.setUpdatedAt(LocalDateTime.now());
        themeTemplateMapper.insert(t);
        return t.getId();
    }

    @Override
    public void update(Long id, ThemeTemplate t) {
        ThemeTemplate exist = get(id);
        String oldName = exist.getName();
        if (t.getName() != null) exist.setName(t.getName());
        if (t.getThemeKey() != null) exist.setThemeKey(t.getThemeKey());
        if (t.getBranchColors() != null) exist.setBranchColors(t.getBranchColors());
        if (t.getConfigJson() != null) exist.setConfigJson(t.getConfigJson());
        if (t.getSort() != null) exist.setSort(t.getSort());
        if (t.getEnabled() != null) exist.setEnabled(t.getEnabled());
        exist.setUpdatedAt(LocalDateTime.now());
        themeTemplateMapper.updateById(exist);
        // 主题名称变更时，同步更新已保存导图中的主题名称，保证导图管理/列表显示一致
        if (t.getName() != null && !t.getName().equals(oldName)) {
            try {
                mindMapMapper.update(null, new LambdaUpdateWrapper<MindMap>()
                        .eq(MindMap::getTheme, oldName)
                        .set(MindMap::getTheme, t.getName()));
            } catch (Exception e) {
                // 同步失败不影响主题本身更新
            }
        }
    }

    @Override
    public void delete(Long id) {
        ThemeTemplate exist = get(id);
        if (exist.getBuiltin() != null && exist.getBuiltin() == 1) {
            throw new BusinessException(400, "内置主题不可删除");
        }
        themeTemplateMapper.deleteById(id);
    }

    @Override
    public void deleteBatch(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return;
        for (Long id : ids) {
            delete(id);
        }
    }

    private Map<String, Object> toMap(ThemeTemplate t) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", t.getId());
        m.put("name", t.getName());
        m.put("themeKey", t.getThemeKey());
        try { m.put("branchColors", objectMapper.readValue(t.getBranchColors(), List.class)); }
        catch (Exception e) { m.put("branchColors", new ArrayList<>()); }
        try { m.put("config", objectMapper.readValue(t.getConfigJson(), Object.class)); }
        catch (Exception e) { m.put("config", new HashMap<>()); }
        m.put("sort", t.getSort());
        m.put("builtin", t.getBuiltin());
        return m;
    }
}
