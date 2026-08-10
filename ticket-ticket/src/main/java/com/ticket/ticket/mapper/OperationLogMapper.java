package com.ticket.ticket.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ticket.ticket.entity.OperationLogRecord;

/**
 * {@code operation_log} 表的 Mapper（ticket 05）。
 * <p>
 * 由 {@code OperationLogAspect} 在 Controller 边界自动切面写入；
 * 读路径（管理页面查操作日志）由后续 ticket 引入。
 */
public interface OperationLogMapper extends BaseMapper<OperationLogRecord> {
}
