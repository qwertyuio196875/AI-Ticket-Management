package com.ticket.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ticket.system.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * {@code sys_user} 数据访问。
 * <p>
 * 继承 MyBatis Plus {@link BaseMapper}，认证只用到按 username 单查，
 * 暂不需要自定义 SQL。
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
}
