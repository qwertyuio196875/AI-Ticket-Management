package com.ticket.security.blacklist;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * {@link TokenBlacklistStore} 的 Redis 实现（生产路径）。
 * <p>
 * key 格式 {@code jwt:blacklist:{jti}}，value 占位不用，TTL = token 剩余寿命
 * —— 到期由 Redis 自动回收（详见 ticket 02 验收标准）。
 */
@Component
public class RedisTokenBlacklistStore implements TokenBlacklistStore {

    /** 黑名单 key 前缀 */
    private static final String KEY_PREFIX = "jwt:blacklist:";
    /** value 只占位，真正的信息是"键是否存在" */
    private static final String PLACEHOLDER = "1";

    private static final Logger log = LoggerFactory.getLogger(RedisTokenBlacklistStore.class);

    private final StringRedisTemplate redisTemplate;

    public RedisTokenBlacklistStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void blacklist(String jti, Duration ttl) {
        if (jti == null || jti.isBlank()) {
            return;
        }
        // 剩余寿命 ≤ 0 说明 token 本就过期，无需占用 Redis
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            log.debug("token jti={} 已过期，跳过拉黑", jti);
            return;
        }
        redisTemplate.opsForValue().set(KEY_PREFIX + jti, PLACEHOLDER, ttl);
        log.debug("token jti={} 已拉黑，TTL={}s", jti, ttl.toSeconds());
    }

    @Override
    public boolean isBlacklisted(String jti) {
        if (jti == null || jti.isBlank()) {
            return false;
        }
        return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + jti));
    }
}
