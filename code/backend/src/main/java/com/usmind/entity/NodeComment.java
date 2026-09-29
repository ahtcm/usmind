package com.usmind.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 分享导图节点评论。
 * 分享页访客可对任意节点评论/回复；作者可在编辑器内查看并回复。
 */
@Data
@TableName("node_comment")
public class NodeComment {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 导图 id（mind_map.id） */
    private Long mindMapId;
    /** 评论所在节点 uid（simple-mind-map 节点 uid） */
    private String nodeId;
    /** 回复的评论 id，null 为根评论（对节点的直接评论） */
    private Long parentId;
    /** 评论者昵称（分享访客强制填写；作者回复固定为作者昵称） */
    private String userName;
    /** 评论内容，≤128 字 */
    private String content;
    /** 1=作者（在编辑器回复），0=分享访客 */
    private Integer isAuthor;
    /** 评论者 IP（审计与超频封禁） */
    private String ip;
    private LocalDateTime createdAt;
}
