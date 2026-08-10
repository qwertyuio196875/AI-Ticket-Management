-- =============================================================
-- AI 智能工单管理系统 — 数据库 schema（MySQL 8）
--
-- 由 Spring Boot spring.sql.init 在启动时执行；DDL 全部 IF NOT EXISTS，
-- 可重复执行（本项目不引入 Flyway，见 spec Out of Scope）。
--
-- 维护说明：ticket 之间如果新增表，请把建表语句 append 到本文件末尾，
-- 并同步更新 src/test/resources/db/h2/schema.sql（去掉 ENGINE / COMMENT），
-- **不要覆盖已有内容** —— 早期 ticket 的建表必须保留。
-- =============================================================

-- 用户表（ticket 02） ------------------------------------------
-- 用户 = 企业内部员工（见 ADR-0001），外部客户不登录系统。
CREATE TABLE IF NOT EXISTS sys_user
(
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    username    VARCHAR(50)     NOT NULL COMMENT '登录名，唯一',
    password    VARCHAR(100)    NOT NULL COMMENT 'BCrypt 密码哈希（禁止明文）',
    nickname    VARCHAR(50)     NOT NULL DEFAULT '' COMMENT '展示名',
    status      TINYINT         NOT NULL DEFAULT 1 COMMENT '状态：1 启用 / 0 禁用',
    create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_user_username (username)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='系统用户（企业内部员工）';

-- 角色表（ticket 03） ----------------------------------------
CREATE TABLE IF NOT EXISTS sys_role
(
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    role_name   VARCHAR(50)     NOT NULL COMMENT '角色名（中文展示）',
    role_key    VARCHAR(50)     NOT NULL COMMENT '角色 key（代码里硬编码引用，如 admin）',
    remark      VARCHAR(255)    NOT NULL DEFAULT '' COMMENT '备注',
    create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_role_key (role_key)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='系统角色';

-- 菜单 / 权限表 -----------------------------------------------
-- menu_type：M=目录（仅承载子节点）/ C=菜单（页面）/ F=按钮（仅 permission，无路由）
-- parent_id 自引用；permission 是按钮级权限字符串，目录与菜单可为空
-- 注意：permission 唯一索引用"前缀长度"形式 —— 多个 ''（空串菜单）能共存，
--       非空字符串仍按完整字符串去重；不会让目录/菜单类型在 permission='' 时撞唯一索引
CREATE TABLE IF NOT EXISTS sys_menu
(
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    parent_id   BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '父菜单 id，0 = 顶级',
    menu_name   VARCHAR(50)     NOT NULL COMMENT '菜单名（侧边栏展示）',
    menu_type   CHAR(1)         NOT NULL DEFAULT 'C' COMMENT '类型：M 目录 / C 菜单 / F 按钮',
    path        VARCHAR(255)    NOT NULL DEFAULT '' COMMENT '前端路由 path',
    component   VARCHAR(255)    NOT NULL DEFAULT '' COMMENT '前端组件路径',
    icon        VARCHAR(50)     NOT NULL DEFAULT '' COMMENT '图标标识',
    sort        INT             NOT NULL DEFAULT 0 COMMENT '同 parent 下的排序号，升序',
    visible     TINYINT         NOT NULL DEFAULT 1 COMMENT '是否显示：1 显示 / 0 隐藏',
    permission  VARCHAR(100)    NOT NULL DEFAULT '' COMMENT '操作权限字符串（按钮级），@PreAuthorize 校验依据',
    create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_sys_menu_parent (parent_id),
    -- 空串取前 1 个字符实际不可能重复（'' 全等），但前缀索引能让 MySQL 把多个空串视为不同行；
    -- 非空字符串仍按完整内容去重。
    UNIQUE KEY uk_sys_menu_permission (permission(100))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='菜单 / 操作权限';

-- 用户 ↔ 角色 ---------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_user_role
(
    user_id BIGINT UNSIGNED NOT NULL COMMENT 'sys_user.id',
    role_id BIGINT UNSIGNED NOT NULL COMMENT 'sys_role.id',
    PRIMARY KEY (user_id, role_id),
    KEY idx_sys_user_role_role (role_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='用户 ↔ 角色';

-- 角色 ↔ 菜单 ---------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_role_menu
(
    role_id BIGINT UNSIGNED NOT NULL COMMENT 'sys_role.id',
    menu_id BIGINT UNSIGNED NOT NULL COMMENT 'sys_menu.id',
    PRIMARY KEY (role_id, menu_id),
    KEY idx_sys_role_menu_menu (menu_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='角色 ↔ 菜单';

-- 数据字典（ticket 04） ----------------------------------------
-- 将 priority / comment_type / status 等枚举的可选项抽到表里管理
-- (dict_type, dict_value) 联合唯一：同 type 下 value 不能重复
CREATE TABLE IF NOT EXISTS sys_dict
(
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    dict_type  VARCHAR(50)     NOT NULL COMMENT '字典类型，如 priority / comment_type / status',
    dict_value VARCHAR(50)     NOT NULL COMMENT '字典值（代码里硬编码引用），如 HIGH / CUSTOMER',
    dict_label VARCHAR(50)     NOT NULL DEFAULT '' COMMENT '字典展示名（中文），如 高 / 客户',
    sort       INT             NOT NULL DEFAULT 0 COMMENT '同 type 下的排序号，升序',
    status     TINYINT         NOT NULL DEFAULT 1 COMMENT '状态：1 启用 / 0 禁用',
    remark     VARCHAR(255)    NOT NULL DEFAULT '' COMMENT '备注',
    create_time DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_dict_type_value (dict_type, dict_value),
    KEY idx_sys_dict_type (dict_type)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='数据字典（priority / comment_type / status 等）';

-- 工单分类（ticket 04） ----------------------------------------
-- 管理员可配置的工单分类字典；AI 分类结果（type）应与本表对齐
CREATE TABLE IF NOT EXISTS ticket_category
(
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    name        VARCHAR(50)     NOT NULL COMMENT '分类名（中文展示）',
    description VARCHAR(255)    NOT NULL DEFAULT '' COMMENT '分类描述',
    sort        INT             NOT NULL DEFAULT 0 COMMENT '排序号，升序',
    status      TINYINT         NOT NULL DEFAULT 1 COMMENT '状态：1 启用 / 0 禁用',
    create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ticket_category_name (name)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='工单分类（与 AI 分类结果对齐）';

-- 工单主表（ticket 05） ----------------------------------------
-- 工单从创建到关闭的完整生命周期主记录；ticket_log 记录状态变更流水
-- 索引设计（spec Phase 5 / 联合索引 EXPLAIN 验证留给 ticket 09）：
--   - 单列 idx_status / idx_handler_id / idx_create_time
--   - 联合 idx_status_handler_createtime 覆盖"我的工单"+"近期"查询
CREATE TABLE IF NOT EXISTS ticket_info
(
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    ticket_no   VARCHAR(30)     NOT NULL COMMENT '工单编号，格式 TK{yyyyMMdd}{9 位}（ADR-0006）',
    title       VARCHAR(100)    NOT NULL DEFAULT '' COMMENT '工单标题',
    content     TEXT            NOT NULL COMMENT '工单内容（详细描述）',
    type        VARCHAR(50)     NOT NULL DEFAULT '' COMMENT '工单分类（来自 ticket_category.name，可空）',
    priority    VARCHAR(20)     NOT NULL DEFAULT 'MEDIUM' COMMENT '优先级 HIGH / MEDIUM / LOW',
    status      VARCHAR(20)     NOT NULL DEFAULT 'PENDING' COMMENT '状态 PENDING / PROCESSING / RESOLVED / CLOSED',
    creator_id  BIGINT UNSIGNED NOT NULL COMMENT '创建人 sys_user.id',
    handler_id  BIGINT UNSIGNED          DEFAULT NULL COMMENT '处理人 sys_user.id（可空）',
    create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted  TINYINT         NOT NULL DEFAULT 0 COMMENT '软删标记：0 未删 / 1 已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ticket_no (ticket_no),
    KEY idx_ticket_info_status (status),
    KEY idx_ticket_info_handler_id (handler_id),
    KEY idx_ticket_info_create_time (create_time),
    KEY idx_ticket_info_status_handler_createtime (status, handler_id, create_time)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='工单主表';

-- 工单业务事件流水（ticket 05） --------------------------------
-- 与 ticket_info 同事务写入（ADR-0012）；event_type 取值见 TicketEventType 枚举
CREATE TABLE IF NOT EXISTS ticket_log
(
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    ticket_id   BIGINT UNSIGNED NOT NULL COMMENT '工单主键 ticket_info.id',
    event_type  VARCHAR(20)     NOT NULL COMMENT '事件类型 CREATED / UPDATED / STATUS_CHANGED / ASSIGNED / COMMENTED / AI_CALLED',
    operator_id BIGINT UNSIGNED          DEFAULT NULL COMMENT '操作人 sys_user.id（系统事件可空）',
    content     TEXT            NOT NULL COMMENT '事件内容（JSON 或文本）',
    create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_ticket_log_ticket_id (ticket_id),
    KEY idx_ticket_log_event_type (event_type)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='工单业务事件流水';

-- HTTP 请求审计日志（ticket 05） -------------------------------
-- 由 OperationLogAspect 在 Controller 边界自动切面写入
-- params 截断 2000 字符、user_agent 截断 500 字符
CREATE TABLE IF NOT EXISTS operation_log
(
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id     BIGINT UNSIGNED          DEFAULT NULL COMMENT 'sys_user.id，未登录时为 null',
    username    VARCHAR(50)     NOT NULL DEFAULT '' COMMENT 'sys_user.username，未登录时为空串',
    operation   VARCHAR(50)     NOT NULL DEFAULT '' COMMENT '操作描述，来自 @OperationLog.value',
    type        VARCHAR(20)     NOT NULL DEFAULT '' COMMENT '操作类型/模块，来自 @OperationLog.type',
    method      VARCHAR(255)    NOT NULL DEFAULT '' COMMENT '方法签名 ClassName.methodName',
    params      VARCHAR(2000)   NOT NULL DEFAULT '' COMMENT '入参 JSON 字符串，截断 2000 字符',
    ip          VARCHAR(50)     NOT NULL DEFAULT '' COMMENT '客户端 IP',
    user_agent  VARCHAR(500)    NOT NULL DEFAULT '' COMMENT 'User-Agent 头，截断 500 字符',
    duration_ms BIGINT          NOT NULL DEFAULT 0 COMMENT '方法耗时毫秒',
    create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_operation_log_user_id (user_id),
    KEY idx_operation_log_create_time (create_time)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='HTTP 请求审计日志';