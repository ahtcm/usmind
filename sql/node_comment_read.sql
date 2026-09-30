-- 节点评论已读游标表（作者端：点击节点阅读评论后记录最大已读评论 id，持久化保证刷新/重启不重现特效）
CREATE TABLE IF NOT EXISTS `node_comment_read` (
  `user_id` BIGINT NOT NULL COMMENT '已读用户 id（导图作者）',
  `mind_map_id` BIGINT NOT NULL COMMENT '导图 id（mind_map.id）',
  `node_id` VARCHAR(64) NOT NULL COMMENT '评论所在节点 uid',
  `last_read_id` BIGINT NOT NULL DEFAULT 0 COMMENT '该节点已读到的最大评论 id（评论 id 单调递增）',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`, `mind_map_id`, `node_id`),
  KEY `idx_mindmap` (`mind_map_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='节点评论已读游标（作者端未读特效持久化）';
