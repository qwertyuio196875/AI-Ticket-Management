package com.ticket.system.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ticket.system.dto.SysUserAssignRolesDTO;
import com.ticket.system.dto.SysUserSaveDTO;
import com.ticket.system.entity.SysUser;

import java.util.List;

/**
 * 用户管理服务（ticket 03 —— RBAC 用户 CRUD + 角色分配）。
 * <p>
 * 业务规则：
 * <ul>
 *     <li>创建时密码必填且自动 BCrypt</li>
 *     <li>更新时密码留空代表"不动密码"</li>
 *     <li>{@code username} 唯一冲突转 {@code USER_DUPLICATE}</li>
 *     <li>不允许删除"最后一个超管"（与 admin 角色挂钩的用户），
 *         防止所有管理员被清掉</li>
 * </ul>
 */
public interface SysUserService {

    /**
     * 分页查询用户。
     *
     * @param keyword  按 {@code username} / {@code nickname} 模糊匹配，可空
     * @param status   按 {@code status} 过滤，可空
     * @param pageNum  页码，从 1 开始
     * @param pageSize 每页条数
     */
    IPage<SysUser> page(String keyword, Integer status, long pageNum, long pageSize);

    /** 按 id 取用户；找不到抛 {@code USER_NOT_FOUND} */
    SysUser getById(Long id);

    /** 创建 —— 密码自动 BCrypt；用户名冲突抛 {@code USER_DUPLICATE} */
    Long create(SysUserSaveDTO dto);

    /** 更新 —— 密码留空则不动；找不到用户抛 {@code USER_NOT_FOUND} */
    void update(SysUserSaveDTO dto);

    /**
     * 删除用户 —— 不允许删"最后一个超管"。
     *
     * @throws BusinessException(USER_NOT_FOUND) 用户不存在
     * @throws BusinessException(LAST_ADMIN_PROTECTED) 想删的是最后一个挂 admin 角色的用户
     */
    void delete(Long id);

    /** 给用户重新分配角色 —— 语义：先清空再批量插 */
    void assignRoles(Long userId, SysUserAssignRolesDTO dto);

    /** 取某用户当前的全部角色 id（用于"编辑回显"） */
    List<Long> listRoleIds(Long userId);

    /** 取某用户当前的全部 permission 字符串（供 JWT 签发、菜单树过滤用） */
    List<String> listPermissions(Long userId);
}