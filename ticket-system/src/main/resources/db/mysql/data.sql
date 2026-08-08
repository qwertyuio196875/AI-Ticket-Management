-- =============================================================
-- ticket 02 — sys_user 种子数据（MySQL 8）
--
-- INSERT IGNORE 配合 uk_sys_user_username 保证可重复执行不报错。
--
-- 初始账号：admin / admin123
-- 哈希由 BCryptPasswordEncoder（strength 10）生成。
-- ⚠ 生产环境务必首次登录后立即改密。
-- =============================================================

INSERT IGNORE INTO sys_user (username, password, nickname, status)
VALUES ('admin',
        '$2a$10$MQKQ7Jv5VkM7Gkfzzcj.GexHeZQdBR8PBtY4BFgifgececRtcpNfm',
        '超级管理员',
        1);
