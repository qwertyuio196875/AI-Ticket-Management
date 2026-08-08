package com.ticket.security.jwt;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * 从 HTTP 请求中提取 JWT。
 * <p>
 * 抽成独立组件，是因为有两处需要读同一个 token：
 * <ul>
 *     <li>{@code JwtAuthFilter}：每次请求的认证</li>
 *     <li>{@code AuthController#logout}：把当前 token 拉黑</li>
 * </ul>
 * 请求头名与前缀都取自 {@link JwtProperties}，改配置时只有这一处解析逻辑。
 */
@Component
public class JwtTokenResolver {

    private final JwtProperties jwtProperties;

    public JwtTokenResolver(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    /**
     * 取出 token。
     *
     * @return token 字符串；请求头缺失、前缀不符或值为空时返回 {@code null}
     */
    public String resolve(HttpServletRequest request) {
        String header = request.getHeader(jwtProperties.getHeader());
        String prefix = jwtProperties.getTokenPrefix();
        if (header == null || !header.startsWith(prefix)) {
            return null;
        }
        String token = header.substring(prefix.length()).trim();
        return token.isEmpty() ? null : token;
    }
}
