package com.ticket.ai.enums;

/**
 * AI 调用类型（ticket 08 ai_ticket_record.call_type）。
 * <p>
 * 用于审计日志区分两种调用场景：
 * <ul>
 *     <li>CLASSIFY —— 工单创建时的自动分类（2s 短超时）</li>
 *     <li>REPLY —— 工单详情页"AI 智能回复"按钮触发的回复（30s 超时）</li>
 * </ul>
 */
public enum AiCallType {

    /** 工单创建时的自动分类 */
    CLASSIFY,
    /** 工单详情页触发的智能回复 */
    REPLY
}