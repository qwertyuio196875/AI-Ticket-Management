package com.ticket.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.Data;

/**
 * 给角色重新分配菜单的请求体 —— 语义同样是"替换"。
 */
@Data
@Schema(description = "角色分配菜单参数")
public class SysRoleAssignMenusDTO {

    @NotEmpty(message = "菜单列表不能为空")
    @Valid
    private List<Long> menuIds;
}