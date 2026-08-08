package com.ticket.security.user;

import lombok.Builder;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * 登录主体 —— Spring Security {@link UserDetails} 的项目实现。
 * <p>
 * 相比原生 {@code User}，额外携带 {@code userId} 与 {@code nickname}：
 * <ul>
 *     <li>{@code userId}：{@code sys_user.id}，业务层要用它写 {@code creator_id} / {@code handler_id}</li>
 *     <li>{@code nickname}：前端展示名</li>
 * </ul>
 * <p>
 * 有两条构造路径，区别要清楚：
 * <ol>
 *     <li><b>登录时</b>由 {@code ticket-system} 从 {@code sys_user} 查库构造 —— 带密码哈希，
 *         用于 {@code PasswordEncoder.matches} 比对</li>
 *     <li><b>后续请求</b>由 {@link #fromToken} 从 JWT 载荷还原 —— <b>不带密码、不带 nickname</b>，
 *         因为无状态认证不再查库（详见 ticket 02）</li>
 * </ol>
 */
@Getter
@Builder
public class LoginUser implements UserDetails {

    /** sys_user.id */
    private final Long userId;

    private final String username;

    /** BCrypt 密码哈希；{@link #fromToken} 构造的实例为 null */
    private final String password;

    /** 展示名；{@link #fromToken} 构造的实例为 null */
    private final String nickname;

    /** 对应 sys_user.status —— false 表示账号禁用，禁止登录 */
    private final boolean enabled;

    private final Collection<? extends GrantedAuthority> authorities;

    /**
     * 从 JWT 载荷还原登录主体，供 {@code JwtAuthFilter} 写入 SecurityContext。
     * <p>
     * 无状态认证不回查数据库，因此密码与 nickname 缺失；{@code enabled} 固定 true ——
     * token 有效即视为可用，账号禁用要等 token 自然过期或被拉黑才生效
     * （单 token 方案的取舍，详见 spec "不做双 token / refresh"）。
     */
    public static LoginUser fromToken(Long userId, String username, Collection<String> authorities) {
        return LoginUser.builder()
                .userId(userId)
                .username(username)
                .enabled(true)
                .authorities(toGrantedAuthorities(authorities))
                .build();
    }

    /** 权限字符串 → {@link GrantedAuthority}；空集合合法（ticket 02 阶段恒为空） */
    public static List<GrantedAuthority> toGrantedAuthorities(Collection<String> permissions) {
        if (permissions == null) {
            return List.of();
        }
        return permissions.stream()
                .map(permission -> (GrantedAuthority) new SimpleGrantedAuthority(permission))
                .toList();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities == null ? List.of() : authorities;
    }

    /** 账号永不过期 —— 本项目不做账号有效期 */
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    /** 账号永不锁定 —— 本项目不做登录失败锁定 */
    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    /** 凭证永不过期 —— 本项目不做密码有效期 */
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
