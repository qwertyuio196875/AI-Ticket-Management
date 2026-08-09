package com.ticket.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ticket.common.exception.BusinessException;
import com.ticket.common.exception.BusinessExceptionCode;
import com.ticket.system.dto.SysRoleAssignMenusDTO;
import com.ticket.system.dto.SysRoleSaveDTO;
import com.ticket.system.entity.SysRole;
import com.ticket.system.entity.SysRoleMenu;
import com.ticket.system.mapper.SysRoleMapper;
import com.ticket.system.mapper.SysRoleMenuMapper;
import com.ticket.system.service.SysRoleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * {@link SysRoleService} 实现（ticket 03）。
 * <p>
 * 关键保护：{@code roleKey == "admin"} 不允许删除 —— 系统级兜底，
 * 避免"管理员角色"被误删后所有 {@code @PreAuthorize("hasAuthority('user:manage')")}
 * 类操作陷入死锁（没人能进管理页面）。
 */
@Service
public class SysRoleServiceImpl implements SysRoleService {

    private static final Logger log = LoggerFactory.getLogger(SysRoleServiceImpl.class);

    /** 系统级保留角色 key —— 硬编码保护 */
    private static final String PROTECTED_ADMIN_KEY = "admin";

    private final SysRoleMapper roleMapper;
    private final SysRoleMenuMapper roleMenuMapper;

    public SysRoleServiceImpl(SysRoleMapper roleMapper, SysRoleMenuMapper roleMenuMapper) {
        this.roleMapper = roleMapper;
        this.roleMenuMapper = roleMenuMapper;
    }

    // ---------- 查询 ----------

    @Override
    public IPage<SysRole> page(String keyword, long pageNum, long pageSize) {
        Page<SysRole> page = Page.of(pageNum, pageSize);
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(SysRole::getRoleName, keyword)
                    .or().like(SysRole::getRoleKey, keyword));
        }
        wrapper.orderByAsc(SysRole::getId);
        return roleMapper.selectPage(page, wrapper);
    }

    @Override
    public SysRole getById(Long id) {
        SysRole role = roleMapper.selectById(id);
        if (role == null) {
            throw BusinessException.of(BusinessExceptionCode.ROLE_NOT_FOUND);
        }
        return role;
    }

    @Override
    public List<SysRole> listAll() {
        return roleMapper.selectList(new LambdaQueryWrapper<SysRole>().orderByAsc(SysRole::getId));
    }

    // ---------- 创建 ----------

    @Override
    @Transactional
    public Long create(SysRoleSaveDTO dto) {
        SysRole role = new SysRole();
        role.setRoleName(dto.getRoleName());
        role.setRoleKey(dto.getRoleKey());
        role.setRemark(dto.getRemark() == null ? "" : dto.getRemark());
        try {
            roleMapper.insert(role);
        } catch (DuplicateKeyException ex) {
            log.warn("创建角色失败 —— roleKey 冲突: roleKey={}", dto.getRoleKey());
            throw BusinessException.of(BusinessExceptionCode.ROLE_DUPLICATE);
        }
        log.info("创建角色: roleId={}, roleKey={}", role.getId(), role.getRoleKey());
        return role.getId();
    }

    // ---------- 更新 ----------

    @Override
    @Transactional
    public void update(SysRoleSaveDTO dto) {
        if (dto.getId() == null) {
            throw BusinessException.of(BusinessExceptionCode.PARAM_INVALID, "更新角色时 id 不能为空");
        }
        SysRole role = getById(dto.getId());
        role.setRoleName(dto.getRoleName());
        if (dto.getRemark() != null) {
            role.setRemark(dto.getRemark());
        }
        // roleKey 不允许改 —— 改 key 会让"按 key 找"的所有硬编码逻辑失准
        try {
            roleMapper.updateById(role);
        } catch (DuplicateKeyException ex) {
            throw BusinessException.of(BusinessExceptionCode.ROLE_DUPLICATE);
        }
        log.info("更新角色: roleId={}", role.getId());
    }

    // ---------- 删除 ----------

    @Override
    @Transactional
    public void delete(Long id) {
        SysRole role = getById(id);
        if (PROTECTED_ADMIN_KEY.equals(role.getRoleKey())) {
            log.warn("拒绝删除受保护角色: roleId={}, roleKey={}", id, role.getRoleKey());
            throw BusinessException.of(BusinessExceptionCode.LAST_ADMIN_PROTECTED,
                    "admin 角色不允许删除");
        }
        roleMapper.deleteById(id);
        // 关联表同步清理
        roleMenuMapper.deleteByRoleId(id);
        log.info("删除角色: roleId={}, roleKey={}", id, role.getRoleKey());
    }

    // ---------- 菜单分配 ----------

    @Override
    @Transactional
    public void assignMenus(Long roleId, SysRoleAssignMenusDTO dto) {
        getById(roleId);
        roleMenuMapper.deleteByRoleId(roleId);
        for (Long menuId : dto.getMenuIds()) {
            roleMenuMapper.insert(new SysRoleMenu(roleId, menuId));
        }
        log.info("分配菜单: roleId={}, menuIds={}", roleId, dto.getMenuIds());
    }

    @Override
    public List<Long> listMenuIds(Long roleId) {
        return roleMenuMapper.findMenuIdsByRoleId(roleId);
    }
}