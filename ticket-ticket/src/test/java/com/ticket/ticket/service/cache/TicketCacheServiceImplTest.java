package com.ticket.ticket.service.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticket.common.exception.BusinessException;
import com.ticket.common.exception.BusinessExceptionCode;
import com.ticket.ticket.entity.TicketInfo;
import com.ticket.ticket.enums.TicketStatus;
import com.ticket.ticket.mapper.TicketInfoMapper;
import com.ticket.ticket.vo.TicketVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

/**
 * {@link TicketCacheServiceImpl} 单元测试（ticket 09 AC）。
 * <p>
 * <b>覆盖范围</b>（spec AC "Unit test: TicketCacheService with mocked Redis + DB"）：
 * <ul>
 *     <li>缓存命中：直接反序列化返回 {@link TicketVO}</li>
 *     <li>缓存命中空值标记：抛 {@code T0101}，不再查 DB（防穿透）</li>
 *     <li>缓存未命中 + DB 命中：加锁、二次校验、写回缓存（TTL 抖动 ±5min）</li>
 *     <li>缓存未命中 + DB 未命中：写空值标记（短 TTL），抛 {@code T0101}</li>
 *     <li>缓存未命中 + 加锁后 double-check 命中：跳过 DB 直查</li>
 *     <li>锁竞争：重试 3 次后仍未拿到锁，<b>降级</b>走 DB 直查（不退化为错误）</li>
 *     <li>{@link TicketCacheServiceImpl#evict(Long)}：删除 Redis key</li>
 *     <li>边界：{@code ticketId == null} 时 {@code getById} 抛 {@code C0400}；
 *         {@code evict} 为 no-op</li>
 * </ul>
 * <p>
 * <b>Mock 策略</b>：
 * <ul>
 *     <li>{@link StringRedisTemplate} + {@link ValueOperations}：mock Redis 客户端，
 *         验证 cache 写入 / 读取 / 失效路径</li>
 *     <li>{@link RedissonClient} + {@link RLock}：mock 分布式锁，
 *         验证加锁 + 释放 + 重试逻辑</li>
 *     <li>{@link TicketInfoMapper}：mock DB，验证 cache miss 时回源查询</li>
 *     <li>{@link ObjectMapper}：真实 Jackson 实例，确保 JSON 序列化与生产一致</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class TicketCacheServiceImplTest {

    private static final Long TICKET_ID = 100L;
    private static final String CACHE_KEY = "ticket:detail:" + TICKET_ID;
    private static final String LOCK_KEY = "ticket:lock:" + TICKET_ID;

    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOps;
    @Mock RedissonClient redissonClient;
    @Mock RLock lock;
    @Mock TicketInfoMapper ticketInfoMapper;

    TicketCacheServiceImpl cacheService;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    @BeforeEach
    void setUp() {
        // 构造 service（生产代码会通过 Spring 自动注入 ObjectMapper / RedisTemplate / Redisson / Mapper）
        cacheService = new TicketCacheServiceImpl(
                redisTemplate, redissonClient, ticketInfoMapper, objectMapper);
        // StringRedisTemplate.opsForValue() 在每次调用时返回 ValueOperations mock
        // 用 lenient：evict/getById=null 路径不调 opsForValue()，严格模式会报 UnnecessaryStubbing
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    // ==================== cache hit ====================

    @Test
    @DisplayName("缓存命中（JSON VO）：直接反序列化返回，不查 DB 不加锁")
    void getById_cacheHit_returnsVo_withoutDbOrLock() throws Exception {
        TicketVO cachedVo = buildVo(TICKET_ID);
        String json = objectMapper.writeValueAsString(cachedVo);
        when(valueOps.get(CACHE_KEY)).thenReturn(json);

        TicketVO result = cacheService.getById(TICKET_ID);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(TICKET_ID);
        assertThat(result.getTitle()).isEqualTo(cachedVo.getTitle());
        // 关键：命中缓存就不应该走 DB、不应该加锁
        verify(ticketInfoMapper, never()).selectById(anyLong());
        verify(redissonClient, never()).getLock(anyString());
    }

    @Test
    @DisplayName("缓存命中空值标记：抛 T0101，不再查 DB（防穿透）")
    void getById_cacheHitEmptyMarker_throwsNotFound_withoutDb() {
        when(valueOps.get(CACHE_KEY)).thenReturn(TicketCacheServiceImpl.EMPTY_MARKER);

        assertThatThrownBy(() -> cacheService.getById(TICKET_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(BusinessExceptionCode.TICKET_NOT_FOUND.getCode());

        verify(ticketInfoMapper, never()).selectById(anyLong());
        verify(redissonClient, never()).getLock(anyString());
    }

    // ==================== cache miss + DB hit ====================

    @Test
    @DisplayName("缓存未命中 + DB 命中：加锁 + 二次校验 + 写回缓存（TTL 30±5min）")
    void getById_cacheMiss_dbHit_acquiresLockAndWritesCache() throws Exception {
        // 1. Redis miss（首次 + 二次校验）
        when(valueOps.get(CACHE_KEY)).thenReturn(null);
        // 2. 加锁成功
        when(redissonClient.getLock(LOCK_KEY)).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        // 3. DB 命中
        TicketInfo dbEntity = buildEntity(TICKET_ID);
        when(ticketInfoMapper.selectById(TICKET_ID)).thenReturn(dbEntity);

        TicketVO result = cacheService.getById(TICKET_ID);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(TICKET_ID);
        assertThat(result.getStatus()).isEqualTo(TicketStatus.PENDING);
        // 加锁 + 释放
        verify(redissonClient, times(1)).getLock(LOCK_KEY);
        verify(lock, times(1)).tryLock(anyLong(), anyLong(), any(TimeUnit.class));
        verify(lock, times(1)).unlock();
        // 写缓存（带 TTL）—— 捕获 TTL 参数
        org.mockito.ArgumentCaptor<java.time.Duration> ttlCaptor =
                org.mockito.ArgumentCaptor.forClass(java.time.Duration.class);
        verify(valueOps, times(1)).set(eq(CACHE_KEY), anyString(), ttlCaptor.capture());
        // TTL 必须在 25 ~ 35 分钟之间（30min ± 5min 抖动）
        long ttlSeconds = ttlCaptor.getValue().getSeconds();
        assertThat(ttlSeconds).isBetween(25L * 60, 35L * 60);
    }

    // ==================== cache miss + DB miss ====================

    @Test
    @DisplayName("缓存未命中 + DB 未命中：写空值标记 + 抛 T0101")
    void getById_cacheMiss_dbMiss_writesEmptyMarker_andThrows() throws Exception {
        when(valueOps.get(CACHE_KEY)).thenReturn(null);
        when(redissonClient.getLock(LOCK_KEY)).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(ticketInfoMapper.selectById(TICKET_ID)).thenReturn(null);

        assertThatThrownBy(() -> cacheService.getById(TICKET_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(BusinessExceptionCode.TICKET_NOT_FOUND.getCode());

        // 写空值标记（防穿透）
        verify(valueOps, times(1)).set(eq(CACHE_KEY), eq(TicketCacheServiceImpl.EMPTY_MARKER), any(java.time.Duration.class));
        // 仍然要释放锁
        verify(lock, times(1)).unlock();
    }

    // ==================== double-check 命中 ====================

    @Test
    @DisplayName("缓存未命中首次 + 加锁后 double-check 命中：跳过 DB 直查")
    void getById_doubleCheckInsideLock_skipsDb() throws Exception {
        // 第一次 GET：null
        // 锁内 GET：返回 VO JSON
        TicketVO cachedVo = buildVo(TICKET_ID);
        String json = objectMapper.writeValueAsString(cachedVo);
        when(valueOps.get(CACHE_KEY)).thenReturn(null).thenReturn(json);
        when(redissonClient.getLock(LOCK_KEY)).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        TicketVO result = cacheService.getById(TICKET_ID);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(TICKET_ID);
        // 关键：double-check 命中后不该再查 DB
        verify(ticketInfoMapper, never()).selectById(anyLong());
        verify(lock, times(1)).unlock();
    }

    // ==================== 锁竞争降级 ====================

    @Test
    @DisplayName("锁竞争：3 次重试后仍未拿到，降级走 DB 直查（不抛错）")
    void getById_lockContention_degradesToDb() throws Exception {
        when(valueOps.get(CACHE_KEY)).thenReturn(null);
        when(redissonClient.getLock(LOCK_KEY)).thenReturn(lock);
        // 3 次重试都拿不到锁
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(false);
        // DB 命中
        TicketInfo dbEntity = buildEntity(TICKET_ID);
        when(ticketInfoMapper.selectById(TICKET_ID)).thenReturn(dbEntity);

        TicketVO result = cacheService.getById(TICKET_ID);

        // 降级路径：不抛错，返回 DB 结果
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(TICKET_ID);
        // 重试 3 次
        verify(lock, times(TicketCacheServiceImpl.MAX_LOCK_RETRIES))
                .tryLock(anyLong(), anyLong(), any(TimeUnit.class));
        // 走降级路径不应调用 unlock（没拿到锁）
        verify(lock, never()).unlock();
        // 应当写入缓存（让后续请求不再击穿）
        verify(valueOps, times(1)).set(eq(CACHE_KEY), anyString(), any(java.time.Duration.class));
    }

    // ==================== evict ====================

    @Test
    @DisplayName("evict：删除 Redis key")
    void evict_deletesCacheKey() {
        cacheService.evict(TICKET_ID);

        verify(redisTemplate, times(1)).delete(CACHE_KEY);
    }

    @Test
    @DisplayName("evict：ticketId=null 是 no-op，不抛错")
    void evict_nullTicketId_isNoOp() {
        cacheService.evict(null);

        verify(redisTemplate, never()).delete(anyString());
    }

    // ==================== 边界 ====================

    @Test
    @DisplayName("getById：ticketId=null 抛 C0400")
    void getById_nullTicketId_throwsParamInvalid() {
        assertThatThrownBy(() -> cacheService.getById(null))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(BusinessExceptionCode.PARAM_INVALID.getCode());

        verify(valueOps, never()).get(anyString());
    }

    // ==================== 测试辅助 ====================

    private static TicketVO buildVo(Long id) {
        TicketVO vo = new TicketVO();
        vo.setId(id);
        vo.setTicketNo("TK2026081000000001");
        vo.setTitle("测试工单");
        vo.setContent("内容");
        vo.setType("OTHER");
        vo.setPriority("MEDIUM");
        vo.setStatus(TicketStatus.PENDING);
        vo.setCreatorId(1L);
        vo.setCreateTime(LocalDateTime.now());
        vo.setUpdateTime(LocalDateTime.now());
        return vo;
    }

    private static TicketInfo buildEntity(Long id) {
        TicketInfo entity = new TicketInfo();
        entity.setId(id);
        entity.setTicketNo("TK2026081000000001");
        entity.setTitle("测试工单");
        entity.setContent("内容");
        entity.setType("OTHER");
        entity.setPriority("MEDIUM");
        entity.setStatus(TicketStatus.PENDING);
        entity.setCreatorId(1L);
        entity.setIsDeleted(TicketInfo.NOT_DELETED);
        return entity;
    }
}