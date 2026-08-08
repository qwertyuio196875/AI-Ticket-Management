package com.ticket.security.jwt;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 配置项，绑定 {@code application.yml} 中的 {@code jwt.*}。
 * <p>
 * 单 token 方案（详见 spec / ticket 02）：不做 refresh token 轮换。
 * <ul>
 *     <li>{@code jwt.secret}：HMAC-SHA256 签名密钥，生产环境用环境变量
 *         {@code JWT_SECRET} 覆盖，长度必须 ≥ 32 字节</li>
 *     <li>{@code jwt.expire-minutes}：过期时间，默认 30 分钟（用户偏好）</li>
 *     <li>{@code jwt.header} / {@code jwt.token-prefix}：token 的携带位置与前缀</li>
 * </ul>
 */
@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /** HMAC-SHA256 签名密钥（≥ 32 字节），env 可覆盖 */
    private String secret;

    /** token 有效期（分钟），默认 30 */
    private long expireMinutes = 30;

    /** 携带 token 的请求头名 */
    private String header = "Authorization";

    /** token 前缀（含尾部空格） */
    private String tokenPrefix = "Bearer ";
}
