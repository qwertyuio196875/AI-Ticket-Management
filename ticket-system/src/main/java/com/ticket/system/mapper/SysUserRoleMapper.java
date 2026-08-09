package com.ticket.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ticket.system.entity.SysUserRole;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * {@code sys_user_role} 数据访问。
 * <p>
 * 复合主键场景下 {@link BaseMapper} 提供的批量 API 不可用（{@code insert} / {@code deleteById}
 * 都基于单字段主键），所以这里显式给出"按 userId 删全部 + 批量插"两条 SQL。
 * 一次事务里"先删后插"是给用户重新分配角色的常用语义（PUT 接口）。
 */
@Mapper
public interface SysUserRoleMapper extends BaseMapper<SysUserRole> {

    /** 删除某用户的所有角色关联 —— 重新分配角色前调用 */
    @Delete("DELETE FROM sys_user_role WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") Long userId);

    /** 批量插入 —— 复用 {@link BaseMapper#insert} 的单条版本即可 */
    int insert(SysUserRole entity);

    /** 取某用户的全部角色 id —— RBAC 计算、菜单过滤都要用 */
    @Select("SELECT role_id FROM sys_user_role WHERE user_id = #{userId}")
    List<Long> findRoleIdsByUserId(@Param("userId") Long userId);
}