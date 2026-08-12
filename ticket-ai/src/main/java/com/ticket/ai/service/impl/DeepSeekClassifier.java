package com.ticket.ai.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticket.ai.client.TicketClassifier;
import com.ticket.ai.client.dto.ClassifyRequest;
import com.ticket.ai.client.dto.TicketClassifyResult;
import com.ticket.ai.config.AiProperties;
import com.ticket.ai.enums.AiCallType;
import com.ticket.ai.enums.TicketClassifyType;
import com.ticket.ai.service.AIRecordPersistService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Objects;

/**
 * DeepSeek 实现的 {@link TicketClassifier}（ticket 08）。
 * <p>
 * <b>调用链路</b>：{@code classify(title, content)} → 加载 {@code classify.st} →
 * PromptTemplate 渲染占位符 → {@code chatClient.call(prompt)} →
 * 从 {@code ChatResponse.getResult().getOutput().getContent()} 拿字符串 → 强约束 JSON 解析 →
 * 返回 {@link TicketClassifyResult}。
 * <p>
 * <b>失败降级（双层防线第一层）</b>：
 * <ul>
 *     <li>Spring AI 抛 {@link RuntimeException}（网络 / 超时 / 4xx / 5xx）→ 写 {@code ai_ticket_record}（success=false，error_log=异常摘要）→ 返回 {@link TicketClassifyResult#fallback()}</li>
 *     <li>AI 返回的字符串不是合法 JSON / 字段缺失 / enum 值越界 → 同样走 fallback（视作"AI 调用成功但结果不可用"）</li>
 * </ul>
 * 任何分支都不向调用方抛异常——保证工单创建主流程不阻塞。
 *
 * @see TicketClassifier
 */
