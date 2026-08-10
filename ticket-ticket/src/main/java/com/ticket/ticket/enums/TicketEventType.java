package com.ticket.ticket.enums;

/**
 * 工单业务事件类型（详见 spec Phase 3 / ticket 05 AC）。
 * <p>
 * 写入 {@code ticket_log.event_type}，与 {@code ticket_info} 同事务（ADR-0012）。
 * <p>
 * <b>当前 ticket 05 仅落地 CREATED</b> —— 创建工单时由 Service 自动写一条
 * {@code CREATED} 事件。后续 ticket 会在各自阶段补齐剩余事件：
 * <ul>
 *     <li>UPDATED —— ticket 05 PUT /tickets/{id} 改 title/content</li>
 *     <li>STATUS_CHANGED / ASSIGNED —— ticket 06 状态机迁移</li>
 *     <li>COMMENTED —— ticket 07 多轮评论</li>
 *     <li>AI_CALLED —— ticket 08 DeepSeek 调用</li>
 * </ul>
 * 枚举先预留全集，避免后续 DDL 变更。
 */
public enum TicketEventType {

    /** 工单创建 —— ticket 05 必写 */
    CREATED,
    /** 工单内容更新（title / content 修改）—— ticket 05 PUT 触发 */
    UPDATED,
    /** 状态变更 —— ticket 06 落地 */
    STATUS_CHANGED,
    /** 分配处理人 —— ticket 06 落地 */
    ASSIGNED,
    /** 多轮评论 —— ticket 07 落地 */
    COMMENTED,
    /** AI 调用 —— ticket 08 落地 */
    AI_CALLED
}
