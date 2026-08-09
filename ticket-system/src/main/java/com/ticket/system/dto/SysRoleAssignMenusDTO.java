package com.ticket.system.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 给角色重新分配菜单的请求体 —— 语义同样是"替换"。
 */
@Data
public class SysRoleAssignMenusDTO {

    @NotEmpty(message = "菜单列表不能为空")
    @Valid
    private List<Long> menuIds;
}