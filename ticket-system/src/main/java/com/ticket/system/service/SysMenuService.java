package com.ticket.system.service;

import com.ticket.system.dto.SysMenuSaveDTO;
import com.ticket.system.entity.SysMenu;
import com.ticket.system.vo.SysMenuTreeVO;

import java.util.List;

/**
 * 菜单管理服务（ticket 03）。
 * <p>
 * 业务规则：
 * <ul>
 *     <li>{@code permission} 非空时唯一，冲突抛 {@code MENU_PERMISSION_DUPLICATE}</li>
 *     <li>不允许把 parent 指向自己 / 自己的子菜单（防成环）</li>
 * </ul>
 */
public interface SysMenuService {

    /** 取全部菜单（不分页） —— 管理页"菜单管理"列表用 */
    List<SysMenu> listAll();

    /**
     * 取"用户可访问的菜单树"。
     * <p>
     * 实现：先按 {@code userId} → 角色 → 菜单 id 集合查出菜单行，
     * 再调用 {@code MenuTreeAssembler} 组装成树（id / parentId 自引用）。
     *
     * @param userId 用户 id；为 null 时返回空树
     */
    List<SysMenuTreeVO> treeByUser(Long userId);

    SysMenu getById(Long id);

    /**
     * 共享的"user → role → menu"链路 —— 取某用户能访问的全部菜单 id。
     * <p>
     * ticket 03 阶段两处复用：
     * <ul>
     *     <li>{@link #treeByUser} 拿菜单行后再组装树</li>
     *     <li>{@link com.ticket.system.service.SysUserService#listPermissions} 拿菜单行后抽 permission</li>
     * </ul>
     * 抽到 Service 层避免两边各写一份 SQL 链。
     */
    List<Long> findMenuIdsByUser(Long userId);

    Long create(SysMenuSaveDTO dto);

    void update(SysMenuSaveDTO dto);

    void delete(Long id);
}