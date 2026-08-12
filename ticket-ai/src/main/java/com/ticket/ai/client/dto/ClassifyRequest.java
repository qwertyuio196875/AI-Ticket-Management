package com.ticket.ai.client.dto;

/**
 * AI 分类请求 DTO（ticket 08）。
 * <p>
 * 业务上把 {@code title} + {@code content} 打包传给 {@link com.ticket.ai.client.TicketClassifier}，
 * 便于实现层统一渲染 Prompt 占位符。
 * <p>
 * <b>注意</b>：虽然业务接口 {@code TicketClassifier.classify(title, content)} 直接收两个参数，
 * 实现层用本 DTO 包装后再渲染 Prompt 是常见做法——这样新增字段（如 priority hint、user role）
 * 不影响接口签名。
 *
 * @param title   工单标题
 * @param content 工单正文
 */
public record ClassifyRequest(String title, String content) {

    /** title/content 任一为空时仍然构造（业务层负责判空）；提供判空便利方法 */
    public boolean isBlank() {
        return (title == null || title.isBlank())
                && (content == null || content.isBlank());
    }
}