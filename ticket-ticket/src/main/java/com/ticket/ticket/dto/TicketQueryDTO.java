package com.ticket.ticket.dto;

import com.ticket.ticket.enums.TicketStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import lombok.Data;

/**
 * 工单分页查询参数（ticket 05 AC：{@code GET — paginated list with filters}）。
 * <p>
 * 全部筛选条件均可空 —— null 字段不参与 SQL 过滤。
 * <p>
 * <b>当前 ticket 05 范围</b>：
 * <ul>
 *     <li>status / priority / type / handlerId —— 直接 where 等值</li>
 *     <li>dateFrom / dateTo —— 闭区间，按 {@code create_time} 过滤（{@code dateFrom} 00:00:00 起，
 *         {@code dateTo} 当天 23:59:59 止）</li>
 * </ul>
 * <p>
 * 不在范围（留后续 ticket）：关键词搜索（{@code title} / {@code content} 模糊查询 —— ticket 09+）。
 */
@Data
@Schema(description = "工单分页查询参数")
public class TicketQueryDTO {

    /** 页码，从 1 起，默认 1 */
    private Long pageNum = 1L;

    /** 每页条数，默认 20 */
    private Long pageSize = 20L;

    /** 状态过滤，可空 */
    private TicketStatus status;

    /** 优先级过滤（HIGH / MEDIUM / LOW），可空 */
    private String priority;

    /** 分类过滤，可空 */
    private String type;

    /** 处理人 id 过滤，可空（ticket 06 后主要用于"我的工单"） */
    private Long handlerId;

    /** 创建时间起点（含），可空 */
    private LocalDate dateFrom;

    /** 创建时间终点（含），可空 —— Service 内部补 23:59:59 */
    private LocalDate dateTo;
}
