package com.ticket.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ticket.common.exception.BusinessException;
import com.ticket.common.exception.BusinessExceptionCode;
import com.ticket.system.dto.SysUserAssignRolesDTO;
import com.ticket.system.dto.SysUserSaveDTO;
import com.ticket.system.entity.SysMenu;
import com.ticket.system.entity.SysRole;
import com.ticket.system.entity.SysUser;
import com.ticket.system.entity.SysUserRole;
import com.ticket.system.mapper.SysMenuMapper;
import com.ticket.system.mapper.SysRoleMapper;
import com.ticket.system.mapper.SysRoleMenuMapper;
import com.ticket.system.mapper.SysUserMapper;
import com.ticket.system.mapper.SysUserRoleMapper;
import com.ticket.system.service.SysUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@link SysUserService} 实现（ticket 03）。
 * <p>
 * 设计要点：
 * <ul>
 *     <li>用户名 / 角色关联唯一约束交给数据库兜底，捕获 {@link DuplicateKeyException} 转业务异常，
 *         不在前置 SELECT 探测（少一次往返，并发安全）</li>
 *     <li>"最后一个超管"保护：删除 / 重新分配角色时都要校验，避免所有 admin 被清光</li>
 *     <li>密码字段：创建必填且 BCrypt；更新时 {@code null / 空} 跳过</li>
 * </ul>
 */
@Service
public class SysUserServiceImpl implements SysUserService {

    private static final Logger log = LoggerFactory.getLogger(SysUserServiceImpl.class);

    /** 超级管理员角色 key —— 硬编码系统级保护 */
    private static final String ADMIN_ROLE_KEY = "admin";

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final SysMenuMapper menuMapper;
    private final PasswordEncoder passwordEncoder;

    public SysUserServiceImpl(SysUserMapper userMapper,
                              SysUserRoleMapper userRoleMapper,
                              SysRoleMapper roleMapper,
                              SysRoleMenuMapper roleMenuMapper,
                              SysMenuMapper menuMapper,
                              PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
        this.roleMenuMapper = roleMenuMapper;
        this.menuMapper = menuMapper;
        this.passwordEncoder = passwordEncoder;
    }

    // ---------- 查询 ----------

