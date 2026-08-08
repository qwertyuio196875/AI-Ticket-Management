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
 * 后续 ticket 中将按需扩充。当前仅放置 tracer bullet 阶段需要的基础码：
 * <ul>
 *     <li>{@link #INTERNAL_ERROR}：兜底异常</li>
 *     <li>{@link #PARAM_INVALID}：参数校验失败</li>
 * </ul>
 */
@Getter
public enum BusinessExceptionCode {

    // ---- Common ----
    /** 兜底异常 — 任何未预期的服务端错误 */
    INTERNAL_ERROR("C0500", HttpStatus.INTERNAL_SERVER_ERROR, "系统内部错误"),
    /** 参数校验失败 — {@code @Valid} / {@code @RequestBody} / {@code @PathVariable} */
    PARAM_INVALID("C0400", HttpStatus.BAD_REQUEST, "参数校验失败"),

    // ---- Ticket ---- （占位，后续 ticket 扩充）
    TICKET_NOT_FOUND("T0101", HttpStatus.NOT_FOUND, "工单不存在"),
    TICKET_INVALID_TRANSITION("T0102", HttpStatus.CONFLICT, "工单状态非法迁移"),

    // ---- System ---- （占位）
    USER_NOT_FOUND("S0101", HttpStatus.NOT_FOUND, "用户不存在"),
    ROLE_NOT_FOUND("S0201", HttpStatus.NOT_FOUND, "角色不存在"),

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