package com.ticket.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ticket.system.entity.SysRoleMenu;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * {@code sys_role_menu} 数据访问。
 * <p>
 * 同 {@link SysUserRoleMapper}：复合主键场景下 {@link BaseMapper} 的批量 API 不够用，
 * 显式给出"按 roleId 删全部 + 批量插"。
 */
@Mapper
public interface SysRoleMenuMapper extends BaseMapper<SysRoleMenu> {

    /** 删除某角色的所有菜单关联 —— 角色分配菜单前调用 */
    @Delete("DELETE FROM sys_role_menu WHERE role_id = #{roleId}")
    int deleteByRoleId(@Param("roleId") Long roleId);

    int insert(SysRoleMenu entity);

    /** 取某角色挂的全部菜单 id */
    @Select("SELECT menu_id FROM sys_role_menu WHERE role_id = #{roleId}")
    List<Long> findMenuIdsByRoleId(@Param("roleId") Long roleId);

    /**
     * 取一组角色挂的全部菜单 id（不去重 —— 调用方按需 distinct）。
     * <p>
     * 给"用户 → permission 列表"路径用：用户→角色→菜单→permission，
     * 角色数量通常很小（个位数到几十个），适合 IN 一次性查。
     */
    @Select("<script>"
            + "SELECT menu_id FROM sys_role_menu WHERE role_id IN "
            + "<foreach collection='roleIds' item='rid' open='(' separator=',' close=')'>"
            + "#{rid}"
            + "</foreach>"
            + "</script>")
    List<Long> findMenuIdsByRoleIds(@Param("roleIds") List<Long> roleIds);
}