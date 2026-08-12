package com.ticket.ticket.service.impl;

import com.ticket.ticket.enums.TicketStatus;
import com.ticket.ticket.mapper.TicketStatsMapper;
import com.ticket.ticket.service.TicketStatsService;
import com.ticket.ticket.vo.PriorityCountVO;
import com.ticket.ticket.vo.StatsSummaryVO;
import com.ticket.ticket.vo.TopHandlerVO;
import com.ticket.ticket.vo.TrendItemVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link TicketStatsServiceImpl} 单元测试（ticket 10 AC）。
 * <p>
 * 纯 Mockito 单测，验证 Service 把 mapper 的 {@code Map<String,Object>} 转换为强类型 VO，
 * 并完成聚合 + 兜底（缺失状态补 0、空集合 → 空列表）。
 * <p>
 * <b>不在范围</b>：Redis 缓存交互（集成测试覆盖）—— 本单测构造不带 Redis 的 service，
 * 保证业务逻辑可独立验证；缓存层在 production 实现里另算一个 seam。
 *
 * <p><b>关于 cache 隔离</b>：{@link TicketStatsServiceImpl} 接收 {@code ObjectMapper}
 * + {@code RedisTemplate}；本测试不构造这两个（用 {@code null}），仅当 service 内部不
 * 触碰缓存时才不 NPE。所以本单测覆盖业务聚合路径，缓存路径由 {@code StatsCacheIntegrationTest}
 * 用 Testcontainers Redis 端到端覆盖。
 */
@ExtendWith(MockitoExtension.class)
class TicketStatsServiceImplTest {

    @Mock TicketStatsMapper ticketStatsMapper;

    TicketStatsService service;

    @BeforeEach
    void setUp() {
        // null 注入 Redis / ObjectMapper：本测试只跑"无缓存"路径；
        // 缓存 key/value 序列化 / TTL 由集成测试覆盖
        service = new TicketStatsServiceImpl(ticketStatsMapper, null, null);
    }

    // ==================== summary ====================

    @Test
    @DisplayName("summary：4 状态全有 → total = 4 状态之和")
    void summary_allFourStatuses() {
        when(ticketStatsMapper.countByStatus()).thenReturn(List.of(
                row("status", "PENDING", "cnt", 5L),
                row("status", "PROCESSING", "cnt", 3L),
                row("status", "RESOLVED", "cnt", 10L),
                row("status", "CLOSED", "cnt", 2L)));

        StatsSummaryVO result = service.summary();

        assertThat(result.getPending()).isEqualTo(5);
        assertThat(result.getProcessing()).isEqualTo(3);
        assertThat(result.getResolved()).isEqualTo(10);
        assertThat(result.getClosed()).isEqualTo(2);
        assertThat(result.getTotal()).isEqualTo(20);
    }

    @Test
    @DisplayName("summary：缺失状态补 0（PENDING 没有 → pending=0）")
    void summary_missingStatusDefaultsToZero() {
        when(ticketStatsMapper.countByStatus()).thenReturn(List.of(
                row("status", "PROCESSING", "cnt", 1L),
                row("status", "RESOLVED", "cnt", 2L)));

        StatsSummaryVO result = service.summary();

        assertThat(result.getPending()).isZero();
        assertThat(result.getClosed()).isZero();
        assertThat(result.getProcessing()).isEqualTo(1);
        assertThat(result.getResolved()).isEqualTo(2);
        assertThat(result.getTotal()).isEqualTo(3);
    }

    @Test
    @DisplayName("summary：空集合 → 全部 0")
    void summary_emptyResult_allZero() {
        when(ticketStatsMapper.countByStatus()).thenReturn(Collections.emptyList());

        StatsSummaryVO result = service.summary();

        assertThat(result.getPending()).isZero();
        assertThat(result.getProcessing()).isZero();
        assertThat(result.getResolved()).isZero();
        assertThat(result.getClosed()).isZero();
        assertThat(result.getTotal()).isZero();
    }

    @Test
    @DisplayName("summary：未知 status 值 → 静默忽略（不抛错）")
    void summary_unknownStatus_silentlyIgnored() {
        when(ticketStatsMapper.countByStatus()).thenReturn(List.of(
                row("status", "PENDING", "cnt", 1L),
                row("status", "WHATEVER_NEW_STATUS", "cnt", 999L)));

        StatsSummaryVO result = service.summary();

        // 未知 status 不计入 4 个标准字段；total = 仅 PENDING = 1
        assertThat(result.getPending()).isEqualTo(1);
        assertThat(result.getTotal()).isEqualTo(1);
    }

    // ==================== trend ====================

    @Test
    @DisplayName("trend(7)：返回 7 个元素，按日期升序；缺失日期补 0")
    void trend_sevenDays_fillsMissingDates() {
        LocalDate today = LocalDate.now();
        when(ticketStatsMapper.countDailyTrend(anyOfDate(), anyOfDate())).thenReturn(List.of(
                row("date", today.minusDays(3).toString(), "cnt", 5L),
                row("date", today.minusDays(1).toString(), "cnt", 2L),
                row("date", today.toString(), "cnt", 7L)));

        List<TrendItemVO> result = service.trend(7);

        assertThat(result).hasSize(7);
        // 第一项应该是 today-6，最后一项是 today
        assertThat(result.get(0).getDate()).isEqualTo(today.minusDays(6));
        assertThat(result.get(6).getDate()).isEqualTo(today);
        // 检查缺失日期 count=0
        assertThat(result.get(0).getCount()).isZero();
        // 检查有数据日期
        TrendItemVO dayMinus3 = result.get(3); // today-3
        assertThat(dayMinus3.getCount()).isEqualTo(5);
        TrendItemVO dayMinus1 = result.get(5);
        assertThat(dayMinus1.getCount()).isEqualTo(2);
        TrendItemVO today2 = result.get(6);
        assertThat(today2.getCount()).isEqualTo(7);
    }

