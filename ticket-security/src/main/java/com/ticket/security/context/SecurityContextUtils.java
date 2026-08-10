package com.ticket.security.context;

import com.ticket.common.exception.BusinessException;
import com.ticket.common.exception.BusinessExceptionCode;
import com.ticket.security.user.LoginUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Spring Security 上下文工具（ticket 05 落地，归口 ticket-security）。
 * <p>
 * 集中处理"从 SecurityContext 取当前登录用户"的样板，
 * 避免每个 Service / 切面重复写 cast。
 * <p>
 * <b>归属</b>：放 ticket-security 而非 ticket-ticket —— AGENTS.md §三
 * "ticket-security 负责 Spring Security 配置 / JWT / 权限拦截"，
 * SecurityContext 工具是这一职责的延伸；ticket-ticket 仅作为
 * 调用方存在，不应在自己的 util 包里再放一份（避免包结构违反 §四）。
 * <p>
 * <b>使用约定</b>：
 * <ul>
 *     <li>{@link #currentUser()} —— 拿不到时返回 null（用于审计/日志场景）</li>
 *     <li>{@link #currentUserIdRequired()} —— 拿不到时抛 {@code AUTH_UNAUTHORIZED}（用于业务）</li>
 * </ul>
 */
public final class SecurityContextUtils {

    private SecurityContextUtils() {
    }

    /**
     * 取当前登录用户。
     *
     * @return 登录主体；未登录或上下文无 {@link LoginUser} 时返回 null
     */
    public static LoginUser currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof LoginUser loginUser) {
            return loginUser;
        }
        return null;
    }

    /**
     * 取当前登录用户 id。
     *
     * @return {@code sys_user.id}
     * @throws BusinessException {@code AUTH_UNAUTHORIZED} 当未登录或上下文无主键
     */
    public static Long currentUserIdRequired() {
        LoginUser user = currentUser();
        if (user == null || user.getUserId() == null) {
            throw BusinessException.of(BusinessExceptionCode.AUTH_UNAUTHORIZED, "未登录或登录已失效");
        }
        return user.getUserId();
    }

    /**
     * 取当前登录用户名（用于审计日志）。
     *
     * @return 用户名；未登录时返回空串（不抛异常 —— 审计场景无登录态是合法的，
     *         例：匿名 {@code /api/v1/ping}）
     */
    public static String currentUsername() {
        LoginUser user = currentUser();
        return user == null ? "" : user.getUsername();
    }

    /**
     * 判断当前用户是否拥有指定权限。
     * <p>
     * 用于 Service 层的权限判定，对应 {@code sys_menu.permission} 字符串。
     *
     * @param permission 权限字符串，如 {@code "ticket:delete"}
     * @return 拥有返回 true
     */
    public static boolean hasAuthority(String permission) {
        LoginUser user = currentUser();
        if (user == null || user.getAuthorities() == null) {
            return false;
        }
        return user.getAuthorities().stream()
                .anyMatch(authority -> permission.equals(authority.getAuthority()));
    }
}
