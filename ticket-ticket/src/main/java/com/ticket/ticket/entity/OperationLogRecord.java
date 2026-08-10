package com.ticket.ticket.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * HTTP 请求审计日志 {@code operation_log}（ticket 05）。
 * <p>
 * 类名刻意叫 {@code OperationLogRecord} —— 与 {@code com.ticket.ticket.aspect.OperationLog}
 * 注解（public API）同名会触发简单名冲突（注解参数类型与 entity 同名时 Spring 注入会混乱）。
 * 实体类名带 {@code Record} 后缀是项目内的约定，避免与 @OperationLog 注解同名；表名仍然是
 * {@code operation_log}，通过 {@code @TableName} 显式映射。
 * <p>
 * 由 {@code OperationLogAspect} 在 Controller 边界自动切面写入，记录调用者 / API / 入参出参 / IP / UA / 耗时。
 * Controller 写端点统一加 {@code @OperationLog} 注解即可。
 * <p>
 * <b>写入语义</b>：
 * <ul>
 *     <li>审计失败 try-catch 吞掉 —— 不影响业务（详见 {@code OperationLogAspect}）</li>
 *     <li>{@code params} 字段：截断到 2000 字符（防大对象 / 文件上传炸日志）</li>
 * </ul>
 */
@Data
@TableName("operation_log")
public class OperationLogRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 当前登录用户 id，未登录时为 null（如匿名 ping） */
    private Long userId;

    /** 当前登录用户名，未登录时为空串 */
    private String username;

    /**
     * 操作描述（来自 {@code @OperationLog("创建工单").value()}）。
     * <p>
     * 例："创建工单" / "更新工单" / "删除工单"。
     */
    private String operation;

    /**
     * 操作类型（来自 {@code @OperationLog(...).type()}，如 "TICKET" / "USER"）。
     * <p>
     * 留字符串而非枚举，方便后续 ticket 加新类型时不影响 DDL。
     */
    private String type;

    /**
     * 调用的方法签名（{@code ClassName.methodName}）。
     * <p>
     * 不带参数（参数放 {@link #params}），便于按方法聚类。
     */
    private String method;

    /**
     * 请求入参 JSON 字符串。
     * <p>
     * 由切面在方法执行前序列化，截断 2000 字符防爆。
     */
    private String params;

    /** 客户端 IP，来自 {@code request.getRemoteAddr()} */
    private String ip;

    /** User-Agent 头，截断 500 字符 */
    private String userAgent;

    /**
     * 方法耗时（毫秒）—— 切面用 {@code System.currentTimeMillis()} 计算。
     * <p>
     * 字段名按 spec AC 用 {@code duration_ms}，DB 列名同之。
     */
    private Long durationMs;

    /** 创建时间 —— DB 默认 CURRENT_TIMESTAMP */
    private LocalDateTime createTime;
}
