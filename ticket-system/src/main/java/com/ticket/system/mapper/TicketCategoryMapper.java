package com.ticket.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ticket.system.entity.TicketCategory;
import org.apache.ibatis.annotations.Mapper;

/**
 * {@code ticket_category} 数据访问（ticket 04）。
 * <p>
 * CRUD 由 {@link BaseMapper} 提供。
 */
@Mapper
public interface TicketCategoryMapper extends BaseMapper<TicketCategory> {
}
