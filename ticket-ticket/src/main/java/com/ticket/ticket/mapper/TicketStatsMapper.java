package com.ticket.ticket.mapper;

import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 工单统计聚合 Mapper（ticket 10 / ADR-0033）。
 * <p>
 * 用 XML 写 SQL（{@code mapper/TicketStatsMapper.xml}）—— 聚合查询涉及 GROUP BY / JOIN，
 * 注解 SQL 难维护；XML 也方便 review EXPLAIN 执行计划。
 * <p>
 * <b>索引复用</b>（来自 ticket 09 / 复合索引）：
 * <ul>
 *     <li>{@link #countByStatus()} —— 全表扫描 + GROUP BY status；规模小时可接受</li>
 *     <li>{@link #countDailyTrend(LocalDate, LocalDate)} —— 走
 *         {@code idx_ticket_info_create_time}（单列）</li>
 *     <li>{@link #countByPriority()} —— 全表扫描 + GROUP BY priority</li>
 *     <li>{@link #topHandlersByResolved(int)} —— 走
 *         {@code idx_ticket_info_status_handler_createtime}
 *         （status='RESOLVED' + handler_id + create_time 复合索引）</li>
 * </ul>
 */
public interface TicketStatsMapper {

    /**
     * 按状态分组统计工单数（不限软删状态以外的）。
     * <p>
     * 返回 Map：key = status 枚举名（{@code PENDING/PROCESSING/RESOLVED/CLOSED}），
     * value = 数量。未出现的状态不会出现在 map 里（Service 层兜底 0）。
     */
    List<Map<String, Object>> countByStatus();

    /**
     * 按日统计新建工单数（{@code is_deleted=0}）。
     *
     * @param from 起始日（含）
     * @param to   结束日（含）
     * @return 列表元素 Map：key = {@code date} (yyyy-MM-dd 字符串)、{@code count} (Long)
     */
    List<Map<String, Object>> countDailyTrend(@Param("from") LocalDate from,
                                               @Param("to") LocalDate to);

    /**
     * 按优先级分组统计（不限软删状态以外的）。
     */
    List<Map<String, Object>> countByPriority();

    /**
     * TOP N 处理人按已解决工单数排序。
     * <p>
     * JOIN {@code sys_user} 拿昵称；仅统计 {@code status = 'RESOLVED'} 且
     * {@code handler_id IS NOT NULL} 的工单。
     *
     * @param limit 限制条数（&gt; 0）
     * @return Map 列表：key = {@code handlerId / handlerName / resolvedCount}
     */
    List<Map<String, Object>> topHandlersByResolved(@Param("limit") int limit);
}