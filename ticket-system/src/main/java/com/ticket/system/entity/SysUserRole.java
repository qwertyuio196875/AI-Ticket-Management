package com.ticket.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户 ↔ 角色关联表 {@code sys_user_role}（详见 ADR-0002）。
 * <p>
 * 复合主键（{@code userId} + {@code roleId}）由数据库约束保证，
 * 实体类不复写 {@code @TableId}，由 Mapper 直接走自定义 INSERT/DELETE。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_user_role")
public class SysUserRole {

    private Long userId;
    private Long roleId;
}