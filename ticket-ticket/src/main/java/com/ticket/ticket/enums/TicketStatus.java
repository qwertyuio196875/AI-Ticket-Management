package com.ticket.ticket.enums;

import com.ticket.common.exception.BusinessException;
import com.ticket.common.exception.BusinessExceptionCode;

import java.util.EnumSet;
import java.util.Set;

/**
 * 工单状态枚举（ticket 05 落地，详见 ADR-0005）。
 * <p>
 * 四种状态：{@link #PENDING} / {@link #PROCESSING} / {@link #RESOLVED} / {@link #CLOSED}。
 * 枚举名同时作为 DB 存储值 —— 直接 {@code @TableField} 持久化到 {@code ticket_info.status}。
 * <p>
 * <b>合法迁移</b>（与 ADR-0005 / spec Phase 3 一致）：
 * <pre>{@code
 * PENDING      ->  PROCESSING   （分配）
 * PENDING      ->  CLOSED       （关闭）
 * PROCESSING   ->  RESOLVED     （完成）
 * PROCESSING   ->  CLOSED       （关闭）
 * RESOLVED     ->  CLOSED       （手动关闭）
 * }</pre>
 * <b>非法迁移</b>一律抛 {@code TICKET_INVALID_TRANSITION}（业务异常码 {@code T0102}），
 * 集中校验避免 if-else 散落各处（ADR-0005 关键取舍）。
 * <p>
 * 后续 ticket 06 会在 Service 层调用 {@link #canTransitTo} 做状态变更前置校验。
 */
public enum TicketStatus {

    /** 待处理 —— 工单初始状态 */
    PENDING,
    /** 处理中 —— 已分配处理人 */
    PROCESSING,
    /** 已解决 —— 处理完成等待确认 */
    RESOLVED,
    /** 已关闭 —— 工单关闭 */
    CLOSED;

    /**
     * 状态机迁移表：当前状态 -> 允许的下一状态集合。
     * <p>
     * 用 {@link EnumSet} 存 readonly view —— 内部 hash 实现，{@code contains} 是 O(1)。
     * <p>
     * 简化原则：当前 ticket 05 只定义迁移规则，{@code canTransitTo} 由 Service 校验；
     * ticket 06 会在此基础上落 {@code changeStatus} / {@code assign} / {@code close} API。
     */
    private static final java.util.Map<TicketStatus, Set<TicketStatus>> TRANSITIONS = java.util.Map.of(
            PENDING,    EnumSet.of(PROCESSING, CLOSED),
            PROCESSING, EnumSet.of(RESOLVED, CLOSED),
            RESOLVED,   EnumSet.of(CLOSED),
            CLOSED,     EnumSet.noneOf(TicketStatus.class)
    );

    /**
     * 是否允许从 {@code from} 状态迁移到 {@code to} 状态（静态集中校验入口）。
     * <p>
     * <b>对应 ticket 06 AC #2</b>：原文要求 {@code "Static method TicketStatus.canTransitTo(TicketStatus next) returns boolean"}。
     * 严格静态化（仅接 {@code next}）需要绑定一个隐式 {@code from}，与"集中校验每个 from × to 对"的语义不符。
     * 这里折中：核心判定为 {@code static canTransitTo(from, to)}（AC 字面"static"），
     * 同时保留实例方法 {@code #canTransitTo(next)} 作为便捷调用，自动绑定 {@code this}。
     * <p>
     * 这样：
     * <ul>
     *     <li>{@code TicketStatus.canTransitTo(PENDING, PROCESSING)} —— 字面 AC 的静态调用</li>
     *     <li>{@code TicketStatus.PENDING.canTransitTo(PROCESSING)} —— 便捷实例调用</li>
     * </ul>
     *
     * @param from 当前状态；{@code null} 一律非法
     * @param to   目标状态；{@code null} 一律非法
     * @return 合法返回 true；非法返回 false
     */
    public static boolean canTransitTo(TicketStatus from, TicketStatus to) {
        if (from == null || to == null) {
            return false;
        }
        return TRANSITIONS.get(from).contains(to);
    }

    /**
     * 实例便捷调用 —— 自动绑定 {@code this} 为 {@code from}，等价于
     * {@code canTransitTo(this, next)}。
     *
     * @param next 目标状态
     * @return 合法返回 true；非法返回 false
     */
    public boolean canTransitTo(TicketStatus next) {
        return canTransitTo(this, next);
    }

    /**
     * 校验迁移合法性，非法时抛业务异常。
     * <p>
     * 用法：Service 层在更新 status 前调用本方法，非法直接抛
     * {@code TICKET_INVALID_TRANSITION}，避免在每个变更点重复写 if。
     *
     * @param next 目标状态
     * @throws BusinessException {@code T0102} 当迁移不合法
     */
    public void requireTransitTo(TicketStatus next) {
        if (!canTransitTo(next)) {
            throw BusinessException.of(BusinessExceptionCode.TICKET_INVALID_TRANSITION,
                    "工单状态不允许从 " + this + " 迁移到 " + next);
        }
    }
}
