package com.ticket.ticket.task;

import com.ticket.ticket.entity.DailyTicketStats;
import com.ticket.ticket.mapper.DailyTicketStatsMapper;
import com.ticket.ticket.mapper.TicketStatsMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 每日工单统计任务（ticket 11 / ADR-0031）。
 * <p>
 * 每天早上 8 点执行一次，统计"昨日"（{@code LocalDate.now().minusDays(1)}）的
 * 新建工单数 / 已解决工单数 / 平均处理时长，写入 {@code daily_ticket_stats}。
 *
 * <p><b>为什么是"昨日"而不是"当日"？</b>
 * 早 8 点跑日切 —— 当天工单还可能在变，统计昨日更稳，符合 spec "daily ticket stats"语义。
 *
 * <p><b>失败语义</b>：本任务失败需要<b>抛出</b>（{@link TaskExecutionRecorder} 的
 * {@code rethrowOnFailure=true}），让 Spring Task 调度器标记本次 FAILED 并触发监控告警；
 * 同时 {@code task_execution_log} 也落一条 FAILED 行便于事后排查。
 */
@Component
public class DailyTicketStatsTask {

    private static final Logger log = LoggerFactory.getLogger(DailyTicketStatsTask.class);

    private final TicketStatsMapper ticketStatsMapper;
    private final DailyTicketStatsMapper dailyTicketStatsMapper;
    private final TaskExecutionRecorder recorder;

    public DailyTicketStatsTask(TicketStatsMapper ticketStatsMapper,
                                DailyTicketStatsMapper dailyTicketStatsMapper,
                                TaskExecutionRecorder recorder) {
        this.ticketStatsMapper = ticketStatsMapper;
        this.dailyTicketStatsMapper = dailyTicketStatsMapper;
        this.recorder = recorder;
    }

    /**
     * 每天早上 8 点执行一次（cron = "秒 分 时 日 月 周"）。
     */
    @Scheduled(cron = "0 0 8 * * ?")
    public void scheduledRun() {
        run();
    }

    /**
     * 业务入口（公开方便测试与手动调用）。
     * <p>
     * 执行 + 落 {@code task_execution_log} 统一交给 {@link TaskExecutionRecorder}；
     * 失败会向上抛出（{@code rethrowOnFailure=true}），详见类 Javadoc。
     */
    public void run() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        recorder.record(getTaskName(), true, () -> {
            long created = ticketStatsMapper.countCreatedOn(yesterday);
            long resolved = ticketStatsMapper.countResolvedOn(yesterday);
            long avgMinutes = ticketStatsMapper.avgHandleMinutesOn(yesterday);

            DailyTicketStats row = new DailyTicketStats();
            row.setDate(yesterday);
            row.setCreatedCount(created);
            row.setResolvedCount(resolved);
            row.setAvgHandleMinutes(avgMinutes);
            dailyTicketStatsMapper.upsert(row);

            log.info("DailyTicketStatsTask 完成：date={} created={} resolved={} avgMinutes={}",
                    yesterday, created, resolved, avgMinutes);
            return null;
        });
    }

    /**
     * 暴露给测试 / 运维观测：任务 Bean 名。
     */
    public String getTaskName() {
        return "dailyTicketStatsTask";
    }
}