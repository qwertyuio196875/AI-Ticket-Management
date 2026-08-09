-- =============================================================
-- ticket 03 — RBAC 表结构（MySQL 8）
--
-- 与 ticket 02 的 sys_user 一起构成完整 RBAC 5 表：
--   sys_user      企业内部员工（ticket 02 已建）
--   sys_role      角色
--   sys_menu      菜单 + permission 操作权限字符串
--   sys_user_role 用户 ↔ 角色多对多
--   sys_role_menu 角色 ↔ 菜单多对多
--
-- 本项目不引入 Flyway（见 spec Out of Scope），改由 Spring Boot
-- spring.sql.init 在启动时执行。DDL 全部 IF NOT EXISTS，可重复执行。
--
-- 权限模型两层（详见 ADR-0002）：
--   1. sys_menu 行存在与否 → 前端路由 + 侧边栏可见性（菜单权限）
--   2. sys_menu.permission 字符串 → @PreAuthorize 后端校验（操作权限）
-- =============================================================

-- 角色表 --------------------------------------------------------
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
    -- permission 可空但要唯一：MySQL 中多个 NULL 不冲突，唯一索引照常生效
    UNIQUE KEY uk_sys_menu_permission (permission)
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