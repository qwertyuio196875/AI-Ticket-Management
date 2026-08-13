package com.ticket.ticket.enums;

/**
 * 定时任务执行状态（ticket 11 / ADR-0031）。
 * <p>
 * 枚举名直接作为数据库 {@code task_execution_log.status} 字段值，
 * 不做翻译层（避免无谓的枚举映射代码）。
 *
 * <p><b>不在范围</b>：{@code RUNNING} 状态 —— 本项目只在结束时落日志
 * （开始 / 结束时间戳均由任务自身在 try/catch 内记录），不维护中间态。
 */
public enum TaskExecutionStatus {

    /** 执行成功 */
    SUCCESS,

    /** 执行失败（含业务异常 / 系统异常） */
    FAILED
}