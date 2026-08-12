package com.ticket.web;

import org.mockito.Mockito;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

/**
 * ticket 09 —— 测试环境 Redisson / StringRedisTemplate 替身配置。
 * <p>
 * 本机无 Redis（spec Seam 3 假设），原有 {@code ticket-web} 集成测试在
 * ticket 9 引入 {@code redisson-spring-boot-starter} 后，因 ApplicationContext
 * 启动时尝试连接 Redis 失败而崩溃。
 * <p>
 * 用 Mockito mock 一个 <b>纯 NoOp</b> 的 RedissonClient + StringRedisTemplate
 * 替身（@Primary 覆盖自动装配的 RedissonClient），让 Spring 装配通过：
 * <ul>
 *     <li>{@link RedissonClient} mock —— 任何方法返回 null/false，
 *         不会触发真实 Redis 连接</li>
 *     <li>{@link StringRedisTemplate} mock —— Redis 操作返回 null</li>
 *     <li>{@link RedisConnectionFactory} mock —— 触发 {@link Mockito#RETURNS_DEEP_STUBS}</li>
 * </ul>
 * <p>
 * <b>注意</b>：本配置<b>不会</b>在 {@code TicketCacheIntegrationTest} 中启用
 * （后者用 {@code @EnabledIfSystemProperty} + Testcontainers 真实 Redis）。
 * 这里 Spring {@code @TestConfiguration} 只对未禁用 Redisson 的测试类生效。
 *
 * @see com.ticket.web.ticket.cache.TicketCacheIntegrationTest
 */
@TestConfiguration
public class TestRedissonConfig {

    @Bean
    @Primary
    public RedissonClient redissonClient() {
        // RETURNS_DEEP_STUBS 让 getLock() 链式返回 mock RLock，
        // 避免 TicketCacheServiceImpl 调 lock.tryLock() 时 NPE
        RedissonClient client = Mockito.mock(RedissonClient.class,
                Mockito.RETURNS_DEEP_STUBS);
        // 默认 stub：getLock 返回 mock lock，tryLock 立即成功，isHeldByCurrentThread false
        RLock lock = Mockito.mock(RLock.class);
        try {
            Mockito.when(client.getLock(Mockito.anyString())).thenReturn(lock);
            Mockito.when(lock.tryLock(Mockito.anyLong(), Mockito.anyLong(), Mockito.any(TimeUnit.class)))
                    .thenReturn(true);
            Mockito.when(lock.isHeldByCurrentThread()).thenReturn(false);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return client;
    }

    @Bean
    @Primary
    public StringRedisTemplate stringRedisTemplate() {
        // RETURNS_DEEP_STUBS 让 opsForValue() 链式返回 mock ValueOperations，
        // 避免 TicketCacheServiceImpl 调 .get() 时 NPE（RETURNS_DEFAULTS 会让
        // opsForValue() 返回 null，进而 NPE）。
        StringRedisTemplate template = Mockito.mock(StringRedisTemplate.class,
                Mockito.RETURNS_DEEP_STUBS);
        return template;
    }

    /**
     * 屏蔽 StringRedisTemplate 构造时调用的 {@code RedisConnectionFactory}。
     * <p>
     * 实际上 {@code StringRedisTemplate} 的无参构造仅 new 一个空对象、不触发连接；
     * 这里保留作为 Spring 显式依赖以备任何自定义配置使用。
     */
    @Bean
    @Primary
    public RedisConnectionFactory redisConnectionFactory() {
        RedisConnectionFactory factory = Mockito.mock(RedisConnectionFactory.class);
        RedisConnection connection = Mockito.mock(RedisConnection.class);
        Mockito.when(factory.getConnection()).thenReturn(connection);
        return factory;
    }
}