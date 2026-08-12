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

-- 数据字典种子（ticket 04） ------------------------------------
-- 3 个 dict_type：priority / comment_type / status
INSERT IGNORE INTO sys_dict (dict_type, dict_value, dict_label, sort, status, remark) VALUES
    -- priority：工单优先级
    ('priority', 'HIGH',   '高',   1, 1, '紧急处理'),
    ('priority', 'MEDIUM', '中',   2, 1, '正常排期'),
    ('priority', 'LOW',    '低',   3, 1, '可延后'),
    -- comment_type：工单评论类型
    ('comment_type', 'CUSTOMER', '客户', 1, 1, '外部客户回复'),
    ('comment_type', 'AGENT',    '客服', 2, 1, '客服坐席回复'),
    ('comment_type', 'INTERNAL', '内部', 3, 1, '内部备注'),
    -- status：工单状态（与 TicketStatus 枚举对齐）
    ('status', 'PENDING',    '待处理', 1, 1, '工单初始状态'),
    ('status', 'PROCESSING', '处理中', 2, 1, '已分配处理人'),
    ('status', 'RESOLVED',   '已解决', 3, 1, '处理完成等待确认'),
    ('status', 'CLOSED',     '已关闭', 4, 1, '工单关闭');

-- 工单分类种子（ticket 04） ------------------------------------
INSERT IGNORE INTO ticket_category (name, description, sort, status) VALUES
    ('系统故障', '服务器、网络、数据库等基础设施故障', 1, 1),
    ('网络问题', '网络连接、VPN、带宽等相关问题',     2, 1),
    ('业务咨询', '业务流程、系统使用相关的咨询',     3, 1),
    ('账号权限', '账号开通、权限变更、密码重置等',   4, 1),
    ('其他',     '未明确分类的工单',                 5, 1);

-- 菜单权限点（ticket 04） ------------------------------------
-- 字典管理、工单分类管理：与 ticket 03 风格一致，顶层 C 类型菜单
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, sort, visible, permission) VALUES
    (6, 0, '字典管理',   'C', '/dicts',          'DictList',          'dict',     6, 1, 'dict:manage'),
    (7, 0, '工单分类',   'C', '/ticket-categories', 'TicketCategoryList', 'category', 7, 1, 'category:manage');

-- 角色 ↔ 菜单（admin 新增两个权限点；agent 维持原状）
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES
    (1, 6), (1, 7);

-- 菜单权限点（ticket 05） ------------------------------------
-- 工单写操作权限：ticket:create（创建）、ticket:update（修改）、ticket:delete（删除）
-- 挂在工单列表菜单（id=5）下作按钮；agent 不绑 —— 写操作只允许管理员
-- 注：{@code ticket:update} 用于 ticket 06+ 的"管理员改他人工单"场景；
-- ticket 05 内仍由 Service 层的"创建人或管理员"规则（{@code ensureCreatorOrAdmin}）把关
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, sort, visible, permission) VALUES
    (8, 5, '新建工单', 'F', '', '', '', 1, 1, 'ticket:create'),
    (9, 5, '修改工单', 'F', '', '', '', 2, 1, 'ticket:update'),
    (10, 5, '删除工单', 'F', '', '', '', 3, 1, 'ticket:delete');

-- 角色 ↔ 菜单：admin 拥有 ticket:create / ticket:update / ticket:delete
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES
    (1, 8), (1, 9), (1, 10);

-- 菜单权限点（ticket 06） ------------------------------------
-- 工单状态机相关权限：
--   ticket:assign —— 分配处理人（PUT /tickets/{id}/assign）
--   ticket:close  —— 关闭工单   （POST /tickets/{id}/close）
-- 同样挂在工单列表菜单（id=5）下作按钮；agent 不绑 —— 管理员专属
-- 注：{@code ticket:update} 同时也是 PATCH /tickets/{id}/status 的权限串（状态变更），
-- 避免再开 ticket:status 这种细粒度按钮 —— spec Phase 3 user story 20 由"处理人"操作，
-- 实际企业里"改状态"和"改内容"通常一起授权
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, sort, visible, permission) VALUES
    (11, 5, '分配工单', 'F', '', '', '', 4, 1, 'ticket:assign'),
    (12, 5, '关闭工单', 'F', '', '', '', 5, 1, 'ticket:close');

-- 角色 ↔ 菜单：admin 拥有 ticket:assign / ticket:close
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES
    (1, 11), (1, 12);

-- 菜单权限点（ticket 07） ------------------------------------
-- ticket:comment —— 工单多轮对话（POST/DELETE 写操作需要；GET 列表只需 ticket:view）
-- admin + agent 都可回复工单
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, sort, visible, permission) VALUES
    (13, 5, '回复工单', 'F', '', '', '', 6, 1, 'ticket:comment');

-- 角色 ↔ 菜单：admin + agent 都拥有 ticket:comment
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES
    (1, 13), (2, 13);

-- 隐藏伪菜单：service 内"管理员"判定的 authority 标记
-- 无 path / component / visible=0 —— 仅作 sys_menu 行存在，让 admin 角色在
-- LoginUser.authorities 中出现 'admin' 串（与 ticket 05/06/07 ensureCreatorOrAdmin / admin-only delete 共用）
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, sort, visible, permission) VALUES
    (14, 0, '管理员标记', 'F', '', '', '', 99, 0, 'admin');

-- 角色 ↔ 菜单：admin 拥有 'admin' 伪 authority
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 14);

-- 菜单权限点（ticket 10） ------------------------------------
-- stats:view —— Dashboard 统计查看（4 个 /stats/tickets/* 端点）
-- admin + agent 都可看统计
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, sort, visible, permission) VALUES
    (15, 1, '统计查看', 'F', '', '', '', 1, 1, 'stats:view');

-- 角色 ↔ 菜单：admin + agent 都拥有 stats:view
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES
    (1, 15), (2, 15);