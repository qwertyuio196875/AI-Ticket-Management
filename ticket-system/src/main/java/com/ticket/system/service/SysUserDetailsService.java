package com.ticket.system.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ticket.security.user.LoginUser;
import com.ticket.system.entity.SysUser;
import com.ticket.system.mapper.SysUserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Spring Security {@link UserDetailsService} 的 {@code sys_user} 实现。
 * <p>
 * 这是 {@code ticket-security} 与 {@code ticket-system} 的唯一衔接点：
 * 安全模块只认 {@link UserDetailsService} 这个 SPI，用户数据从哪来它不关心 ——
 * 因此 security 不需要反向依赖 system，模块依赖保持单向（详见 spec 模块边界）。
 * <p>
 * <b>ticket 03 起</b>：登录时连表查用户的权限字符串（{@code sys_menu.permission}），
 * 写入 {@link LoginUser#getAuthorities()} —— 后续 JWT 签发、{@code @PreAuthorize} 校验、
 * 前端菜单渲染都从这条链路上拿权限。
 */
@Service
public class SysUserDetailsService implements UserDetailsService {

    private static final Logger log = LoggerFactory.getLogger(SysUserDetailsService.class);

    private final SysUserMapper sysUserMapper;
    private final SysUserService sysUserService;

    public SysUserDetailsService(SysUserMapper sysUserMapper, SysUserService sysUserService) {
        this.sysUserMapper = sysUserMapper;
        this.sysUserService = sysUserService;
    }

    /**
     * 按登录名查用户。
     *
     * @throws UsernameNotFoundException 用户不存在 —— 由 {@code AuthService} 转成统一的 401 话术
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        SysUser user = sysUserMapper.selectOne(
                Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, username));
        if (user == null) {
            log.debug("sys_user 中不存在用户: {}", username);
            throw new UsernameNotFoundException("用户不存在: " + username);
        }
        // ticket 03：连表查用户全部角色的全部菜单的 permission，写入 LoginUser.authorities
        List<String> permissions = sysUserService.listPermissions(user.getId());
        List<GrantedAuthority> authorities = LoginUser.toGrantedAuthorities(permissions);
        return LoginUser.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .password(user.getPassword())
                .nickname(user.getNickname())
                .enabled(SysUser.STATUS_ENABLED.equals(user.getStatus()))
                .authorities(authorities)
                .build();
    }
}
