package com.ticket.system.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ticket.system.dto.SysRoleAssignMenusDTO;
import com.ticket.system.dto.SysRoleSaveDTO;
import com.ticket.system.entity.SysRole;

import java.util.List;

/**
 * 角色管理服务（ticket 03）。
 * <p>
 * 业务规则：
 * <ul>
 *     <li>{@code role_key} 唯一冲突转 {@code ROLE_DUPLICATE}</li>
 *     <li>不允许删除 "admin"（硬编码系统级保护，避免角色被清光）</li>
 * </ul>
 */
public interface SysRoleService {

    IPage<SysRole> page(String keyword, long pageNum, long pageSize);

    SysRole getById(Long id);

    /** 列出全部角色（不分页）—— 用户管理页面"分配角色"下拉框用 */
    List<SysRole> listAll();

    Long create(SysRoleSaveDTO dto);

    void update(SysRoleSaveDTO dto);

    /** 删除 —— 不允许删除 {@code roleKey == "admin"} */
    void delete(Long id);

    /** 给角色重新分配菜单 —— 语义：先清空再批量插 */
    void assignMenus(Long roleId, SysRoleAssignMenusDTO dto);

    List<Long> listMenuIds(Long roleId);
}