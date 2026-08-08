package com.ticket.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * JWT 签发与校验工具（HMAC-SHA256 单 token 方案，详见 ticket 02）。
 * <p>
 * 载荷约定：
 * <ul>
 *     <li>{@code sub}：username</li>
 *     <li>{@code userId}：{@code sys_user.id}，业务层据此定位当前用户</li>
 *     <li>{@code authorities}：操作权限字符串列表。
 *         <b>ticket 02 阶段恒为空</b> — 权限数据源 {@code sys_menu.permission}
 *         在 ticket 03 建表后接入，此处只保证链路打通</li>
 *     <li>{@code jti}：token 唯一 ID，登出时作为 Redis 黑名单的键</li>
 * </ul>
 * <p>
 * 密钥来自 {@link JwtProperties#getSecret()}（{@code jwt.secret}，env 可覆盖）。
 * 弱密钥在构造期即失败，避免运行期才暴露。
 */
@Component
public class JwtUtil {

    /** userId 自定义 claim 名 */
    private static final String CLAIM_USER_ID = "userId";
    /** 操作权限列表自定义 claim 名 */
    private static final String CLAIM_AUTHORITIES = "authorities";
    /** HMAC-SHA256 密钥下限：256 bit = 32 字节 */
    private static final int MIN_SECRET_BYTES = 32;
    private static final long MILLIS_PER_MINUTE = 60_000L;

    private final SecretKey secretKey;
    private final long expireMinutes;

    public JwtUtil(JwtProperties properties) {
        byte[] secretBytes = properties.getSecret() == null
                ? new byte[0]
                : properties.getSecret().getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "jwt.secret 长度不足：HMAC-SHA256 要求至少 " + MIN_SECRET_BYTES
                            + " 字节，当前 " + secretBytes.length + " 字节");
        }
        this.secretKey = Keys.hmacShaKeyFor(secretBytes);
        this.expireMinutes = properties.getExpireMinutes();
    }

    /**
     * 签发 token。
     *
     * @param userId      {@code sys_user.id}
     * @param username    登录名，写入 {@code sub}
     * @param authorities 操作权限字符串集合，可为 null / 空
     * @return 紧凑格式 JWT 字符串
     */
    public String generateToken(Long userId, String username, Collection<String> authorities) {
        Date issuedAt = new Date();
        Date expiration = new Date(issuedAt.getTime() + expireMinutes * MILLIS_PER_MINUTE);
        return Jwts.builder()
                // jti 每次签发都不同 —— 这样登出拉黑只影响当前这一个 token
                .id(UUID.randomUUID().toString())
                .subject(username)
                .claim(CLAIM_USER_ID, userId)
                .claim(CLAIM_AUTHORITIES, authorities == null ? List.of() : List.copyOf(authorities))
                .issuedAt(issuedAt)
                .expiration(expiration)
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * 解析并验签。
     *
     * @throws ExpiredJwtException token 已过期
     * @throws JwtException        签名不匹配 / 结构非法 / 载荷被篡改
     */
    public Claims parseToken(String jwt) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(jwt)
                .getPayload();
    }

    /**
     * 是否已过期。
     * <p>
     * 无法解析的 token（结构非法 / 签名不符 / 空串）一并视为不可用返回 {@code true} —
     * 过滤器用它做前置判断，不能因为脏 token 抛异常中断请求链路。
     */
    public boolean isExpired(String jwt) {
        try {
            return parseToken(jwt).getExpiration().before(new Date());
        } catch (ExpiredJwtException ex) {
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            return true;
        }
    }

    /** 从载荷取 userId（JSON 数字反序列化为 Integer / Long，统一转 long） */
    public Long getUserId(Claims claims) {
        Object raw = claims.get(CLAIM_USER_ID);
        return raw instanceof Number number ? number.longValue() : null;
    }

    /** 从载荷取操作权限列表；缺失时返回空列表而非 null */
    public List<String> getAuthorities(Claims claims) {
        Object raw = claims.get(CLAIM_AUTHORITIES);
        if (!(raw instanceof Collection<?> collection)) {
            return List.of();
        }
        return collection.stream().map(String::valueOf).toList();
    }

    /** 从载荷取 username（存在 {@code sub} 标准字段） */
    public String getUsername(Claims claims) {
        return claims.getSubject();
    }

    /** token 唯一 ID —— Redis 黑名单的键 */
    public String getJti(Claims claims) {
        return claims.getId();
    }

    /** 过期时刻 —— 用于计算黑名单条目的剩余 TTL */
    public Date getExpiration(Claims claims) {
        return claims.getExpiration();
    }
}
