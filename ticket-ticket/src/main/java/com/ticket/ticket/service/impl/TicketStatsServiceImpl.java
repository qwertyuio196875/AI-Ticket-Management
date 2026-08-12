package com.ticket.ticket.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticket.common.exception.BusinessException;
import com.ticket.common.exception.BusinessExceptionCode;
import com.ticket.ticket.enums.TicketStatus;
import com.ticket.ticket.mapper.TicketStatsMapper;
import com.ticket.ticket.service.TicketStatsService;
import com.ticket.ticket.vo.PriorityCountVO;
import com.ticket.ticket.vo.StatsSummaryVO;
import com.ticket.ticket.vo.TopHandlerVO;
import com.ticket.ticket.vo.TrendItemVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;

/**
 * {@link TicketStatsService} 实现（ticket 10 / ADR-0033）。
 * <p>
 * <b>职责分层</b>：
 * <ol>
 *     <li>把 mapper 返回的 {@code List<Map<String,Object>>} 转换为强类型 VO</li>
 *     <li>缺失数据兜底（如 {@code byPriority} 永远返回 3 项；{@code trend} 缺失日期补 0）</li>
 *     <li>Redis 缓存 5 min（{@code stats:tickets:{endpoint}:{paramsHash}}）</li>
 *     <li>JSON 序列化失败 → log warn 后继续走 DB 路径（不阻塞业务）</li>
 * </ol>
 *
 * <p><b>缓存策略</b>（ADR-0033 / spec AC #6）：
 * <ul>
 *     <li>key 前缀 {@code stats:tickets:{endpoint}:{paramsHash}}，{@code paramsHash} 是
 *         参数的 SHA-256 hex 前 16 位（避免超长 key）</li>
 *     <li>value 用 Jackson 序列化为 JSON</li>
 *     <li>TTL = 5 min（dashboard 实时性要求不高）</li>
 *     <li>Redis 读 / 写异常 → 降级走 mapper，不抛错（保护可用性）</li>
 *     <li>{@code redisTemplate} / {@code objectMapper} 允许为 null（单测场景）——
 *         此时所有方法跳过缓存，纯 DB 路径</li>
 * </ul>
 *
 * <p><b>不在本 Service 范围</b>：
 * <ul>
 *     <li>权限校验 —— Controller 层 {@code @PreAuthorize} 把关</li>
 *     <li>数据权限（按部门过滤）—— spec 明确不做</li>
 * </ul>
 */
@Service
public class TicketStatsServiceImpl implements TicketStatsService {

    private static final Logger log = LoggerFactory.getLogger(TicketStatsServiceImpl.class);

    private static final String CACHE_KEY_PREFIX = "stats:tickets:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    /** trend 默认天数（days 非法时兜底） */
    private static final int DEFAULT_TREND_DAYS = 7;
    /** trend 天数上限 */
    private static final int MAX_TREND_DAYS = 30;

    /** topHandlers 默认 / 上限 */
    private static final int DEFAULT_TOP_HANDLERS = 10;
    private static final int MAX_TOP_HANDLERS = 50;

    /** byPriority 固定顺序（与数据字典 HIGH/MEDIUM/LOW 对齐） */
    private static final List<String> PRIORITY_ORDER = List.of("HIGH", "MEDIUM", "LOW");

    private final TicketStatsMapper ticketStatsMapper;
    private final StringRedisTemplate redisTemplate; // 可为 null（单测）
    private final ObjectMapper objectMapper;          // 可为 null（单测）

