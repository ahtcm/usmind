package com.usmind.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.usmind.common.BusinessException;
import com.usmind.entity.MindMap;
import com.usmind.entity.NodeComment;
import com.usmind.entity.NodeCommentRead;
import com.usmind.entity.SysUser;
import com.usmind.mapper.MindMapMapper;
import com.usmind.mapper.NodeCommentMapper;
import com.usmind.mapper.NodeCommentReadMapper;
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
import java.util.ArrayList;
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
    private final NodeCommentReadMapper nodeCommentReadMapper;
    private final MindMapMapper mindMapMapper;
    private final SysUserMapper sysUserMapper;
    private final StringRedisTemplate redisTemplate;
    private final SettingsService settingsService;
    private final IpGuardService ipGuardService;
    private final OrgService orgService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public NodeCommentServiceImpl(NodeCommentMapper nodeCommentMapper, NodeCommentReadMapper nodeCommentReadMapper,
                                  MindMapMapper mindMapMapper, SysUserMapper sysUserMapper,
                                  StringRedisTemplate redisTemplate,
                                  SettingsService settingsService, IpGuardService ipGuardService,
                                  OrgService orgService) {
        this.nodeCommentMapper = nodeCommentMapper;
        this.nodeCommentReadMapper = nodeCommentReadMapper;
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
                              String content, int isAuthor, long userId, String source, String ip) {
        NodeComment c = new NodeComment();
        c.setMindMapId(mindMapId);
        c.setNodeId(nodeId);
        c.setParentId(parentId);
        c.setUserName(nick);
        c.setContent(content);
        c.setIsAuthor(isAuthor);
        c.setUserId(userId);
        c.setSource(source);
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
                                       Long parentId, String nick, String content, String ip, String clientSid) {
        MindMap m = verifyShare(token, password);
        rateLimit(ip);
        if (!StringUtils.hasText(nodeId)) throw new BusinessException(400, "节点标识缺失");
        validateText(content, nick);
        checkParent(m, parentId);
        NodeComment c = build(m.getId(), nodeId.trim(), parentId, nick.trim(), content.trim(), 0, 0L, "share", ip);
        c.setClientSid(sanitizeSid(clientSid));
        nodeCommentMapper.insert(c);
        return c;
    }

    /** 会话 ID 清洗：仅保留安全字符，超长截断；空/非法返回 null */
    private String sanitizeSid(String sid) {
        if (!StringUtils.hasText(sid)) return null;
        String s = sid.trim();
        if (s.length() > 64) s = s.substring(0, 64);
        if (!s.matches("[A-Za-z0-9_\\-]+")) return null;
        return s;
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
        String src = target.getSource() == null ? "share" : target.getSource();
        NodeComment c = build(m.getId(), target.getNodeId(), commentId, nick, content.trim(), 1, userId, src, ip);
        nodeCommentMapper.insert(c);
        return c;
    }

    @Override
    public NodeComment addOrgComment(long userId, Long mindMapId, String nodeId,
                                     Long parentId, String content, String ip) {
        MindMap m = requireOwned(userId, mindMapId);
        rateLimit(ip);
        if (!StringUtils.hasText(nodeId)) throw new BusinessException(400, "节点标识缺失");
        if (!StringUtils.hasText(content)) throw new BusinessException(400, "评论内容不能为空");
        if (content.trim().length() > MAX_CONTENT) throw new BusinessException(400, "评论最多 " + MAX_CONTENT + " 字");
        checkParent(m, parentId);
        SysUser u = sysUserMapper.selectById(userId);
        String nick = u != null && StringUtils.hasText(u.getNickname()) ? u.getNickname()
                : (u != null && StringUtils.hasText(u.getUsername()) ? u.getUsername() : "成员");
        NodeComment c = build(m.getId(), nodeId.trim(), parentId, nick, content.trim(), 1, userId, "org", ip);
        nodeCommentMapper.insert(c);
        return c;
    }

    @Override
    public void markNodeRead(long userId, Long mindMapId, String nodeId) {
        MindMap m = requireOwned(userId, mindMapId);
        if (!StringUtils.hasText(nodeId)) return;
        // 记录该节点当前最大评论 id 为已读游标（口径与 unread 一致：访客评论 + 其他成员评论，不含自己发的）
        Long maxId = nodeCommentMapper.selectList(new LambdaQueryWrapper<NodeComment>()
                        .eq(NodeComment::getMindMapId, m.getId())
                        .eq(NodeComment::getNodeId, nodeId.trim())
                        .and(w -> w.eq(NodeComment::getIsAuthor, 0)
                                .or(n -> n.eq(NodeComment::getIsAuthor, 1).ne(NodeComment::getUserId, userId))))
                .stream().mapToLong(NodeComment::getId).max().orElse(0L);
        if (maxId <= 0) return;
        // 持久化到 MySQL（刷新/重启不丢失已读状态）；存在则只推进游标
        NodeCommentRead rec = nodeCommentReadMapper.selectOne(new LambdaQueryWrapper<NodeCommentRead>()
                .eq(NodeCommentRead::getUserId, userId)
                .eq(NodeCommentRead::getMindMapId, m.getId())
                .eq(NodeCommentRead::getNodeId, nodeId.trim()));
        if (rec == null) {
            NodeCommentRead nr = new NodeCommentRead();
            nr.setUserId(userId);
            nr.setMindMapId(m.getId());
            nr.setNodeId(nodeId.trim());
            nr.setLastReadId(maxId);
            nodeCommentReadMapper.insert(nr);
        } else if (maxId > (rec.getLastReadId() == null ? 0L : rec.getLastReadId())) {
            rec.setLastReadId(maxId);
            nodeCommentReadMapper.update(rec, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<NodeCommentRead>()
                    .eq(NodeCommentRead::getUserId, userId)
                    .eq(NodeCommentRead::getMindMapId, m.getId())
                    .eq(NodeCommentRead::getNodeId, nodeId.trim()));
        }
    }

    @Override
    public Map<String, Integer> unreadByNode(long userId, Long mindMapId) {
        MindMap m = requireOwned(userId, mindMapId);
        List<NodeComment> all = nodeCommentMapper.selectList(new LambdaQueryWrapper<NodeComment>()
                .eq(NodeComment::getMindMapId, m.getId()));
        Map<String, Integer> unread = new HashMap<>();
        if (all.isEmpty()) return unread;
        // 排除自己发的评论（自己回复/自己组织评论不算自己的未读）；分享访客评论 userId=0 不受影响
        all.removeIf(c -> (c.getIsAuthor() != null && c.getIsAuthor() == 1)
                && c.getUserId() != null && c.getUserId() == userId);
        List<NodeCommentRead> reads = nodeCommentReadMapper.selectList(new LambdaQueryWrapper<NodeCommentRead>()
                .eq(NodeCommentRead::getUserId, userId)
                .eq(NodeCommentRead::getMindMapId, m.getId()));
        Map<String, Long> lastRead = new HashMap<>();
        for (NodeCommentRead r : reads) {
            lastRead.put(r.getNodeId(), r.getLastReadId() == null ? 0L : r.getLastReadId());
        }
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

    /** 评论提醒目标：特效只发给这些人，其他人仅收到评论信息 */
    @Override
    public List<Long> targetUserIds(NodeComment c) {
        List<Long> targets = new ArrayList<>();
        if (c == null || c.getMindMapId() == null) return targets;
        MindMap m = mindMapMapper.selectById(c.getMindMapId());
        if (m == null) return targets;
        // 1) 回复指定评论 → 仅被回复人（有账号）；被回复人是访客(userId=0) → 退回节点/导图提醒
        if (c.getParentId() != null) {
            NodeComment p = nodeCommentMapper.selectById(c.getParentId());
            if (p != null && p.getUserId() != null && p.getUserId() > 0 && !p.getUserId().equals(c.getUserId())) {
                targets.add(p.getUserId());
                return targets;
            }
        }
        // 2) 组织内节点评论 → 导图所有者 + 节点创建者；分享评论 → 导图所有者
        if ("org".equals(c.getSource())) {
            addTarget(targets, m.getUserId(), c.getUserId());
            addTarget(targets, findNodeCreator(m, c.getNodeId()), c.getUserId());
        } else {
            addTarget(targets, m.getUserId(), c.getUserId());
        }
        return targets;
    }

    @Override
    public String replySidOf(NodeComment c) {
        if (c == null || c.getParentId() == null) return "";
        NodeComment p = nodeCommentMapper.selectById(c.getParentId());
        return (p != null && p.getClientSid() != null) ? p.getClientSid() : "";
    }

    private void addTarget(List<Long> list, Long uid, Long selfId) {
        if (uid == null || uid <= 0) return;
        if (selfId != null && uid.equals(selfId)) return;
        if (!list.contains(uid)) list.add(uid);
    }

    /** 从导图 data 递归查找节点 creatorUid（历史节点无 creatorUid 视为导图所有者创建） */
    private long findNodeCreator(MindMap m, String nodeId) {
        if (nodeId == null || nodeId.isBlank() || m.getData() == null) return 0;
        try {
            JsonNode root = objectMapper.readTree(m.getData());
            if (root != null && root.has("root")) root = root.get("root");
            return findCreatorIn(root, nodeId);
        } catch (Exception e) {
            return 0;
        }
    }

    private long findCreatorIn(JsonNode n, String nodeId) {
        if (n == null || !n.isObject()) return 0;
        JsonNode d = n.path("data");
        String uid = d.isObject() ? d.path("uid").asText("") : n.path("uid").asText("");
        if (nodeId.equals(uid)) {
            if (d.isObject() && d.has("creatorUid")) return d.path("creatorUid").asLong(0);
            return n.path("creatorUid").asLong(0);
        }
        JsonNode children = n.path("children");
        if (children.isArray()) {
            for (JsonNode ch : children) {
                long r = findCreatorIn(ch, nodeId);
                if (r != 0) return r;
            }
        }
        return 0;
    }
}
