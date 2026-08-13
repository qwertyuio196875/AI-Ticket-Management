package com.ticket.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建 / 更新菜单的请求参数（ticket 03）。
 * <p>
 * permission 对按钮类型必填，对目录 / 菜单可为空。
 */
@Data
@Schema(description = "菜单创建 / 更新参数")
public class SysMenuSaveDTO {

    private Long id;

    /** 父菜单 id；顶级传 0 */
    @Min(value = 0, message = "parentId 不能小于 0")
    private Long parentId;

    @NotBlank(message = "菜单名不能为空")
    @Size(max = 50, message = "菜单名长度不能超过 50")
    private String menuName;

    /** {@link com.ticket.system.entity.SysMenu#TYPE_DIRECTORY} / {@code TYPE_MENU} / {@code TYPE_BUTTON} */
    @NotBlank(message = "菜单类型不能为空")
    @Size(min = 1, max = 1, message = "菜单类型必须是单个字符")
    private String menuType;

    private String path;
    private String component;
    private String icon;
    private Integer sort;
    private Integer visible;

    @Size(max = 100, message = "permission 长度不能超过 100")
    private String permission;
}