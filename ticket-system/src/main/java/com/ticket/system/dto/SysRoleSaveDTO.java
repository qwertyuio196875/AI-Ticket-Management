package com.ticket.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建 / 更新角色的请求参数（ticket 03）。
 */
@Data
public class SysRoleSaveDTO {

    private Long id;

    @NotBlank(message = "角色名不能为空")
    @Size(max = 50, message = "角色名长度不能超过 50")
    private String roleName;

    @NotBlank(message = "角色 key 不能为空")
    @Size(max = 50, message = "角色 key 长度不能超过 50")
    private String roleKey;

    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}