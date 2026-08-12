package com.ticket.ticket.service;

import com.ticket.ticket.vo.PriorityCountVO;
import com.ticket.ticket.vo.StatsSummaryVO;
import com.ticket.ticket.vo.TopHandlerVO;
import com.ticket.ticket.vo.TrendItemVO;

import java.util.List;

/**
 * 工单统计聚合 Service（ticket 10 / ADR-0033）。
 * <p>
 * 4 个聚合方法对应 Dashboard 4 张图（饼图 / 趋势线 / 优先级柱 / TOP 处理人）。
 * 每个方法被 Redis 缓存 5 min（{@code stats:tickets:{endpoint}:{paramsHash}}），
 * 缓存与 mapper 解耦——单元测试可纯 Mockito 注入 mapper，验证业务逻辑。
 * <p>
 * 该接口存在的意义：
 * <ul>
 *     <li>聚合 SQL 与 Controller 边界分明：Service 把 {@code Map<String,Object>} 转换为强类型 VO</li>
 *     <li>缓存策略（5min TTL）集中在 Service，调用方（StatsController）不感知</li>
 *     <li>单测 seam：mock mapper 直接断言聚合结果</li>
 * </ul>
 */
public interface TicketStatsService {

    /**
     * 状态分布 + 总数（饼图）。
     */
    StatsSummaryVO summary();

    /**
     * 近 N 天新建工单趋势（折线图）。
     *
     * @param days 1 ~ 30；超出范围由 Service 兜底到默认区间
     */
    List<TrendItemVO> trend(int days);

    /**
     * 按优先级统计（柱状图）。
     * <p>
     * 始终返回完整 3 项（{@code HIGH / MEDIUM / LOW}），缺失项补 0，
     * 便于前端固定渲染 3 个柱子。
     */
    List<PriorityCountVO> byPriority();

    /**
     * TOP N 处理人按已解决工单数排序（柱状图）。
     *
     * @param limit 1 ~ 50；超出范围兜底到 10
     */
    List<TopHandlerVO> topHandlers(int limit);
}