package com.ticket.system.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ticket.security.user.LoginUser;
import com.ticket.system.entity.SysUser;
import com.ticket.system.mapper.SysUserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 */
@Service
public class SysUserDetailsService implements UserDetailsService {

    private static final Logger log = LoggerFactory.getLogger(SysUserDetailsService.class);

    private final SysUserMapper sysUserMapper;

    public SysUserDetailsService(SysUserMapper sysUserMapper) {
        this.sysUserMapper = sysUserMapper;
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
        return LoginUser.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .password(user.getPassword())
                .nickname(user.getNickname())
                .enabled(SysUser.STATUS_ENABLED.equals(user.getStatus()))
                // ticket 02 阶段权限恒为空；ticket 03 建 sys_role / sys_menu 后
                // 在此查出 sys_menu.permission 列表，经 LoginUser.toGrantedAuthorities 转换填入
                .authorities(List.of())
                .build();
    }
}
