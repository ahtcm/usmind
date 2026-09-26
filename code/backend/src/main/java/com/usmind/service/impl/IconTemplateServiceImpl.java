package com.usmind.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.usmind.common.BusinessException;
import com.usmind.entity.IconTemplate;
import com.usmind.mapper.IconTemplateMapper;
import com.usmind.service.IconTemplateService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class IconTemplateServiceImpl implements IconTemplateService {

    private final IconTemplateMapper iconTemplateMapper;

    public IconTemplateServiceImpl(IconTemplateMapper iconTemplateMapper) {
        this.iconTemplateMapper = iconTemplateMapper;
    }

    @Override
    public List<Map<String, Object>> listEnabledGrouped() {
        List<IconTemplate> list = iconTemplateMapper.selectList(new LambdaQueryWrapper<IconTemplate>()
                .eq(IconTemplate::getEnabled, 1)
                .orderByAsc(IconTemplate::getSort)
                .orderByAsc(IconTemplate::getId));
        Map<String, List<String>> groups = new LinkedHashMap<>();
        for (IconTemplate i : list) {
            String cat = i.getCategory() == null || i.getCategory().isEmpty() ? "默认" : i.getCategory();
            groups.computeIfAbsent(cat, k -> new ArrayList<>()).add(i.getName());
        }
        List<Map<String, Object>> result = new ArrayList<>();
        groups.forEach((cat, emojis) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("category", cat);
            m.put("emojis", emojis);
            result.add(m);
        });
        return result;
    }

    @Override
    public List<IconTemplate> listAll() {
        return iconTemplateMapper.selectList(new LambdaQueryWrapper<IconTemplate>()
                .orderByAsc(IconTemplate::getSort)
                .orderByAsc(IconTemplate::getId));
    }

    @Override
    public Long create(IconTemplate icon) {
        if (icon.getName() == null || icon.getName().trim().isEmpty()) throw new BusinessException(400, "图标不能为空");
        if (icon.getCategory() == null || icon.getCategory().isEmpty()) icon.setCategory("默认");
        if (icon.getSort() == null) icon.setSort(99);
        if (icon.getEnabled() == null) icon.setEnabled(1);
        if (icon.getBuiltin() == null) icon.setBuiltin(0);
        icon.setCreatedAt(LocalDateTime.now());
        icon.setUpdatedAt(LocalDateTime.now());
        iconTemplateMapper.insert(icon);
        return icon.getId();
    }

    @Override
    public int batchCreate(String category, String emojiText) {
        if (category == null || category.isEmpty()) category = "默认";
        if (emojiText == null || emojiText.isEmpty()) throw new BusinessException(400, "请粘贴 emoji 内容");
        String[] parts = emojiText.split("[\\s,，;；]+");
        int added = 0;
        for (String p : parts) {
            String e = p.trim();
            if (e.isEmpty()) continue;
            Long count = iconTemplateMapper.selectCount(new LambdaQueryWrapper<IconTemplate>()
                    .eq(IconTemplate::getName, e).eq(IconTemplate::getCategory, category));
            if (count != null && count > 0) continue;
            IconTemplate i = new IconTemplate();
            i.setName(e);
            i.setCategory(category);
            i.setSort(99);
            i.setEnabled(1);
            i.setBuiltin(0);
            i.setCreatedAt(LocalDateTime.now());
            i.setUpdatedAt(LocalDateTime.now());
            iconTemplateMapper.insert(i);
            added++;
        }
        return added;
    }

    @Override
    public void delete(Long id) {
        IconTemplate exist = iconTemplateMapper.selectById(id);
        if (exist == null) throw new BusinessException(404, "图标不存在");
        iconTemplateMapper.deleteById(id);
    }

    @Override
    public void deleteBatch(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return;
        for (Long id : ids) {
            delete(id);
        }
    }
}