@Service
public class DeepSeekClassifier implements TicketClassifier {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekClassifier.class);

    /** classify.st 路径（resources/prompts/ 下） */
    private static final String CLASSIFY_PROMPT_RESOURCE = "prompts/classify.st";

    private final ChatClient chatClient;
    private final AiProperties aiProperties;
    private final AIRecordPersistService recordPersistService;
    private final ObjectMapper objectMapper;

    /** PromptTemplate 在类初始化时一次性加载（资源文件不变，重复加载无意义） */
    private final PromptTemplate classifyPromptTemplate;

    @Autowired
    public DeepSeekClassifier(ChatClient chatClient,
                              AiProperties aiProperties,
                              AIRecordPersistService recordPersistService,
                              ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.aiProperties = aiProperties;
        this.recordPersistService = recordPersistService;
        this.objectMapper = objectMapper;
        this.classifyPromptTemplate = new PromptTemplate(
                new ClassPathResource(CLASSIFY_PROMPT_RESOURCE));
    }

    /**
     * {@inheritDoc}
     * <p>
     * <b>关于 ticketId</b>：实现层需要 ticketId 才能写 {@code ai_ticket_record}，但 interface
     * 不暴露此字段——调用方（TicketInfoServiceImpl.create）拿到 ticketId 后再写记录。
     * 当前实现仅返回 {@link TicketClassifyResult}，{@code ai_ticket_record} 的写入由调用方
     * 在 create() 内统一处理（既覆盖 CLASSIFY 成功 / 失败，也覆盖 REPLY 场景）。
     * <p>
     * 注：这条方法**不直接写** ai_ticket_record——这是为了避免与调用方"事务外落库"约束冲突。
     * 调用方（TicketInfoServiceImpl）拿到 result 后，再显式调
     * {@link AIRecordPersistService#saveSuccess} 或 {@link AIRecordPersistService#saveFailure}
     * 落 ai_ticket_record，并控制事务边界。
     */
    @Override
    public TicketClassifyResult classify(String title, String content) {
        return doClassify(new ClassifyRequest(title, content));
    }

    @Override
    public TicketClassifyResult classify(ClassifyRequest request) {
        if (request == null) {
            return TicketClassifyResult.fallback();
        }
        return doClassify(request);
    }

    /** 实际调 Spring AI 的私有方法 */
    private TicketClassifyResult doClassify(ClassifyRequest request) {
        try {
            // 1. 渲染 Prompt：classify.st 用 ${ticketTitle} / ${ticketContent} 占位
            String renderedPrompt = classifyPromptTemplate.render(Map.of(
                    "ticketTitle", safeStr(request.title()),
                    "ticketContent", safeStr(request.content())));

            // 2. 调 Spring AI ChatClient
            ChatClient.ChatClientRequestSpec spec = chatClient.prompt(renderedPrompt);
            String responseText = Objects.requireNonNullElse(
                    spec.call().content(), "");

            // 3. 解析响应 JSON
            TicketClassifyResult parsed = parseResponse(responseText);
            if (parsed == null || !parsed.isValid()) {
                // JSON 结构不合法 / 字段缺失 → 兜底（视作 AI 调用成功但结果不可用）
                log.warn("AI 分类响应解析失败: title={}, response={}",
                        safeStr(request.title()), truncate(responseText, 200));
                return TicketClassifyResult.fallback();
            }
            return parsed;
        } catch (RuntimeException ex) {
            // Spring AI 内部异常 → 兜底
            log.warn("AI 分类调用异常: title={}, cause={}",
                    safeStr(request.title()), ex.toString());
            return TicketClassifyResult.fallback();
        }
    }

    /**
     * 把 AI 返回的字符串解析为 {@link TicketClassifyResult}。
     * <p>
     * 实现说明：
     * <ul>
     *     <li>先尝试 Jackson 解析整段为 {@link JsonNode}</li>
     *     <li>部分模型会在 JSON 前后包 Markdown 围栏（```json ... ```），
     *         用 {@link #extractJsonObject(String)} 提取首对花括号</li>
     *     <li>枚举值大小写不敏感（DeepSeek 可能返回 "network" 而非 "NETWORK"）</li>
     * </ul>
     *
     * @return 解析成功返回 result；失败返回 null（由调用方走兜底）
     */
    private TicketClassifyResult parseResponse(String responseText) {
        if (responseText == null || responseText.isBlank()) {
            return null;
        }
        try {
            String json = extractJsonObject(responseText);
            if (json == null) {
                return null;
            }
            JsonNode node = objectMapper.readTree(json);

            String typeStr = textOrNull(node, "type");
            String priorityStr = textOrNull(node, "priority");
            String department = textOrNull(node, "department");

            if (typeStr == null || priorityStr == null || department == null) {
                return null;
            }

            TicketClassifyType type = parseType(typeStr);
            if (type == null) {
                return null;
            }
            // priority 不做严格 enum 校验 —— 业务字典可能扩展
            String priority = priorityStr.trim().toUpperCase();
            return new TicketClassifyResult(type, priority, department.trim());
        } catch (Exception ex) {
            // 覆盖 Jackson 抛出的 checked IOException 等所有异常
            return null;
        }
    }

    /** 大小写不敏感的 enum 解析；返回 null 表示解析失败 */
    private static TicketClassifyType parseType(String raw) {
        try {
            return TicketClassifyType.valueOf(raw.trim().toUpperCase());
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /**
     * 从响应文本中提取首对花括号包围的 JSON 子串。
     * <p>
     * DeepSeek 部分版本会包 Markdown 围栏或加前缀解释，本方法剥掉这些噪声。
     */
    private static String extractJsonObject(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        return text.substring(start, end + 1);
    }

    /** 读 JsonNode 字符串字段；null / 非字符串返回 null */
    private static String textOrNull(JsonNode node, String field) {
        JsonNode child = node.get(field);
        if (child == null || child.isNull() || !child.isTextual()) {
            return null;
        }
        String value = child.asText();
        return value.isBlank() ? null : value;
    }

    /** null-safe toString；空入参返回 "" */
    private static String safeStr(String s) {
        return s == null ? "" : s;
    }

    /** 简单字符串截断（避免日志过长） */
    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}