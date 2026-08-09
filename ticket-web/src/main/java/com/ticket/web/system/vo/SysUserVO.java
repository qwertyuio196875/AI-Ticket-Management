package com.ticket.web.system.vo;

import com.ticket.system.entity.SysUser;

import java.time.LocalDateTime;

/**
 * 用户 VO —— 前端展示用，屏蔽密码字段。
 */
public record SysUserVO(
        Long id,
        String username,
        String nickname,
        Integer status,
        LocalDateTime createTime
) {

    public static SysUserVO from(SysUser user) {
        return new SysUserVO(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getStatus(),
                user.getCreateTime());
    }
}