package com.ticket.ticket.aspect;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * HTTP 请求审计注解（ticket 05）。
 * <p>
 * 标注在 Controller 的写端点方法上，
 * {@link OperationLogAspect} 会自动在方法执行前后织入审计逻辑，
 * 把调用者 / API / 入参 / 耗时 / IP / UA 写入 {@code operation_log} 表。
 * <p>
 * <b>典型用法</b>：
 * <pre>{@code
 * @PostMapping
 * @PreAuthorize("hasAuthority('ticket:create')")
 * @OperationLog(value = "创建工单", type = "TICKET")
 * public Result<Long> create(@Valid @RequestBody TicketCreateDTO dto) { ... }
 * }</pre>
 *
 * @see OperationLogAspect
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperationLog {

    /**
     * 操作描述（中文），写入 {@code operation_log.operation}。
     * <p>
     * 例：{@code "创建工单"} / {@code "更新工单"} / {@code "删除工单"}。
     */
    String value();

    /**
     * 操作类型（模块标识），写入 {@code operation_log.type}。
     * <p>
     * 例：{@code "TICKET"} / {@code "USER"}。留空表示未分类。
     * <p>
     * 用 String 而非 enum —— 避免新增模块时改注解源文件。
     */
    String type() default "";
}
