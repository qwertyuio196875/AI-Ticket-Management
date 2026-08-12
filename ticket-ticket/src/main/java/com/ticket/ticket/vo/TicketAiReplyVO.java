package com.ticket.ticket.vo;

import lombok.Data;

/**
 * AI 智能回复响应 VO（ticket 08）。
 *
 * @param reply     AI 智能回复内容
 * @param recordId  ai_ticket_record.id；落库失败时为 null
 * @param fallback  是否降级（true=模板兜底，false=真实 AI 回复）
 */
@Data
public class TicketAiReplyVO {

    private String reply;

    private Long recordId;

    private Boolean fallback;
}
