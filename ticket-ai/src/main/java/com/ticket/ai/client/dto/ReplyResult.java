package com.ticket.ai.client.dto;

/**
 * AI 智能回复结果（ticket 08）。
 * <p>
 * 用 {@code reply + fallback} 替代裸 String 返回，让 caller 准确知道本次回复是否降级。
 */
public record ReplyResult(String reply, boolean fallback) {

    /**
     * 构建降级结果（fallback=true）。
     *
     * @param fallbackTemplate 兜底模板字符串（含 {@code %s} 占位符）
     * @param ticketNo         工单号，用于填充占位符
     * @return reply=格式化后的兜底字符串，fallback=true
     */
    public static ReplyResult fallback(String fallbackTemplate, String ticketNo) {
        return new ReplyResult(String.format(fallbackTemplate, ticketNo), true);
    }
}
