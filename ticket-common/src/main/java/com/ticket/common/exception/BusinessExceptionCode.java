package com.ticket.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 业务异常码枚举（详见 ADR-0009）。
 * <p>
 * 设计要点：
 * <ul>
 *     <li><b>code</b>：业务字符串码，格式 <code>"模块前缀 + 序号"</code>
 *         （如 <code>"T0101"</code>、<code>"C0500"</code>），前端按此匹配提示。</li>
 *     <li><b>httpStatus</b>：HTTP 状态码，由 {@link GlobalExceptionHandler}
 *         在包装响应时单独使用 — 与 body 中的 code 解耦。</li>
 *     <li><b>message</b>：面向用户的默认提示文本，可被具体异常覆盖。</li>
 * </ul>
 * <p>
 * 后续 ticket 中将按需扩充。当前包含：
 * <ul>
 *     <li>{@link #INTERNAL_ERROR}：兜底异常</li>
 *     <li>{@link #PARAM_INVALID}：参数校验失败</li>
 *     <li>{@link #AUTH_UNAUTHORIZED} / {@link #AUTH_FORBIDDEN}：认证授权失败（ticket 02）</li>
 * </ul>
 */
@Getter
public enum BusinessExceptionCode {

    // ---- Common ----
    /** 兜底异常 — 任何未预期的服务端错误 */
    INTERNAL_ERROR("C0500", HttpStatus.INTERNAL_SERVER_ERROR, "系统内部错误"),
    /** 参数校验失败 — {@code @Valid} / {@code @RequestBody} / {@code @PathVariable} */
    PARAM_INVALID("C0400", HttpStatus.BAD_REQUEST, "参数校验失败"),

    // ---- Auth ---- （ticket 02）
    /**
     * 认证失败 — 用户名 / 密码错误、账号禁用、token 缺失 / 过期 / 篡改 / 已登出。
     * <p>
     * code 取字面值 {@code "401"} 而非 {@code "S03xx"} 模块前缀格式：
     * ticket 02 验收标准明确要求登录失败返回 {@code Result.error(401, ...)}，
     * 此处服从 ticket 文档。HTTP 状态同为 401。
     */
    AUTH_UNAUTHORIZED("401", HttpStatus.UNAUTHORIZED, "认证失败"),
    /** 已认证但无权限 — {@code @PreAuthorize} 校验不通过（权限数据源见 ticket 03） */
    AUTH_FORBIDDEN("403", HttpStatus.FORBIDDEN, "没有访问权限"),

    // ---- Ticket ---- （占位，后续 ticket 扩充）
    TICKET_NOT_FOUND("T0101", HttpStatus.NOT_FOUND, "工单不存在"),
    TICKET_INVALID_TRANSITION("T0102", HttpStatus.CONFLICT, "工单状态非法迁移"),

    // ---- System ---- （ticket 03 补齐 RBAC 相关码）
    /** 用户不存在 —— {@code sys_user.id} 未命中 */
    USER_NOT_FOUND("S0101", HttpStatus.NOT_FOUND, "用户不存在"),
    /** 用户名已被占用 —— 唯一索引冲突转业务异常 */
    USER_DUPLICATE("S0102", HttpStatus.CONFLICT, "用户名已存在"),
    /** 角色不存在 —— {@code sys_role.id} 未命中 */
    ROLE_NOT_FOUND("S0201", HttpStatus.NOT_FOUND, "角色不存在"),
    /** 角色 key 重复 —— role_key 唯一索引冲突 */
    ROLE_DUPLICATE("S0202", HttpStatus.CONFLICT, "角色标识已存在"),
    /** 菜单不存在 —— {@code sys_menu.id} 未命中 */
    MENU_NOT_FOUND("S0301", HttpStatus.NOT_FOUND, "菜单不存在"),
    /** 菜单 permission 重复 —— 同一权限字符串被两个菜单占用 */
    MENU_PERMISSION_DUPLICATE("S0302", HttpStatus.CONFLICT, "菜单权限字符串已存在"),
    /** 至少需要一个超级管理员 —— 防止把所有 admin 角色全删光 */
    LAST_ADMIN_PROTECTED("S0303", HttpStatus.CONFLICT, "系统至少保留一名超级管理员"),

    // ---- System ---- （ticket 04 补齐数据字典 / 工单分类相关码）
    /** 字典条目不存在 —— {@code sys_dict.id} 未命中 */
    DICT_NOT_FOUND("S0401", HttpStatus.NOT_FOUND, "字典条目不存在"),
    /** 字典重复 —— 同 {@code dict_type} 下 {@code dict_value} 唯一索引冲突 */
    DICT_DUPLICATE("S0402", HttpStatus.CONFLICT, "同类型下字典值已存在"),
    /** 工单分类不存在 —— {@code ticket_category.id} 未命中 */
    CATEGORY_NOT_FOUND("S0501", HttpStatus.NOT_FOUND, "工单分类不存在"),
    /** 工单分类重复 —— {@code name} 唯一索引冲突 */
    CATEGORY_DUPLICATE("S0502", HttpStatus.CONFLICT, "工单分类名称已存在"),

    // ---- AI ---- （占位）
    AI_UNAVAILABLE("A0101", HttpStatus.SERVICE_UNAVAILABLE, "AI 服务不可用");

    /** 业务字符串码（如 "T0101"） */
    private final String code;
    /** HTTP 状态码 */
    private final HttpStatus httpStatus;
    /** 默认提示消息 */
    private final String message;

    BusinessExceptionCode(String code, HttpStatus httpStatus, String message) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.message = message;
    }
}