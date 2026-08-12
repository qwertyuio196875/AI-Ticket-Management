package com.ticket.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 调用记录实体（ticket 08 ai_ticket_record）。
 * <p>
 * DeepSeek AI 调用的完整审计：成功响应 / 失败 error_log / 模型 / Prompt 版本号。
 * <p>
 * <b>为什么 ticket_id 不加外键约束？</b>AI 记录是"异步审计"性质，与 ticket_info 不同事务
 *（详见 ADR-0012），加 FK 会让 ticket_info 删除/重置时连带删 AI 记录——丢失审计。
 * 当前为软引用关系，由应用层保证语义一致。
 *
 * @author AI 集成（ticket 08）
 */
@Data
@TableName("ai_ticket_record")
public class AiTicketRecord {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 工单主键 ticket_info.id（软引用） */
    private Long ticketId;

    /** 调用类型 CLASSIFY / REPLY（见 AiCallType） */
    private String callType;

    /** 模型名，如 deepseek-chat */
    private String model;

    /** Prompt 版本号（v1/v2/...，留空 = 未启用版本管理） */
    private String promptVersion;

    /** AI 原始响应（成功：响应内容；失败：空串） */
    private String responseContent;

    /** 失败时的异常摘要（异常类名 + message），成功为 null */
    private String errorLog;

    /** 是否成功：1 成功 / 0 失败 */
    private Boolean success;

    /** 创建时间 */
    private LocalDateTime createTime;
}