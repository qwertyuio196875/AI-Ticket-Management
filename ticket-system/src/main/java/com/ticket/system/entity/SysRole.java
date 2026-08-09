package com.ticket.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统角色 {@code sys_role} —— RBAC 第二层（详见 ADR-0002 / CONTEXT.md）。
 * <p>
 * {@code role_key} 是代码里硬编码引用的标识（如 {@code admin} / {@code agent}），
 * 由唯一索引兜底去重；{@code role_name} 仅供前端展示。
 */
@Data
@TableName("sys_role")
public class SysRole {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 角色名（中文展示） */
    private String roleName;

    /** 角色 key（代码里硬编码引用，如 {@code admin}） */
    private String roleKey;

    /** 备注 */
    private String remark;

    private LocalDateTime createTime;
}