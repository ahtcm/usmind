package com.usmind.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.usmind.entity.FileResource;
import com.usmind.entity.MindMap;
import com.usmind.entity.OssConfig;
import com.usmind.entity.SysUser;
import com.usmind.mapper.FileResourceMapper;
import com.usmind.mapper.MindMapMapper;
import com.usmind.mapper.OssConfigMapper;
import com.usmind.mapper.SysUserMapper;
import com.usmind.service.StatsService;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class StatsServiceImpl implements StatsService {

    private final SysUserMapper userMapper;
    private final MindMapMapper mindMapMapper;
    private final FileResourceMapper fileResourceMapper;
    private final OssConfigMapper ossConfigMapper;

    public StatsServiceImpl(SysUserMapper userMapper, MindMapMapper mindMapMapper,
                            FileResourceMapper fileResourceMapper, OssConfigMapper ossConfigMapper) {
        this.userMapper = userMapper;
        this.mindMapMapper = mindMapMapper;
        this.fileResourceMapper = fileResourceMapper;
        this.ossConfigMapper = ossConfigMapper;
    }

    @Override
    public Map<String, Object> dashboard() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userCount", userMapper.selectCount(null));
        data.put("mindMapCount", mindMapMapper.selectCount(null));
        data.put("shareCount", mindMapMapper.selectCount(new LambdaQueryWrapper<MindMap>()
                .eq(MindMap::getIsShared, 1)));
        data.put("fileCount", fileResourceMapper.selectCount(null));

        // 最近5个注册用户
        Page<SysUser> up = new Page<>(1, 5);
        Page<SysUser> uPage = userMapper.selectPage(up,
                new LambdaQueryWrapper<SysUser>().orderByDesc(SysUser::getId));
        List<Map<String, Object>> recentUsers = uPage.getRecords().stream().map(u -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", u.getId());
            m.put("username", u.getUsername());
            m.put("nickname", u.getNickname());
            m.put("createdAt", u.getCreatedAt());
            return m;
        }).toList();
        data.put("recentUsers", recentUsers);

        // 最近5个更新的导图
        Page<MindMap> mp = new Page<>(1, 5);
        Page<MindMap> mPage = mindMapMapper.selectPage(mp,
                new LambdaQueryWrapper<MindMap>().orderByDesc(MindMap::getUpdatedAt));
        List<Map<String, Object>> recentMindMaps = mPage.getRecords().stream().map(m -> {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("id", m.getId());
            x.put("name", m.getName());
            x.put("userId", m.getUserId());
            x.put("updatedAt", m.getUpdatedAt());
            return x;
        }).toList();
        data.put("recentMindMaps", recentMindMaps);

        // 存储信息
        OssConfig active = ossConfigMapper.selectOne(new LambdaQueryWrapper<OssConfig>()
                .eq(OssConfig::getIsActive, 1).last("LIMIT 1"));
        Map<String, Object> storage = new HashMap<>();
        storage.put("type", active == null ? "local" : active.getType());
        storage.put("bucket", active == null ? "" : active.getBucket());
        Long totalSize = fileResourceMapper.sumSize();
        storage.put("totalSize", totalSize == null ? 0 : totalSize);
        data.put("storage", storage);

        return data;
    }
}
