package com.ticket.ticket.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ticket.ticket.enums.TaskExecutionStatus;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 定时任务执行日志 {@code task_execution_log}（ticket 11 / ADR-0031）。
 * <p>
 * 字段对齐 ticket 11 AC：
 * <ul>
 *     <li>{@code taskName} —— Spring Bean 名（如 {@code jwtBlacklistCleanupTask}）</li>
 *     <li>{@code startTime} / {@code endTime} —— 由任务在 try/catch 内记录</li>
 *     <li>{@code status} —— {@link TaskExecutionStatus} 枚举（{@code SUCCESS} / {@code FAILED}），枚举名持久化</li>
 *     <li>{@code errorMessage} —— 失败时为 {@code e.getClass().getSimpleName() + ": " + e.getMessage()} 截断 2000 字符</li>
 * </ul>
 *
 * <p><b>不在范围</b>：{@code created_by} / {@code updated_by} 等运维审计字段；
 * 本表由系统自身写入，无操作人概念。
 */
@Data
@TableName("task_execution_log")
public class TaskExecutionLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** Spring Bean 名（如 {@code jwtBlacklistCleanupTask}） */
    private String taskName;

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 结束时间 */
    private LocalDateTime endTime;

    /** 执行状态 —— 枚举名持久化（SUCCESS / FAILED），避免字符串漂移 */
    private TaskExecutionStatus status;

    /** 失败时的异常摘要（截断 2000 字符） */
    private String errorMessage;

    /** 落库时间 —— DB 默认 CURRENT_TIMESTAMP */
    private LocalDateTime createTime;
}