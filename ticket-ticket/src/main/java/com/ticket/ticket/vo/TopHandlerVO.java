package com.ticket.ticket.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 处理人 TOP N 聚合 VO（ticket 10 / ADR-0033）。
 * <p>
 * 对应端点 {@code GET /api/v1/stats/tickets/top-handlers?limit=N} 的响应元素。
 * 按 {@code resolvedCount} 降序排列；{@code handlerName} 由 SQL JOIN {@code sys_user} 拼装。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "TOP 处理人统计结果")
public class TopHandlerVO {

    private Long handlerId;
    private String handlerName;
    private long resolvedCount;
}