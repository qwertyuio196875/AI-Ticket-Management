package com.ticket.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ticket.system.entity.SysRole;
import org.apache.ibatis.annotations.Mapper;

/**
 * {@code sys_role} 数据访问。
 * <p>
 * CRUD 由 {@link BaseMapper} 提供；自定义 SQL 在此追加（ticket 03 阶段无）。
 */
@Mapper
public interface SysRoleMapper extends BaseMapper<SysRole> {
}