    public TicketStatsServiceImpl(TicketStatsMapper ticketStatsMapper,
                                  StringRedisTemplate redisTemplate,
                                  ObjectMapper objectMapper) {
        this.ticketStatsMapper = ticketStatsMapper;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    // ==================== summary ====================

    @Override
    public StatsSummaryVO summary() {
        return cachedSingle("summary", "", StatsSummaryVO.class,
                () -> computeSummary());
    }

    private StatsSummaryVO computeSummary() {
        Map<TicketStatus, Long> statusCount = new HashMap<>();
        for (Map<String, Object> row : safeList(ticketStatsMapper.countByStatus())) {
            String statusName = asString(row.get("status"));
            Long cnt = asLong(row.get("cnt"));
            if (statusName == null || cnt == null) {
                continue;
            }
            try {
                TicketStatus s = TicketStatus.valueOf(statusName);
                statusCount.merge(s, cnt, Long::sum);
            } catch (IllegalArgumentException ex) {
                // 未知 status 名静默忽略 —— 防御脏数据 / 后续新加状态未及时同步
                log.warn("summary：未知 status 值 {}，忽略", statusName);
            }
        }

        long pending = statusCount.getOrDefault(TicketStatus.PENDING, 0L);
        long processing = statusCount.getOrDefault(TicketStatus.PROCESSING, 0L);
        long resolved = statusCount.getOrDefault(TicketStatus.RESOLVED, 0L);
        long closed = statusCount.getOrDefault(TicketStatus.CLOSED, 0L);
        long total = pending + processing + resolved + closed;
        return new StatsSummaryVO(pending, processing, resolved, closed, total);
    }

    // ==================== trend ====================

    @Override
    public List<TrendItemVO> trend(int days) {
        int normalized = normalizeDays(days);
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(normalized - 1L);
        String params = "d" + normalized;
        return cachedList("trend", params, new TypeReference<List<TrendItemVO>>() {},
                () -> computeTrend(from, to));
    }

    private List<TrendItemVO> computeTrend(LocalDate from, LocalDate to) {
        // 先按 mapper 拿到的数据填充到 TreeMap（date → count）
        Map<LocalDate, Long> bucket = new TreeMap<>();
        for (Map<String, Object> row : safeList(ticketStatsMapper.countDailyTrend(from, to))) {
            String dateStr = asString(row.get("date"));
            Long cnt = asLong(row.get("cnt"));
            if (dateStr == null || cnt == null) {
                continue;
            }
            try {
                LocalDate d = LocalDate.parse(dateStr);
                bucket.put(d, cnt);
            } catch (Exception ex) {
                log.warn("trend：日期格式非法 {}，忽略", dateStr);
            }
        }
        // 缺失日期补 0 → 完整 N 天序列（按日期升序）
        List<TrendItemVO> result = new ArrayList<>(to.getDayOfYear() - from.getDayOfYear() + 1);
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            result.add(new TrendItemVO(d, bucket.getOrDefault(d, 0L)));
        }
        return result;
    }

    private static int normalizeDays(int days) {
        if (days <= 0 || days > MAX_TREND_DAYS) {
            return DEFAULT_TREND_DAYS;
        }
        return days;
    }

    // ==================== byPriority ====================

    @Override
    public List<PriorityCountVO> byPriority() {
        return cachedList("by-priority", "", new TypeReference<List<PriorityCountVO>>() {},
                () -> computeByPriority());
    }

    private List<PriorityCountVO> computeByPriority() {
        Map<String, Long> bucket = new HashMap<>();
        for (Map<String, Object> row : safeList(ticketStatsMapper.countByPriority())) {
            String p = asString(row.get("priority"));
            Long cnt = asLong(row.get("cnt"));
            if (p == null || cnt == null) {
                continue;
            }
            bucket.put(p, cnt);
        }
        // 固定顺序 3 项：缺失补 0
        List<PriorityCountVO> result = new ArrayList<>(PRIORITY_ORDER.size());
        for (String p : PRIORITY_ORDER) {
            result.add(new PriorityCountVO(p, bucket.getOrDefault(p, 0L)));
        }
        return result;
    }

    // ==================== topHandlers ====================

    @Override
    public List<TopHandlerVO> topHandlers(int limit) {
        int normalized = normalizeLimit(limit);
        String params = "l" + normalized;
        return cachedList("top-handlers", params, new TypeReference<List<TopHandlerVO>>() {},
                () -> computeTopHandlers(normalized));
    }

    private List<TopHandlerVO> computeTopHandlers(int limit) {
        List<TopHandlerVO> result = new ArrayList<>();
        for (Map<String, Object> row : safeList(ticketStatsMapper.topHandlersByResolved(limit))) {
            Long handlerId = asLong(row.get("handlerId"));
            String handlerName = asString(row.get("handlerName"));
            Long resolvedCount = asLong(row.get("resolvedCount"));
            if (handlerId == null || resolvedCount == null) {
                continue;
            }
            result.add(new TopHandlerVO(handlerId, handlerName, resolvedCount));
        }
        return result;
    }

