package com.ticket.ai.service.impl;

import com.ticket.ai.client.TicketReplier;
import com.ticket.ai.client.dto.ChatMessage;
import com.ticket.ai.client.dto.ReplyContext;
import com.ticket.ai.client.dto.ReplyResult;
import com.ticket.ai.config.AiProperties;
import com.ticket.ai.enums.AiCallType;
import com.ticket.ai.service.AIRecordPersistService;
import com.ticket.ai.service.ChatContentExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * DeepSeek 实现的 {@link TicketReplier}（ticket 08）。
 * <p>
 * <b>调用链路</b>：{@code reply(ctx)} → 加载 {@code reply.st} 作为系统 Prompt →
 * 用 ticketNo / ticketTitle / ticketContent / status 渲染占位符 →
 * 拼接 ctx.historyMessages（业务中性的 {@link ChatMessage}）→
 * 转换为 Spring AI {@link Message} → 构造 {@code Prompt(List<Message>)} →
 * {@link ChatContentExtractor#extract(Prompt)} → 返回字符串。
 * <p>
 * <b>依赖设计</b>：本实现只依赖 {@link ChatContentExtractor} 接口（不依赖 Spring AI
 * ChatClient）。Spring 配置层（{@code AiConfig}）把 ChatClient 包成 ChatContentExtractor。
 * 这样：
 * <ul>
 *     <li>业务模块（ticket-ticket）只看到 {@link TicketReplier}，不感知 Spring AI 类型</li>
 *     <li>单测可以用 Mockito 直接 mock {@code ChatContentExtractor.extract()}，无需 chain stub</li>
 * </ul>
 * <p>
 * <b>多轮对话历史</b>：业务层（ticket-ticket AI 回复端点）从 {@code ticket_comment} 表读历史评论
 * 后，组装成 {@link List<ChatMessage>}（USER / ASSISTANT）传入。内部实现负责转换为
 * Spring AI {@link Message} 类型。
 * <p>
 * <b>失败降级（双层防线第一层）</b>：
 * <ul>
 *     <li>Spring AI 抛 {@link RuntimeException} → 写 {@code ai_ticket_record}（success=false，error_log=完整异常类名+message）
 *         → 返回 {@link ReplyResult}（fallback=true，reply=兜底字符串）</li>
 *     <li>AI 返回 null / blank → 同样写 {@code ai_ticket_record}（success=false，error_log="empty response"）
 *         → 返回 {@link ReplyResult}（fallback=true，reply=兜底字符串）</li>
 * </ul>
 * 任何分支不向调用方抛异常。
 *
 * @see TicketReplier
 */
@Service
public class DeepSeekReplier implements TicketReplier {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekReplier.class);

    /** reply.st 路径 */
    private static final String REPLY_PROMPT_RESOURCE = "prompts/reply.st";

    private final ChatContentExtractor chatContentExtractor;
    private final AiProperties aiProperties;
    private final AIRecordPersistService recordPersistService;

    private final PromptTemplate systemPromptTemplate;

    @Autowired
    public DeepSeekReplier(ChatContentExtractor chatContentExtractor,
                           AiProperties aiProperties,
                           AIRecordPersistService recordPersistService) {
        this.chatContentExtractor = chatContentExtractor;
        this.aiProperties = aiProperties;
        this.recordPersistService = recordPersistService;
        this.systemPromptTemplate = new PromptTemplate(
                new ClassPathResource(REPLY_PROMPT_RESOURCE));
    }

    @Override
    public ReplyResult reply(ReplyContext ctx) {
        if (ctx == null) {
            return ReplyResult.fallback(aiProperties.replyFallbackTemplate(), "unknown");
        }

        String ticketNo = safeStr(ctx.ticketNo());
        // 用于落库的 ticketId（从 ticketNo 上下文无法获取，这里用 0L 占位）
        Long ticketIdForRecord = 0L;

        try {
            // 1. 系统 Prompt 渲染（reply.st 的占位符）
            String systemText = systemPromptTemplate.render(Map.of(
                    "ticketNo", ticketNo,
                    "ticketTitle", safeStr(ctx.ticketTitle()),
                    "ticketContent", safeStr(ctx.ticketContent()),
                    "status", safeStr(ctx.status())));

            // 2. 拼接多轮对话历史（业务中性的 ChatMessage → Spring AI Message）
            List<Message> messages = new ArrayList<>();
            messages.add(new SystemMessage(systemText));
            if (ctx.historyMessages() != null) {
                for (ChatMessage cm : ctx.historyMessages()) {
                    messages.add(toSpringAiMessage(cm));
                }
            }

            Prompt prompt = new Prompt(messages);

            // 3. 通过 ChatContentExtractor 调 AI
            String responseText = chatContentExtractor.extract(prompt);

            // 4. 失败兜底（响应为空）
            if (responseText == null || responseText.isBlank()) {
                log.warn("AI 智能回复响应为空: ticketNo={}", ticketNo);
                // 写 ai_ticket_record（Layer 1 落库职责）
                persistFailure(ticketIdForRecord, "deepseek-chat", "empty response");
                return ReplyResult.fallback(aiProperties.replyFallbackTemplate(), ticketNo);
            }

            // 成功：不写 ai_ticket_record（reply 场景的落库由 caller 在业务层统一处理）
            return new ReplyResult(responseText, false);
        } catch (RuntimeException ex) {
            log.warn("AI 智能回复调用异常: ticketNo={}, cause={}", ticketNo, ex.toString());
            // 写 ai_ticket_record（Layer 1 落库职责），error_log 用完整异常类名+message
            String errorLog = ex.getClass().getName() + ": " + (ex.getMessage() != null ? ex.getMessage() : ex.toString());
            persistFailure(ticketIdForRecord, "deepseek-chat", errorLog);
            return ReplyResult.fallback(aiProperties.replyFallbackTemplate(), ticketNo);
        }
    }

    /**
     * 将业务中性的 {@link ChatMessage} 转换为 Spring AI 的 {@link Message}。
     */
    private Message toSpringAiMessage(ChatMessage cm) {
        if (cm == null || cm.content() == null) {
            return new UserMessage("");
        }
        return switch (cm.role()) {
            case USER -> new UserMessage(cm.content());
            case ASSISTANT -> new AssistantMessage(cm.content());
            case SYSTEM -> new SystemMessage(cm.content());
        };
    }

    /** 落库失败记录（失败时调用，不抛异常） */
    private void persistFailure(Long ticketId, String model, String errorLog) {
        try {
            recordPersistService.saveFailure(ticketId, AiCallType.REPLY, model, "", errorLog);
        } catch (RuntimeException ex) {
            // 落库失败不影响主流程，只记 warn
            log.warn("AI 回复失败记录落库失败: ticketId={}, cause={}", ticketId, ex.toString());
        }
    }

    /** null-safe toString；空入参返回 "" */
    private static String safeStr(String s) {
        return s == null ? "" : s;
    }
}
