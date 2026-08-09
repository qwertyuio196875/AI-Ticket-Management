-- =============================================================
-- ticket 03 — 集成测试种子数据（H2）
--
-- admin 的哈希与 ticket 02 保持一致（admin/admin123）；
-- ticket 03 起新增普通用户 agent_user，挂 agent 角色，
-- 用于验证「无 user:manage 权限时 POST /users 被 @PreAuthorize 拦成 403」。
-- =============================================================

-- 用户 ---------------------------------------------------------
MERGE INTO sys_user (id, username, password, nickname, status) KEY (id)
    VALUES (1, 'admin',
            '$2a$10$MQKQ7Jv5VkM7Gkfzzcj.GexHeZQdBR8PBtY4BFgifgececRtcpNfm',
            '超级管理员', 1);
MERGE INTO sys_user (id, username, password, nickname, status) KEY (id)
    VALUES (2, 'resigned',
            '$2a$10$MQKQ7Jv5VkM7Gkfzzcj.GexHeZQdBR8PBtY4BFgifgececRtcpNfm',
            '已离职员工', 0);
-- agent_user：普通客服，密码 admin123，仅有 ticket:view / dashboard:view
MERGE INTO sys_user (id, username, password, nickname, status) KEY (id)
    VALUES (3, 'agent_user',
            '$2a$10$MQKQ7Jv5VkM7Gkfzzcj.GexHeZQdBR8PBtY4BFgifgececRtcpNfm',
            '客服坐席', 1);

-- 角色 ---------------------------------------------------------
MERGE INTO sys_role (id, role_name, role_key, remark) KEY (id)
    VALUES (1, '超级管理员', 'admin', '拥有全部权限');
MERGE INTO sys_role (id, role_name, role_key, remark) KEY (id)
    VALUES (2, '客服坐席',   'agent', '负责处理工单');

-- 菜单 ---------------------------------------------------------
MERGE INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, sort, visible, permission) KEY (id)
    VALUES (1, 0, 'Dashboard',  'C', '/dashboard',  'Dashboard',  'dashboard', 1, 1, 'dashboard:view');
MERGE INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, sort, visible, permission) KEY (id)
    VALUES (2, 0, '用户管理',   'C', '/users',      'UserList',   'user',      2, 1, 'user:manage');
MERGE INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, sort, visible, permission) KEY (id)
    VALUES (3, 0, '角色管理',   'C', '/roles',      'RoleList',   'role',      3, 1, 'role:manage');
MERGE INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, sort, visible, permission) KEY (id)
    VALUES (4, 0, '菜单管理',   'C', '/menus',      'MenuList',   'menu',      4, 1, 'menu:manage');
MERGE INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, sort, visible, permission) KEY (id)
    VALUES (5, 0, '工单列表',   'C', '/tickets',    'TicketList', 'ticket',    5, 1, 'ticket:view');

-- 角色 ↔ 菜单 ---------------------------------------------------
-- admin：全菜单
MERGE INTO sys_role_menu (role_id, menu_id) KEY (role_id, menu_id) VALUES (1, 1);
MERGE INTO sys_role_menu (role_id, menu_id) KEY (role_id, menu_id) VALUES (1, 2);
MERGE INTO sys_role_menu (role_id, menu_id) KEY (role_id, menu_id) VALUES (1, 3);
MERGE INTO sys_role_menu (role_id, menu_id) KEY (role_id, menu_id) VALUES (1, 4);
MERGE INTO sys_role_menu (role_id, menu_id) KEY (role_id, menu_id) VALUES (1, 5);
-- agent：仅 dashboard + ticket
MERGE INTO sys_role_menu (role_id, menu_id) KEY (role_id, menu_id) VALUES (2, 1);
MERGE INTO sys_role_menu (role_id, menu_id) KEY (role_id, menu_id) VALUES (2, 5);

-- 用户 ↔ 角色 ---------------------------------------------------
MERGE INTO sys_user_role (user_id, role_id) KEY (user_id, role_id) VALUES (1, 1);
MERGE INTO sys_user_role (user_id, role_id) KEY (user_id, role_id) VALUES (3, 2);