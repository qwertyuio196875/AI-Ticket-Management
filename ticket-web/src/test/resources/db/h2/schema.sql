-- =============================================================
-- ticket 02 — sys_user 表结构（H2，MySQL 兼容模式，仅测试用）
--
-- 与 ticket-system/src/main/resources/db/mysql/schema.sql 结构一致，
-- 去掉 H2 不支持的 ENGINE / COLLATE / COMMENT 语法。
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
