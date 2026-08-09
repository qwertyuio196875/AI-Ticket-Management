package com.ticket.web.system.vo;

import com.ticket.system.entity.SysMenu;

import java.time.LocalDateTime;

/**
 * 菜单 VO —— 与 SysUserVO / SysRoleVO 风格一致，屏蔽 entity 直接外露。
 */
public record SysMenuVO(
        Long id,
        Long parentId,
        String menuName,
        String menuType,
        String path,
        String component,
        String icon,
        Integer sort,
        Integer visible,
        String permission,
        LocalDateTime createTime
) {

    public static SysMenuVO from(SysMenu menu) {
        return new SysMenuVO(
                menu.getId(),
                menu.getParentId(),
                menu.getMenuName(),
                menu.getMenuType(),
                menu.getPath(),
                menu.getComponent(),
                menu.getIcon(),
                menu.getSort(),
                menu.getVisible(),
                menu.getPermission(),
                menu.getCreateTime());
    }
}