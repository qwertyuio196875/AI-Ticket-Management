package com.ticket.web.ticket;

import com.ticket.common.result.Result;
import com.ticket.ticket.service.TicketStatsService;
import com.ticket.ticket.vo.PriorityCountVO;
import com.ticket.ticket.vo.StatsSummaryVO;
import com.ticket.ticket.vo.TopHandlerVO;
import com.ticket.ticket.vo.TrendItemVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 工单统计 Dashboard 接口（ticket 10 / ADR-0033）。
 * <p>
 * 4 个端点对应 ECharts Dashboard 4 张图：
 * <ul>
 *     <li>{@code GET /api/v1/stats/tickets/summary}        —— 状态分布 + 总数（饼图）</li>
 *     <li>{@code GET /api/v1/stats/tickets/trend?days=7}   —— 近 N 天新建工单趋势（折线图）</li>
 *     <li>{@code GET /api/v1/stats/tickets/by-priority}    —— 优先级分布（柱状图）</li>
 *     <li>{@code GET /api/v1/stats/tickets/top-handlers?limit=10} —— TOP N 处理人（柱状图）</li>
 * </ul>
 *
 * <p><b>权限</b>：统一 {@code stats:view} 权限点；admin + agent 都可访问（见 sys_menu seed）。
 *
 * <p><b>不在本 Controller 范围</b>：
 * <ul>
 *     <li>缓存策略（5min TTL）—— Service 层实现，Controller 不感知</li>
 *     <li>SQL 聚合（MySQL GROUP BY + 索引）—— Mapper XML</li>
 *     <li>数据权限（按部门过滤）—— spec 明确不做</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/stats/tickets")
@Tag(name = "stats", description = "数据统计：Dashboard 4 张图")
public class StatsController {

    private final TicketStatsService ticketStatsService;

    public StatsController(TicketStatsService ticketStatsService) {
        this.ticketStatsService = ticketStatsService;
    }

    /**
     * 状态分布 + 总数。
     */
    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('stats:view')")
    @Operation(summary = "工单状态分布 + 总数")
    public Result<StatsSummaryVO> summary() {
        return Result.success(ticketStatsService.summary());
    }

    /**
     * 近 N 天新建工单趋势。
     *
     * @param days 天数（1 ~ 30；非法值兜底到 7）
     */
    @GetMapping("/trend")
    @PreAuthorize("hasAuthority('stats:view')")
    @Operation(summary = "近 N 天新建工单趋势")
    public Result<List<TrendItemVO>> trend(@RequestParam(defaultValue = "7") int days) {
        return Result.success(ticketStatsService.trend(days));
    }

    /**
     * 按优先级统计（HIGH / MEDIUM / LOW，固定顺序，缺失补 0）。
     */
    @GetMapping("/by-priority")
    @PreAuthorize("hasAuthority('stats:view')")
    @Operation(summary = "按优先级统计工单数")
    public Result<List<PriorityCountVO>> byPriority() {
        return Result.success(ticketStatsService.byPriority());
    }

    /**
     * TOP N 处理人按已解决工单数排序。
     *
     * @param limit 限制条数（1 ~ 50；非法值兜底到 10）
     */
    @GetMapping("/top-handlers")
    @PreAuthorize("hasAuthority('stats:view')")
    @Operation(summary = "TOP N 处理人按已解决工单数排序")
    public Result<List<TopHandlerVO>> topHandlers(@RequestParam(defaultValue = "10") int limit) {
        return Result.success(ticketStatsService.topHandlers(limit));
    }
}