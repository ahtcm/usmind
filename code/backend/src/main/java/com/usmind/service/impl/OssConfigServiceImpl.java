package com.usmind.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.usmind.common.BusinessException;
import com.usmind.common.SecretCipher;
import com.usmind.dto.OssConfigRequest;
import com.usmind.entity.OssConfig;
import com.usmind.mapper.OssConfigMapper;
import com.usmind.service.OssConfigService;
import com.usmind.storage.StorageFactory;
import com.usmind.storage.StorageStrategy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class OssConfigServiceImpl implements OssConfigService {

    private final OssConfigMapper ossConfigMapper;
    private final SecretCipher secretCipher;
    private final StorageFactory storageFactory;
    private final StringRedisTemplate redisTemplate;

    public OssConfigServiceImpl(OssConfigMapper ossConfigMapper, SecretCipher secretCipher,
                                StorageFactory storageFactory, StringRedisTemplate redisTemplate) {
        this.ossConfigMapper = ossConfigMapper;
        this.secretCipher = secretCipher;
        this.storageFactory = storageFactory;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public List<OssConfig> list() {
        List<OssConfig> list = ossConfigMapper.selectList(new LambdaQueryWrapper<OssConfig>()
                .orderByDesc(OssConfig::getIsActive)
                .orderByAsc(OssConfig::getId));
        // 不返回密钥
        list.forEach(c -> c.setSecretKey(null));
        return list;
    }

    @Override
    public Long create(OssConfigRequest req) {
        if (!StringUtils.hasText(req.getName()) || !StringUtils.hasText(req.getType())) {
            throw new BusinessException(400, "名称和类型不能为空");
        }
        OssConfig c = new OssConfig();
        fill(c, req, true);
        c.setIsActive(0);
        ossConfigMapper.insert(c);
        return c.getId();
    }

    @Override
    public void update(Long id, OssConfigRequest req) {
        OssConfig c = ossConfigMapper.selectById(id);
        if (c == null) throw new BusinessException(404, "配置不存在");
        fill(c, req, false);
        ossConfigMapper.updateById(c);
        if (c.getIsActive() != null && c.getIsActive() == 1) {
            storageFactory.refresh();
            clearCache();
        }
    }

    private void fill(OssConfig c, OssConfigRequest req, boolean isCreate) {
        if (req.getName() != null) c.setName(req.getName());
        if (req.getType() != null) c.setType(req.getType());
        if (req.getEndpoint() != null) c.setEndpoint(req.getEndpoint());
        if (req.getAccessKey() != null) c.setAccessKey(req.getAccessKey());
        if (StringUtils.hasText(req.getSecretKey())) {
            c.setSecretKey(secretCipher.encrypt(req.getSecretKey()));
        } else if (isCreate) {
            c.setSecretKey("");
        }
        if (req.getBucket() != null) c.setBucket(req.getBucket());
        if (req.getRegion() != null) c.setRegion(req.getRegion());
        if (req.getDomain() != null) c.setDomain(req.getDomain());
        if (req.getConfigJson() != null) c.setConfigJson(req.getConfigJson());
    }

    @Override
    public void delete(Long id) {
        OssConfig c = ossConfigMapper.selectById(id);
        if (c == null) throw new BusinessException(404, "配置不存在");
        if (c.getIsActive() != null && c.getIsActive() == 1) {
            throw new BusinessException(400, "不能删除当前激活的配置");
        }
        ossConfigMapper.deleteById(id);
    }

    @Override
    public void activate(Long id) {
        OssConfig c = ossConfigMapper.selectById(id);
        if (c == null) throw new BusinessException(404, "配置不存在");
        // 全部置为非激活
        OssConfig reset = new OssConfig();
        reset.setIsActive(0);
        ossConfigMapper.update(reset, new LambdaQueryWrapper<>());
        c.setIsActive(1);
        ossConfigMapper.updateById(c);
        // 运行时热切换
        storageFactory.refresh();
        clearCache();
    }

    @Override
    public Map<String, Object> test(Long id) {
        OssConfig c = ossConfigMapper.selectById(id);
        if (c == null) throw new BusinessException(404, "配置不存在");
        Map<String, Object> result = new HashMap<>();
        try {
            StorageStrategy s = storageFactory.buildForTest(c);
            s.test();
            result.put("success", true);
            result.put("message", "连接成功");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "连接失败: " + e.getMessage());
        }
        return result;
    }

    private void clearCache() {
        try {
            redisTemplate.delete("oss:active-config");
        } catch (Exception ignored) {}
    }
}
