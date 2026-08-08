package com.ticket.common.exception;

import lombok.Getter;

/**
 * 业务异常。
 * <p>
 * Service 层抛出业务级别的错误，全局异常处理（{@link GlobalExceptionHandler}）
 * 会捕获并包装为 {@code Result.error(code, message)} 返回给前端，
 * 其中 HTTP 状态由 {@link BusinessExceptionCode#getHttpStatus()} 决定。
 *
 * @see GlobalExceptionHandler
 * @see BusinessExceptionCode
 */
@Getter
public class BusinessException extends RuntimeException {

    /** 业务异常码（字符串格式，如 "T0101"），由 {@link BusinessExceptionCode} 给出 */
    private final String code;

    public BusinessException(String code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    /**
     * 基于枚举的便捷构造。
     */
    public static BusinessException of(BusinessExceptionCode codeEnum) {
        return new BusinessException(codeEnum.getCode(), codeEnum.getMessage());
    }

    /**
     * 基于枚举 + 自定义消息的便捷构造。
     */
    public static BusinessException of(BusinessExceptionCode codeEnum, String message) {
        return new BusinessException(codeEnum.getCode(), message);
    }
}