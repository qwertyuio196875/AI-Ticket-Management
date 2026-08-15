package com.ticket.ticket.service;

import com.ticket.ticket.vo.TicketLogVO;

import java.util.List;

/**
 * 工单时间线（{@code ticket_log} 事件流）查询 Service（ticket 14）。
 * <p>
 * 只读接口：时间线事件全部由各业务 Service 在同事务内写入（ADR-0012），
 * 本 Service 仅负责按工单聚合读取并拼装操作人昵称。
 * <p>
 * <b>排序</b>：{@code ORDER BY create_time ASC, id ASC} —— 时间升序、同刻按
 * 落库顺序（id）稳定排序，与前端时间线展示一致。
 */
public interface TicketLogService {

    /**
     * 查询工单时间线事件列表。
     * <p>
     * 流程：
     * <ol>
     *     <li>校验 {@code ticketId} 非空（不满足 → 抛 {@code C0400}）</li>
     *     <li>校验工单存在（不存在 → 抛 {@code T0101}，与详情接口一致）</li>
     *     <li>按 {@code create_time ASC, id ASC} 查询事件流水</li>
     *     <li>批量拼装 {@code operatorName}（{@code sys_user.nickname}，避免 N+1）</li>
     * </ol>
     *
     * @param ticketId 工单主键（来自 path）
     * @return 时间线事件 VO 列表（按时间升序）
     */
    List<TicketLogVO> list(Long ticketId);
}
