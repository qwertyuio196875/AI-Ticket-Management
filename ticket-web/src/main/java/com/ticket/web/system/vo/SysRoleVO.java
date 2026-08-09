package com.ticket.web.system.vo;

import com.ticket.system.entity.SysRole;

import java.time.LocalDateTime;

/**
 * 角色 VO —— 前端展示用。
 */
public record SysRoleVO(
        Long id,
        String roleName,
        String roleKey,
        String remark,
        LocalDateTime createTime
) {

    public static SysRoleVO from(SysRole role) {
        return new SysRoleVO(
                role.getId(),
                role.getRoleName(),
                role.getRoleKey(),
                role.getRemark(),
                role.getCreateTime());
    }
}