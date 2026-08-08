package com.ticket.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * {@link JwtUtil} 单元测试（Seam 1 — 纯 JUnit，不启动 Spring 上下文）。
 * <p>
 * 覆盖 ticket 02 验收标准：
 * <ul>
 *     <li>generate → parse 往返（userId / username / authorities / jti 均可取回）</li>
 *     <li>过期 token 被拒绝</li>
 *     <li>签名被篡改的 token 被拒绝</li>
 *     <li>换密钥签发的 token 被拒绝（HMAC 密钥隔离）</li>
 * </ul>
 */
class JwtUtilTest {

    /** HS256 要求密钥 ≥ 256 bit，这里用 54 字节 */
    private static final String SECRET = "test-secret-key-for-jwt-hmac-sha256-at-least-32-bytes";
    private static final String OTHER_SECRET = "another-secret-key-totally-different-but-long-enough";

    private static JwtUtil jwtUtil(String secret, long expireMinutes) {
        JwtProperties props = new JwtProperties();
        props.setSecret(secret);
        props.setExpireMinutes(expireMinutes);
        return new JwtUtil(props);
    }

    private static JwtUtil jwtUtil() {
        return jwtUtil(SECRET, 30);
    }

    // ---------- 往返 ----------

    @Test
    void generate_then_parse_round_trips_all_claims() {
        JwtUtil util = jwtUtil();

        String token = util.generateToken(42L, "admin", List.of("user:manage", "ticket:view"));
        Claims claims = util.parseToken(token);

        assertThat(claims.getSubject()).isEqualTo("admin");
        assertThat(util.getUserId(claims)).isEqualTo(42L);
        assertThat(util.getAuthorities(claims)).containsExactly("user:manage", "ticket:view");
        // jti 是黑名单的键，必须存在且唯一
        assertThat(claims.getId()).isNotBlank();
    }

    @Test
    void generated_token_carries_expiry_from_configured_minutes() {
        JwtUtil util = jwtUtil(SECRET, 30);
        long before = System.currentTimeMillis();

        Claims claims = util.parseToken(util.generateToken(1L, "admin", List.of()));

        Date exp = claims.getExpiration();
        assertThat(exp).isNotNull();
        // 30 min 后过期，容忍 ±1 min 的执行漂移
        long expectedMillis = before + 30 * 60_000L;
        assertThat(exp.getTime()).isBetween(expectedMillis - 60_000L, expectedMillis + 60_000L);
    }

    @Test
    void each_generated_token_has_a_distinct_jti() {
        JwtUtil util = jwtUtil();

        String jti1 = util.parseToken(util.generateToken(1L, "admin", List.of())).getId();
        String jti2 = util.parseToken(util.generateToken(1L, "admin", List.of())).getId();

        assertThat(jti1).isNotEqualTo(jti2);
    }

    @Test
    void empty_authorities_round_trip_as_empty_list() {
        JwtUtil util = jwtUtil();

        // ticket 02 阶段权限数据源（sys_menu）尚未建立，登录签发的就是空权限
        Claims claims = util.parseToken(util.generateToken(7L, "employee", List.of()));

        assertThat(util.getAuthorities(claims)).isEmpty();
    }

    // ---------- 过期 ----------

    @Test
    void expired_token_is_rejected_by_parse() {
        // 负过期时间 → 签发即过期
        JwtUtil util = jwtUtil(SECRET, -1);
        String expired = util.generateToken(1L, "admin", List.of());

        assertThatThrownBy(() -> util.parseToken(expired))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void isExpired_is_true_for_expired_token_and_false_for_fresh_token() {
        assertThat(jwtUtil(SECRET, -1).isExpired(jwtUtil(SECRET, -1).generateToken(1L, "admin", List.of())))
                .isTrue();

        JwtUtil valid = jwtUtil();
        assertThat(valid.isExpired(valid.generateToken(1L, "admin", List.of()))).isFalse();
    }

    // ---------- 签名 ----------

    @Test
    void tampered_signature_is_rejected() {
        JwtUtil util = jwtUtil();
        String token = util.generateToken(1L, "admin", List.of());

        assertThatThrownBy(() -> util.parseToken(tamperSignature(token)))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void tampered_payload_is_rejected() {
        JwtUtil util = jwtUtil();
        String token = util.generateToken(1L, "admin", List.of());
        String[] parts = token.split("\\.");
        // 替换 payload 段 → 签名校验失败（防止提权改 userId/authorities）
        String forged = parts[0] + "." + parts[1].substring(0, parts[1].length() - 2) + "AA." + parts[2];

        assertThatThrownBy(() -> util.parseToken(forged))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void token_signed_with_another_secret_is_rejected() {
        String foreign = jwtUtil(OTHER_SECRET, 30).generateToken(1L, "attacker", List.of("user:manage"));

        assertThatThrownBy(() -> jwtUtil().parseToken(foreign))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void malformed_token_is_rejected() {
        JwtUtil util = jwtUtil();

        assertThatThrownBy(() -> util.parseToken("not-a-jwt")).isInstanceOf(JwtException.class);
    }

    @Test
    void isExpired_treats_unparsable_token_as_expired() {
        // 过滤器用 isExpired 做前置判断，不能因为脏 token 抛异常炸链路
        assertThat(jwtUtil().isExpired("not-a-jwt")).isTrue();
    }

    // ---------- 密钥强度 ----------

    @Test
    void too_short_secret_fails_fast_at_construction() {
        // HS256 要求 ≥ 256 bit，弱密钥必须启动即失败而不是运行期才暴露
        assertThatThrownBy(() -> jwtUtil("short-secret", 30))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jwt.secret");
    }

    @Test
    void exactly_32_byte_secret_is_accepted() {
        assertDoesNotThrow(() -> jwtUtil("12345678901234567890123456789012", 30));
    }

    /** 把签名段最后两个字符改掉，保持 JWT 结构合法但签名失效 */
    private static String tamperSignature(String token) {
        String[] parts = token.split("\\.");
        String sig = parts[2];
        String mutated = sig.substring(0, sig.length() - 2) + (sig.endsWith("AA") ? "BB" : "AA");
        return parts[0] + "." + parts[1] + "." + mutated;
    }
}
