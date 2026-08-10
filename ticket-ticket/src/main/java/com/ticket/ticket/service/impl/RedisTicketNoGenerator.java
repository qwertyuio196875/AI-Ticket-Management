package com.ticket.ticket.service.impl;

import com.ticket.ticket.service.TicketNoGenerator;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 基于 Redis {@code INCR} 的工单编号生成器（ticket 05 / ADR-0006）。
 * <p>
 * Key 格式：{@code ticket:no:seq:{yyyyMMdd}}，例如 {@code ticket:no:seq:20260808}。
 * 每日 0 点新一天的 key 首次 {@code INCR} 返回 1，自动从 1 开始 —— 满足"每日重置"需求。
 * <p>
 * <b>原子性</b>：Redis {@code INCR} 单 key 是原子操作，无需分布式锁。
 * <p>
 * <b>格式化</b>：{@link String#format} {@code %09d} 把 sequence zero-pad 到 9 位。
 * <p>
 * <b>Redis 不可用的取舍</b>：当前实现不做降级 —— 工单创建是核心业务，
 * 没有编号生成不应假装成功。Redis 宕机由业务层冒泡成 5xx，
 * 比给客户一个重复/缺失编号更安全。
 */
@Component
public class RedisTicketNoGenerator implements TicketNoGenerator {

    /** Redis key 前缀；日期段由 {@link #next()} 拼接 */
    static final String KEY_PREFIX = "ticket:no:seq:";

    /** 工单编号前缀 TK */
    private static final String TICKET_NO_PREFIX = "TK";

    /** 9 位 zero-padded sequence 的格式串 */
    private static final String SEQUENCE_FORMAT = "%09d";

    /** Redis key 的日期段格式 */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final StringRedisTemplate redisTemplate;

    public RedisTicketNoGenerator(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public String next() {
        String dateKey = LocalDate.now().format(DATE_FORMATTER);
        String redisKey = KEY_PREFIX + dateKey;
        // INCR 原子返回最新值；首日首张工单返回 1
        Long sequence = redisTemplate.opsForValue().increment(redisKey);
        if (sequence == null) {
            // 理论上不会发生：StringRedisTemplate.opsForValue().increment 不会出现 null
            // 但兜底抛业务异常比让系统卡住好
            throw new IllegalStateException("Redis INCR 返回 null，key=" + redisKey);
        }
        return TICKET_NO_PREFIX + dateKey + String.format(SEQUENCE_FORMAT, sequence);
    }
}
