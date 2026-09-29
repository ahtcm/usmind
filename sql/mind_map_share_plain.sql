-- 分享密码明文列（仅用于作者端回显原分享密码，验证仍使用 share_password 的 BCrypt 哈希）
ALTER TABLE mind_map
  ADD COLUMN share_password_plain varchar(256) NOT NULL DEFAULT '' COMMENT '分享密码明文（仅用于作者端回显）' AFTER share_password;
