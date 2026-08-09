package com.ticket.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 菜单 / 操作权限 {@code sys_menu} —— RBAC 双层载体（详见 ADR-0002）。
 * <p>
 * 两层权限都落在这张表里：
 * <ul>
 *     <li><b>菜单权限</b>：行存在与否决定前端路由 + 侧边栏可见性</li>
 *     <li><b>操作权限</b>：{@link #permission} 字符串（按钮级），
 *         经 {@code @PreAuthorize("hasAuthority('xxx')")} 在后端校验</li>
 * </ul>
 * 目录（{@link #TYPE_DIRECTORY}）只承载子节点、不挂权限；
 * 按钮（{@link #TYPE_BUTTON}）没有 path / component、只有 permission。
 */
@Data
@TableName("sys_menu")
public class SysMenu {

    /** 顶级菜单的 parentId —— 用 0 而非 null，简化查询与树构造 */
    public static final Long TOP_LEVEL_PARENT_ID = 0L;

    /** 目录：仅承载子节点 */
    public static final String TYPE_DIRECTORY = "M";
    /** 菜单：实际页面 */
    public static final String TYPE_MENU = "C";
    /** 按钮：仅 permission，无路由 */
    public static final String TYPE_BUTTON = "F";

    /** 显示 */
    public static final Integer VISIBLE_YES = 1;
    /** 隐藏 */
    public static final Integer VISIBLE_NO = 0;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 父菜单 id；{@link #TOP_LEVEL_PARENT_ID} 表示顶级 */
    private Long parentId;

    private String menuName;

    /** {@link #TYPE_DIRECTORY} / {@link #TYPE_MENU} / {@link #TYPE_BUTTON} */
    private String menuType;

    /** 前端路由 path */
    private String path;

    /** 前端组件路径 */
    private String component;

    /** 图标标识 */
    private String icon;

    /** 同 parent 下的排序号，升序 */
    private Integer sort;

    /** 是否显示：{@link #VISIBLE_YES} / {@link #VISIBLE_NO} */
    private Integer visible;

    /**
     * 操作权限字符串（按钮级）。
     * <p>
     * 目录 / 菜单可为空（不参与按钮校验）；
     * 唯一索引允许多个 NULL，仅对非空字符串去重。
     */
    private String permission;

    private LocalDateTime createTime;
}