-- =============================================================
-- AI 智能工单管理系统 — 数据库种子数据（MySQL 8）
--
-- 由 Spring Boot spring.sql.init 在启动时执行；INSERT IGNORE
-- 配合唯一索引保证可重复执行不报错。
--
-- 维护说明：ticket 之间如果新增种子，请 append 到本文件末尾，
-- 并同步更新 src/test/resources/db/h2/data.sql。
-- =============================================================

-- 用户种子（ticket 02） ----------------------------------------
-- admin / admin123 —— BCryptPasswordEncoder (strength 10) 生成。
-- ⚠ 生产环境务必首次登录后立即改密。
INSERT IGNORE INTO sys_user (username, password, nickname, status)
VALUES ('admin',
        '$2a$10$MQKQ7Jv5VkM7Gkfzzcj.GexHeZQdBR8PBtY4BFgifgececRtcpNfm',
        '超级管理员',
        1);

-- 角色种子（ticket 03） ----------------------------------------
INSERT IGNORE INTO sys_role (id, role_name, role_key, remark) VALUES
    (1, '超级管理员', 'admin', '拥有全部权限'),
    (2, '客服坐席',   'agent', '负责处理工单');

-- 菜单 ----------------------------------------------------------
-- permission 与 ticket 03 验收标准对应：
--   dashboard:view  / user:manage / role:manage /
--   menu:manage     / ticket:view
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, sort, visible, permission) VALUES
    (1, 0, 'Dashboard',  'C', '/dashboard',  'Dashboard',     'dashboard', 1, 1, 'dashboard:view'),
    (2, 0, '用户管理',   'C', '/users',      'UserList',      'user',      2, 1, 'user:manage'),
    (3, 0, '角色管理',   'C', '/roles',      'RoleList',      'role',      3, 1, 'role:manage'),
    (4, 0, '菜单管理',   'C', '/menus',      'MenuList',      'menu',      4, 1, 'menu:manage'),
    (5, 0, '工单列表',   'C', '/tickets',    'TicketList',    'ticket',    5, 1, 'ticket:view');

-- 角色 ↔ 菜单 ---------------------------------------------------
-- admin 拿全；agent 只拿 dashboard + ticket
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES
    (1, 1), (1, 2), (1, 3), (1, 4), (1, 5),
    (2, 1), (2, 5);

-- 用户 ↔ 角色 ---------------------------------------------------
-- 默认 admin 用户（ticket 02 已建，id=1）挂 admin 角色
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (1, 1);