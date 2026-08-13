package com.ticket.ticket.task;

import com.ticket.ticket.entity.DailyTicketStats;
import com.ticket.ticket.entity.TaskExecutionLog;
import com.ticket.ticket.enums.TaskExecutionStatus;
import com.ticket.ticket.mapper.DailyTicketStatsMapper;
import com.ticket.ticket.task.TaskExecutionRecorder;
import com.ticket.ticket.mapper.TaskExecutionLogMapper;
import com.ticket.ticket.mapper.TicketStatsMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link DailyTicketStatsTask} 单元测试（ticket 11 AC）。
 * <p>
 * 覆盖 spec 验收点：
 * <ul>
 *     <li>统计"昨日"工单：created_count / resolved_count / avg_handle_minutes</li>
 *     <li>插入 daily_ticket_stats（INSERT IGNORE 语义：重复日期不抛错）</li>
 *     <li>正常完成 → task_execution_log 落 SUCCESS</li>
 *     <li>任意 mapper 抛异常 → 落 FAILED 日志、再次抛出让上层调度感知</li>
 * </ul>
 *
 * <p><b>不在范围</b>：cron 表达式触发（Spring 框架负责）、
 * 真实 SQL 聚合（H2 集成测试覆盖）。
 */
@ExtendWith(MockitoExtension.class)
class DailyTicketStatsTaskTest {

    @Mock TicketStatsMapper ticketStatsMapper;
    @Mock DailyTicketStatsMapper dailyTicketStatsMapper;
    @Mock TaskExecutionLogMapper taskExecutionLogMapper;

    @Test
    void run_computes_yesterday_stats_and_inserts_to_daily_table() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        // mapper 返回昨日新建 / 已解决 / 平均处理时长
        when(ticketStatsMapper.countCreatedOn(any(LocalDate.class))).thenReturn(12L);
        when(ticketStatsMapper.countResolvedOn(any(LocalDate.class))).thenReturn(7L);
        when(ticketStatsMapper.avgHandleMinutesOn(any(LocalDate.class))).thenReturn(45L);

        DailyTicketStatsTask task = new DailyTicketStatsTask(ticketStatsMapper, dailyTicketStatsMapper, new TaskExecutionRecorder(taskExecutionLogMapper));
        task.run();

        // 1. 统计的日期是昨日（不是今天）
        ArgumentCaptor<LocalDate> dateCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(ticketStatsMapper).countCreatedOn(dateCaptor.capture());
        verify(ticketStatsMapper).countResolvedOn(dateCaptor.capture());
        verify(ticketStatsMapper).avgHandleMinutesOn(dateCaptor.capture());
        assertThat(dateCaptor.getAllValues()).allMatch(d -> d.equals(yesterday));

        // 2. daily_ticket_stats 落 1 行
        ArgumentCaptor<DailyTicketStats> rowCaptor = ArgumentCaptor.forClass(DailyTicketStats.class);
        verify(dailyTicketStatsMapper, times(1)).upsert(rowCaptor.capture());
        DailyTicketStats row = rowCaptor.getValue();
        assertThat(row.getDate()).isEqualTo(yesterday);
        assertThat(row.getCreatedCount()).isEqualTo(12);
        assertThat(row.getResolvedCount()).isEqualTo(7);
        assertThat(row.getAvgHandleMinutes()).isEqualTo(45);

