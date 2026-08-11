package com.ticket.ticket.enums;

import com.ticket.common.exception.BusinessException;
import com.ticket.common.exception.BusinessExceptionCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link TicketStatus} 状态机迁移校验（ticket 06 AC 末条 —— 集中校验的核心代码）。
 * <p>
 * 覆盖 ADR-0005 列出的 5 条合法迁移 + 全部非法组合（含 {@code null}），
 * 以及 {@link TicketStatus#requireTransitTo(TicketStatus)} 的异常抛出路径。
 * <p>
 * 纯 JUnit 5 + JUnit Jupiter Params，无 Mockito，启动零成本。
 */
class TicketStatusTest {

    // ---------- 合法迁移（5 条 ADR-0005 全集）----------

    /**
     * 枚举所有"合法迁移 from → to"对。每对都应返回 {@code true}。
     * <p>
     * 这里采用 {@link MethodSource} 而非硬编码 5 个 {@code @Test}，
     * 后续 ADR-0005 调整时只需改 {@code legalTransitions()} 一处。
     */
    @ParameterizedTest(name = "legal: {0} -> {1}")
    @MethodSource("legalTransitions")
    @DisplayName("5 条合法迁移全部通过 canTransitTo")
    void canTransitTo_returns_true_for_all_legal_pairs(TicketStatus from, TicketStatus to) {
        assertThat(from.canTransitTo(to))
                .as("迁移 %s -> %s 应该是合法的（ADR-0005）", from, to)
                .isTrue();
    }

    /**
     * 静态 {@code canTransitTo(from, to)} —— 对应 ticket 06 AC #2 的字面 "Static method"。
     * 行为应与实例方法完全一致。
     */
    @ParameterizedTest(name = "static legal: {0} -> {1}")
    @MethodSource("legalTransitions")
    @DisplayName("静态 canTransitTo(from, to) 与实例方法等价")
    void static_canTransitTo_returns_true_for_all_legal_pairs(TicketStatus from, TicketStatus to) {
        assertThat(TicketStatus.canTransitTo(from, to))
                .as("静态迁移 %s -> %s 应该是合法的（ADR-0005）", from, to)
                .isTrue();
    }

    /**
     * 静态方法 null 入参处理：{@code from} 或 {@code to} 任一为 {@code null} 都应非法。
     */
    @ParameterizedTest
    @EnumSource(TicketStatus.class)
    @DisplayName("静态 canTransitTo(null, X) / (X, null) 一律非法")
    void static_canTransitTo_null_is_always_illegal(TicketStatus status) {
        assertThat(TicketStatus.canTransitTo(null, status)).isFalse();
        assertThat(TicketStatus.canTransitTo(status, null)).isFalse();
        assertThat(TicketStatus.canTransitTo(null, null)).isFalse();
    }

    static Stream<Arguments> legalTransitions() {
        return Stream.of(
                Arguments.of(TicketStatus.PENDING, TicketStatus.PROCESSING),
                Arguments.of(TicketStatus.PENDING, TicketStatus.CLOSED),
                Arguments.of(TicketStatus.PROCESSING, TicketStatus.RESOLVED),
                Arguments.of(TicketStatus.PROCESSING, TicketStatus.CLOSED),
                Arguments.of(TicketStatus.RESOLVED, TicketStatus.CLOSED)
        );
    }

    // ---------- 非法迁移：穷举笛卡尔积 - 合法集合 = 非法集合 ----------

    /**
     * 枚举所有 from × to 的笛卡尔积，再剔除合法集合，得到"非法迁移全集"。
     * <p>
     * <b>为什么要这样写</b>：硬编码若干非法 case 容易漏掉 corner case（如
     * {@code PENDING → PENDING} 自迁）。笛卡尔积能确保覆盖所有 from/to 组合，
     * 未来给 {@link TicketStatus} 加新值时本测试自动覆盖。
     * <p>
     * <b>实现细节</b>：{@code Arguments} 不重写 {@code equals}，直接用 {@code Set<Arguments>}
     * 去重无效。这里改用 {@code String} 编码对 {@code "FROM->TO"}，保证去重生效。
     */
    @ParameterizedTest(name = "illegal: {0} -> {1}")
    @MethodSource("illegalTransitions")
    @DisplayName("所有非合法组合的 canTransitTo 返回 false")
    void canTransitTo_returns_false_for_all_illegal_pairs(TicketStatus from, TicketStatus to) {
        assertThat(from.canTransitTo(to))
                .as("迁移 %s -> %s 应该是非法的（ADR-0005）", from, to)
                .isFalse();
    }

    static Stream<Arguments> illegalTransitions() {
        Set<String> legalKeys = legalTransitions().map(args -> {
            TicketStatus from = (TicketStatus) args.get()[0];
            TicketStatus to = (TicketStatus) args.get()[1];
            return from.name() + "->" + to.name();
        }).collect(java.util.stream.Collectors.toSet());
        return EnumSet.allOf(TicketStatus.class).stream()
                .flatMap(from -> EnumSet.allOf(TicketStatus.class).stream()
                        .map(to -> Arguments.of(from, to)))
                .filter(args -> {
                    TicketStatus from = (TicketStatus) args.get()[0];
                    TicketStatus to = (TicketStatus) args.get()[1];
                    return !legalKeys.contains(from.name() + "->" + to.name());
                });
    }

    /**
     * 关键 corner case —— "原地自迁"必须非法（ADR-0005 没列）。
     * 单独拎出来便于排错定位。
     */
    @ParameterizedTest
    @EnumSource(TicketStatus.class)
    @DisplayName("所有状态的自迁都非法")
    void self_transition_is_always_illegal(TicketStatus status) {
        assertThat(status.canTransitTo(status))
                .as("自迁 %s -> %s 应当非法", status, status)
                .isFalse();
    }

    /**
     * {@code CLOSED} 是终态 —— 从 CLOSED 出发的所有迁移都非法。
     * 这是面试常考的"工单关闭后能不能重开"问题，
     * ADR-0005 已显式否决（删掉 RESOLVED 7 天重开）。
     */
    @ParameterizedTest
    @EnumSource(TicketStatus.class)
    @DisplayName("CLOSED 是终态，从 CLOSED 出发的所有迁移都非法")
    void closed_is_terminal(TicketStatus to) {
        assertThat(TicketStatus.CLOSED.canTransitTo(to))
                .as("CLOSED -> %s 应当非法（CLOSED 是终态）", to)
                .isFalse();
    }

    /**
     * {@code null} 目标状态必须非法 —— Service 层 {@code requireTransitTo} 也据此抛异常。
     */
    @ParameterizedTest
    @EnumSource(TicketStatus.class)
    @DisplayName("canTransitTo(null) 一律非法")
    void canTransitTo_null_is_always_illegal(TicketStatus from) {
        assertThat(from.canTransitTo(null)).isFalse();
    }

    // ---------- requireTransitTo 抛异常路径 ----------

    /**
     * 非法迁移 → 抛 {@link BusinessException}，code = {@code T0102}，
     * message 中携带 from / to 便于日志排查。
     */
    @ParameterizedTest(name = "require: {0} -> {1} 抛 T0102")
    @MethodSource("illegalTransitions")
    @DisplayName("非法迁移走 requireTransitTo 抛 T0102")
    void requireTransitTo_throws_T0102_for_illegal(TicketStatus from, TicketStatus to) {
        assertThatThrownBy(() -> from.requireTransitTo(to))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(BusinessExceptionCode.TICKET_INVALID_TRANSITION.getCode());
    }

    /**
     * 合法迁移 → 不抛异常，正常返回。
     */
    @ParameterizedTest(name = "require: {0} -> {1} 不抛异常")
    @MethodSource("legalTransitions")
    @DisplayName("合法迁移走 requireTransitTo 不抛异常")
    void requireTransitTo_does_not_throw_for_legal(TicketStatus from, TicketStatus to) {
        // 不抛异常 = 通过
        from.requireTransitTo(to);
    }
}
