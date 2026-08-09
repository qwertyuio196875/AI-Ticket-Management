package com.ticket.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ticket.common.exception.BusinessException;
import com.ticket.common.exception.BusinessExceptionCode;
import com.ticket.system.dto.SysMenuSaveDTO;
import com.ticket.system.entity.SysMenu;
import com.ticket.system.mapper.SysMenuMapper;
import com.ticket.system.mapper.SysRoleMenuMapper;
import com.ticket.system.mapper.SysUserRoleMapper;
import com.ticket.system.service.SysMenuService;
import com.ticket.system.service.assembler.MenuTreeAssembler;
import com.ticket.system.vo.SysMenuTreeVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * {@link SysMenuService} 实现（ticket 03）。
 * <p>
 * 菜单树组装走"用户→角色→菜单 id 集合→菜单行→MenuTreeAssembler"
 * 三跳链，逻辑故意留在 Service 层而非数据库 JOIN —— JOIN 出来的树形结果
 * 用 MyBatis-Plus 处理不优雅，组装成 entity 后用纯函数组装器更易单测。
 */
@Service
public class SysMenuServiceImpl implements SysMenuService {

    private static final Logger log = LoggerFactory.getLogger(SysMenuServiceImpl.class);

    private final SysMenuMapper menuMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMenuMapper roleMenuMapper;

    public SysMenuServiceImpl(SysMenuMapper menuMapper,
                              SysUserRoleMapper userRoleMapper,
                              SysRoleMenuMapper roleMenuMapper) {
        this.menuMapper = menuMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleMenuMapper = roleMenuMapper;
    }

    // ---------- 查询 ----------

    @Override
    public List<SysMenu> listAll() {
        return menuMapper.selectList(new LambdaQueryWrapper<SysMenu>().orderByAsc(SysMenu::getSort));
    }

    /**
     * 按用户取菜单树。
     * <p>
     * 链路：{@code userId → sys_user_role.roleId → sys_role_menu.menuId → sys_menu.*}，
     * 三次查询都是小数据量（用户角色数 / 角色菜单数 / 菜单行数均 < 几十到几百）。
     */
    @Override
    public List<SysMenuTreeVO> treeByUser(Long userId) {
        if (userId == null) {
            return List.of();
        }
        List<Long> roleIds = userRoleMapper.findRoleIdsByUserId(userId);
        if (roleIds.isEmpty()) {
            return List.of();
        }
        List<Long> menuIds = roleMenuMapper.findMenuIdsByRoleIds(roleIds);
        if (menuIds.isEmpty()) {
            return List.of();
        }
        List<SysMenu> menus = menuMapper.selectBatchIds(menuIds);
        return MenuTreeAssembler.assemble(menus);
    }

    @Override
    public SysMenu getById(Long id) {
        SysMenu menu = menuMapper.selectById(id);
        if (menu == null) {
            throw BusinessException.of(BusinessExceptionCode.MENU_NOT_FOUND);
        }
        return menu;
    }

    // ---------- 创建 ----------

    @Override
    @Transactional
    public Long create(SysMenuSaveDTO dto) {
        validateBasics(dto);
        SysMenu menu = toEntity(dto, null);
        try {
            menuMapper.insert(menu);
        } catch (DuplicateKeyException ex) {
            log.warn("创建菜单失败 —— permission 冲突: permission={}", dto.getPermission());
            throw BusinessException.of(BusinessExceptionCode.MENU_PERMISSION_DUPLICATE);
        }
        log.info("创建菜单: menuId={}, menuName={}", menu.getId(), menu.getMenuName());
        return menu.getId();
    }

    // ---------- 更新 ----------

    @Override
    @Transactional
    public void update(SysMenuSaveDTO dto) {
        if (dto.getId() == null) {
            throw BusinessException.of(BusinessExceptionCode.PARAM_INVALID, "更新菜单时 id 不能为空");
        }
        validateBasics(dto);
        SysMenu existing = getById(dto.getId());
        preventSelfParenting(dto.getId(), dto.getParentId());
        SysMenu menu = toEntity(dto, existing);
        try {
            menuMapper.updateById(menu);
        } catch (DuplicateKeyException ex) {
            throw BusinessException.of(BusinessExceptionCode.MENU_PERMISSION_DUPLICATE);
        }
        log.info("更新菜单: menuId={}", menu.getId());
    }

