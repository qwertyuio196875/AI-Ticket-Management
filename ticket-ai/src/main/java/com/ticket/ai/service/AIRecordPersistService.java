package com.ticket.ai.service;

import com.ticket.ai.enums.AiCallType;
import com.ticket.ai.entity.AiTicketRecord;

/**
 * AI 调用记录持久化服务（ticket 08 ai_ticket_record）。
 * <p>
 * 由 {@link com.ticket.ai.service.impl.DeepSeekClassifier} /
 * {@link com.ticket.ai.service.impl.DeepSeekReplier} 在 AI 调用结束（成功或失败）后调用，
 * 写入 {@code ai_ticket_record} 行，供后续审计 / 失败排查使用。
 * <p>
 * <b>事务边界</b>：与 ticket_info 不同事务（ADR-0012）；AI 落库失败不影响主业务流——
 * 实现层捕获后只记 warn 日志，不抛异常给调用方。
 */
public interface AIRecordPersistService {

    /**
     * 写入 AI 调用记录。
     *
     * @param recordId        自增主键（保留位，方便调用方引用；当前实现下由 DB 自动生成，可传 null）
     * @param ticketId        工单 id
     * @param callType        调用类型（CLASSIFY / REPLY）
     * @param model           模型名（如 deepseek-chat）
     * @param promptVersion   Prompt 版本号（如 v1，留空）
     * @param responseContent 成功响应内容（成功写这里；失败写空串）
     * @param errorLog        失败异常摘要（成功写 null）
     * @return 新插入行的 id（DB 自增主键）；落库失败时返回 -1L，由调用方降级处理
     */
    Long save(Long recordId,
              Long ticketId,
              AiCallType callType,
              String model,
              String promptVersion,
              String responseContent,
              String errorLog);

    /**
     * 写一条成功记录（便捷方法）。
     */
    default Long saveSuccess(Long ticketId, AiCallType callType, String model,
                             String promptVersion, String responseContent) {
        return save(null, ticketId, callType, model, promptVersion, responseContent, null);
    }

    /**
     * 写一条失败记录（便捷方法）。
     */
    default Long saveFailure(Long ticketId, AiCallType callType, String model,
                             String promptVersion, String errorLog) {
        return save(null, ticketId, callType, model, promptVersion, "", errorLog);
    }

    /**
     * 由 {@link com.ticket.ai.entity.AiTicketRecord} 直接写库（保留扩展位）。
     * <p>
     * recordId 传 null（由 DB 自增生成）；callType 从 String 转回 enum。
     */
    default Long save(AiTicketRecord record) {
        return save(null,
                record.getTicketId(),
                record.getCallType() == null ? null : AiCallType.valueOf(record.getCallType()),
                record.getModel(),
                record.getPromptVersion(),
                record.getResponseContent(),
                record.getErrorLog());
    }
}