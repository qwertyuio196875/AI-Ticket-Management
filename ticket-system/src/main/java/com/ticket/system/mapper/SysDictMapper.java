package com.ticket.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ticket.system.entity.SysDict;
import org.apache.ibatis.annotations.Mapper;

/**
 * {@code sys_dict} 数据访问（ticket 04）。
 * <p>
 * CRUD 由 {@link BaseMapper} 提供。
 */
@Mapper
public interface SysDictMapper extends BaseMapper<SysDict> {
}
