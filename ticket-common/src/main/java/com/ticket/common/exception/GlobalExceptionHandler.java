package com.ticket.common.exception;

import com.ticket.common.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * 全局异常处理（详见 AGENTS.md 第 5 节、ADR-0009）。
 * <p>
 * 统一捕获 Controller 抛出的异常并包装为 {@link Result}：
 * <ul>
 *     <li>{@link BusinessException}：业务异常，HTTP 状态取自 {@link BusinessExceptionCode#getHttpStatus()}</li>
 *     <li>{@link AccessDeniedException}：{@code @PreAuthorize} 拒绝，HTTP 403</li>
 *     <li>{@link MethodArgumentNotValidException}：
 *         {@code @Valid @RequestBody} 校验失败，HTTP 400 + 第一个字段错误</li>
 *     <li>{@link Exception} 兜底：未预期异常，HTTP 500 + 通用消息</li>
 * </ul>
 * <p>
 * 异常日志统一在这里记录（业务异常 WARN、未知异常 ERROR）。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 业务异常 — 由 Service 层显式抛出。
     * <p>
     * HTTP 状态取自抛出时使用的 {@link BusinessExceptionCode}；
     * body 中的 {@code code} 来自 {@link BusinessException#getCode()}。
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusinessException(BusinessException ex, HttpServletRequest request) {
        log.warn("BusinessException at [{} {}]: code={}, message={}",
                request.getMethod(), request.getRequestURI(), ex.getCode(), ex.getMessage());
        BusinessExceptionCode statusSource = resolveByCodeOrFallback(ex.getCode());
        return respond(statusSource, ex.getCode(), ex.getMessage());
    }

    /**
     * {@code @PreAuthorize} 拒绝（Spring Security 6 在方法拦截器处抛 {@link AccessDeniedException}）。
     * <p>
     * 这里兜底处理的原因：当请求进入 Controller 方法前被 AOP 拦截拒绝，
     * Spring 的 {@code ExceptionTranslationFilter} 通常会被绕过（AOP 异常不经过过滤器链），
     * 直接落到 {@code @ExceptionHandler(Exception.class)}，被错误地包成 500。
     * 显式声明后能恢复正确的 403 语义。
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Result<Void>> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        log.warn("AccessDenied at [{} {}]: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        BusinessExceptionCode source = BusinessExceptionCode.AUTH_FORBIDDEN;
        return respond(source, source.getCode(), source.getMessage());
    }

    /**
     * {@code @Valid @RequestBody} 校验失败 — Spring 抛 {@link MethodArgumentNotValidException}。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                     HttpServletRequest request) {
        FieldError fe = ex.getBindingResult().getFieldErrors().stream().findFirst().orElse(null);
        String detail = fe == null
                ? "请求体校验失败"
                : fe.getField() + " " + fe.getDefaultMessage();
        log.warn("Validation failed at [{} {}]: {}", request.getMethod(), request.getRequestURI(), detail);
        BusinessExceptionCode source = BusinessExceptionCode.PARAM_INVALID;
        return respond(source, source.getCode(), source.getMessage() + ": " + detail);
    }

    /**
     * 文件大小超过上限 — Spring multipart 在请求解析阶段抛 {@link MaxUploadSizeExceededException}
     * （ticket 12：{@code spring.servlet.multipart.max-file-size} 触发）。
     * <p>
     * 统一返回 400 + 中文提示。文案刻意<b>不写具体数值</b>：容器上限在 ticket-web 配置、
     * 业务校验（{@code OssFileValidator}）在 ticket-ticket 模块，两者分属不同模块
     * 无法共享常量，写死数字会导致人肉同步漂移（改配置忘改文案 / 反之亦然）。
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Result<Void>> handleMaxUploadSize(MaxUploadSizeExceededException ex,
                                                            HttpServletRequest request) {
        log.warn("MaxUploadSizeExceeded at [{} {}]: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        BusinessExceptionCode source = BusinessExceptionCode.PARAM_INVALID;
        return respond(source, source.getCode(), "上传文件大小超出限制，请压缩后重试");
    }

    /**
     * 兜底异常 — 记录 ERROR 日志并返回通用错误。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleException(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception at [{} {}]", request.getMethod(), request.getRequestURI(), ex);
        BusinessExceptionCode source = BusinessExceptionCode.INTERNAL_ERROR;
        return respond(source, source.getCode(), source.getMessage());
    }

    /**
     * 单一聚合点 — 由 {@link BusinessExceptionCode} 决定 HTTP 状态，
     * body 的 code / message 由调用方指定。
     */
    private ResponseEntity<Result<Void>> respond(BusinessExceptionCode statusSource,
                                                 String bodyCode,
                                                 String bodyMessage) {
        return ResponseEntity
                .status(statusSource.getHttpStatus())
                .body(Result.error(bodyCode, bodyMessage));
    }

    /**
     * 由业务 code 反查枚举值；找不到时返回兜底 {@link BusinessExceptionCode#INTERNAL_ERROR}。
     * <p>
     * 线性扫描：当前枚举值 < 10 个，性能可接受。
     */
    private BusinessExceptionCode resolveByCodeOrFallback(String code) {
        for (BusinessExceptionCode value : BusinessExceptionCode.values()) {
            if (value.getCode().equals(code)) {
                return value;
            }
        }
        return BusinessExceptionCode.INTERNAL_ERROR;
    }
}