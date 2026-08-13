package com.ticket.ticket.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ticket.ticket.entity.DailyTicketStats;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * {@code daily_ticket_stats} 数据访问（ticket 11）。
 * <p>
 * 写路径：{@link #upsert(DailyTicketStats)} —— MySQL 走
 * {@code INSERT ... ON DUPLICATE KEY UPDATE}，H2 走 {@code MERGE INTO}，
 * 二者语法在 XML 里分支（见 {@code mapper/DailyTicketStatsMapper.xml}）。
 * 同一日期多次执行不会抛唯一索引冲突。
 */
@Mapper
public interface DailyTicketStatsMapper extends BaseMapper<DailyTicketStats> {

    /**
     * 插入或更新（按 {@code date} 唯一键匹配）。
     * <p>
     * 重复日期时覆盖 {@code created_count} / {@code resolved_count} /
     * {@code avg_handle_minutes}，便于手动重跑任务时纠正数据。
     *
     * @param row 待写入的统计数据
     */
    void upsert(DailyTicketStats row);
}