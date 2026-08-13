package com.ticket.web.auth.vo;

import com.ticket.security.service.LoginResult;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 登录成功响应体。
 *
 * @param token     JWT，前端存起来后续放进 {@code Authorization} 头
 * @param tokenType 固定 {@code "Bearer"}
 * @param expiresIn 有效期（秒），前端可据此提前提示重新登录
 * @param userId    sys_user.id
 * @param username  登录名
 * @param nickname  展示名
 */
@Schema(description = "登录成功响应体")
public record LoginVO(
        @Schema(description = "JWT token", example = "eyJhbGciOiJIUzI1NiJ9...")
        String token,
        @Schema(description = "Token 类型", example = "Bearer")
        String tokenType,
        @Schema(description = "有效期（秒）", example = "1800")
        long expiresIn,
        @Schema(description = "sys_user.id", example = "1")
        Long userId,
        @Schema(description = "登录名", example = "admin")
        String username,
        @Schema(description = "展示名", example = "超级管理员")
        String nickname
) {

    /** 由 Service 层结果映射而来 —— Controller 不做业务加工 */
    public static LoginVO from(LoginResult result) {
        return new LoginVO(
                result.token(),
                result.tokenType(),
                result.expiresIn(),
                result.userId(),
                result.username(),
                result.nickname());
    }
}
