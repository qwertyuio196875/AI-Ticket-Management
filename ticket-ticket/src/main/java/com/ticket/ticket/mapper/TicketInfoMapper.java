package com.ticket.ticket.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ticket.ticket.entity.TicketInfo;

/**
 * {@code ticket_info} 表的 Mapper（ticket 05）。
 * <p>
 * 标准 CRUD 走 MP {@link BaseMapper} 自带方法；
 * 复杂查询（带 JOIN / 聚合）由 XML 或注解 {@code @Select} 在 Service 层组合。
 */
public interface TicketInfoMapper extends BaseMapper<TicketInfo> {
}