    @Test
    @DisplayName("trend(0)：days 非法 → 兜底到 7")
    void trend_zeroDays_defaultsToSeven() {
        when(ticketStatsMapper.countDailyTrend(anyOfDate(), anyOfDate())).thenReturn(Collections.emptyList());

        List<TrendItemVO> result = service.trend(0);

        assertThat(result).hasSize(7);
    }

    @Test
    @DisplayName("trend(100)：days 上限 → 兜底到 7")
    void trend_tooManyDays_defaultsToSeven() {
        when(ticketStatsMapper.countDailyTrend(anyOfDate(), anyOfDate())).thenReturn(Collections.emptyList());

        List<TrendItemVO> result = service.trend(100);

        assertThat(result).hasSize(7);
    }

    // ==================== byPriority ====================

    @Test
    @DisplayName("byPriority：返回固定 3 项（HIGH/MEDIUM/LOW），缺失项补 0")
    void byPriority_returnsThreeItemsAlways() {
        when(ticketStatsMapper.countByPriority()).thenReturn(List.of(
                row("priority", "HIGH", "cnt", 3L),
                row("priority", "LOW", "cnt", 5L)));

        List<PriorityCountVO> result = service.byPriority();

        assertThat(result).hasSize(3);
        // 必须包含 3 个 priority（顺序按 spec）
        assertThat(result).extracting(PriorityCountVO::getPriority)
                .containsExactly("HIGH", "MEDIUM", "LOW");
        assertThat(result.get(0).getCount()).isEqualTo(3);
        assertThat(result.get(1).getCount()).isZero(); // MEDIUM 缺失
        assertThat(result.get(2).getCount()).isEqualTo(5);
    }

    @Test
    @DisplayName("byPriority：mapper 返回空 → 3 项全 0")
    void byPriority_emptyReturnsThreeZeros() {
        when(ticketStatsMapper.countByPriority()).thenReturn(Collections.emptyList());

        List<PriorityCountVO> result = service.byPriority();

        assertThat(result).hasSize(3);
        assertThat(result).extracting(PriorityCountVO::getCount).containsExactly(0L, 0L, 0L);
    }

    // ==================== topHandlers ====================

    @Test
    @DisplayName("topHandlers(3)：返回 limit 条，按 resolvedCount 降序")
    void topHandlers_returnsTopN() {
        when(ticketStatsMapper.topHandlersByResolved(3)).thenReturn(List.of(
                row("handlerId", 7L, "handlerName", "alice", "resolvedCount", 10L),
                row("handlerId", 3L, "handlerName", "bob", "resolvedCount", 7L),
                row("handlerId", 5L, "handlerName", "carol", "resolvedCount", 2L)));

        List<TopHandlerVO> result = service.topHandlers(3);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).getHandlerId()).isEqualTo(7L);
        assertThat(result.get(0).getHandlerName()).isEqualTo("alice");
        assertThat(result.get(0).getResolvedCount()).isEqualTo(10L);
        assertThat(result.get(2).getResolvedCount()).isEqualTo(2L);
        verify(ticketStatsMapper, times(1)).topHandlersByResolved(3);
    }

    @Test
    @DisplayName("topHandlers(0)：limit 非法 → 兜底到 10")
    void topHandlers_zeroLimit_defaultsToTen() {
        when(ticketStatsMapper.topHandlersByResolved(10)).thenReturn(Collections.emptyList());

        List<TopHandlerVO> result = service.topHandlers(0);

        assertThat(result).isEmpty();
        verify(ticketStatsMapper).topHandlersByResolved(10);
    }

    @Test
    @DisplayName("topHandlers(100)：limit 过大 → 兜底到 10")
    void topHandlers_tooLargeLimit_defaultsToTen() {
        when(ticketStatsMapper.topHandlersByResolved(10)).thenReturn(Collections.emptyList());

        List<TopHandlerVO> result = service.topHandlers(100);

        verify(ticketStatsMapper).topHandlersByResolved(10);
    }

    // ---------- 辅助 ----------

    /**
     * 构造一个 {@code Map<String, Object>} 行（仿 SQL 查询返回）。
     * 调用方传成对 key/value：{@code row("k1", v1, "k2", v2)}。
     */
    private static Map<String, Object> row(Object... kvPairs) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kvPairs.length; i += 2) {
            m.put((String) kvPairs[i], kvPairs[i + 1]);
        }
        return m;
    }

    /** 任意日期 matcher —— Mockito anyOfLocalDate 不存在，简化为任意 LocalDate */
    private static LocalDate anyOfDate() {
        return org.mockito.ArgumentMatchers.any(LocalDate.class);
    }
}