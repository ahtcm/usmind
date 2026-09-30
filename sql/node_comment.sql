-- 导图节点评论表（分享页访客 + 组织内成员）
CREATE TABLE IF NOT EXISTS `node_comment` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `mind_map_id` BIGINT NOT NULL COMMENT '导图 id（mind_map.id）',
  `node_id` VARCHAR(64) NOT NULL COMMENT '评论所在节点 uid',
  `parent_id` BIGINT DEFAULT NULL COMMENT '回复的评论 id，NULL=根评论',
  `user_name` VARCHAR(64) NOT NULL COMMENT '评论者昵称（分享访客填写；作者/组织成员为昵称）',
  `content` VARCHAR(512) NOT NULL COMMENT '评论内容（≤128 字）',
  `is_author` TINYINT NOT NULL DEFAULT 0 COMMENT '1=登录用户（作者/组织成员），0=分享访客',
  `user_id` BIGINT NOT NULL DEFAULT 0 COMMENT '评论者用户 id，0=分享访客',
  `source` VARCHAR(10) NOT NULL DEFAULT 'share' COMMENT '评论来源：share=分享页，org=组织内',
  `ip` VARCHAR(64) NOT NULL DEFAULT '' COMMENT '评论者 IP（审计/超频封禁）',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_mindmap` (`mind_map_id`),
  KEY `idx_node` (`node_id`),
  KEY `idx_parent` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='导图节点评论（分享页/组织内）';
