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
    private final com.usmind.mapper.OrgMemberMapper orgMemberMapper;
    private final com.usmind.mapper.OrgCollabMapper orgCollabMapper;
    private final com.usmind.mapper.SysUserMapper sysUserMapper;
    private final com.usmind.service.SettingsService settingsService;
    private final com.usmind.service.OrgService orgService;
    private final com.usmind.ws.MindMapWsHandler mindMapWsHandler;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MindMapServiceImpl(MindMapMapper mindMapMapper,
                              com.usmind.mapper.OrgMemberMapper orgMemberMapper,
                              com.usmind.mapper.OrgCollabMapper orgCollabMapper,
                              com.usmind.mapper.SysUserMapper sysUserMapper,
                              com.usmind.service.SettingsService settingsService,
                              com.usmind.service.OrgService orgService,
                              com.usmind.ws.MindMapWsHandler mindMapWsHandler) {
        this.mindMapMapper = mindMapMapper;
        this.orgMemberMapper = orgMemberMapper;
        this.orgCollabMapper = orgCollabMapper;
        this.sysUserMapper = sysUserMapper;
        this.settingsService = settingsService;
        this.orgService = orgService;
        this.mindMapWsHandler = mindMapWsHandler;
    }

    /** 是否拥有该导图的读写权限：属主本人，或所属机构协作成员 */
    private boolean canAccess(long userId, MindMap m) {
        if (m.getUserId().equals(userId)) return true;
        return orgService.canAccessMindMap(userId, m.getId());
    }

    private boolean isVip(Long userId) {
        com.usmind.entity.SysUser u = sysUserMapper.selectById(userId);
        if (u == null || u.getMemberLevel() == null || u.getMemberLevel() <= 0) return false;
        if (u.getMemberExpire() == null) return false;
        return u.getMemberExpire().isAfter(java.time.LocalDateTime.now());
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
        // 合并本人参与的机构协作导图（排除已属自己的），标记 isCollab
        java.util.List<Long> collabIds = new java.util.ArrayList<>();
        try {
            java.util.List<com.usmind.entity.OrgCollab> cs = orgCollabOfUser(userId);
            for (com.usmind.entity.OrgCollab c : cs) {
                com.usmind.entity.MindMap m = mindMapMapper.selectById(c.getMindMapId());
                if (m != null && !m.getUserId().equals(userId)) collabIds.add(m.getId());
            }
        } catch (Exception e) { /* 机构服务异常不影响个人列表 */ }
        if (!collabIds.isEmpty()) {
            java.util.List<MindMap> collabs = mindMapMapper.selectList(new LambdaQueryWrapper<MindMap>()
                    .in(MindMap::getId, collabIds).select(MindMap.class, f -> !f.getColumn().equals("data")));
            // 空分页时 records 可能是不可变空列表，先包装为可变列表再合并
            java.util.List<MindMap> records = new java.util.ArrayList<>(result.getRecords());
            java.util.Set<Long> ownIds = new java.util.HashSet<>();
            for (MindMap m : records) ownIds.add(m.getId());
            for (MindMap m : collabs) {
                if (ownIds.contains(m.getId())) continue;
                m.setIsCollab(true);
                records.add(m);
                result.setTotal(result.getTotal() + 1);
            }
            result.setRecords(records);
        }
        return result;
    }

    private java.util.List<com.usmind.entity.OrgCollab> orgCollabOfUser(long userId) {
        // 用户所在机构的全部协作导图
        java.util.List<com.usmind.entity.OrgMember> ms = orgMemberMapper.selectList(
                new LambdaQueryWrapper<com.usmind.entity.OrgMember>().eq(com.usmind.entity.OrgMember::getUserId, userId));
        if (ms.isEmpty()) return java.util.Collections.emptyList();
        java.util.List<Long> orgIds = new java.util.ArrayList<>();
        for (com.usmind.entity.OrgMember m : ms) orgIds.add(m.getOrgId());
        return orgCollabMapper.selectList(new LambdaQueryWrapper<com.usmind.entity.OrgCollab>()
                .in(com.usmind.entity.OrgCollab::getOrgId, orgIds));
    }

    @Override
    public Long create(long userId, MindMapSaveRequest req) {
        // 画布个数限制：非会员 mindmapFreeCount / 会员 mindmapVipCount
        boolean vip = isVip(userId);
        int maxCount = settingsService.getInt(vip ? "mindmapVipCount" : "mindmapFreeCount", 200);
        Long count = mindMapMapper.selectCount(new LambdaQueryWrapper<MindMap>().eq(MindMap::getUserId, userId));
        if (count != null && count >= maxCount) {
            throw new BusinessException(400, "画布数量已达上限(" + maxCount + "个)" + (vip ? "" : "，升级会员可提升上限"));
        }
        MindMap m = new MindMap();
        m.setUserId(userId);
        m.setName(StringUtils.hasText(req.getName()) ? req.getName() : "未命名");
        m.setData(toJson(req.getData()));
        m.setLayout(StringUtils.hasText(req.getLayout()) ? req.getLayout() : "logicalStructure");
        m.setTheme(StringUtils.hasText(req.getTheme()) ? req.getTheme() : "晨曦白");
        m.setViewData(toJson(req.getViewData()));
        m.setManualStyles(req.getManualStyles() != null ? toJson(req.getManualStyles()) : null);
        m.setIsShared(0);
        mindMapMapper.insert(m);
        return m.getId();
    }

    @Override
    public MindMap get(long userId, Long id) {
        MindMap m = mindMapMapper.selectById(id);
        if (m == null || !canAccess(userId, m)) {
            throw new BusinessException(404, "导图不存在");
        }
        return m;
    }

    @Override
    public int update(long userId, Long id, MindMapSaveRequest req) {
        MindMap m = mindMapMapper.selectById(id);
        if (m == null || !canAccess(userId, m)) {
            throw new BusinessException(404, "导图不存在");
        }
        // 协作冲突检测（乐观锁）：前端传入 baseVersion，与当前版本不一致且未强制覆盖时返回 409
        int cur = m.getVersion() == null ? 0 : m.getVersion();
        boolean force = req.getForce() != null && req.getForce();
        if (!force && req.getBaseVersion() != null && req.getBaseVersion() != cur) {
            throw new BusinessException(409, "导图已被其他成员修改，请选择「加载最新」或「以我的为准覆盖」");
        }
        if (req.getName() != null) m.setName(req.getName());
        if (req.getData() != null) m.setData(toJson(req.getData()));
        if (req.getLayout() != null) m.setLayout(req.getLayout());
        if (req.getTheme() != null) m.setTheme(req.getTheme());
        if (req.getViewData() != null) m.setViewData(toJson(req.getViewData()));
        if (req.getManualStyles() != null) m.setManualStyles(toJson(req.getManualStyles()));
        m.setVersion(cur + 1);
        mindMapMapper.updateById(m);
        // 协作实时同步：广播变更通知（前端据此自动加载最新 / 弹更新提示）
        try {
            String updater = "";
            com.usmind.entity.SysUser u = sysUserMapper.selectById(userId);
            if (u != null && u.getNickname() != null) updater = u.getNickname();
            Map<String, Object> msg = new HashMap<>();
            msg.put("event", "mindmap_updated");
            msg.put("mindMapId", id);
            msg.put("version", m.getVersion());
            msg.put("updatedBy", userId);
            msg.put("updatedByName", updater);
            msg.put("ts", System.currentTimeMillis());
            mindMapWsHandler.broadcast(String.valueOf(id), objectMapper.writeValueAsString(msg));
        } catch (Exception e) {
            // 广播失败不影响保存
        }
        return m.getVersion();
    }

    @Override
    public void delete(long userId, Long id) {
        MindMap m = mindMapMapper.selectById(id);
        if (m == null || !m.getUserId().equals(userId)) {
            throw new BusinessException(404, "导图不存在");
        }
        mindMapMapper.deleteById(id);
        // 删除协作关系
        orgCollabMapper.delete(new LambdaQueryWrapper<com.usmind.entity.OrgCollab>()
                .eq(com.usmind.entity.OrgCollab::getMindMapId, id));
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
        boolean alreadyShared = StringUtils.hasText(m.getShareToken());
        if (!alreadyShared) {
            m.setShareToken(UUID.randomUUID().toString().replace("-", ""));
        }
        m.setIsShared(1);
        // password==null：复用分享且保持原密码；password==""：清除密码；否则设置新密码
        if (password == null) {
            if (!alreadyShared) {
                m.setSharePassword("");
                m.setSharePasswordPlain("");
            }
        } else if (password.isEmpty()) {
            m.setSharePassword("");
            m.setSharePasswordPlain("");
        } else {
            // 分享密码加盐哈希存储（BCrypt），明文仅用于作者端回显
            m.setSharePassword(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(password));
            m.setSharePasswordPlain(password);
        }
        mindMapMapper.updateById(m);
        Map<String, Object> data = new HashMap<>();
        data.put("shareToken", m.getShareToken());
        data.put("shareUrl", "/share/" + m.getShareToken());
        data.put("hasPassword", StringUtils.hasText(m.getSharePasswordPlain()) || StringUtils.hasText(m.getSharePassword()));
        data.put("sharePassword", m.getSharePasswordPlain() == null ? "" : m.getSharePasswordPlain());
        return data;
    }

    @Override
    public Map<String, Object> shareInfo(long userId, Long id) {
        MindMap m = mindMapMapper.selectById(id);
        if (m == null || !m.getUserId().equals(userId)) {
            throw new BusinessException(404, "导图不存在");
        }
        Map<String, Object> data = new HashMap<>();
        data.put("isShared", Integer.valueOf(1).equals(m.getIsShared()) && StringUtils.hasText(m.getShareToken()));
        data.put("shareToken", m.getShareToken() == null ? "" : m.getShareToken());
        data.put("shareUrl", StringUtils.hasText(m.getShareToken()) ? "/share/" + m.getShareToken() : "");
        data.put("hasPassword", StringUtils.hasText(m.getSharePasswordPlain()) || StringUtils.hasText(m.getSharePassword()));
        data.put("sharePassword", m.getSharePasswordPlain() == null ? "" : m.getSharePasswordPlain());
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
        String stored = m.getSharePassword();
        if (StringUtils.hasText(stored)) {
            boolean ok;
            if (stored.startsWith("$2")) {
                // 新格式：BCrypt 哈希校验
                ok = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder()
                        .matches(password == null ? "" : password, stored);
            } else {
                // 兼容存量明文数据：命中后自动升级为哈希
                ok = stored.equals(password);
                if (ok) {
                    m.setSharePassword(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder()
                            .encode(password == null ? "" : password));
                    mindMapMapper.updateById(m);
                }
            }
            if (!ok) {
                throw new BusinessException(403, "需要分享密码");
            }
        }
        m.setViewCount(m.getViewCount() == null ? 1 : m.getViewCount() + 1);
        mindMapMapper.updateById(m);
        return m;
    }
}
