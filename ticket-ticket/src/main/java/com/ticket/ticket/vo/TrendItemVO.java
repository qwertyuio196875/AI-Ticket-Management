package com.ticket.ticket.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工单按日趋势聚合 VO（ticket 10 / ADR-0033）。
 * <p>
 * 对应端点 {@code GET /api/v1/stats/tickets/trend?days=N} 的响应元素。
 * 直接喂给 ECharts 折线图：{@code {xAxis: date, series: count}}。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "工单趋势统计结果")
public class TrendItemVO {

    /** 当日（{@code yyyy-MM-dd}） */
    private LocalDate date;

    /** 当日新建工单数 */
    private long count;
}