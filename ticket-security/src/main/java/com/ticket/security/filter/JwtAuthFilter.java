package com.ticket.security.filter;

import com.ticket.security.blacklist.TokenBlacklistStore;
import com.ticket.security.jwt.JwtTokenResolver;
import com.ticket.security.jwt.JwtUtil;
import com.ticket.security.user.LoginUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT 认证过滤器 —— 每请求一次，把合法 token 还原成 {@code SecurityContext} 中的认证信息。
 * <p>
 * 处理流程：
 * <ol>
 *     <li>登录端点直接跳过（{@link #shouldNotFilter}）—— 它不带 token，
 *         也就无需解析和查黑名单</li>
 *     <li>从 {@code Authorization: Bearer xxx} 取 token；没有则放行到后续授权决策
 *         （受保护端点会被 {@code AuthenticationEntryPoint} 拦成 401）</li>
 *     <li>验签 + 判过期；命中 Redis 黑名单（已登出）视为无效</li>
 *     <li>合法则写入 {@link UsernamePasswordAuthenticationToken}，principal 是 {@link LoginUser}</li>
 * </ol>
 * <p>
 * <b>本过滤器从不直接写 401 响应</b> —— 认证失败只表现为"不设置 Authentication"，
 * 由 {@code SecurityConfig} 里配的 EntryPoint 统一出错误体，保证响应格式只有一个出口。
 * <p>
 * <b>刻意不加 {@code @Component}</b>：Spring Boot 会把容器里任何 {@code Filter} bean
 * 自动注册到 Servlet 容器的过滤器链上，那样本过滤器会在 Security 的 {@code FilterChainProxy}
 * 之外再跑一遍。它只由 {@code SecurityConfig} 用 {@code new} 装进 Security 链。
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    /** 登录端点：公开且不携带 token */
    private static final String LOGIN_PATH = "/api/v1/auth/login";

    private final JwtUtil jwtUtil;
    private final JwtTokenResolver tokenResolver;
    private final TokenBlacklistStore blacklistStore;

    public JwtAuthFilter(JwtUtil jwtUtil, JwtTokenResolver tokenResolver, TokenBlacklistStore blacklistStore) {
        this.jwtUtil = jwtUtil;
        this.tokenResolver = tokenResolver;
        this.blacklistStore = blacklistStore;
    }

    /** 登录端点跳过整个 JWT 链路（含黑名单查询），省一次 Redis 往返 */
    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return LOGIN_PATH.equals(request.getServletPath());
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String token = tokenResolver.resolve(request);
        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            Claims claims = jwtUtil.parseToken(token);
            String jti = jwtUtil.getJti(claims);
            if (blacklistStore.isBlacklisted(jti)) {
                // 已登出的 token：签名仍然有效，但业务上必须失效
                log.debug("token 已在黑名单中: jti={}", jti);
                SecurityContextHolder.clearContext();
                filterChain.doFilter(request, response);
                return;
            }

            LoginUser principal = LoginUser.fromToken(
                    jwtUtil.getUserId(claims),
                    jwtUtil.getUsername(claims),
                    jwtUtil.getAuthorities(claims));
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    principal, null, principal.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (JwtException | IllegalArgumentException ex) {
            // 过期 / 验签失败 / 结构非法：不认证，交给 EntryPoint 出 401
            log.debug("token 校验失败: {}", ex.getMessage());
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}
