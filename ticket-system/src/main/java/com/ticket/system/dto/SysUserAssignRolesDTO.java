package com.ticket.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.Data;

/**
 * 给用户重新分配角色的请求体。
 * <p>
 * 语义是"替换"（先删后插），空集合会把用户角色全部清空 —— 由调用方按业务决定是否允许。
 */
@Data
@Schema(description = "用户分配角色参数")
public class SysUserAssignRolesDTO {

    @NotEmpty(message = "角色列表不能为空")
    @Valid
    private List<Long> roleIds;
}