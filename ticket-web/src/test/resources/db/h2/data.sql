-- =============================================================
-- ticket 03/04 — 集成测试种子数据（H2）
--
-- admin 的哈希与 ticket 02 保持一致（admin/admin123）；
-- ticket 03 起新增普通用户 agent_user，挂 agent 角色，
-- 用于验证「无 user:manage 权限时 POST /users 被 @PreAuthorize 拦成 403」。
-- ticket 04 起新增字典/分类种子 + 两个权限点。
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
-- ticket 04 新增两个权限点
MERGE INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, sort, visible, permission) KEY (id)
    VALUES (6, 0, '字典管理', 'C', '/dicts',          'DictList',          'dict',     6, 1, 'dict:manage');
MERGE INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, sort, visible, permission) KEY (id)
    VALUES (7, 0, '工单分类', 'C', '/ticket-categories', 'TicketCategoryList', 'category', 7, 1, 'category:manage');
-- ticket 05 新增三个按钮权限点（挂在工单列表菜单 id=5 下；写操作只允许管理员）
-- ticket:update 预留 ticket 06+，ticket 05 仍由 Service "创建人或管理员" 规则把关
MERGE INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, sort, visible, permission) KEY (id)
    VALUES (8, 5, '新建工单', 'F', '', '', '', 1, 1, 'ticket:create');
MERGE INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, sort, visible, permission) KEY (id)
    VALUES (9, 5, '修改工单', 'F', '', '', '', 2, 1, 'ticket:update');
MERGE INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, sort, visible, permission) KEY (id)
    VALUES (10, 5, '删除工单', 'F', '', '', '', 3, 1, 'ticket:delete');
-- ticket 06 新增两个按钮权限点：ticket:assign / ticket:close（admin 专属）
MERGE INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, sort, visible, permission) KEY (id)
    VALUES (11, 5, '分配工单', 'F', '', '', '', 4, 1, 'ticket:assign');
MERGE INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, sort, visible, permission) KEY (id)
    VALUES (12, 5, '关闭工单', 'F', '', '', '', 5, 1, 'ticket:close');

-- 角色 ↔ 菜单 ---------------------------------------------------
-- admin：全菜单
MERGE INTO sys_role_menu (role_id, menu_id) KEY (role_id, menu_id) VALUES (1, 1);
MERGE INTO sys_role_menu (role_id, menu_id) KEY (role_id, menu_id) VALUES (1, 2);
MERGE INTO sys_role_menu (role_id, menu_id) KEY (role_id, menu_id) VALUES (1, 3);
MERGE INTO sys_role_menu (role_id, menu_id) KEY (role_id, menu_id) VALUES (1, 4);
MERGE INTO sys_role_menu (role_id, menu_id) KEY (role_id, menu_id) VALUES (1, 5);
MERGE INTO sys_role_menu (role_id, menu_id) KEY (role_id, menu_id) VALUES (1, 6);
MERGE INTO sys_role_menu (role_id, menu_id) KEY (role_id, menu_id) VALUES (1, 7);
-- ticket 05：admin 拥有 ticket:create / ticket:update / ticket:delete 按钮权限
MERGE INTO sys_role_menu (role_id, menu_id) KEY (role_id, menu_id) VALUES (1, 8);
MERGE INTO sys_role_menu (role_id, menu_id) KEY (role_id, menu_id) VALUES (1, 9);
MERGE INTO sys_role_menu (role_id, menu_id) KEY (role_id, menu_id) VALUES (1, 10);
-- ticket 06：admin 拥有 ticket:assign / ticket:close 按钮权限
MERGE INTO sys_role_menu (role_id, menu_id) KEY (role_id, menu_id) VALUES (1, 11);
MERGE INTO sys_role_menu (role_id, menu_id) KEY (role_id, menu_id) VALUES (1, 12);
-- agent：仅 dashboard + ticket
MERGE INTO sys_role_menu (role_id, menu_id) KEY (role_id, menu_id) VALUES (2, 1);
MERGE INTO sys_role_menu (role_id, menu_id) KEY (role_id, menu_id) VALUES (2, 5);

-- 用户 ↔ 角色 ---------------------------------------------------
MERGE INTO sys_user_role (user_id, role_id) KEY (user_id, role_id) VALUES (1, 1);
MERGE INTO sys_user_role (user_id, role_id) KEY (user_id, role_id) VALUES (3, 2);

-- 数据字典种子（ticket 04） ------------------------------------
MERGE INTO sys_dict (id, dict_type, dict_value, dict_label, sort, status, remark) KEY (id) VALUES
    (1,  'priority',     'HIGH',      '高',       1, 1, '紧急处理'),
    (2,  'priority',     'MEDIUM',    '中',       2, 1, '正常排期'),
    (3,  'priority',     'LOW',       '低',       3, 1, '可延后'),
    (4,  'comment_type', 'CUSTOMER',  '客户',     1, 1, '外部客户回复'),
    (5,  'comment_type', 'AGENT',     '客服',     2, 1, '客服坐席回复'),
    (6,  'comment_type', 'INTERNAL',  '内部',     3, 1, '内部备注'),
    (7,  'status',       'PENDING',   '待处理',   1, 1, '工单初始状态'),
    (8,  'status',       'PROCESSING','处理中',   2, 1, '已分配处理人'),
    (9,  'status',       'RESOLVED',  '已解决',   3, 1, '处理完成等待确认'),
    (10, 'status',       'CLOSED',    '已关闭',   4, 1, '工单关闭');

-- 工单分类种子（ticket 04） ------------------------------------
MERGE INTO ticket_category (id, name, description, sort, status) KEY (id) VALUES
    (1, '系统故障',  '服务器、网络、数据库等基础设施故障', 1, 1),
    (2, '网络问题',  '网络连接、VPN、带宽等相关问题',     2, 1),
    (3, '业务咨询',  '业务流程、系统使用相关的咨询',     3, 1),
    (4, '账号权限',  '账号开通、权限变更、密码重置等',   4, 1),
    (5, '其他',      '未明确分类的工单',                 5, 1);