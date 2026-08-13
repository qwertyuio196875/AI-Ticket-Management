package com.ticket.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建 / 更新用户的请求参数（ticket 03）。
 * <p>
 * 创建时密码必填；更新时密码留空代表"不改密码"。
 */
@Data
@Schema(description = "用户创建 / 更新参数")
public class SysUserSaveDTO {

    private Long id;

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 50, message = "用户名长度需在 3-50 之间")
    private String username;

    /** 仅创建或重置密码时填写；更新时为空代表"不动密码" */
    private String password;

    @NotBlank(message = "昵称不能为空")
    @Size(max = 50, message = "昵称长度不能超过 50")
    private String nickname;

    /** {@link com.ticket.system.entity.SysUser#STATUS_ENABLED} / {@code STATUS_DISABLED} */
    private Integer status;
}