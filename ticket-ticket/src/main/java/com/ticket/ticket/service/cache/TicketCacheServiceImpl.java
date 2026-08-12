package com.ticket.ticket.service.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticket.common.exception.BusinessException;
import com.ticket.common.exception.BusinessExceptionCode;
import com.ticket.ticket.entity.TicketInfo;
import com.ticket.ticket.mapper.TicketInfoMapper;
import com.ticket.ticket.vo.TicketVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * {@link TicketCacheService} 实现（ticket 09 / ADR-0004）。
 * <p>
 * <b>读路径（cache-aside + 双重检查 + 分布式锁防击穿 + 空值标记防穿透）</b>：
 * <ol>
 *     <li>Redis {@code GET ticket:detail:{id}} —— 命中 JSON → 反序列化返回；
 *         命中 {@link #EMPTY_MARKER} → 抛 {@code T0101}（防穿透）</li>
 *     <li>Redis miss → {@link RedissonClient#getLock(String) Redisson 锁}
 *         {@code ticket:lock:{id}}，<b>最多 {@link #MAX_LOCK_RETRIES} 次重试</b>。
 *         重试期间每次短暂 sleep（指数退避的简化版）</li>
 *     <li>拿到锁 → <b>二次校验 Redis</b>（防上一次持锁人已写缓存），
 *         二次命中直接返回，未命中则查 DB</li>
 *     <li>查 DB：命中 → 序列化为 JSON 写 Redis（TTL = 30min ± 5min 抖动 防雪崩）→
 *         返回 VO；DB miss → 写 {@link #EMPTY_MARKER}（短 TTL）→ 抛 {@code T0101}</li>
 *     <li>锁竞争降级：3 次重试后仍未拿到锁 → 直接查 DB（不抛错，保护可用性），
 *         并把结果写缓存让后续请求不再击穿</li>
 *     <li>{@code finally} 块释放锁（仅当锁由当前线程持有时，避免 unlock 异常）</li>
 * </ol>
 * <p>
 * <b>写失效（write-invalidate）</b>：
 * <ul>
 *     <li>{@link #evict(Long)} —— {@code DEL ticket:detail:{id}}。在
 *         {@code @Transactional} 方法 <b>commit 之后</b>调用（spec AC "after commit"），
 *         避免事务回滚后留下失效但已清空的缓存状态（实际场景影响极小，但
 *         严格语义要求 after-commit）</li>
 *     <li>DB miss 不写业务数据，只写 {@link #EMPTY_MARKER} 防穿透</li>
 * </ul>
 * <p>
 * <b>关于"AI 分类不刷缓存"的说明</b>：ticket 08 AI 分类在 {@code create()} 内调用，
 * 会修改 {@code ticket_info.type / priority}。create 是新工单写入，缓存里本来
 * 没有 key（evict 是 no-op），所以 ticket 09 不强制要求 create 调 evict。
 * <p>
 * <b>YAGNI</b>：
 * <ul>
 *     <li>不做布隆过滤器防穿透 —— 空值标记足够（ADR-0004 简化）</li>
 *     <li>不做缓存预热 —— 冷启动由首次请求承担</li>
 *     <li>不缓存 {@code ticket_log} / 列表 / 统计 —— 维度多命中率低</li>
 *     <li>序列化失败 → 不抛业务异常，log warn 后继续走 DB 路径</li>
 * </ul>
 */
@Service
public class TicketCacheServiceImpl implements TicketCacheService {

    private static final Logger log = LoggerFactory.getLogger(TicketCacheServiceImpl.class);

    // ---------- Redis Key / Lock Key ----------

    /** 缓存 key 前缀 —— {@code ticket:detail:{id}} */
    static final String CACHE_KEY_PREFIX = "ticket:detail:";

    /** 分布式锁 key 前缀 —— {@code ticket:lock:{id}}（防止同 id 缓存击穿） */
    static final String LOCK_KEY_PREFIX = "ticket:lock:";

    // ---------- TTL ----------

    /** 业务数据 TTL：30 分钟（基础值） */
    static final Duration BASE_TTL = Duration.ofMinutes(30);

    /** TTL 抖动上限：±5 分钟（防雪崩） */
    static final Duration TTL_JITTER = Duration.ofMinutes(5);

    /** 空值标记 TTL：1 分钟（防穿透，DB miss 不长期占用缓存） */
    static final Duration EMPTY_TTL = Duration.ofMinutes(1);

    // ---------- 锁参数 ----------

    /** 锁单次等待时间（ms）：500ms */
    static final long LOCK_WAIT_MS = 500L;

    /** 锁租约时间（ms）：3s —— 持锁人超过 3s 视为卡死，锁自动释放 */
    static final long LOCK_LEASE_MS = 3000L;

    /** 锁竞争最大重试次数：3 */
    static final int MAX_LOCK_RETRIES = 3;

    /** 重试间隔（ms）：50ms（指数退避的简化版） */
    static final long LOCK_RETRY_INTERVAL_MS = 50L;

    // ---------- 空值标记 ----------

    /** DB miss 占位符 —— 命中即抛 {@code T0101}，避免缓存穿透到 DB */
    public static final String EMPTY_MARKER = "__EMPTY__";

    // ---------- 依赖 ----------

    private final StringRedisTemplate redisTemplate;
    private final RedissonClient redissonClient;
    private final TicketInfoMapper ticketInfoMapper;
    private final ObjectMapper objectMapper;

    public TicketCacheServiceImpl(StringRedisTemplate redisTemplate,
                                  RedissonClient redissonClient,
                                  TicketInfoMapper ticketInfoMapper,
                                  ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.redissonClient = redissonClient;
        this.ticketInfoMapper = ticketInfoMapper;
        this.objectMapper = objectMapper;
    }

    // ---------- 读路径 ----------

    @Override
    public TicketVO getById(Long ticketId) {
        if (ticketId == null) {
            throw BusinessException.of(BusinessExceptionCode.PARAM_INVALID, "工单 id 不能为空");
        }
        String cacheKey = CACHE_KEY_PREFIX + ticketId;

        // 1. Redis GET：命中 JSON → 反序列化返回；命中 EMPTY → 抛 T0101
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            if (EMPTY_MARKER.equals(cached)) {
                // 防穿透：DB 已知不存在
                throw BusinessException.of(BusinessExceptionCode.TICKET_NOT_FOUND);
            }
            try {
                return objectMapper.readValue(cached, TicketVO.class);
            } catch (Exception ex) {
                // 反序列化失败（缓存值与新 schema 不兼容等）—— log 后继续走 DB 路径
                log.warn("缓存反序列化失败，降级到 DB path key={} cause={}",
                        cacheKey, ex.toString());
            }
        }

        // 2. Redis miss：尝试加锁 + double-check + DB 查询
        return loadWithLock(ticketId, cacheKey);
    }

    /**
     * 加锁 + double-check + DB 回源。
     * <p>
     * 锁竞争 3 次后仍未拿到 → <b>降级</b>走 DB 直查（不抛错，保护可用性）。
     */
    private TicketVO loadWithLock(Long ticketId, String cacheKey) {
        String lockKey = LOCK_KEY_PREFIX + ticketId;
        RLock lock = redissonClient.getLock(lockKey);

        boolean locked = false;
        try {
            int retries = 0;
            while (retries < MAX_LOCK_RETRIES) {
                locked = lock.tryLock(LOCK_WAIT_MS, LOCK_LEASE_MS, TimeUnit.MILLISECONDS);
                if (locked) {
                    break;
                }
                retries++;
                if (retries < MAX_LOCK_RETRIES) {
                    Thread.sleep(LOCK_RETRY_INTERVAL_MS);
                }
            }

            if (!locked) {
                // 锁竞争降级：拿不到锁也返回数据，不抛错（保护可用性优先于严格防击穿）
                log.warn("工单缓存锁 {} 次重试后仍未拿到，降级走 DB 直查 ticketId={}",
                        MAX_LOCK_RETRIES, ticketId);
                return loadFromDbAndCache(ticketId, cacheKey);
            }

            // 拿到锁：double-check
            try {
                String cachedAgain = redisTemplate.opsForValue().get(cacheKey);
                if (cachedAgain != null) {
                    if (EMPTY_MARKER.equals(cachedAgain)) {
                        throw BusinessException.of(BusinessExceptionCode.TICKET_NOT_FOUND);
                    }
                    try {
                        return objectMapper.readValue(cachedAgain, TicketVO.class);
                    } catch (Exception ex) {
                        log.warn("缓存二次校验反序列化失败，降级 DB path key={} cause={}",
                                cacheKey, ex.toString());
                        // 继续走 DB
                    }
                }
                return loadFromDbAndCache(ticketId, cacheKey);
            } finally {
                // 仅当锁由当前线程持有时才释放（避免 unlock 异常）
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw BusinessException.of(BusinessExceptionCode.INTERNAL_ERROR, "缓存查询被中断");
        }
    }

    /**
     * DB 回源 + 写缓存（含 TTL 抖动）。
     * <p>
     * 命中 → 写 JSON（TTL = 30min ± 5min）；miss → 写 {@link #EMPTY_MARKER}（TTL = 1min）。
     */
    private TicketVO loadFromDbAndCache(Long ticketId, String cacheKey) {
        TicketInfo entity = ticketInfoMapper.selectById(ticketId);
        if (entity == null) {
            // DB miss → 写空值标记防穿透
            try {
                redisTemplate.opsForValue().set(cacheKey, EMPTY_MARKER, EMPTY_TTL);
            } catch (Exception ex) {
                log.warn("写空值标记失败 ticketId={} cause={}", ticketId, ex.toString());
            }
            throw BusinessException.of(BusinessExceptionCode.TICKET_NOT_FOUND);
        }

        TicketVO vo = toVO(entity);
        try {
            String json = objectMapper.writeValueAsString(vo);
            // TTL 抖动：±5min
            long jitterSeconds = ThreadLocalRandom.current()
                    .nextLong(-TTL_JITTER.getSeconds(), TTL_JITTER.getSeconds() + 1);
            Duration ttl = BASE_TTL.plusSeconds(jitterSeconds);
            redisTemplate.opsForValue().set(cacheKey, json, ttl);
        } catch (JsonProcessingException ex) {
            log.warn("工单缓存序列化失败 ticketId={} cause={}", ticketId, ex.toString());
        } catch (Exception ex) {
            log.warn("工单缓存写入失败 ticketId={} cause={}", ticketId, ex.toString());
        }
        return vo;
    }

    // ---------- 写失效 ----------

    @Override
    public void evict(Long ticketId) {
        if (ticketId == null) {
            return; // no-op
        }
        String cacheKey = CACHE_KEY_PREFIX + ticketId;
        try {
            redisTemplate.delete(cacheKey);
        } catch (Exception ex) {
            // 缓存失效失败不能阻塞业务 —— 仅 log（避免 Redis 宕机导致写操作回滚）
            log.warn("工单缓存失效失败 ticketId={} cause={}", ticketId, ex.toString());
        }
    }

    @Override
    public void evictAfterCommit(Long ticketId) {
        if (ticketId == null) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evict(ticketId);
                }
            });
        } else {
            // 非事务上下文（单元测试 / Service 单独调用）—— 立即 evict
            evict(ticketId);
        }
    }

    // ---------- 内部辅助 ----------

    /**
     * Entity → VO —— 与 {@code TicketInfoServiceImpl.toVO} 同语义。
     * <p>
     * 这里复用一份是为了让 {@link TicketCacheServiceImpl} 自包含：
     * 不依赖 {@code TicketInfoService} 避免循环依赖，同时让"读缓存"语义
     * 完全内聚（不暴露 mapper 给业务方）。
     */
    private static TicketVO toVO(TicketInfo entity) {
        TicketVO vo = new TicketVO();
        vo.setId(entity.getId());
        vo.setTicketNo(entity.getTicketNo());
        vo.setTitle(entity.getTitle());
        vo.setContent(entity.getContent());
        vo.setType(entity.getType());
        vo.setPriority(entity.getPriority());
        // 与 TicketInfoServiceImpl.toVO 行为一致 —— status 字段不做隐式 fallback。
        // DB 层有非空约束（schema.sql line 140 DEFAULT 'PENDING'），调用方若拿到 null
        // 应视为数据异常而非静默修复。code-review Standards smell #2 修复。
        vo.setStatus(entity.getStatus());
        vo.setCreatorId(entity.getCreatorId());
        vo.setHandlerId(entity.getHandlerId());
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());
        return vo;
    }
}