package com.ticket.ticket.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ticket.ticket.mapper.TicketStatsMapper;
import com.ticket.ticket.service.TicketStatsService;
import com.ticket.ticket.vo.StatsSummaryVO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link TicketStatsServiceImpl} 集成测试（ticket 10 AC）。
 * <p>
 * <b>覆盖</b>（spec AC "Integration test using Testcontainers Redis: stats endpoint hit twice
 * returns identical response (second from cache)"）：
 * <ul>
 *     <li><b>缓存命中</b>：第二次调用返回相同响应，DB mapper 只被调用 1 次</li>
 *     <li><b>TTL 设置</b>：缓存项确实有 TTL，且落在 5min 内（容差 ±30s）</li>
 *     <li><b>key 格式</b>：{@code stats:tickets:{endpoint}:{paramsHash}}，key 落在 Redis 命名空间下</li>
 *     <li><b>endpoints 隔离</b>：不同 endpoint 互不影响（summary / trend 各自缓存）</li>
 * </ul>
 *
 * <p><b>启用条件</b>：默认 <b>不启用</b>（{@code @EnabledIfSystemProperty} +
 * {@code -Dtest.integration=true}），与项目"本机无 Docker"默认假设一致。
 *
 * <p><b>不启动 Spring Boot 上下文</b>：直接构造 {@link TicketStatsServiceImpl} 注入
 * 真实 StringRedisTemplate + Mockito TicketStatsMapper。命中真实 Testcontainers Redis。
 */
@Tag("integration")
@Testcontainers
@EnabledIfSystemProperty(named = "test.integration", matches = "true")
class StatsCacheIntegrationTest {

    private static final DockerImageName REDIS_IMAGE = DockerImageName.parse("redis:7-alpine");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(REDIS_IMAGE)
            .withExposedPorts(6379)
            .withReuse(true);

    private static LettuceConnectionFactory lettuceFactory;
    private static StringRedisTemplate redisTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private TicketStatsMapper ticketStatsMapper;
    private TicketStatsService service;

    @BeforeAll
    static void startRedis() {
        String host = redis.getHost();
        Integer port = redis.getMappedPort(6379);

        RedisStandaloneConfiguration standalone = new RedisStandaloneConfiguration(host, port);
        lettuceFactory = new LettuceConnectionFactory(standalone);
        lettuceFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(lettuceFactory);
        redisTemplate.afterPropertiesSet();
    }

    @AfterAll
    static void stopRedis() {
        if (lettuceFactory != null) {
            lettuceFactory.destroy();
        }
    }

    @BeforeEach
    void setUp() {
        // 清空测试 namespace —— 避免上一次 run 残留
        if (redisTemplate != null) {
            redisTemplate.delete(redisTemplate.keys("stats:tickets:*"));
        }
        ticketStatsMapper = mock(TicketStatsMapper.class);
        service = new TicketStatsServiceImpl(ticketStatsMapper, redisTemplate, objectMapper);
    }

    // ==================== 缓存命中 ====================

    @Test
    @DisplayName("summary 第二次调用：返回相同响应，mapper 只被调用 1 次")
    void summary_secondCall_returnsCached_withoutReQuery() {
        when(ticketStatsMapper.countByStatus()).thenReturn(List.of(
                row("status", "PENDING", "cnt", 5L),
                row("status", "RESOLVED", "cnt", 10L)));

        StatsSummaryVO first = service.summary();
        StatsSummaryVO second = service.summary();

        assertThat(second).isEqualTo(first);
        // mapper 整个测试只被调用 1 次（第二次命中缓存）
        verify(ticketStatsMapper, times(1)).countByStatus();
    }

    // ==================== TTL 设置 ====================

    @Test
    @DisplayName("缓存项确实有 TTL（5min ±30s）")
    void cachedItem_hasTtlWithinFiveMinutes() {
        when(ticketStatsMapper.countByStatus()).thenReturn(List.of(
                row("status", "PROCESSING", "cnt", 3L)));

        service.summary();

        // 拿到真实的 Redis key —— 端点 = summary，无 params → 无 hash
        Long ttlSeconds = redisTemplate.getExpire("stats:tickets:summary");
        assertThat(ttlSeconds).isNotNull().isPositive();
        // 5 min = 300s；容差 ±30s
        assertThat(ttlSeconds).isBetween(270L, 300L);
    }

    // ==================== key 格式 ====================

    @Test
    @DisplayName("trend 缓存 key：stats:tickets:trend:{paramsHash}（带 hash）")
    void trendCacheKey_includesParamsHash() {
        when(ticketStatsMapper.countDailyTrend(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenReturn(List.of());

        service.trend(7);

        // 找出 Redis 里所有 stats:tickets:* key
        java.util.Set<String> keys = redisTemplate.keys("stats:tickets:*");
        assertThat(keys).isNotEmpty();
        // 必须有一个以 "stats:tickets:trend:" 开头（带 hash 后缀）
        assertThat(keys).anyMatch(k -> k.startsWith("stats:tickets:trend:"));
    }

    // ==================== endpoints 隔离 ====================

    @Test
    @DisplayName("summary / byPriority 缓存 key 各自独立，互不污染")
    void differentEndpoints_haveIndependentCacheKeys() {
        when(ticketStatsMapper.countByStatus()).thenReturn(List.of());
        when(ticketStatsMapper.countByPriority()).thenReturn(List.of());

        service.summary();
        service.byPriority();

        java.util.Set<String> keys = redisTemplate.keys("stats:tickets:*");
        assertThat(keys).contains("stats:tickets:summary", "stats:tickets:by-priority");
    }

    // ---------- 辅助 ----------

    private static Map<String, Object> row(Object... kvPairs) {
        Map<String, Object> m = new java.util.HashMap<>();
        for (int i = 0; i < kvPairs.length; i += 2) {
            m.put((String) kvPairs[i], kvPairs[i + 1]);
        }
        return m;
    }
}