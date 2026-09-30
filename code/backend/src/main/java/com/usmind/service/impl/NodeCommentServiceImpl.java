package com.usmind.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.usmind.common.BusinessException;
import com.usmind.entity.MindMap;
import com.usmind.entity.NodeComment;
import com.usmind.entity.SysUser;
import com.usmind.mapper.MindMapMapper;
import com.usmind.mapper.NodeCommentMapper;
import com.usmind.mapper.SysUserMapper;
import com.usmind.service.IpGuardService;
import com.usmind.service.NodeCommentService;
import com.usmind.service.OrgService;
import com.usmind.service.SettingsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class NodeCommentServiceImpl implements NodeCommentService {

    private static final int MAX_CONTENT = 128;
    private static final int MAX_NICK = 32;

    private final NodeCommentMapper nodeCommentMapper;
    private final MindMapMapper mindMapMapper;
    private final SysUserMapper sysUserMapper;
    private final StringRedisTemplate redisTemplate;
    private final SettingsService settingsService;
    private final IpGuardService ipGuardService;
    private final OrgService orgService;

    public NodeCommentServiceImpl(NodeCommentMapper nodeCommentMapper, MindMapMapper mindMapMapper,
                                  SysUserMapper sysUserMapper, StringRedisTemplate redisTemplate,
                                  SettingsService settingsService, IpGuardService ipGuardService,
                                  OrgService orgService) {
        this.nodeCommentMapper = nodeCommentMapper;
        this.mindMapMapper = mindMapMapper;
        this.sysUserMapper = sysUserMapper;
        this.redisTemplate = redisTemplate;
        this.settingsService = settingsService;
        this.ipGuardService = ipGuardService;
        this.orgService = orgService;
    }

    /** 校验分享 token 与密码（不刷新 viewCount），返回导图；失败抛 403 */
    private MindMap verifyShare(String token, String password) {
        if (!StringUtils.hasText(token)) throw new BusinessException(400, "分享标识缺失");
        MindMap m = mindMapMapper.selectOne(new LambdaQueryWrapper<MindMap>()
                .eq(MindMap::getShareToken, token)
                .eq(MindMap::getIsShared, 1)
                .last("LIMIT 1"));
        if (m == null) throw new BusinessException(404, "分享不存在或已取消");
        String stored = m.getSharePassword();
        if (StringUtils.hasText(stored)) {
            boolean ok = stored.startsWith("$2")
                    ? new BCryptPasswordEncoder().matches(password == null ? "" : password, stored)
                    : stored.equals(password); // 存量明文兜底（getByShareToken 会渐进升级）
            if (!ok) throw new BusinessException(403, "需要分享密码");
        }
        return m;
    }

    /** 评论接口独立频率限制：每窗口超限则自动拉黑 IP（复用 IpGuard 拉黑） */
    private void rateLimit(String ip) {
        if (ip == null || ip.isBlank()) return;
        int limit = settingsService.getInt("commentRateLimit", 5);
        int window = settingsService.getInt("commentRateWindow", 60);
        String key = "cmt:rate:" + ip + ":" + (System.currentTimeMillis() / (window * 1000L));
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, window, TimeUnit.SECONDS);
        }
        if (count != null && count > limit) {
            ipGuardService.banIp(ip);
            log.warn("评论超频拉黑 IP: {}", ip);
            throw new BusinessException(429, "评论过于频繁，请稍后再试");
        }
    }

    private NodeComment build(Long mindMapId, String nodeId, Long parentId, String nick,
                              String content, int isAuthor, String ip) {
        NodeComment c = new NodeComment();
        c.setMindMapId(mindMapId);
        c.setNodeId(nodeId);
        c.setParentId(parentId);
        c.setUserName(nick);
        c.setContent(content);
        c.setIsAuthor(isAuthor);
        c.setIp(ip == null ? "" : ip);
        c.setCreatedAt(LocalDateTime.now());
        return c;
    }

    private void checkParent(MindMap m, Long parentId) {
        if (parentId == null) return;
        NodeComment p = nodeCommentMapper.selectById(parentId);
        if (p == null || !m.getId().equals(p.getMindMapId())) {
            throw new BusinessException(400, "回复的评论不存在");
        }
    }

    private void validateText(String content, String nick) {
        if (!StringUtils.hasText(content)) throw new BusinessException(400, "评论内容不能为空");
        if (content.trim().length() > MAX_CONTENT) throw new BusinessException(400, "评论最多 " + MAX_CONTENT + " 字");
        if (!StringUtils.hasText(nick)) throw new BusinessException(400, "请输入评论昵称");
        if (nick.trim().length() > MAX_NICK) throw new BusinessException(400, "昵称最多 " + MAX_NICK + " 字");
    }

    @Override
    public NodeComment addShareComment(String token, String password, String nodeId,
                                       Long parentId, String nick, String content, String ip) {
        MindMap m = verifyShare(token, password);
        rateLimit(ip);
        if (!StringUtils.hasText(nodeId)) throw new BusinessException(400, "节点标识缺失");
        validateText(content, nick);
        checkParent(m, parentId);
        NodeComment c = build(m.getId(), nodeId.trim(), parentId, nick.trim(), content.trim(), 0, ip);
        nodeCommentMapper.insert(c);
        return c;
    }

    @Override
    public List<NodeComment> listShareComments(String token, String password) {
        MindMap m = verifyShare(token, password);
        return nodeCommentMapper.selectList(new LambdaQueryWrapper<NodeComment>()
                .eq(NodeComment::getMindMapId, m.getId())
                .orderByAsc(NodeComment::getId));
    }

    @Override
    public List<NodeComment> listMindMapComments(long userId, Long mindMapId, Long afterId) {
        MindMap m = requireOwned(userId, mindMapId);
        LambdaQueryWrapper<NodeComment> qw = new LambdaQueryWrapper<NodeComment>()
                .eq(NodeComment::getMindMapId, m.getId())
                .orderByAsc(NodeComment::getId);
        if (afterId != null && afterId > 0) qw.gt(NodeComment::getId, afterId);
        return nodeCommentMapper.selectList(qw);
    }

    @Override
    public NodeComment authorReply(long userId, Long mindMapId, Long commentId, String content, String ip) {
        MindMap m = requireOwned(userId, mindMapId);
        rateLimit(ip);
        NodeComment target = nodeCommentMapper.selectById(commentId);
        if (target == null || !m.getId().equals(target.getMindMapId())) {
            throw new BusinessException(404, "评论不存在");
        }
        if (!StringUtils.hasText(content)) throw new BusinessException(400, "回复内容不能为空");
        if (content.trim().length() > MAX_CONTENT) throw new BusinessException(400, "回复最多 " + MAX_CONTENT + " 字");
        SysUser u = sysUserMapper.selectById(userId);
        String nick = u != null && StringUtils.hasText(u.getNickname()) ? u.getNickname()
                : (u != null && StringUtils.hasText(u.getUsername()) ? u.getUsername() : "作者");
        NodeComment c = build(m.getId(), target.getNodeId(), commentId, nick, content.trim(), 1, ip);
        nodeCommentMapper.insert(c);
        return c;
    }

    @Override
    public void markNodeRead(long userId, Long mindMapId, String nodeId) {
        MindMap m = requireOwned(userId, mindMapId);
        if (!StringUtils.hasText(nodeId)) return;
        // 记录该节点当前最大评论 id 为已读游标
        Long maxId = nodeCommentMapper.selectList(new LambdaQueryWrapper<NodeComment>()
                        .eq(NodeComment::getMindMapId, m.getId())
                        .eq(NodeComment::getNodeId, nodeId.trim())
                        .eq(NodeComment::getIsAuthor, 0))
                .stream().mapToLong(NodeComment::getId).max().orElse(0L);
        if (maxId <= 0) return;
        redisTemplate.opsForHash().put("cmt:read:" + userId + ":" + m.getId(), nodeId.trim(), String.valueOf(maxId));
    }

    @Override
    public Map<String, Integer> unreadByNode(long userId, Long mindMapId) {
        MindMap m = requireOwned(userId, mindMapId);
        List<NodeComment> all = nodeCommentMapper.selectList(new LambdaQueryWrapper<NodeComment>()
                .eq(NodeComment::getMindMapId, m.getId()));
        Map<String, Integer> unread = new HashMap<>();
        if (all.isEmpty()) return unread;
        // 作者自己的回复不算未读提醒（只统计访客产生的评论）
        all.removeIf(c -> c.getIsAuthor() != null && c.getIsAuthor() == 1);
        Map<Object, Object> read = redisTemplate.opsForHash().entries("cmt:read:" + userId + ":" + m.getId());
        Map<String, Long> lastRead = new HashMap<>();
        read.forEach((k, v) -> {
            try { lastRead.put(String.valueOf(k), Long.parseLong(String.valueOf(v))); } catch (Exception ignored) {}
        });
        Map<String, Integer> counts = new HashMap<>();
        for (NodeComment c : all) {
            counts.merge(c.getNodeId(), 1, Integer::sum);
        }
        // 未读数 = 该节点评论总数 - 已读游标内数量（简化：评论 id 单调，count(id>lastRead)）
        for (NodeComment c : all) {
            Long last = lastRead.getOrDefault(c.getNodeId(), 0L);
            if (c.getId() > last) {
                unread.merge(c.getNodeId(), 1, Integer::sum);
            }
        }
        return unread;
    }

    @Override
    public long countByMindMap(Long mindMapId) {
        if (mindMapId == null) return 0;
        Long n = nodeCommentMapper.selectCount(new LambdaQueryWrapper<NodeComment>()
                .eq(NodeComment::getMindMapId, mindMapId));
        return n == null ? 0 : n;
    }

    private MindMap requireOwned(long userId, Long mindMapId) {
        if (mindMapId == null) throw new BusinessException(400, "导图标识缺失");
        MindMap m = mindMapMapper.selectById(mindMapId);
        if (m == null) throw new BusinessException(404, "导图不存在");
        // 导图创建者 或 协作组织成员 可访问评论（协同编辑模式下成员也能看/回复评论、接收未读提示）
        if (!m.getUserId().equals(userId) && !orgService.canAccessMindMap(userId, mindMapId)) {
            throw new BusinessException(403, "无权访问该导图");
        }
        return m;
    }
}
