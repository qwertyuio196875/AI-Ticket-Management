package com.ticket.security.blacklist;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link RedisTokenBlacklistStore} 单元测试（mock {@link StringRedisTemplate}）。
 * <p>
 * 集成测试用的是内存替身（本机无 Redis），生产实现的 key 前缀与 TTL 语义
 * 只能靠这一层守住 —— 写错了这里会红。
 */
class RedisTokenBlacklistStoreTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private RedisTokenBlacklistStore store;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        store = new RedisTokenBlacklistStore(redisTemplate);
    }

    @Test
    void blacklist_writes_prefixed_key_with_the_given_ttl() {
        store.blacklist("jti-123", Duration.ofMinutes(20));

        // key 格式是 ticket 02 验收标准明确规定的 jwt:blacklist:{jti}
        verify(valueOperations).set("jwt:blacklist:jti-123", "1", Duration.ofMinutes(20));
    }

    @Test
    void blacklist_skips_write_when_ttl_is_zero_or_negative() {
        store.blacklist("jti-expired", Duration.ZERO);
        store.blacklist("jti-expired", Duration.ofSeconds(-5));

        // token 本就过期，没必要占 Redis 空间
        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void blacklist_ignores_blank_jti() {
        store.blacklist(null, Duration.ofMinutes(20));
        store.blacklist("  ", Duration.ofMinutes(20));

        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void isBlacklisted_reflects_key_presence() {
        when(redisTemplate.hasKey("jwt:blacklist:present")).thenReturn(true);
        when(redisTemplate.hasKey("jwt:blacklist:absent")).thenReturn(false);

        assertThat(store.isBlacklisted("present")).isTrue();
        assertThat(store.isBlacklisted("absent")).isFalse();
    }

    @Test
    void isBlacklisted_treats_null_response_as_not_blacklisted() {
        // Redis 不可达时 hasKey 可能返回 null，不能因此 NPE
        when(redisTemplate.hasKey(anyString())).thenReturn(null);

        assertThat(store.isBlacklisted("whatever")).isFalse();
    }

    @Test
    void isBlacklisted_ignores_blank_jti() {
        assertThat(store.isBlacklisted(null)).isFalse();
        assertThat(store.isBlacklisted("  ")).isFalse();
    }
}
