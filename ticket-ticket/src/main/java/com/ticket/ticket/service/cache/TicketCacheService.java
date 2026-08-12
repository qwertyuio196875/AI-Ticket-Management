package com.ticket.ticket.service.cache;

import com.ticket.ticket.vo.TicketVO;

/**
 * 工单详情 Redis 缓存抽象（ticket 09 / ADR-0004）。
 * <p>
 * 提供 cache-aside 读路径 + 写失效。Redis 配置 / 序列化 / 锁获取等细节
 * 全部隐藏在实现类，调用方（{@code TicketInfoService}）只感知
 * <ul>
 *     <li>{@link #getById(Long)} —— 缓存命中或 DB 命中时返回 {@link TicketVO}，
 *         工单不存在抛 {@code TICKET_NOT_FOUND}</li>
 *     <li>{@link #evict(Long)} —— 在 {@code ticket_info} 写操作 commit 后调用，
 *         清除对应缓存键</li>
 * </ul>
 * <p>
 * 该接口存在的意义：
 * <ul>
 *     <li>解耦 Service 与 Redis/Redisson API（便于单测 mock）</li>
 *     <li>集中缓存策略（TTL 抖动、空值标记、锁重试），业务侧无感</li>
 *     <li>为 ticket 10（统计）+ ticket 11（定时任务）可能的缓存扩展预留 seam</li>
 * </ul>
 */
public interface TicketCacheService {

    /**
     * 读路径（cache-aside）。
     * <p>
     * 命中顺序：Redis → Redisson 锁 + 二次校验 → DB。
     * 工单不存在抛 {@code BusinessException(T0101)}；命中空值标记也抛
     * {@code T0101}（防穿透）。
     *
     * @param ticketId 工单主键
     * @return 工单详情 VO
     * @throws com.ticket.common.exception.BusinessException {@code T0101}
     *                                                    当工单不存在
     */
    TicketVO getById(Long ticketId);

    /**
     * 失效缓存 —— 写操作 commit 后调用（spec AC "after commit"）。
     * <p>
     * key 不存在时为 no-op；{@code ticketId} 为空时也 no-op（不抛异常）。
     *
     * @param ticketId 工单主键
     */
    void evict(Long ticketId);

    /**
     * 注册 after-commit 失效 —— {@link org.springframework.transaction.annotation.Transactional @Transactional}
     * 方法内调用，Spring 事务 commit 钩子触发 {@link #evict(Long)}；事务回滚则不清缓存
     * （避免脏 evict 留下空 cache）。
     * <p>
     * <b>非事务上下文</b>（{@code TransactionSynchronizationManager.isSynchronizationActive() == false}）
     * → 立即 {@link #evict(Long)}，兼容单元测试或 Service 单独调用场景。
     * <p>
     * <b>存在意义</b>：避免 {@code TicketInfoService} / {@code TicketStatusService} 重复实现
     * 同一段 {@code TransactionSynchronizationManager.registerSynchronization(...)} 模板代码
     * （Fowler Duplicated Code）。
     *
     * @param ticketId 工单主键
     */
    void evictAfterCommit(Long ticketId);
}