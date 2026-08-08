package com.ticket.web.auth.vo;

import com.ticket.security.user.LoginUser;
import org.springframework.security.core.GrantedAuthority;

import java.util.List;

/**
 * 当前登录用户信息（{@code GET /api/v1/auth/me}）。
 * <p>
 * 数据全部来自 JWT 载荷，<b>不回查数据库</b> —— 无状态认证的体现。
 * 因此这里没有 nickname（未写进 token）。
 *
 * @param userId      sys_user.id
 * @param username    登录名
 * @param authorities 操作权限字符串；ticket 02 阶段恒为空，ticket 03 接入 sys_menu.permission 后才有值
 */
public record UserInfoVO(
        Long userId,
        String username,
        List<String> authorities
) {

    public static UserInfoVO from(LoginUser user) {
        return new UserInfoVO(
                user.getUserId(),
                user.getUsername(),
                user.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList());
    }
}
