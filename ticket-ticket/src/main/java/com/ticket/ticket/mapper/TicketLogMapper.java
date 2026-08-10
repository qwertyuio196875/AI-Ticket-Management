package com.ticket.ticket.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ticket.ticket.entity.TicketLog;

/**
 * {@code ticket_log} 表的 Mapper（ticket 05）。
 * <p>
 * 工单业务事件流水表 —— 全部写入由 Service 在业务事务内追加。
 * 读路径（事件流展示）由 ticket 06+ 引入，本接口先留空。
 */
public interface TicketLogMapper extends BaseMapper<TicketLog> {
}
