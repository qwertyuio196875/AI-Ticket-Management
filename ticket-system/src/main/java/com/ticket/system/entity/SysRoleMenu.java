package com.ticket.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 角色 ↔ 菜单关联表 {@code sys_role_menu}（详见 ADR-0002）。
 * <p>
 * 复合主键（{@code roleId} + {@code menuId}）由数据库约束保证；
 * 实体类不复写 {@code @TableId}。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_role_menu")
public class SysRoleMenu {

    private Long roleId;
    private Long menuId;
}