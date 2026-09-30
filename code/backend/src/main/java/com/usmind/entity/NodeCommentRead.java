package com.usmind.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 节点评论已读游标（作者端）。
 * 点击节点阅读评论后记录该节点最大已读评论 id，持久化到 MySQL，
 * 保证刷新/重启后已读状态不丢失（未读特效不因刷新重现）。
 * 主键 (user_id, mind_map_id, node_id)。
 */
@Data
@TableName("node_comment_read")
public class NodeCommentRead {
    /** 已读用户 id（导图作者） */
    private Long userId;
    /** 导图 id（mind_map.id） */
    private Long mindMapId;
    /** 评论所在节点 uid（simple-mind-map 节点 uid） */
    private String nodeId;
    /** 该节点已读到的最大评论 id（评论 id 单调递增） */
    private Long lastReadId;
    private LocalDateTime updatedAt;
}
