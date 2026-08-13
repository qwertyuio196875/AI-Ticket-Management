package com.ticket.ticket.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ticket.ticket.entity.TaskExecutionLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * {@code task_execution_log} 数据访问（ticket 11）。
 * <p>
 * 写入由各定时任务类自行调用 {@code insert}（见 {@link com.ticket.ticket.task.JwtBlacklistCleanupTask}
 * / {@link com.ticket.ticket.task.DailyTicketStatsTask}）。
 */
@Mapper
public interface TaskExecutionLogMapper extends BaseMapper<TaskExecutionLog> {
}