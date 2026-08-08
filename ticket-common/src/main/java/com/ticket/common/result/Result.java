package com.ticket.common.result;

import lombok.Data;

/**
 * 统一返回结果包装类。
 * <p>
 * 所有 REST API 强制以本类型作为响应体，格式：
 * <pre>{@code
 * {
 *   "code": "200",
 *   "message": "success",
 *   "data": { ... }
 * }
 * }</pre>
 *
 * <p>设计要点（详见 AGENTS.md / ADR-0009）：
 * <ul>
 *     <li>{@code code} 是字符串：成功为 {@code "200"}；业务错误为 {@code BusinessExceptionCode} 定义的字符串码（如 {@code "T0101"}）</li>
 *     <li>HTTP 状态由 {@link com.ticket.common.exception.GlobalExceptionHandler} 独立决定，
 *         不与 body 中的 code 绑定</li>
 * </ul>
 *
 * @param <T> 业务数据类型
 */
@Data
public class Result<T> {

    private String code;
    private String message;
    private T data;

    public Result() {
    }

    public Result(String code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /** 成功响应（带数据） */
    public static <T> Result<T> success(T data) {
        return new Result<>("200", "success", data);
    }

    /** 错误响应（指定 code + message） */
    public static <T> Result<T> error(String code, String message) {
        return new Result<>(code, message, null);
    }
}