package com.ticket.web.auth;

import com.ticket.common.result.Result;
import com.ticket.security.jwt.JwtTokenResolver;
import com.ticket.security.service.AuthService;
import com.ticket.security.user.LoginUser;
import com.ticket.web.auth.dto.LoginDTO;
import com.ticket.web.auth.vo.LoginVO;
import com.ticket.web.auth.vo.UserInfoVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口（详见 ticket 02）。
 * <ul>
 *     <li>{@code POST /api/v1/auth/login}：公开，校验凭证并返回 JWT</li>
 *     <li>{@code GET  /api/v1/auth/me}：需认证，返回 JWT 中的当前用户信息</li>
 *     <li>{@code POST /api/v1/auth/logout}：需认证，把当前 token 拉黑</li>
 * </ul>
 * <p>
 * 按 AGENTS.md 约束，Controller 只做参数接收与转发，业务在
 * {@link AuthService}；异常一律抛给 {@code GlobalExceptionHandler} 包装。
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "auth", description = "认证授权：登录 / 登出 / 当前用户")
public class AuthController {

    private final AuthService authService;
    private final JwtTokenResolver tokenResolver;

    public AuthController(AuthService authService, JwtTokenResolver tokenResolver) {
        this.authService = authService;
        this.tokenResolver = tokenResolver;
    }

    /**
     * 登录。
     * <p>
     * 凭证错误时 {@link AuthService} 抛业务异常，全局异常处理输出
     * {@code Result.error("401", ...)} + HTTP 401。
     */
    @PostMapping("/login")
    @Operation(summary = "登录", description = "校验用户名密码，返回 JWT token")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO loginDTO) {
        return Result.success(LoginVO.from(
                authService.login(loginDTO.getUsername(), loginDTO.getPassword())));
    }

    /**
     * 当前登录用户。
     * <p>
     * principal 由 {@code JwtAuthFilter} 从 token 还原，此处不查库。
     */
    @GetMapping("/me")
    @Operation(summary = "当前登录用户", description = "返回 JWT 中的当前用户信息（含权限列表）")
    public Result<UserInfoVO> me(@AuthenticationPrincipal LoginUser currentUser) {
        return Result.success(UserInfoVO.from(currentUser));
    }

    /**
     * 登出 —— 把当前请求携带的 token 加入 Redis 黑名单。
     * <p>
     * token 从请求头现取：JWT 无状态，服务端没有"当前会话"可供撤销。
     */
    @PostMapping("/logout")
    @Operation(summary = "登出", description = "把当前 token 加入 Redis 黑名单，30 分钟内失效")
    public Result<Void> logout(HttpServletRequest request) {
        authService.logout(tokenResolver.resolve(request));
        return Result.success(null);
    }
}
