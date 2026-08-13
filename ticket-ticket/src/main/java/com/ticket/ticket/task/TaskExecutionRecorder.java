package com.ticket.ticket.task;

import com.ticket.ticket.entity.TaskExecutionLog;
import com.ticket.ticket.enums.TaskExecutionStatus;
import com.ticket.ticket.mapper.TaskExecutionLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.function.Supplier;

/**
 * 定时任务执行记录器（ticket 11 / ADR-0031）。
 * <p>
 * 抽取两个定时任务共享的"计时 + 状态 + 落 {@code task_execution_log}"模板：
 * <ol>
 *     <li>记录 {@code startTime}，执行任务主体（{@link Supplier}）</li>
 *     <li>成功 → {@code SUCCESS}；异常 → {@code FAILED} + error_message（截断 2000 字符）</li>
 *     <li>落 {@code task_execution_log}（落库失败只 log，不二次抛错）</li>
 *     <li>按 {@code rethrowOnFailure} 决定失败是否向上抛出（让 Spring Task 调度感知 / 触发告警）</li>
 * </ol>
 *
 * <p><b>设计取舍</b>：两个任务的失败语义不同 —— {@link JwtBlacklistCleanupTask}
 * 是 housekeeping 不允许单次失败杀死调度链路（{@code rethrowOnFailure=false}）；
 * {@link DailyTicketStatsTask} 需要告警感知（{@code rethrowOnFailure=true}）。
 * 故布尔参数留给调用方决定，不写死。
 */
@Component
public class TaskExecutionRecorder {

    /** 异常 message 截断上限 —— DB 列 {@code error_message VARCHAR(2000)} */
    private static final int MAX_ERROR_MESSAGE = 2000;

    private static final Logger log = LoggerFactory.getLogger(TaskExecutionRecorder.class);

    private final TaskExecutionLogMapper logMapper;

    public TaskExecutionRecorder(TaskExecutionLogMapper logMapper) {
        this.logMapper = logMapper;
    }

    /**
     * 执行任务主体并记录执行日志。
     *
     * @param taskName          任务 Bean 名（写进 {@code task_execution_log.task_name}）
     * @param rethrowOnFailure  失败时是否向上抛出（true = 让 Spring Task 调度标记 FAILED）
     * @param body              任务主体，返回值透传给调用方（如删除的 key 数）
     * @param <T>               任务主体返回类型
     * @return 任务主体返回值；主体抛异常时返回 {@code null}
     */
    public <T> T record(String taskName, boolean rethrowOnFailure, Supplier<T> body) {
        LocalDateTime start = LocalDateTime.now();
        TaskExecutionStatus status = TaskExecutionStatus.SUCCESS;
        String errorMessage = null;
        T result = null;
        try {
            result = body.get();
            log.info("{} 执行完成", taskName);
        } catch (Exception ex) {
            status = TaskExecutionStatus.FAILED;
            errorMessage = truncate(ex.getClass().getSimpleName() + ": " + ex.getMessage());
            log.error("{} 执行失败", taskName, ex);
        }
        persistLogSafely(taskName, start, LocalDateTime.now(), status, errorMessage);
        if (status == TaskExecutionStatus.FAILED && rethrowOnFailure) {
            throw new IllegalStateException(taskName + " failed: " + errorMessage);
        }
        return result;
    }

    /**
     * 落 {@code task_execution_log}。落库失败只 log，不向调用方抛出 ——
     * 任务主体已结束，再抛会让调度误判 / 覆盖原有状态。
     */
    private void persistLogSafely(String taskName, LocalDateTime start, LocalDateTime end,
                                  TaskExecutionStatus status, String errorMessage) {
        try {
            TaskExecutionLog row = new TaskExecutionLog();
            row.setTaskName(taskName);
            row.setStartTime(start);
            row.setEndTime(end);
            row.setStatus(status);
            row.setErrorMessage(errorMessage);
            logMapper.insert(row);
        } catch (Exception ex) {
            log.error("{} 落 task_execution_log 失败", taskName, ex);
        }
    }

    private static String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() <= MAX_ERROR_MESSAGE ? s : s.substring(0, MAX_ERROR_MESSAGE);
    }
}