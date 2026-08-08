package com.ticket.security.service;

import com.ticket.common.exception.BusinessException;
import com.ticket.common.exception.BusinessExceptionCode;
import com.ticket.security.blacklist.TokenBlacklistStore;
import com.ticket.security.jwt.JwtProperties;
import com.ticket.security.jwt.JwtUtil;
import com.ticket.security.user.LoginUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Date;
import java.util.List;

/**
 * 认证业务 —— 登录签发 token、登出拉黑 token。
 * <p>
 * <b>为什么手写校验而不用 {@code AuthenticationManager}</b>：
 * 这里显式走"查用户 → 判禁用 → 比密码 → 签 token"四步，链路一眼可见，
 * 且能被纯 Mockito 单测覆盖（spec Seam 1）；
 * {@code DaoAuthenticationProvider} 的隐式流程对学习项目反而是黑盒。
 * <p>
 * 用户数据通过 Spring Security 原生的 {@link UserDetailsService} SPI 获取，
 * 实现在 {@code ticket-system}（{@code sys_user}）—— 因此本模块不依赖任何业务模块。
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    /** 认证失败对外的统一话术 —— 不区分"用户不存在"与"密码错误"，防用户名枚举 */
    private static final String BAD_CREDENTIALS_MESSAGE = "用户名或密码错误";
    private static final String DISABLED_MESSAGE = "账号已禁用，请联系管理员";
    private static final String TOKEN_TYPE = "Bearer";
    private static final int SECONDS_PER_MINUTE = 60;

    private final UserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final TokenBlacklistStore blacklistStore;
    private final JwtProperties jwtProperties;

    public AuthService(UserDetailsService userDetailsService,
                       PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil,
                       TokenBlacklistStore blacklistStore,
                       JwtProperties jwtProperties) {
        this.userDetailsService = userDetailsService;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.blacklistStore = blacklistStore;
        this.jwtProperties = jwtProperties;
    }

    /**
     * 登录：校验凭证并签发 JWT。
     *
     * @throws BusinessException 凭证错误或账号禁用，业务码 {@code "401"}
     */
    public LoginResult login(String username, String rawPassword) {
        LoginUser user = loadUser(username);

        // 先判禁用：离职 / 停用账号即使密码正确也不得签发 token
        if (!user.isEnabled()) {
            log.warn("登录失败 —— 账号已禁用: username={}", username);
            throw BusinessException.of(BusinessExceptionCode.AUTH_UNAUTHORIZED, DISABLED_MESSAGE);
        }
        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            log.warn("登录失败 —— 密码不匹配: username={}", username);
            throw BusinessException.of(BusinessExceptionCode.AUTH_UNAUTHORIZED, BAD_CREDENTIALS_MESSAGE);
        }

        // ticket 02 阶段权限集恒为空，数据源（sys_menu.permission）在 ticket 03 接入
        List<String> authorities = user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        String token = jwtUtil.generateToken(user.getUserId(), user.getUsername(), authorities);

        log.info("登录成功: userId={}, username={}", user.getUserId(), user.getUsername());
        return new LoginResult(
                token,
                TOKEN_TYPE,
                jwtProperties.getExpireMinutes() * SECONDS_PER_MINUTE,
                user.getUserId(),
                user.getUsername(),
                user.getNickname());
    }

    /**
     * 登出：把当前 token 的 {@code jti} 写入黑名单，TTL 取剩余寿命。
     * <p>
     * 无法解析的 token 视为已失效，静默跳过 —— 登出天然幂等。
     */
    public void logout(String token) {
        Claims claims;
        try {
            claims = jwtUtil.parseToken(token);
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("登出收到不可解析的 token，按已失效处理");
            return;
        }

        Duration remaining = remainingLifetime(claims);
        blacklistStore.blacklist(jwtUtil.getJti(claims), remaining);
        log.info("登出成功: userId={}, 黑名单 TTL={}s",
                jwtUtil.getUserId(claims), remaining.toSeconds());
    }

    /** 查用户；不存在时抛与"密码错误"完全一致的异常，防用户名枚举 */
    private LoginUser loadUser(String username) {
        UserDetails details;
        try {
            details = userDetailsService.loadUserByUsername(username);
        } catch (UsernameNotFoundException ex) {
            log.warn("登录失败 —— 用户不存在: username={}", username);
            throw BusinessException.of(BusinessExceptionCode.AUTH_UNAUTHORIZED, BAD_CREDENTIALS_MESSAGE);
        }
        if (!(details instanceof LoginUser loginUser)) {
            // UserDetailsService 的实现必须返回 LoginUser，否则拿不到 userId
            throw new IllegalStateException(
                    "UserDetailsService 必须返回 LoginUser，实际为 "
                            + (details == null ? "null" : details.getClass().getName()));
        }
        return loginUser;
    }

    /** token 剩余寿命；已过期则返回 {@link Duration#ZERO} */
    private Duration remainingLifetime(Claims claims) {
        Date expiration = jwtUtil.getExpiration(claims);
        if (expiration == null) {
            return Duration.ZERO;
        }
        long millis = expiration.getTime() - System.currentTimeMillis();
        return millis > 0 ? Duration.ofMillis(millis) : Duration.ZERO;
    }
}
