package com.ticket.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统用户 {@code sys_user} —— 企业内部员工（详见 CONTEXT.md / ADR-0001）。
 * <p>
 * ticket 02 只建表 + 认证所需字段；用户 CRUD 与角色关联在 ticket 03。
 */
@Data
@TableName("sys_user")
public class SysUser {

    /** 启用 */
    public static final Integer STATUS_ENABLED = 1;
    /** 禁用（离职 / 停用） */
    public static final Integer STATUS_DISABLED = 0;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 登录名，唯一 */
    private String username;

    /** BCrypt 密码哈希 —— 禁止存明文 */
    private String password;

    /** 展示名 */
    private String nickname;

    /** 状态：{@link #STATUS_ENABLED} / {@link #STATUS_DISABLED} */
    private Integer status;

    private LocalDateTime createTime;
}
