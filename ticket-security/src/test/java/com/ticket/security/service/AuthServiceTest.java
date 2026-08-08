package com.ticket.security.service;

import com.ticket.common.exception.BusinessException;
import com.ticket.security.blacklist.TokenBlacklistStore;
import com.ticket.security.jwt.JwtProperties;
import com.ticket.security.jwt.JwtUtil;
import com.ticket.security.user.LoginUser;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AuthService} 单元测试（Seam 1 — Mockito，不启动 Spring 上下文）。
 * <p>
 * {@link UserDetailsService} / {@link TokenBlacklistStore} 用 mock，
 * {@link JwtUtil} 与 {@link PasswordEncoder} 用真实实现 —— 签名与哈希是本用例要断言的核心行为。
 * <p>
 * 覆盖 ticket 02 验收标准：登录成功签发 token、凭证错误返回 401、
 * 账号禁用拒绝登录、登出把 jti 按剩余有效期写入黑名单。
 */
class AuthServiceTest {

    private static final String SECRET = "test-secret-key-for-jwt-hmac-sha256-at-least-32-bytes";
    private static final String RAW_PASSWORD = "admin123";

    private UserDetailsService userDetailsService;
    private TokenBlacklistStore blacklistStore;
    private JwtUtil jwtUtil;
    private PasswordEncoder passwordEncoder;
    private AuthService authService;

    private String bcryptHash;

    @BeforeEach
    void setUp() {
        userDetailsService = mock(UserDetailsService.class);
        blacklistStore = mock(TokenBlacklistStore.class);
        passwordEncoder = new BCryptPasswordEncoder();
        bcryptHash = passwordEncoder.encode(RAW_PASSWORD);

        JwtProperties props = new JwtProperties();
        props.setSecret(SECRET);
        props.setExpireMinutes(30);
        jwtUtil = new JwtUtil(props);

        authService = new AuthService(userDetailsService, passwordEncoder, jwtUtil, blacklistStore, props);
    }

    private LoginUser enabledUser() {
        return LoginUser.builder()
                .userId(1L)
                .username("admin")
                .password(bcryptHash)
                .nickname("超级管理员")
                .enabled(true)
                .authorities(List.of())
                .build();
    }

    // ---------- 登录成功 ----------

    @Test
    void login_with_correct_credentials_returns_signed_token() {
        when(userDetailsService.loadUserByUsername("admin")).thenReturn(enabledUser());

        LoginResult result = authService.login("admin", RAW_PASSWORD);

        assertThat(result.token()).isNotBlank();
        Claims claims = jwtUtil.parseToken(result.token());
        assertThat(claims.getSubject()).isEqualTo("admin");
        assertThat(jwtUtil.getUserId(claims)).isEqualTo(1L);
    }

    @Test
    void login_result_carries_user_info_and_expiry_for_the_frontend() {
        when(userDetailsService.loadUserByUsername("admin")).thenReturn(enabledUser());

        LoginResult result = authService.login("admin", RAW_PASSWORD);

        assertThat(result.userId()).isEqualTo(1L);
        assertThat(result.username()).isEqualTo("admin");
        assertThat(result.nickname()).isEqualTo("超级管理员");
        assertThat(result.tokenType()).isEqualTo("Bearer");
        // 30 min = 1800 s
        assertThat(result.expiresIn()).isEqualTo(1800L);
    }

    @Test
    void login_issues_empty_authorities_until_rbac_lands_in_ticket_03() {
        when(userDetailsService.loadUserByUsername("admin")).thenReturn(enabledUser());

        Claims claims = jwtUtil.parseToken(authService.login("admin", RAW_PASSWORD).token());

        assertThat(jwtUtil.getAuthorities(claims)).isEmpty();
    }

    // ---------- 登录失败 ----------

    @Test
    void login_with_wrong_password_is_rejected_with_401() {
        when(userDetailsService.loadUserByUsername("admin")).thenReturn(enabledUser());

        assertThatThrownBy(() -> authService.login("admin", "wrong-password"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("401"));
    }

    @Test
    void login_with_unknown_username_is_rejected_with_401() {
        when(userDetailsService.loadUserByUsername("ghost"))
                .thenThrow(new UsernameNotFoundException("ghost"));

        assertThatThrownBy(() -> authService.login("ghost", RAW_PASSWORD))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("401"));
    }

    @Test
    void login_with_unknown_username_does_not_leak_whether_the_account_exists() {
        when(userDetailsService.loadUserByUsername("ghost"))
                .thenThrow(new UsernameNotFoundException("ghost"));
        when(userDetailsService.loadUserByUsername("admin")).thenReturn(enabledUser());

        String unknownUserMessage = catchMessage(() -> authService.login("ghost", RAW_PASSWORD));
        String wrongPasswordMessage = catchMessage(() -> authService.login("admin", "wrong-password"));

        // 用户不存在与密码错误必须返回同一句提示，避免用户名枚举
        assertThat(unknownUserMessage).isEqualTo(wrongPasswordMessage);
    }

    @Test
    void login_with_disabled_account_is_rejected_with_401() {
        LoginUser disabled = LoginUser.builder()
                .userId(2L)
                .username("resigned")
                .password(bcryptHash)
                .nickname("已离职员工")
                .enabled(false)
                .authorities(List.of())
                .build();
        when(userDetailsService.loadUserByUsername("resigned")).thenReturn(disabled);

        assertThatThrownBy(() -> authService.login("resigned", RAW_PASSWORD))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("401"));
    }

    @Test
    void disabled_account_is_rejected_before_password_is_checked() {
        LoginUser disabled = LoginUser.builder()
                .userId(2L).username("resigned").password(bcryptHash)
                .nickname("已离职员工").enabled(false).authorities(List.of())
                .build();
        when(userDetailsService.loadUserByUsername("resigned")).thenReturn(disabled);

        // 禁用账号即使密码正确也不得签发 token
        assertThatThrownBy(() -> authService.login("resigned", RAW_PASSWORD))
                .isInstanceOf(BusinessException.class);
    }

    // ---------- 登出 ----------

    @Test
    void logout_blacklists_the_jti_with_the_tokens_remaining_lifetime() {
        when(userDetailsService.loadUserByUsername("admin")).thenReturn(enabledUser());
        String token = authService.login("admin", RAW_PASSWORD).token();
        String expectedJti = jwtUtil.parseToken(token).getId();

        authService.logout(token);

        ArgumentCaptor<String> jtiCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(blacklistStore).blacklist(jtiCaptor.capture(), ttlCaptor.capture());

        assertThat(jtiCaptor.getValue()).isEqualTo(expectedJti);
        // 刚签发的 token 剩余寿命应接近 30 min
        assertThat(ttlCaptor.getValue()).isBetween(Duration.ofMinutes(29), Duration.ofMinutes(30));
    }

    @Test
    void logout_with_unparsable_token_is_a_no_op() {
        // 受保护端点理论上拦得住脏 token；这里保证真漏进来也不炸
        authService.logout("not-a-jwt");

        verify(blacklistStore, never()).blacklist(anyString(), any());
    }

    private static String catchMessage(Runnable action) {
        try {
            action.run();
            throw new AssertionError("expected BusinessException but nothing was thrown");
        } catch (BusinessException ex) {
            return ex.getMessage();
        }
    }
}
