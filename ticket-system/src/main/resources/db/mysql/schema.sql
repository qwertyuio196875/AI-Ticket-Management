-- =============================================================
-- ticket 02 — sys_user 表结构（MySQL 8）
--
-- 本项目不引入 Flyway（见 spec Out of Scope），改由 Spring Boot
-- spring.sql.init 在启动时执行。DDL 全部 IF NOT EXISTS，可重复执行。
--
-- 用户 = 企业内部员工（见 ADR-0001），外部客户不登录系统。
-- =============================================================

CREATE TABLE IF NOT EXISTS sys_user
(
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    username    VARCHAR(50)     NOT NULL COMMENT '登录名，唯一',
    password    VARCHAR(100)    NOT NULL COMMENT 'BCrypt 密码哈希（禁止明文）',
    nickname    VARCHAR(50)     NOT NULL DEFAULT '' COMMENT '展示名',
    status      TINYINT         NOT NULL DEFAULT 1 COMMENT '状态：1 启用 / 0 禁用',
    create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    -- 登录按 username 单查，唯一索引兼顾去重与查询
    UNIQUE KEY uk_sys_user_username (username)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='系统用户（企业内部员工）';
