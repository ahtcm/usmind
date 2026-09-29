package com.usmind.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.usmind.common.BusinessException;
import com.usmind.entity.Org;
import com.usmind.entity.OrgCollab;
import com.usmind.entity.OrgMember;
import com.usmind.mapper.OrgCollabMapper;
import com.usmind.mapper.OrgMapper;
import com.usmind.mapper.OrgMemberMapper;
import com.usmind.service.OrgService;
import com.usmind.service.SettingsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class OrgServiceImpl implements OrgService {

    private final OrgMapper orgMapper;
    private final OrgMemberMapper orgMemberMapper;
    private final OrgCollabMapper orgCollabMapper;
    private final com.usmind.mapper.SysUserMapper sysUserMapper;
    private final com.usmind.mapper.MindMapMapper mindMapMapper;
    private final SettingsService settingsService;

    public OrgServiceImpl(OrgMapper orgMapper, OrgMemberMapper orgMemberMapper, OrgCollabMapper orgCollabMapper,
                          com.usmind.mapper.SysUserMapper sysUserMapper, com.usmind.mapper.MindMapMapper mindMapMapper,
                          SettingsService settingsService) {
        this.orgMapper = orgMapper;
        this.orgMemberMapper = orgMemberMapper;
        this.orgCollabMapper = orgCollabMapper;
        this.sysUserMapper = sysUserMapper;
        this.mindMapMapper = mindMapMapper;
        this.settingsService = settingsService;
    }

    private boolean isVip(Long userId) {
        com.usmind.entity.SysUser u = sysUserMapper.selectById(userId);
        if (u == null || u.getMemberLevel() == null || u.getMemberLevel() <= 0) return false;
        if (u.getMemberExpire() == null) return false;
        return u.getMemberExpire().isAfter(LocalDateTime.now());
    }

    private Org requireOrg(Long orgId) {
        Org o = orgMapper.selectById(orgId);
        if (o == null || o.getStatus() == null || o.getStatus() != 1) {
            throw new BusinessException(404, "机构不存在");
        }
        return o;
    }

    private OrgMember requireMember(Long orgId, Long userId) {
        OrgMember m = orgMemberMapper.selectOne(new LambdaQueryWrapper<OrgMember>()
                .eq(OrgMember::getOrgId, orgId).eq(OrgMember::getUserId, userId));
        if (m == null) throw new BusinessException(403, "你不在该机构中");
        return m;
    }

    @Override
    @Transactional
    public Map<String, Object> create(long userId, String name) {
        if (name == null || name.isBlank()) throw new BusinessException(400, "机构名称不能为空");
        if (name.length() > 50) throw new BusinessException(400, "机构名称不能超过50字");
        if (settingsService.getInt("orgCreateNeedMember", 1) == 1 && !isVip(userId)) {
            throw new BusinessException(403, "创建机构需要会员，请升级会员或在后台关闭该限制");
        }
        Long count = orgMapper.selectCount(new LambdaQueryWrapper<Org>().eq(Org::getOwnerId, userId).eq(Org::getStatus, 1));
        if (count != null && count >= 20) throw new BusinessException(400, "最多创建20个机构");
        Org o = new Org();
        o.setName(name.trim());
        o.setInviteCode(UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase());
        o.setOwnerId(userId);
        o.setMemberCount(1);
        o.setStatus(1);
        o.setCreatedAt(LocalDateTime.now());
        o.setUpdatedAt(LocalDateTime.now());
        orgMapper.insert(o);
        OrgMember om = new OrgMember();
        om.setOrgId(o.getId());
        om.setUserId(userId);
        om.setRole("owner");
        om.setJoinedAt(LocalDateTime.now());
        orgMemberMapper.insert(om);
        Map<String, Object> data = new HashMap<>();
        data.put("id", o.getId());
        data.put("name", o.getName());
        data.put("inviteCode", o.getInviteCode());
        return data;
    }

    @Override
    public List<Map<String, Object>> myOrgs(long userId) {
        List<OrgMember> ms = orgMemberMapper.selectList(new LambdaQueryWrapper<OrgMember>()
                .eq(OrgMember::getUserId, userId));
        List<Map<String, Object>> list = new ArrayList<>();
        for (OrgMember m : ms) {
            Org o = orgMapper.selectById(m.getOrgId());
            if (o == null || o.getStatus() == null || o.getStatus() != 1) continue;
            Map<String, Object> item = new HashMap<>();
            item.put("id", o.getId());
            item.put("name", o.getName());
            item.put("inviteCode", o.getInviteCode());
            item.put("memberCount", o.getMemberCount());
            item.put("role", m.getRole());
            item.put("ownerId", o.getOwnerId());
            item.put("createdAt", o.getCreatedAt());
            list.add(item);
        }
        return list;
    }

    @Override
    public Map<String, Object> detail(long userId, Long orgId) {
        Org o = requireOrg(orgId);
        requireMember(orgId, userId);
        Map<String, Object> data = new HashMap<>();
        data.put("id", o.getId());
        data.put("name", o.getName());
        data.put("inviteCode", o.getInviteCode());
        data.put("memberCount", o.getMemberCount());
        data.put("ownerId", o.getOwnerId());
        // 成员列表
        List<Map<String, Object>> members = new ArrayList<>();
        List<OrgMember> mlist = orgMemberMapper.selectList(new LambdaQueryWrapper<OrgMember>()
                .eq(OrgMember::getOrgId, orgId).orderByAsc(OrgMember::getJoinedAt));
        for (OrgMember m : mlist) {
            com.usmind.entity.SysUser u = sysUserMapper.selectById(m.getUserId());
            Map<String, Object> mi = new HashMap<>();
            mi.put("userId", m.getUserId());
            mi.put("role", m.getRole());
            mi.put("nickname", u == null ? "已注销" : (u.getNickname() == null || u.getNickname().isBlank() ? u.getUsername() : u.getNickname()));
            mi.put("avatar", u == null ? "" : (u.getAvatar() == null ? "" : u.getAvatar()));
            members.add(mi);
        }
        data.put("members", members);
        return data;
    }

    @Override
    @Transactional
    public Map<String, Object> join(long userId, String inviteCode) {
        if (inviteCode == null || inviteCode.isBlank()) throw new BusinessException(400, "邀请码不能为空");
        Org o = orgMapper.selectOne(new LambdaQueryWrapper<Org>()
                .eq(Org::getInviteCode, inviteCode.trim().toUpperCase()).eq(Org::getStatus, 1));
        if (o == null) throw new BusinessException(404, "邀请码无效或机构已解散");
        if (orgMemberMapper.selectCount(new LambdaQueryWrapper<OrgMember>()
                .eq(OrgMember::getOrgId, o.getId()).eq(OrgMember::getUserId, userId)) > 0) {
            throw new BusinessException(400, "你已在该机构中");
        }
        OrgMember om = new OrgMember();
        om.setOrgId(o.getId());
        om.setUserId(userId);
        om.setRole("member");
        om.setJoinedAt(LocalDateTime.now());
        orgMemberMapper.insert(om);
        o.setMemberCount((o.getMemberCount() == null ? 1 : o.getMemberCount()) + 1);
        o.setUpdatedAt(LocalDateTime.now());
        orgMapper.updateById(o);
        Map<String, Object> data = new HashMap<>();
        data.put("id", o.getId());
        data.put("name", o.getName());
        return data;
    }

    @Override
    @Transactional
    public void kick(long userId, Long orgId, Long targetUserId) {
        Org o = requireOrg(orgId);
        OrgMember me = requireMember(orgId, userId);
        if (!"owner".equals(me.getRole())) throw new BusinessException(403, "仅机构创建者可踢出成员");
        if (targetUserId == null || targetUserId.equals(userId)) throw new BusinessException(400, "不能踢出自己");
        OrgMember target = orgMemberMapper.selectOne(new LambdaQueryWrapper<OrgMember>()
                .eq(OrgMember::getOrgId, orgId).eq(OrgMember::getUserId, targetUserId));
        if (target == null) throw new BusinessException(404, "成员不在该机构中");
        if ("owner".equals(target.getRole())) throw new BusinessException(400, "不能踢出机构创建者");
        orgMemberMapper.deleteById(target.getId());
        o.setMemberCount(Math.max(1, (o.getMemberCount() == null ? 1 : o.getMemberCount()) - 1));
        o.setUpdatedAt(LocalDateTime.now());
        orgMapper.updateById(o);
    }

    @Override
    @Transactional
    public void exit(long userId, Long orgId) {
        Org o = requireOrg(orgId);
        OrgMember me = requireMember(orgId, userId);
        if ("owner".equals(me.getRole())) {
            // 创建者退出 = 解散机构
            disband(orgId);
            return;
        }
        orgMemberMapper.deleteById(me.getId());
        o.setMemberCount(Math.max(1, (o.getMemberCount() == null ? 1 : o.getMemberCount()) - 1));
        o.setUpdatedAt(LocalDateTime.now());
        orgMapper.updateById(o);
    }

    @Override
    @Transactional
    public void addCollab(long userId, Long orgId, Long mindMapId) {
        Org o = requireOrg(orgId);
        requireMember(orgId, userId);
        if (mindMapId == null) throw new BusinessException(400, "导图ID不能为空");
        com.usmind.entity.MindMap m = mindMapMapper.selectById(mindMapId);
        if (m == null || !m.getUserId().equals(userId)) {
            throw new BusinessException(404, "只能协作自己名下的导图");
        }
        // 发起协作需会员（后台可关）
        if (settingsService.getInt("orgInviteNeedMember", 1) == 1 && !isVip(userId)) {
            throw new BusinessException(403, "发起协作需要会员：请续费会员，或由管理员关闭「协作需要会员」限制");
        }
        if (orgCollabMapper.selectCount(new LambdaQueryWrapper<OrgCollab>()
                .eq(OrgCollab::getOrgId, orgId).eq(OrgCollab::getMindMapId, mindMapId)) > 0) {
            throw new BusinessException(400, "该导图已在协作中");
        }
        OrgCollab c = new OrgCollab();
        c.setOrgId(orgId);
        c.setMindMapId(mindMapId);
        c.setOwnerId(userId);
        c.setCreatedAt(LocalDateTime.now());
        orgCollabMapper.insert(c);
    }

    @Override
    public List<Map<String, Object>> collabList(long userId, Long orgId) {
        requireOrg(orgId);
        requireMember(orgId, userId);
        List<OrgCollab> cs = orgCollabMapper.selectList(new LambdaQueryWrapper<OrgCollab>()
                .eq(OrgCollab::getOrgId, orgId).orderByDesc(OrgCollab::getId));
        List<Map<String, Object>> list = new ArrayList<>();
        for (OrgCollab c : cs) {
            com.usmind.entity.MindMap m = mindMapMapper.selectById(c.getMindMapId());
            if (m == null) continue;
            Map<String, Object> item = new HashMap<>();
            item.put("mindMapId", m.getId());
            item.put("name", m.getName());
            item.put("updatedAt", m.getUpdatedAt());
            item.put("ownerId", m.getUserId());
            com.usmind.entity.SysUser u = sysUserMapper.selectById(m.getUserId());
            item.put("ownerNick", u == null ? "已注销" : (u.getNickname() == null || u.getNickname().isBlank() ? u.getUsername() : u.getNickname()));
            list.add(item);
        }
        return list;
    }

    @Override
    @Transactional
    public void removeCollab(long userId, Long orgId, Long mindMapId) {
        Org o = requireOrg(orgId);
        OrgMember me = requireMember(orgId, userId);
        OrgCollab c = orgCollabMapper.selectOne(new LambdaQueryWrapper<OrgCollab>()
                .eq(OrgCollab::getOrgId, orgId).eq(OrgCollab::getMindMapId, mindMapId));
        if (c == null) throw new BusinessException(404, "该导图不在协作中");
        if (!o.getOwnerId().equals(userId) && !c.getOwnerId().equals(userId)) {
            throw new BusinessException(403, "仅导图属主或机构创建者可移出协作");
        }
        orgCollabMapper.deleteById(c.getId());
    }

    @Override
    public boolean canAccessMindMap(long userId, Long mindMapId) {
        if (mindMapId == null) return false;
        List<OrgCollab> cs = orgCollabMapper.selectList(new LambdaQueryWrapper<OrgCollab>()
                .eq(OrgCollab::getMindMapId, mindMapId));
        for (OrgCollab c : cs) {
            Org o = orgMapper.selectById(c.getOrgId());
            if (o == null || o.getStatus() == null || o.getStatus() != 1) continue;
            if (orgMemberMapper.selectCount(new LambdaQueryWrapper<OrgMember>()
                    .eq(OrgMember::getOrgId, c.getOrgId()).eq(OrgMember::getUserId, userId)) > 0) {
                return true;
            }
        }
        return false;
    }

    @Override
    @Transactional
    public void disband(Long orgId) {
        Org o = orgMapper.selectById(orgId);
        if (o == null) return;
        orgMemberMapper.delete(new LambdaQueryWrapper<OrgMember>().eq(OrgMember::getOrgId, orgId));
        orgCollabMapper.delete(new LambdaQueryWrapper<OrgCollab>().eq(OrgCollab::getOrgId, orgId));
        orgMapper.deleteById(orgId);
    }
}
