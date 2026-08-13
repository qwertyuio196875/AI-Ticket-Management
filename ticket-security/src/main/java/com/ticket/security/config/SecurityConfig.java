package com.ticket.security.config;

import com.ticket.security.blacklist.TokenBlacklistStore;
import com.ticket.security.filter.JwtAuthFilter;
import com.ticket.security.jwt.JwtTokenResolver;
import com.ticket.security.jwt.JwtUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 6 配置（详见 ticket 02）。
 * <p>
 * 关键取舍：
 * <ul>
 *     <li><b>无状态</b>：{@link SessionCreationPolicy#STATELESS} —— 认证状态只在 JWT 里，
 *         服务端不建 session，天然支持水平扩容</li>
 *     <li><b>关 CSRF</b>：CSRF 攻击依赖浏览器自动携带 cookie；本项目 token 由前端显式放进
 *         请求头，不存在自动携带，故无需 CSRF token</li>
 *     <li><b>关表单登录 / HTTP Basic</b>：登录统一走 {@code POST /api/v1/auth/login} 返回 JWT</li>
 *     <li>{@link EnableMethodSecurity}：为 {@code @PreAuthorize("hasAuthority('xxx')")} 开路
 *         （权限数据源 sys_menu.permission 在 ticket 03 接入，见 ADR-0002）</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /** 公开端点：登录 */
    private static final String LOGIN_ENDPOINT = "/api/v1/auth/login";

    /**
     * 公开端点：ticket 01 tracer bullet 的连通性探针。
     * <p>
     * 它们先于认证体系存在，且不涉及任何业务数据 —— 放行以保持 ticket 01 的验收不被本次改动破坏。
     */
    private static final String[] TRACER_BULLET_ENDPOINTS = {"/api/v1/ping", "/api/v1/echo"};

    /**
     * 公开端点：Knife4j / Springdoc API 文档与 UI（ticket 11 / ADR-0032）。
     * <p>
     * 包含：
     * <ul>
     *     <li>{@code /doc.html} —— Knife4j 增强 UI</li>
     *     <li>{@code /swagger-ui.html} 与 {@code /swagger-ui/**} —— Springdoc 默认 UI（含 webjars 静态资源）</li>
     *     <li>{@code /v3/api-docs/**} —— OpenAPI JSON（{@code /v3/api-docs} 自身 + 按 group 索引的子 doc）</li>
     *     <li>{@code /webjars/**} —— Swagger UI 的静态资源（springdoc 依赖）</li>
     * </ul>
     * 生产环境通过 {@code knife4j.production=true} 关闭 UI（{@code /doc.html} + {@code /swagger-ui.html}），
     * 不影响 {@code /v3/api-docs}（脚本仍可拉取 OpenAPI）。
     */
    private static final String[] DOC_ENDPOINTS = {
            "/doc.html",
            "/doc.html/**",
            "/swagger-ui.html",
            "/swagger-ui/**",
            // 注意: AntPathMatcher 的 /** 不匹配根路径本身, 必须显式列出
            "/v3/api-docs",
            "/v3/api-docs/**",
            "/v3/api-docs.yaml",
            "/webjars/**",
            "/favicon.ico"
    };

    /** BCrypt —— 自带随机盐，同一密码每次哈希结果不同 */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtUtil jwtUtil,
                                                   JwtTokenResolver tokenResolver,
                                                   TokenBlacklistStore blacklistStore,
                                                   RestAuthErrorHandler authErrorHandler) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                // 默认 /logout 端点不要，登出走 POST /api/v1/auth/logout（要把 token 拉黑）
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(registry -> registry
                        .requestMatchers(LOGIN_ENDPOINT).permitAll()
                        .requestMatchers(TRACER_BULLET_ENDPOINTS).permitAll()
                        // ticket 11 —— Knife4j / Springdoc 文档端点放行
                        .requestMatchers(DOC_ENDPOINTS).permitAll()
                        // 其余一律需要认证；细粒度操作权限由 @PreAuthorize 在 ticket 03 补
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authErrorHandler)
                        .accessDeniedHandler(authErrorHandler))
                // JWT 过滤器要早于用户名密码过滤器，先把 SecurityContext 填好。
                // 这里用 new 而非注入 bean：Filter 类型的 bean 会被 Spring Boot 自动注册到
                // Servlet 容器过滤器链，导致它在 Security 链之外重复执行一次。
                .addFilterBefore(new JwtAuthFilter(jwtUtil, tokenResolver, blacklistStore),
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
