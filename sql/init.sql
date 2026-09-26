-- ============================================================
-- USMIND 数据库初始化脚本
-- 数据库: usmind_db  字符集: utf8mb4
-- ============================================================
CREATE DATABASE IF NOT EXISTS usmind_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE usmind_db;

-- ---------- 用户表 ----------
DROP TABLE IF EXISTS sys_user;
CREATE TABLE sys_user (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  username      VARCHAR(64)  NOT NULL UNIQUE COMMENT '登录账号',
  password      VARCHAR(128) NOT NULL COMMENT 'BCrypt加密密码',
  nickname      VARCHAR(64)  DEFAULT '' COMMENT '昵称',
  avatar        VARCHAR(512) DEFAULT '' COMMENT '头像URL',
  email         VARCHAR(128) DEFAULT '' COMMENT '邮箱',
  phone         VARCHAR(32)  DEFAULT '' COMMENT '手机号',
  role          VARCHAR(16)  DEFAULT 'USER' COMMENT '角色: ADMIN/USER',
  status        TINYINT      DEFAULT 1 COMMENT '状态: 1启用 0禁用',
  created_at    DATETIME     DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 默认管理员: admin / admin123
INSERT INTO sys_user (username, password, nickname, role) VALUES
('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5E', '系统管理员', 'ADMIN');
-- 注意: 上面的hash是占位, 后端首次启动会校验并重置为正确的BCrypt('admin123')

-- ---------- 图表 ----------
DROP TABLE IF EXISTS mind_map;
CREATE TABLE mind_map (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id        BIGINT       NOT NULL COMMENT '所属用户',
  name           VARCHAR(256) DEFAULT '未命名' COMMENT '导图名称',
  data           MEDIUMTEXT   COMMENT '导图JSON数据(root结构)',
  layout         VARCHAR(32)  DEFAULT 'logicalStructure' COMMENT '布局',
  theme          VARCHAR(64)  DEFAULT 'XMind 浅色' COMMENT '主题名',
  view_data      TEXT         COMMENT '视图数据(缩放/平移)',
  is_shared      TINYINT      DEFAULT 0 COMMENT '是否分享: 1是 0否',
  share_token    VARCHAR(64)  DEFAULT '' COMMENT '分享令牌',
  share_password VARCHAR(64)  DEFAULT '' COMMENT '分享密码(可选)',
  view_count     INT          DEFAULT 0 COMMENT '浏览次数',
  created_at     DATETIME     DEFAULT CURRENT_TIMESTAMP,
  updated_at     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_user_id (user_id),
  INDEX idx_share_token (share_token)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='思维导图表';

-- ---------- OSS配置表 ----------
DROP TABLE IF EXISTS oss_config;
CREATE TABLE oss_config (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  name         VARCHAR(64)  NOT NULL COMMENT '配置名称',
  type         VARCHAR(16)  NOT NULL COMMENT '类型: aliyun/tencent/qiniu/minio/local',
  endpoint     VARCHAR(256) DEFAULT '' COMMENT '端点',
  access_key   VARCHAR(256) DEFAULT '' COMMENT 'AccessKey',
  secret_key   VARCHAR(512) DEFAULT '' COMMENT 'SecretKey(加密存储)',
  bucket       VARCHAR(128) DEFAULT '' COMMENT '存储桶',
  region       VARCHAR(64)  DEFAULT '' COMMENT '区域',
  domain       VARCHAR(256) DEFAULT '' COMMENT '自定义域名',
  is_active    TINYINT      DEFAULT 0 COMMENT '是否当前启用: 1是 0否',
  config_json  TEXT         COMMENT '额外配置JSON',
  created_at   DATETIME     DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OSS配置表';

-- 默认本地兜底存储配置
INSERT INTO oss_config (name, type, is_active) VALUES ('本地存储', 'local', 1);

-- ---------- 文件资源表 ----------
DROP TABLE IF EXISTS file_resource;
CREATE TABLE file_resource (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id       BIGINT       NOT NULL COMMENT '上传用户',
  mind_map_id   BIGINT       DEFAULT 0 COMMENT '关联导图ID',
  original_name VARCHAR(256) NOT NULL COMMENT '原始文件名',
  stored_name   VARCHAR(256) NOT NULL COMMENT '存储文件名',
  storage_type  VARCHAR(16)  DEFAULT 'local' COMMENT '存储类型',
  url           VARCHAR(512) DEFAULT '' COMMENT '访问URL',
  size          BIGINT       DEFAULT 0 COMMENT '文件大小(字节)',
  mime_type     VARCHAR(128) DEFAULT '' COMMENT 'MIME类型',
  created_at    DATETIME     DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_user_id (user_id),
  INDEX idx_mind_map_id (mind_map_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件资源表';

-- ---------- 系统配置表 ----------
CREATE TABLE IF NOT EXISTS sys_config (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  config_key   VARCHAR(64)  NOT NULL UNIQUE COMMENT '配置键',
  config_value TEXT         COMMENT '配置值',
  updated_at   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统配置表';
