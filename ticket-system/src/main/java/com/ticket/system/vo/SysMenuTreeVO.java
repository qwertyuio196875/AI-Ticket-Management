package com.ticket.system.vo;

import com.ticket.system.entity.SysMenu;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 菜单树节点 VO（ticket 03）。
 * <p>
 * 比 {@link SysMenu} 多了 {@link #children} 字段，用于前端渲染侧边栏。
 * 单独抽 VO 而不是直接在 entity 里挂 children —— 树结构是视图层概念，
 * 不应污染数据库实体。
 */
@Data
public class SysMenuTreeVO {

    private Long id;
    private Long parentId;
    private String menuName;
    private String menuType;
    private String path;
    private String component;
    private String icon;
    private Integer sort;
    private Integer visible;
    private String permission;
    private LocalDateTime createTime;

    /** 子菜单 —— 树结构延迟构造 */
    private List<SysMenuTreeVO> children = new ArrayList<>();

    /** entity → tree 节点映射 */
    public static SysMenuTreeVO from(SysMenu menu) {
        SysMenuTreeVO vo = new SysMenuTreeVO();
        vo.setId(menu.getId());
        vo.setParentId(menu.getParentId());
        vo.setMenuName(menu.getMenuName());
        vo.setMenuType(menu.getMenuType());
        vo.setPath(menu.getPath());
        vo.setComponent(menu.getComponent());
        vo.setIcon(menu.getIcon());
        vo.setSort(menu.getSort());
        vo.setVisible(menu.getVisible());
        vo.setPermission(menu.getPermission());
        vo.setCreateTime(menu.getCreateTime());
        return vo;
    }
}