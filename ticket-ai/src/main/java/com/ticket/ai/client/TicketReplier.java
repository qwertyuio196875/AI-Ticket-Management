package com.ticket.ai.client;

import com.ticket.ai.client.dto.ReplyContext;
import com.ticket.ai.client.dto.ReplyResult;

/**
 * AI 智能回复接口（ticket 08）。
 * <p>
 * 业务模块（ticket-web 详情页）调用此接口触发 AI 排查思路 / 解决方案回复。
 * <p>
 * <b>失败兜底契约</b>（双层防线第一层）：
 * 实现内部 catch 所有 RuntimeException，写 {@code ai_ticket_record.error_log}（完整异常类名+message），
 * 返回 {@link ReplyResult}（fallback=true，reply=内置模板回复字符串）。
 * <b>禁止</b>向调用方抛异常——AI 失败也要让前端拿到可展示的字符串。
 *
 * @see com.ticket.ai.service.impl.DeepSeekReplier
 */
public interface TicketReplier {

    /**
     * 同步发起 AI 智能回复（30s 超时，Spring AI 默认）。
     *
     * @param ctx 工单上下文（基本信息 + 多轮对话历史）
     * @return AI 回复结果；失败时 {@code reply}=模板兜底字符串，{@code fallback}=true
     */
    ReplyResult reply(ReplyContext ctx);
}
