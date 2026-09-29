-- 机构协作：机构 / 机构成员 / 机构协作导图 + 导图版本号（冲突处理乐观锁）
CREATE TABLE IF NOT EXISTS org (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(100) NOT NULL COMMENT '机构名称',
  invite_code VARCHAR(32) NOT NULL COMMENT '加入机构邀请码',
  owner_id BIGINT NOT NULL COMMENT '创建人（机构主）',
  member_count INT NOT NULL DEFAULT 1 COMMENT '成员数',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '1 正常 0 已解散',
  created_at DATETIME,
  updated_at DATETIME,
  UNIQUE KEY uk_invite_code (invite_code),
  KEY idx_owner (owner_id)
) COMMENT '机构表';

CREATE TABLE IF NOT EXISTS org_member (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  org_id BIGINT NOT NULL COMMENT '机构ID',
  user_id BIGINT NOT NULL COMMENT '用户ID',
  role VARCHAR(16) NOT NULL DEFAULT 'member' COMMENT 'owner/member',
  joined_at DATETIME,
  UNIQUE KEY uk_org_user (org_id, user_id),
  KEY idx_user (user_id)
) COMMENT '机构成员表';

CREATE TABLE IF NOT EXISTS org_collab (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  org_id BIGINT NOT NULL COMMENT '机构ID',
  mind_map_id BIGINT NOT NULL COMMENT '导图ID',
  owner_id BIGINT NOT NULL COMMENT '导图属主（发起协作的人）',
  created_at DATETIME,
  UNIQUE KEY uk_org_map (org_id, mind_map_id),
  KEY idx_map (mind_map_id)
) COMMENT '机构协作导图表';

-- 导图乐观锁版本号（冲突解决）
ALTER TABLE mind_map ADD COLUMN version INT NOT NULL DEFAULT 0 COMMENT '保存版本号（乐观锁冲突检测）' AFTER view_count;