    private static int normalizeLimit(int limit) {
        if (limit <= 0 || limit > MAX_TOP_HANDLERS) {
            return DEFAULT_TOP_HANDLERS;
        }
        return limit;
    }

    // ==================== 缓存 helper ====================

    /**
     * 缓存读 / 写 + 降级（单值 VO 版本）。任何缓存异常仅 log，不抛业务异常（保护可用性）。
     *
     * @param endpoint 端点名（summary / trend / by-priority / top-handlers）
     * @param params   参数标签（e.g. {@code "d7"} / {@code "l10"} / 空串）
     * @param clazz    返回 VO 类型
     * @param loader   缓存未命中时的回源函数（执行 mapper 聚合 + 兜底）
     */
    private <T> T cachedSingle(String endpoint, String params, Class<T> clazz, Supplier<T> loader) {
        String key = cacheKey(endpoint, params);
        // 1. 读缓存
        if (redisTemplate != null && objectMapper != null) {
            try {
                String cached = redisTemplate.opsForValue().get(key);
                if (cached != null) {
                    return objectMapper.readValue(cached, clazz);
                }
            } catch (Exception ex) {
                log.warn("统计缓存读取失败 key={} cause={}", key, ex.toString());
            }
        }
        // 2. 回源
        T result = loader.get();
        // 3. 写缓存（best-effort）
        if (redisTemplate != null && objectMapper != null) {
            try {
                String json = objectMapper.writeValueAsString(result);
                redisTemplate.opsForValue().set(key, json, CACHE_TTL);
            } catch (Exception ex) {
                log.warn("统计缓存写入失败 key={} cause={}", key, ex.toString());
            }
        }
        return result;
    }

    /**
     * 缓存读 / 写 + 降级（列表 VO 版本）。
     * <p>
     * 与 {@link #cachedSingle} 拆分是 Java 泛型擦除的妥协：{@code List<T>}
     * 不能用 {@code Class<T>} 表示，必须传 {@link TypeReference}。
     */
    private <T> T cachedList(String endpoint, String params, TypeReference<T> typeRef, Supplier<T> loader) {
        String key = cacheKey(endpoint, params);
        // 1. 读缓存
        if (redisTemplate != null && objectMapper != null) {
            try {
                String cached = redisTemplate.opsForValue().get(key);
                if (cached != null) {
                    return objectMapper.readValue(cached, typeRef);
                }
            } catch (Exception ex) {
                log.warn("统计缓存读取失败 key={} cause={}", key, ex.toString());
            }
        }
        // 2. 回源
        T result = loader.get();
        // 3. 写缓存（best-effort）
        if (redisTemplate != null && objectMapper != null) {
            try {
                String json = objectMapper.writeValueAsString(result);
                redisTemplate.opsForValue().set(key, json, CACHE_TTL);
            } catch (Exception ex) {
                log.warn("统计缓存写入失败 key={} cause={}", key, ex.toString());
            }
        }
        return result;
    }

    private String cacheKey(String endpoint, String params) {
        // 始终带 :{hash} 后缀 —— 即使无参端点（summary / by-priority）也用空字符串的 hash，
        // 与 spec 字面 "stats:tickets:{endpoint}:{paramsHash}" 一致；同时保证
        // 不同端点有独立 key。
        return CACHE_KEY_PREFIX + endpoint + ":" + sha256Short(params == null ? "" : params);
    }

    /**
     * SHA-256 hex 前 16 位 —— key 紧凑够用，冲突概率极低。
     */
    private static String sha256Short(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(16);
            for (int i = 0; i < 8; i++) {
                sb.append(String.format("%02x", digest[i]));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            // 不会发生 —— SHA-256 是 JDK 必带算法
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    // ==================== 工具 ====================

    private static <T> List<T> safeList(List<T> list) {
        return list == null ? Collections.emptyList() : list;
    }

    private static String asString(Object o) {
        if (o == null) {
            return null;
        }
        return o.toString();
    }

    private static Long asLong(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Long) {
            return (Long) o;
        }
        if (o instanceof Number) {
            return ((Number) o).longValue();
        }
        try {
            return Long.parseLong(o.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}