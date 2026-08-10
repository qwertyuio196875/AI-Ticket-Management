package com.ticket.ticket.service;

/**
 * 工单编号生成器（ticket 05 / ADR-0006）。
 * <p>
 * 输出格式固定：{@code TK{yyyyMMdd}{9 位 zero-padded sequence}}，
 * 例：{@code TK2026080800000001}。sequence 从 1 开始，每日 0 点归零。
 * <p>
 * <b>默认实现</b>：{@code RedisTicketNoGenerator} —— Redis INCR 保证原子性，
 * 无需分布式协调器。
 * <p>
 * <b>可替代实现</b>：集成测试用 {@code InMemoryTicketNoGenerator}（在
 * {@code ticket-web/src/test/java/.../TicketCrudIntegrationTest} 的
 * {@code @TestConfiguration} 内提供）覆盖注入，
 * 因为测试机无 Redis。
 */
public interface TicketNoGenerator {

    /**
     * 生成下一个工单编号。
     *
     * @return {@code TK{yyyyMMdd}{9 位}} 格式的字符串
     */
    String next();
}
