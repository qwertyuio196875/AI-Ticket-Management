package com.ticket.web.ticket.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticket.common.exception.BusinessException;
import com.ticket.common.exception.BusinessExceptionCode;
import com.ticket.ticket.entity.TicketInfo;
import com.ticket.ticket.enums.TicketStatus;
import com.ticket.ticket.mapper.TicketInfoMapper;
import com.ticket.ticket.service.cache.TicketCacheService;
import com.ticket.ticket.service.cache.TicketCacheServiceImpl;
import com.ticket.ticket.vo.TicketVO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link TicketCacheServiceImpl} 集成测试（ticket 09 AC）。
 * <p>
 * <b>覆盖</b>（spec AC "Integration test using Testcontainers Redis: real cache
 * hit path (second call returns cached, DB not queried); real eviction path
 * (write deletes cache)"）：
 * <ul>
 *     <li><b>真实缓存命中</b>：第二次调用返回缓存，DB mapper 不被调用</li>
 *     <li><b>真实失效路径</b>：evict 后 Redis key 被删除</li>
 *     <li><b>TTL 抖动</b>：缓存项确实有 TTL，且落在合理区间（&gt;0 且 &lt;{@code BASE_TTL + JITTER}）</li>
 *     <li><b>空值标记防穿透</b>：DB miss 后 Redis 写入 {@code EMPTY_MARKER}，
 *         后续直接命中并抛 {@code T0101}</li>
 * </ul>
 * <p>
 * <b>启用条件</b>：默认 <b>不启用</b>（用 {@code @EnabledIfSystemProperty} + {@code -Dtest.integration=true}），
 * 与项目"本机无 Docker"默认假设一致；CI 或开发者本机有 Docker Desktop 时启用。
 * <p>
 * <b>不启动 Spring Boot 上下文</b>：直接构造 {@link TicketCacheServiceImpl} 注入
 * 真实的 RedissonClient / StringRedisTemplate / TicketInfoMapper（mock），命中真实的
 * Testcontainers Redis。这避免了整个 Spring 上下文装配（简化测试、最小化依赖）。
 */
@Tag("integration")
@Testcontainers
@EnabledIfSystemProperty(named = "test.integration", matches = "true")
class TicketCacheIntegrationTest {

    /** Redis 镜像 —— 用 7-alpine 体积小、启动快 */
    private static final DockerImageName REDIS_IMAGE = DockerImageName.parse("redis:7-alpine");

    /** key 隔离：每个测试用随机 ticketId 避免跨测试污染 */
    private static final String RUN_ID = UUID.randomUUID().toString().substring(0, 8);

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(REDIS_IMAGE)
            .withExposedPorts(6379)
            .withReuse(true);  // 允许跨测试类复用容器（CI 加速）

    private static RedissonClient redissonClient;
    private static LettuceConnectionFactory lettuceFactory;
    private static StringRedisTemplate redisTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    /** 在 mapper 上记录调用次数 —— 验证 cache hit 时 mapper 不被调用 */
    private final java.util.concurrent.atomic.AtomicInteger mapperCallCount =
            new java.util.concurrent.atomic.AtomicInteger(0);

    private TicketInfoMapper ticketInfoMapper;
    private TicketCacheServiceImpl cacheService;

    private Long ticketId;

    @BeforeAll
    static void startRedis() {
        String host = redis.getHost();
        Integer port = redis.getMappedPort(6379);

        // Redisson 客户端
        Config redissonConfig = new Config();
        redissonConfig.useSingleServer()
                .setAddress("redis://" + host + ":" + port)
                .setConnectionMinimumIdleSize(1)
                .setConnectionPoolSize(2);
        redissonClient = Redisson.create(redissonConfig);

        // Spring Data Redis Template（用 Lettuce，与 ticket-web 生产环境一致）
        RedisStandaloneConfiguration standalone = new RedisStandaloneConfiguration(host, port);
        lettuceFactory = new LettuceConnectionFactory(standalone);
        lettuceFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(lettuceFactory);
        redisTemplate.afterPropertiesSet();
    }

    @AfterAll
    static void stopRedis() {
        if (redissonClient != null) {
            redissonClient.shutdown();
        }
        if (lettuceFactory != null) {
            lettuceFactory.destroy();
        }
    }

    @BeforeEach
    void setUp() {
        // 每个测试用独立 key 避免相互影响
        ticketId = (long) Math.abs(RUN_ID.hashCode() % 1_000_000) + System.nanoTime() % 1000;
        mapperCallCount.set(0);

        // mock mapper —— 真实 TicketInfoMapper 接 H2 会拖入整个持久层上下文，
        // 这里用动态代理 + 计数器实现"半真实"的 DB 替身
        ticketInfoMapper = mockMapper();
        cacheService = new TicketCacheServiceImpl(redisTemplate, redissonClient, ticketInfoMapper, objectMapper);
    }

    // ==================== 真实缓存命中 ====================

