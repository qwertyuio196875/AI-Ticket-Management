package com.ticket.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ticket.system.entity.SysMenu;
import org.apache.ibatis.annotations.Mapper;

/**
 * {@code sys_menu} 数据访问。
 * <p>
 * CRUD 由 {@link BaseMapper} 提供；菜单树组装所需的"按用户 ID 过滤"
 * 自定义 SQL 在 {@link SysMenuMapper} 之外的 Service 拼接
 * （见 {@code SysMenuService.findMenusByUserId}）。
 */
@Mapper
public interface SysMenuMapper extends BaseMapper<SysMenu> {
}