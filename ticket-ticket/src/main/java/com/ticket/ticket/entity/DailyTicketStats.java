package com.ticket.ticket.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 每日工单统计 {@code daily_ticket_stats}（ticket 11 / ADR-0031）。
 * <p>
 * 字段对齐 ticket 11 AC：
 * <ul>
 *     <li>{@code date} —— 统计日期（{@code (date)} 唯一索引防重复落库）</li>
 *     <li>{@code createdCount} / {@code resolvedCount} —— 当日新建 / 已解决数（{@code is_deleted = 0}）</li>
 *     <li>{@code avgHandleMinutes} —— 平均处理时长（分钟，向下取整）；
 *         ticket 创建时间 → 第一次 STATUS_CHANGED → RESOLVED 的分钟差，
 *         多张工单取平均</li>
 * </ul>
 *
 * <p><b>不在范围</b>：按优先级 / 分类 / 处理人拆解 —— 当日维度只 4 个核心字段，
 * 详细拆解交给 ticket 10 的 Dashboard 实时查询。
 */
@Data
@TableName("daily_ticket_stats")
public class DailyTicketStats {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 统计日期（精确到日） */
    private LocalDate date;

    /** 当日新建工单数 */
    private Long createdCount;

    /** 当日已解决工单数 */
    private Long resolvedCount;

    /** 平均处理时长（分钟，向下取整） */
    private Long avgHandleMinutes;

    /** 落库时间 —— DB 默认 CURRENT_TIMESTAMP */
    private LocalDateTime createTime;
}