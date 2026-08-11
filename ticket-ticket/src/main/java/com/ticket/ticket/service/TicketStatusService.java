package com.ticket.ticket.service;

import com.ticket.ticket.enums.TicketStatus;

/**
 * 工单状态机服务（ticket 06 落地）。
 * <p>
 * <b>职责边界</b>：所有工单状态变更、分配、关闭操作必须走本接口，
 * 由 {@code TicketStatusServiceImpl.changeStatus} 统一收口 —— ADR-0005 +
 * ADR-0012 的核心约束。Controller 与其他 Service（{@code TicketInfoService}）
 * 不得直接 mapper.updateById + 写 {@code ticket_log}，避免绕过
 * {@code canTransitTo} 集中校验或破坏事务原子性。
 * <p>
 * <b>事务边界</b>：每个公开方法都标了 {@code @Transactional}，
 * {@code ticket_info} 与 {@code ticket_log} 走同一事务 —— 缺一日志等于业务错误
 * （ADR-0012）。
 * <p>
 * <b>权限校验</b>：{@code @PreAuthorize("hasAuthority('xxx')")} 放在 Controller 层；
 * Service 层只校验业务合法性（状态机迁移、handler 存在等）。
 */
public interface TicketStatusService {

    /**
     * 通用状态变更 —— ticket 06 AC 中"状态变更入口"的唯一对外方法。
     * <p>
     * <b>流程</b>：
     * <ol>
     *     <li>加载工单（不存在 / 已软删 → 抛 {@code TICKET_NOT_FOUND}）</li>
     *     <li>{@code currentStatus.requireTransitTo(target)} 校验迁移合法性
     *         （非法 → 抛 {@code TICKET_INVALID_TRANSITION}）</li>
     *     <li>{@code ticketInfoMapper.updateById(...)}</li>
     *     <li>同事务追加 {@code ticket_log(event=STATUS_CHANGED)}</li>
     * </ol>
     *
     * @param ticketId     工单主键
     * @param targetStatus 目标状态
     * @param reason       变更原因（可空，写入 ticket_log.content）
     * @param operatorId   操作人 sys_user.id（来自 SecurityContext）
     * @throws com.ticket.common.exception.BusinessException {@code T0101} 工单不存在；
     *                                                          {@code T0102} 非法迁移
     */
    void changeStatus(Long ticketId, TicketStatus targetStatus, String reason, Long operatorId);

    /**
     * 分配处理人（ticket 06 AC）。
     * <p>
     * <b>流程</b>：
     * <ol>
     *     <li>校验 handlerId 对应 {@code sys_user} 存在且 {@code status = 1}
     *         （不满足 → 抛 {@code USER_NOT_FOUND}）</li>
     *     <li>加载工单（不存在 / 已软删 → 抛 {@code TICKET_NOT_FOUND}）</li>
     *     <li>更新 {@code handler_id}，追加 {@code ticket_log(event=ASSIGNED)}</li>
     *     <li>若当前状态为 {@code PENDING}，触发状态迁移
     *         {@code PENDING → PROCESSING}（同事务追加
     *         {@code ticket_log(event=STATUS_CHANGED)}）</li>
     * </ol>
     * <p>
     * <b>设计取舍</b>：允许在非 PENDING 状态下分配（覆盖处理人），但只有
     * PENDING → PROCESSING 这一条自动状态迁移 —— 其它状态下不强制改状态，
     * 避免覆盖业务当前语义（处理人可能只是换人不改状态）。
     *
     * @param ticketId   工单主键
     * @param handlerId  接手人 sys_user.id
     * @param reason     分配原因（可空）
     * @param operatorId 操作人 sys_user.id
     * @throws com.ticket.common.exception.BusinessException {@code S0101} 处理人不存在或已禁用；
     *                                                          {@code T0101} 工单不存在
     */
    void assign(Long ticketId, Long handlerId, String reason, Long operatorId);

    /**
     * 关闭工单（ticket 06 AC）。
     * <p>
     * 等价于 {@code changeStatus(CLOSED, reason ?? "closed", operatorId)}。
     * 单独成方法有两个目的：
     * <ul>
     *     <li>Controller 端点的语义更清晰（{@code POST /tickets/{id}/close}）</li>
     *     <li>便于审计日志按端点识别（{@code @OperationLog("关闭工单")}）</li>
     * </ul>
     *
     * @param ticketId   工单主键
     * @param reason     关闭原因（可空，缺省时写 "closed"）
     * @param operatorId 操作人 sys.user.id
     */
    void close(Long ticketId, String reason, Long operatorId);
}
