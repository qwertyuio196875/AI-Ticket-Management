package com.ticket.ticket.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工单状态分布聚合 VO（ticket 10 / ADR-0033）。
 * <p>
 * 对应端点 {@code GET /api/v1/stats/tickets/summary} 的响应 body。
 * 直接喂给 ECharts 饼图：{@code {value: pending, name: '待处理'}} × 4 状态 + total。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StatsSummaryVO {

    private long pending;
    private long processing;
    private long resolved;
    private long closed;
    private long total;
}