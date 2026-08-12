package com.ticket.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 模块配置属性（ticket 08）。
 * <p>
 * 绑定 {@code application.yml} 中 {@code ticket.ai.*} 段；Spring AI 自身的连接参数
 * （api-key / base-url / chat.options.*）由 {@code spring.ai.openai.*} 自动装配，
 * 不再通过本类绑定（避免与 Spring AI 内部属性双重管理）。
 * <p>
 * <b>当前 ticket 08 预留以下开关位</b>，供后续 ticket 调用：
 * <ul>
 *     <li>{@code classify-timeout-ms} —— 创建工单同步分类的超时（默认 2000ms，与 spec 一致）</li>
 *     <li>{@code model} —— 模型名（默认 {@code deepseek-chat}，与 yaml 对齐）</li>
 *     <li>{@code reply-fallback-template} —— AI 回复失败时返回的兜底字符串</li>
 * </ul>
 *
 * @param classifyTimeoutMs        同步分类超时（毫秒），超过即走默认分类
 * @param model                    模型名（记录到 ai_ticket_record.model）
 * @param replyFallbackTemplate    AI 回复失败时返回的兜底字符串模板，{@code %s} 占位为 ticketNo
 */
@ConfigurationProperties(prefix = "ticket.ai")
public record AiProperties(
        int classifyTimeoutMs,
        String model,
        String replyFallbackTemplate) {

    /**
     * Spring Boot 配置属性绑定 + 默认值兜底（缺省时给合理默认，避免空配置导致 NPE）。
     */
    public AiProperties {
        if (classifyTimeoutMs <= 0) {
            classifyTimeoutMs = 2000;
        }
        if (model == null || model.isBlank()) {
            model = "deepseek-chat";
        }
        if (replyFallbackTemplate == null || replyFallbackTemplate.isBlank()) {
            replyFallbackTemplate = "问题已记录，工单号 %s，预计 1 个工作日内回复";
        }
    }
}