    @Override
    public IPage<SysUser> page(String keyword, Integer status, long pageNum, long pageSize) {
        Page<SysUser> page = Page.of(pageNum, pageSize);
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(SysUser::getUsername, keyword)
                    .or().like(SysUser::getNickname, keyword));
        }
        if (status != null) {
            wrapper.eq(SysUser::getStatus, status);
        }
        // 列表按 id desc —— 新建的用户排前面
        wrapper.orderByDesc(SysUser::getId);
        return userMapper.selectPage(page, wrapper);
    }

    @Override
    public SysUser getById(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw BusinessException.of(BusinessExceptionCode.USER_NOT_FOUND);
        }
        return user;
    }

    // ---------- 创建 ----------

    @Override
    @Transactional
    public Long create(SysUserSaveDTO dto) {
        if (!StringUtils.hasText(dto.getPassword())) {
            // 创建路径必填密码 —— 校验失败转业务异常，避免泄露给前端的细节不一致
            throw BusinessException.of(BusinessExceptionCode.PARAM_INVALID, "创建用户时密码不能为空");
        }
        SysUser user = new SysUser();
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setNickname(dto.getNickname());
        user.setStatus(dto.getStatus() == null ? SysUser.STATUS_ENABLED : dto.getStatus());
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException ex) {
            log.warn("创建用户失败 —— 用户名冲突: username={}", dto.getUsername());
            throw BusinessException.of(BusinessExceptionCode.USER_DUPLICATE);
        }
        log.info("创建用户: userId={}, username={}", user.getId(), user.getUsername());
        return user.getId();
    }

    // ---------- 更新 ----------

    @Override
    @Transactional
    public void update(SysUserSaveDTO dto) {
        if (dto.getId() == null) {
            throw BusinessException.of(BusinessExceptionCode.PARAM_INVALID, "更新用户时 id 不能为空");
        }
        SysUser user = getById(dto.getId());
        user.setNickname(dto.getNickname());
        if (dto.getStatus() != null) {
            user.setStatus(dto.getStatus());
        }
        if (StringUtils.hasText(dto.getPassword())) {
            user.setPassword(passwordEncoder.encode(dto.getPassword()));
        }
        try {
            userMapper.updateById(user);
        } catch (DuplicateKeyException ex) {
            // username 改了名字但撞唯一索引
            throw BusinessException.of(BusinessExceptionCode.USER_DUPLICATE);
        }
        log.info("更新用户: userId={}", user.getId());
    }

    // ---------- 删除 ----------

    @Override
    @Transactional
    public void delete(Long id) {
        SysUser user = getById(id);
        ensureNotLastAdmin(id);
        userMapper.deleteById(id);
        // 关联表同步清理 —— 避免遗留孤立记录
        userRoleMapper.deleteByUserId(id);
        log.info("删除用户: userId={}, username={}", id, user.getUsername());
    }

    // ---------- 角色分配 ----------

    @Override
    @Transactional
    public void assignRoles(Long userId, SysUserAssignRolesDTO dto) {
        // 用户存在性 + 防"最后一个超管"
        getById(userId);
        ensureNotLastAdmin(userId);

        Set<Long> requestedIds = dto.getRoleIds().stream().collect(Collectors.toSet());
        Set<Long> existingRoleIds = userRoleMapper.findRoleIdsByUserId(userId).stream()
                .collect(Collectors.toSet());

        // 如果请求集合里"丢了 admin"，且当前用户挂 admin，则视作想把自己降级 —— 触发保护
        boolean wasAdmin = existingRoleIds.stream()
                .map(this::resolveRoleKey)
                .anyMatch(ADMIN_ROLE_KEY::equals);
        boolean stillAdmin = requestedIds.stream()
                .map(this::resolveRoleKey)
                .anyMatch(ADMIN_ROLE_KEY::equals);
        if (wasAdmin && !stillAdmin) {
            ensureNotLastAdmin(userId);
        }

        userRoleMapper.deleteByUserId(userId);
        for (Long roleId : requestedIds) {
            userRoleMapper.insert(new SysUserRole(userId, roleId));
        }
        log.info("分配角色: userId={}, roleIds={}", userId, requestedIds);
    }

    @Override
    public List<Long> listRoleIds(Long userId) {
        return userRoleMapper.findRoleIdsByUserId(userId);
    }

    @Override
    public List<String> listPermissions(Long userId) {
        // 用户 → 角色 → 菜单 → permission；用 IN 一次性查
        List<Long> roleIds = userRoleMapper.findRoleIdsByUserId(userId);
        if (roleIds.isEmpty()) {
            return List.of();
        }
        List<Long> menuIds = roleMenuMapper.findMenuIdsByRoleIds(roleIds);
        if (menuIds.isEmpty()) {
            return List.of();
        }
        Set<String> dedup = new HashSet<>();
        for (SysMenu menu : menuMapper.selectBatchIds(menuIds)) {
            // permission 可空（目录 / 菜单可能没挂权限）—— 仅收非空
            if (StringUtils.hasText(menu.getPermission())) {
                dedup.add(menu.getPermission());
            }
        }
        // 固定顺序返回 —— List.copyOf 会保持迭代顺序
        return List.copyOf(dedup);
    }

    // ---------- 内部辅助 ----------

    /** 校验"目标用户不是最后一个挂 admin 角色的用户"；是则抛 LAST_ADMIN_PROTECTED */
    private void ensureNotLastAdmin(Long userId) {
        List<Long> userRoleIds = userRoleMapper.findRoleIdsByUserId(userId);
        boolean isAdmin = userRoleIds.stream()
                .map(this::resolveRoleKey)
                .anyMatch(ADMIN_ROLE_KEY::equals);
        if (!isAdmin) {
            return;
        }
        long adminCount = countUsersInRole(resolveRoleIdByKey(ADMIN_ROLE_KEY));
        if (adminCount <= 1) {
            log.warn("拒绝删/降级最后一个超管: userId={}", userId);
            throw BusinessException.of(BusinessExceptionCode.LAST_ADMIN_PROTECTED);
        }
    }

    /** 角色 id → role_key；不存在返回 null */
    private String resolveRoleKey(Long roleId) {
        SysRole role = roleMapper.selectById(roleId);
        return role == null ? null : role.getRoleKey();
    }

    /** role_key → 角色 id；不存在返回 null */
    private Long resolveRoleIdByKey(String roleKey) {
        SysRole role = roleMapper.selectOne(
                new LambdaQueryWrapper<SysRole>().eq(SysRole::getRoleKey, roleKey));
        return role == null ? null : role.getId();
    }

    /** 统计挂某角色的用户数 —— 跨用户数 + 自身去重 */
    private long countUsersInRole(Long roleId) {
        if (roleId == null) {
            return 0L;
        }
        // 复用 userRoleMapper.findRoleIdsByUserId 不合适（按 user 维度）；
        // 这里走最朴素的方式：直接 count
        Long count = userRoleMapper.selectCount(
                new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getRoleId, roleId));
        return count == null ? 0L : count;
    }
}