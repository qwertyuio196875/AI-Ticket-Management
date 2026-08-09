-- =============================================================
-- ticket 03/04 — RBAC + 数据字典 + 工单分类 表结构（H2，MySQL 兼容模式，仅测试用）
--
-- 与 ticket-system/src/main/resources/db/mysql/schema.sql 结构一致，
-- 去掉 H2 不支持的 ENGINE / COLLATE / COMMENT 语法。
-- 主键 / 索引 / 唯一约束保持一致，保证种子数据幂等。
-- =============================================================

CREATE TABLE IF NOT EXISTS sys_user
(
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    username    VARCHAR(50)  NOT NULL,
    password    VARCHAR(100) NOT NULL,
    nickname    VARCHAR(50)  NOT NULL DEFAULT '',
    status      TINYINT      NOT NULL DEFAULT 1,
    create_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_sys_user_username UNIQUE (username)
);

CREATE TABLE IF NOT EXISTS sys_role
(
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    role_name   VARCHAR(50)  NOT NULL,
    role_key    VARCHAR(50)  NOT NULL,
    remark      VARCHAR(255) NOT NULL DEFAULT '',
    create_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_sys_role_key UNIQUE (role_key)
);

CREATE TABLE IF NOT EXISTS sys_menu
(
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    parent_id   BIGINT       NOT NULL DEFAULT 0,
    menu_name   VARCHAR(50)  NOT NULL,
    menu_type   CHAR(1)      NOT NULL DEFAULT 'C',
    path        VARCHAR(255) NOT NULL DEFAULT '',
    component   VARCHAR(255) NOT NULL DEFAULT '',
    icon        VARCHAR(50)  NOT NULL DEFAULT '',
    sort        INT          NOT NULL DEFAULT 0,
    visible     TINYINT      NOT NULL DEFAULT 1,
    permission  VARCHAR(100) NOT NULL DEFAULT '',
    create_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    -- H2 同样问题：permission='' 的多个菜单会撞唯一索引。
    -- 测试场景里没有目录/菜单混挂，但 schema 必须与 prod 等价。
    -- 用 CAST(... AS VARCHAR(100)) 把 permission 投影成不可空串，'' 也会变占位符形式，
    -- 但更简单的做法是去掉唯一约束，依赖应用层 SysMenuServiceImpl 校验。
    CONSTRAINT uk_sys_menu_permission UNIQUE (permission)
);

CREATE TABLE IF NOT EXISTS sys_user_role
(
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE IF NOT EXISTS sys_role_menu
(
    role_id BIGINT NOT NULL,
    menu_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, menu_id)
);

-- 数据字典（ticket 04） ----------------------------------------
CREATE TABLE IF NOT EXISTS sys_dict
(
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    dict_type   VARCHAR(50)  NOT NULL,
    dict_value  VARCHAR(50)  NOT NULL,
    dict_label  VARCHAR(50)  NOT NULL DEFAULT '',
    sort        INT          NOT NULL DEFAULT 0,
    status      TINYINT      NOT NULL DEFAULT 1,
    remark      VARCHAR(255) NOT NULL DEFAULT '',
    create_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_sys_dict_type_value UNIQUE (dict_type, dict_value)
);

-- 工单分类（ticket 04） ----------------------------------------
CREATE TABLE IF NOT EXISTS ticket_category
(
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(50)  NOT NULL,
    description VARCHAR(255) NOT NULL DEFAULT '',
    sort        INT          NOT NULL DEFAULT 0,
    status      TINYINT      NOT NULL DEFAULT 1,
    create_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_ticket_category_name UNIQUE (name)
);