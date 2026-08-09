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