        // 3. task_execution_log 落 SUCCESS
        ArgumentCaptor<TaskExecutionLog> logCaptor = ArgumentCaptor.forClass(TaskExecutionLog.class);
        verify(taskExecutionLogMapper, times(1)).insert(logCaptor.capture());
        assertThat(logCaptor.getValue().getStatus()).isEqualTo(TaskExecutionStatus.SUCCESS);
    }

    @Test
    void run_logs_failed_when_mapper_throws_and_reraises() {
        when(ticketStatsMapper.countCreatedOn(any(LocalDate.class)))
                .thenThrow(new RuntimeException("DB 不可达"));

        DailyTicketStatsTask task = new DailyTicketStatsTask(ticketStatsMapper, dailyTicketStatsMapper, new TaskExecutionRecorder(taskExecutionLogMapper));

        // 任务失败必须抛出（让 Spring TaskExecution 标记本次 FAILED），
        // 但同时 task_execution_log 已经落一条 FAILED 记录
        try {
            task.run();
            // 不应走到这里
            assertThat(false).as("任务失败时应抛出").isTrue();
        } catch (RuntimeException expected) {
            assertThat(expected).hasMessageContaining("DB 不可达");
        }

        ArgumentCaptor<TaskExecutionLog> captor = ArgumentCaptor.forClass(TaskExecutionLog.class);
        verify(taskExecutionLogMapper, times(1)).insert(captor.capture());
        TaskExecutionLog row = captor.getValue();
        assertThat(row.getStatus()).isEqualTo(TaskExecutionStatus.FAILED);
        assertThat(row.getErrorMessage()).contains("DB 不可达");
        // daily_ticket_stats 不应被插入（任务失败）
        verify(dailyTicketStatsMapper, times(0)).upsert(any(DailyTicketStats.class));
    }

    @Test
    void run_upsert_failure_logs_failed_but_does_not_swallow() {
        // mapper 查询成功 → upsert 失败 → 任务 FAILED 且抛错
        when(ticketStatsMapper.countCreatedOn(any(LocalDate.class))).thenReturn(0L);
        when(ticketStatsMapper.countResolvedOn(any(LocalDate.class))).thenReturn(0L);
        when(ticketStatsMapper.avgHandleMinutesOn(any(LocalDate.class))).thenReturn(0L);
        doThrow(new RuntimeException("约束冲突")).when(dailyTicketStatsMapper).upsert(any(DailyTicketStats.class));

        DailyTicketStatsTask task = new DailyTicketStatsTask(ticketStatsMapper, dailyTicketStatsMapper, new TaskExecutionRecorder(taskExecutionLogMapper));

        try {
            task.run();
            assertThat(false).as("upsert 失败时应抛出").isTrue();
        } catch (RuntimeException expected) {
            assertThat(expected).hasMessageContaining("约束冲突");
        }

        ArgumentCaptor<TaskExecutionLog> captor = ArgumentCaptor.forClass(TaskExecutionLog.class);
        verify(taskExecutionLogMapper, times(1)).insert(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(TaskExecutionStatus.FAILED);
    }

    @Test
    void getTaskName_returns_bean_name() {
        DailyTicketStatsTask task = new DailyTicketStatsTask(ticketStatsMapper, dailyTicketStatsMapper, new TaskExecutionRecorder(taskExecutionLogMapper));
        assertThat(task.getTaskName()).isEqualTo("dailyTicketStatsTask");
    }

    @Test
    void run_handles_zero_counts() {
        // 边界：昨日没有任何工单（created/resolved/avg 都是 0）
        when(ticketStatsMapper.countCreatedOn(any(LocalDate.class))).thenReturn(0L);
        when(ticketStatsMapper.countResolvedOn(any(LocalDate.class))).thenReturn(0L);
        when(ticketStatsMapper.avgHandleMinutesOn(any(LocalDate.class))).thenReturn(0L);

        DailyTicketStatsTask task = new DailyTicketStatsTask(ticketStatsMapper, dailyTicketStatsMapper, new TaskExecutionRecorder(taskExecutionLogMapper));
        task.run();

        ArgumentCaptor<DailyTicketStats> captor = ArgumentCaptor.forClass(DailyTicketStats.class);
        verify(dailyTicketStatsMapper).upsert(captor.capture());
        DailyTicketStats row = captor.getValue();
        assertThat(row.getCreatedCount()).isZero();
        assertThat(row.getResolvedCount()).isZero();
        assertThat(row.getAvgHandleMinutes()).isZero();
    }
}