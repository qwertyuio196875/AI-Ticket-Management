-- =============================================================
-- ticket 02 — 集成测试种子数据（H2）
--
-- admin 的哈希与 MySQL 种子脚本完全一致 ——
-- 集成测试用 admin/admin123 登录，因此这份哈希一旦写错测试立刻红，
-- 生产种子数据的正确性也就被顺带守住了。
--
-- resigned 是测试专用的禁用账号，用于验证 status=0 拒绝登录。
-- =============================================================

MERGE INTO sys_user (id, username, password, nickname, status) KEY (id)
    VALUES (1, 'admin',
            '$2a$10$MQKQ7Jv5VkM7Gkfzzcj.GexHeZQdBR8PBtY4BFgifgececRtcpNfm',
            '超级管理员', 1);

MERGE INTO sys_user (id, username, password, nickname, status) KEY (id)
    VALUES (2, 'resigned',
            '$2a$10$MQKQ7Jv5VkM7Gkfzzcj.GexHeZQdBR8PBtY4BFgifgececRtcpNfm',
            '已离职员工', 0);
