package com.usmind.service;

import com.usmind.entity.NodeComment;

import java.util.List;
import java.util.Map;

/**
 * 导图节点评论：分享访客评论/回复、作者/组织成员查看/回复/已读、组织内评论。
 * 评论接口独立频率限制：超频自动拉黑 IP 一段时间（与全局 IpGuard 互补）。
 */
public interface NodeCommentService {

    /** 分享页发评论（校验分享密码；parentId 为空=对节点的直接评论） */
    NodeComment addShareComment(String token, String password, String nodeId,
                                Long parentId, String nick, String content, String ip);

    /** 分享页拉取全部评论（按 id 升序；校验分享密码） */
    List<NodeComment> listShareComments(String token, String password);

    /** 作者拉取增量评论（afterId 之后新增，升序；校验导图归属） */
    List<NodeComment> listMindMapComments(long userId, Long mindMapId, Long afterId);

    /** 作者/组织成员回复某条评论（isAuthor=1，来源继承目标评论） */
    NodeComment authorReply(long userId, Long mindMapId, Long commentId, String content, String ip);

    /** 组织内评论：协作导图中登录成员对任意节点发根评论（source=org，isAuthor=1） */
    NodeComment addOrgComment(long userId, Long mindMapId, String nodeId,
                              Long parentId, String content, String ip);

    /** 作者标记某节点已读（记录该节点当前最大评论 id） */
    void markNodeRead(long userId, Long mindMapId, String nodeId);

    /** 作者视角：各节点未读评论数（id > 已读游标，排除自己发的），供前端轮询判断闪烁 */
    Map<String, Integer> unreadByNode(long userId, Long mindMapId);

    /** 某导图全部评论数（作者端角标展示） */
    long countByMindMap(Long mindMapId);
}
