package com.ticket.ai.client.dto;

import java.util.List;

/**
 * AI 智能回复上下文（ticket 08）。
 * <p>
 * 把工单基本信息 + 多轮对话历史打包给 {@link com.ticket.ai.client.TicketReplier}。
 * <p>
 * <b>关于 {@code historyMessages} 用业务中性的 {@link ChatMessage} 类型</b>：
 * <ul>
 *     <li>业务模块（ticket-ticket）只看到 {@link ChatMessage}，不感知 Spring AI 内部类型</li>
 *     <li>{@code ticket-ai} 内部实现（DeepSeekReplier）负责将 {@link ChatMessage}
 *         转换为 Spring AI 的 {@code org.springframework.ai.chat.messages.Message}</li>
 * </ul>
 *
 * @param ticketNo        工单号（用于回复模板里嵌入业务可读的工单号）
 * @param ticketTitle     工单标题
 * @param ticketContent   工单原始内容
 * @param status          工单当前状态（PENDING / PROCESSING / RESOLVED / CLOSED），帮助模型理解上下文
 * @param historyMessages 多轮对话历史（{@link ChatMessage.ChatRole#USER} / {@link ChatMessage.ChatRole#ASSISTANT} 列表，按时间正序）
 */
public record ReplyContext(
        String ticketNo,
        String ticketTitle,
        String ticketContent,
        String status,
        List<ChatMessage> historyMessages) {

    /** 紧凑构造：防御性处理 historyMessages 为 null 的情况（避免下游 NPE） */
    public ReplyContext {
        if (historyMessages == null) {
            historyMessages = List.of();
        }
    }
}