    @Test
    @DisplayName("cache hit：第二次调用直接返回缓存，DB mapper 不被调用")
    void cacheHit_secondCall_returnsCached_withoutMapperCall() {
        // 第一次：DB 命中 → 写缓存
        TicketInfo entity = buildEntity(ticketId);
        ticketInfoMapper = mockMapperReturning(entity);
        mapperCallCount.set(0);
        cacheService = new TicketCacheServiceImpl(redisTemplate, redissonClient, ticketInfoMapper, objectMapper);

        TicketVO first = cacheService.getById(ticketId);
        assertThat(first.getId()).isEqualTo(ticketId);
        assertThat(mapperCallCount.get()).isEqualTo(1);

        // 第二次：应该命中缓存，mapper 不再被调用
        TicketVO second = cacheService.getById(ticketId);
        assertThat(second.getId()).isEqualTo(ticketId);
        assertThat(mapperCallCount.get()).isEqualTo(1);  // <-- 没增加

        // Redis key 确实存在（TTL 内）
        String cachedRaw = redisTemplate.opsForValue().get("ticket:detail:" + ticketId);
        assertThat(cachedRaw).isNotNull();
        assertThat(cachedRaw).startsWith("{").contains("\"id\":" + ticketId);
    }

    // ==================== 真实失效 ====================

    @Test
    @DisplayName("evict：调用后 Redis key 被删除")
    void evict_removesCacheKey() {
        // 1. 先写缓存
        TicketInfo entity = buildEntity(ticketId);
        ticketInfoMapper = mockMapperReturning(entity);
        cacheService = new TicketCacheServiceImpl(redisTemplate, redissonClient, ticketInfoMapper, objectMapper);
        cacheService.getById(ticketId);
        assertThat(redisTemplate.opsForValue().get("ticket:detail:" + ticketId)).isNotNull();

        // 2. evict
        cacheService.evict(ticketId);

        // 3. Redis key 已被删除
        assertThat(redisTemplate.opsForValue().get("ticket:detail:" + ticketId)).isNull();

        // 4. 再 getById 会重新查 DB（mapper 调用 +1）
        mapperCallCount.set(0);
        cacheService.getById(ticketId);
        assertThat(mapperCallCount.get()).isEqualTo(1);
    }

    // ==================== TTL 抖动 ====================

    @Test
    @DisplayName("缓存项确实有 TTL（落在 25 ~ 35 min 区间）")
    void cachedItem_hasTtlWithinJitterRange() {
        TicketInfo entity = buildEntity(ticketId);
        ticketInfoMapper = mockMapperReturning(entity);
        cacheService = new TicketCacheServiceImpl(redisTemplate, redissonClient, ticketInfoMapper, objectMapper);

        cacheService.getById(ticketId);

        Long ttlSeconds = redisTemplate.getExpire("ticket:detail:" + ticketId);
        assertThat(ttlSeconds).isNotNull().isPositive();
        // 25 ~ 35 min 之间（30min ± 5min）
        assertThat(ttlSeconds).isBetween(25L * 60, 35L * 60);
    }

    // ==================== 空值标记防穿透 ====================

    @Test
    @DisplayName("DB miss：写空值标记到 Redis（短 TTL），第二次直接命中标记抛 T0101")
    void dbMiss_emptyMarker_preventsPenetration() {
        // 1. 第一次：DB miss → 写空值标记
        ticketInfoMapper = mockMapperReturning(null);
        cacheService = new TicketCacheServiceImpl(redisTemplate, redissonClient, ticketInfoMapper, objectMapper);
        mapperCallCount.set(0);

        assertThatThrownBy(() -> cacheService.getById(ticketId))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(BusinessExceptionCode.TICKET_NOT_FOUND.getCode());
        assertThat(mapperCallCount.get()).isEqualTo(1);

        // Redis 写入空值标记
        String cachedRaw = redisTemplate.opsForValue().get("ticket:detail:" + ticketId);
        assertThat(cachedRaw).isEqualTo(TicketCacheServiceImpl.EMPTY_MARKER);

        // 2. 第二次：命中空值标记，mapper 不被调用
        assertThatThrownBy(() -> cacheService.getById(ticketId))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(BusinessExceptionCode.TICKET_NOT_FOUND.getCode());
        assertThat(mapperCallCount.get()).isEqualTo(1);  // <-- 仍然是 1，没穿透
    }

    // ==================== 测试辅助 ====================

    private TicketInfoMapper mockMapperReturning(TicketInfo result) {
        return (TicketInfoMapper) java.lang.reflect.Proxy.newProxyInstance(
                TicketInfoMapper.class.getClassLoader(),
                new Class<?>[]{TicketInfoMapper.class},
                (proxy, method, args) -> {
                    if ("selectById".equals(method.getName())) {
                        mapperCallCount.incrementAndGet();
                        return result;
                    }
                    // 其它方法（如 selectCount / selectList）返回合理默认值
                    if (method.getReturnType() == int.class) return 0;
                    if (method.getReturnType() == long.class) return 0L;
                    if (method.getReturnType() == boolean.class) return false;
                    return null;
                });
    }

    private TicketInfoMapper mockMapper() {
        return mockMapperReturning(null);
    }

    private static TicketInfo buildEntity(Long id) {
        TicketInfo entity = new TicketInfo();
        entity.setId(id);
        entity.setTicketNo("TK" + System.currentTimeMillis() + "0000001");
        entity.setTitle("测试工单-" + id);
        entity.setContent("缓存集成测试");
        entity.setType("OTHER");
        entity.setPriority("MEDIUM");
        entity.setStatus(TicketStatus.PENDING);
        entity.setCreatorId(1L);
        entity.setIsDeleted(TicketInfo.NOT_DELETED);
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        return entity;
    }
}