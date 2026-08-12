package com.ticket.ai.client;

import com.ticket.ai.client.dto.ClassifyRequest;
import com.ticket.ai.client.dto.TicketClassifyResult;

/**
 * AI 工单分类接口（ticket 08）。
 * <p>
 * 业务模块（ticket-ticket）只依赖此 interface，不感知 Spring AI 类型；
 * 由 {@code DeepSeekClassifier} 提供默认实现。
 * <p>
 * <b>失败兜底契约</b>（双层防线第一层）：
 * 实现内部 catch 所有 RuntimeException，写 {@code ai_ticket_record.error_log}，
 * 返回 {@link TicketClassifyResult#fallback()}。<b>禁止</b>向调用方抛异常——
 * 调用方（工单创建主流程）绝不能因 AI 失败而失败。
 *
 * @see com.ticket.ai.service.impl.DeepSeekClassifier
 */
public interface TicketClassifier {

    /**
     * 同步发起 AI 分类。
     *
     * @param title   工单标题
     * @param content 工单正文
     * @return 分类结果（成功：AI 解析；失败：fallback 实现）
     */
    TicketClassifyResult classify(String title, String content);

    /** 重载：接收 {@link ClassifyRequest} 包装对象，便于实现层内部统一处理 */
    default TicketClassifyResult classify(ClassifyRequest request) {
        if (request == null) {
            return TicketClassifyResult.fallback();
        }
        return classify(request.title(), request.content());
    }
}