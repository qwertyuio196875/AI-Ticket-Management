package com.ticket.ticket.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工单按优先级聚合 VO（ticket 10 / ADR-0033）。
 * <p>
 * 对应端点 {@code GET /api/v1/stats/tickets/by-priority} 的响应元素。
 * 直接喂给 ECharts 柱状图：{@code {xAxis: priority, series: count}}。
 * <p>
 * {@code priority} 取 {@code sys_dict(dict_type='priority').dict_value}（HIGH / MEDIUM / LOW）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "优先级统计结果")
public class PriorityCountVO {

    private String priority;
    private long count;
}