    // ---------- 删除 ----------

    @Override
    @Transactional
    public void delete(Long id) {
        getById(id);
        // 子菜单同步删 —— 防止 parent 消失后子菜单孤立
        List<SysMenu> children = menuMapper.selectList(
                new LambdaQueryWrapper<SysMenu>().eq(SysMenu::getParentId, id));
        if (!children.isEmpty()) {
            List<Long> childIds = children.stream().map(SysMenu::getId).toList();
            menuMapper.deleteBatchIds(childIds);
            log.info("删除菜单触发级联 —— 父 menuId={}, 子菜单数={}", id, childIds.size());
        }
        menuMapper.deleteById(id);
        log.info("删除菜单: menuId={}", id);
    }

    // ---------- 内部辅助 ----------

    /**
     * 校验基本字段：parentId 合法、permission 在按钮类型下必填。
     * <p>
     * 不校验"parentId 是否指向真实存在的菜单"—— 那种结构性校验放给
     * 数据完整性（脏数据写入不会影响功能，只是显示异常）。
     */
    private void validateBasics(SysMenuSaveDTO dto) {
        Long parentId = dto.getParentId() == null ? SysMenu.TOP_LEVEL_PARENT_ID : dto.getParentId();
        if (parentId < 0) {
            throw BusinessException.of(BusinessExceptionCode.PARAM_INVALID, "parentId 不能为负");
        }
        if (SysMenu.TYPE_BUTTON.equals(dto.getMenuType()) && !StringUtils.hasText(dto.getPermission())) {
            throw BusinessException.of(BusinessExceptionCode.PARAM_INVALID,
                    "按钮类型菜单必须填写 permission");
        }
    }

    /** 防止把 parent 指向自己 / 自己的子菜单（避免成环） */
    private void preventSelfParenting(Long selfId, Long newParentId) {
        if (newParentId == null || SysMenu.TOP_LEVEL_PARENT_ID.equals(newParentId)) {
            return;
        }
        if (selfId.equals(newParentId)) {
            throw BusinessException.of(BusinessExceptionCode.PARAM_INVALID, "不能把菜单父级指向自己");
        }
        // BFS 检测新 parent 是否是当前菜单的子孙 —— 避免把 A 挂到自己的子孙下
        Set<Long> visited = new HashSet<>();
        Long cursor = newParentId;
        while (cursor != null && !SysMenu.TOP_LEVEL_PARENT_ID.equals(cursor)) {
            if (!visited.add(cursor)) {
                return; // 已访问过（防御成环）
            }
            if (selfId.equals(cursor)) {
                throw BusinessException.of(BusinessExceptionCode.PARAM_INVALID,
                        "不能把菜单父级指向自己的子孙");
            }
            SysMenu parent = menuMapper.selectById(cursor);
            cursor = parent == null ? null : parent.getParentId();
        }
    }

    private SysMenu toEntity(SysMenuSaveDTO dto, SysMenu existing) {
        SysMenu menu = existing == null ? new SysMenu() : existing;
        menu.setParentId(dto.getParentId() == null ? SysMenu.TOP_LEVEL_PARENT_ID : dto.getParentId());
        menu.setMenuName(dto.getMenuName());
        menu.setMenuType(dto.getMenuType());
        menu.setPath(dto.getPath() == null ? "" : dto.getPath());
        menu.setComponent(dto.getComponent() == null ? "" : dto.getComponent());
        menu.setIcon(dto.getIcon() == null ? "" : dto.getIcon());
        menu.setSort(dto.getSort() == null ? 0 : dto.getSort());
        menu.setVisible(dto.getVisible() == null ? SysMenu.VISIBLE_YES : dto.getVisible());
        menu.setPermission(dto.getPermission() == null ? "" : dto.getPermission());
        return menu;
    }
}