-- 迁移：node_comment 增加 client_sid（分享访客会话ID，匿名定向评论特效）
ALTER TABLE `node_comment` ADD COLUMN `client_sid` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '分享访客会话ID（匿名定向特效用）' AFTER `ip`;