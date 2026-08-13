package com.ticket.web.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 登录请求参数。
 * <p>
 * 校验失败由 {@code GlobalExceptionHandler} 统一转成 {@code C0400}。
 */
@Data
@Schema(description = "登录请求参数")
public class LoginDTO {

    @NotBlank(message = "用户名不能为空")
    @Size(max = 50, message = "用户名长度不能超过 50")
    @Schema(description = "用户名", example = "admin", requiredMode = Schema.RequiredMode.REQUIRED)
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(max = 100, message = "密码长度不能超过 100")
    @Schema(description = "BCrypt 哈希前的明文密码", example = "admin123", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;
}
