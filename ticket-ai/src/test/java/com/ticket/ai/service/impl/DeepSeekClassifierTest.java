package com.ticket.ai.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.chat.client.ChatClient.CallResponseSpec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * DeepSeekClassifier 单测（ticket 08 spec AC — 3 cases）。
 * <p>
 * <b>覆盖范围</b>：mock Spring AI ChatClient，验证：
 * <ol>
 *     <li>Happy path —— ChatClient 返回合法 JSON → 解析为 TicketClassifyResult 字段</li>
 *     <li>JSON abnormal —— ChatClient 返回自然语言 / 畸形 JSON → fallback (OTHER/MEDIUM/待人工分配)</li>
 *     <li>ChatClient exception —— ChatClient 抛 RuntimeException → fallback</li>
 * </ol>
 * <b>不</b>验证 ai_ticket_record 落库 —— ticketId 是调用方责任（由 TicketInfoServiceImpl
 * 在 create() 内统一写库，包含 success / failure 两种路径）。
 * 由 {@code TicketAiIntegrationTest} 覆盖端到端落库行为。
 */
class DeepSeekClassifierTest {

    private ChatClient chatClient;
    private ChatClientRequestSpec requestSpec;
    private CallResponseSpec callSpec;
    private DeepSeekClassifier classifier;

    @BeforeEach
    void setUp() {
        // Spring AI 1.0.0-M6 ChatClient chain：prompt → ChatClientRequestSpec → call() → CallResponseSpec → content()
        // 用 RETURNS_DEEP_STUBS 让中间环节自动 mock，避免 Mockito strict stub 警告
        // + 显式 stub 三段链路确保测试只 stub 末端也能让中间环节返回 mock 实例
        chatClient = mock(ChatClient.class);
        requestSpec = mock(ChatClientRequestSpec.class);
        callSpec = mock(CallResponseSpec.class);

        // 完整 stub 链：prompt(String) → requestSpec → call() → callSpec → content()
        when(chatClient.prompt(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);

        classifier = new DeepSeekClassifier(
                chatClient,
                new com.ticket.ai.config.AiProperties(2000, "deepseek-chat",
                        "fallback template %s"),
                mock(com.ticket.ai.service.AIRecordPersistService.class),
                new ObjectMapper());
    }

    // ---------- Case 1: Happy path ----------

    @Test
    void classify_happy_path_returns_parsed_result() {
        String validJson = "{\"type\":\"NETWORK\",\"priority\":\"HIGH\",\"department\":\"网络运维\"}";
        when(callSpec.content()).thenReturn(validJson);

        com.ticket.ai.client.dto.TicketClassifyResult result =
                classifier.classify("VPN 登录不上", "测试机访问内网 VPN 客户端报 720 错误");

        assertThat(result).isNotNull();
        assertThat(result.type()).isEqualTo(com.ticket.ai.enums.TicketClassifyType.NETWORK);
        assertThat(result.priority()).isEqualTo("HIGH");
        assertThat(result.department()).isEqualTo("网络运维");
        assertThat(result.isValid()).isTrue();
    }

    @Test
    void classify_happy_path_tolerates_markdown_fence_and_lowercase_enum() {
        // DeepSeek 部分版本会包 ```json ... ``` 围栏 + 大小写不严格
        String noisy = "好的，以下是分类结果：\n```json\n"
                + "{\"type\":\"software\",\"priority\":\"medium\",\"department\":\"应用支持\"}\n```";
        when(callSpec.content()).thenReturn(noisy);

        com.ticket.ai.client.dto.TicketClassifyResult result =
                classifier.classify("软件崩溃", "Excel 打开文件闪退");

        assertThat(result).isNotNull();
        assertThat(result.type()).isEqualTo(com.ticket.ai.enums.TicketClassifyType.SOFTWARE);
        assertThat(result.priority()).isEqualTo("MEDIUM");
        assertThat(result.department()).isEqualTo("应用支持");
    }

    // ---------- Case 2: JSON abnormal ----------

    @Test
    void classify_returns_fallback_when_response_is_natural_language() {
        when(callSpec.content()).thenReturn("我不太确定这个工单属于哪个分类");

        com.ticket.ai.client.dto.TicketClassifyResult result =
                classifier.classify("奇怪的问题", "描述含糊不清");

        assertThat(result).isNotNull();
        // 兜底值（spec 明确要求）：OTHER / MEDIUM / 待人工分配
        assertThat(result.type()).isEqualTo(com.ticket.ai.enums.TicketClassifyType.OTHER);
        assertThat(result.priority()).isEqualTo("MEDIUM");
        assertThat(result.department()).isEqualTo("待人工分配");
    }

    @Test
    void classify_returns_fallback_when_response_is_malformed_json() {
        when(callSpec.content()).thenReturn("{type: invalid}");

        com.ticket.ai.client.dto.TicketClassifyResult result =
                classifier.classify("x", "y");

        assertThat(result.type()).isEqualTo(com.ticket.ai.enums.TicketClassifyType.OTHER);
        assertThat(result.priority()).isEqualTo("MEDIUM");
        assertThat(result.department()).isEqualTo("待人工分配");
    }

    @Test
    void classify_returns_fallback_when_required_fields_missing() {
        when(callSpec.content()).thenReturn("{\"type\":\"NETWORK\"}");

        com.ticket.ai.client.dto.TicketClassifyResult result =
                classifier.classify("x", "y");

        assertThat(result.type()).isEqualTo(com.ticket.ai.enums.TicketClassifyType.OTHER);
    }

    @Test
    void classify_returns_fallback_when_type_enum_value_unknown() {
        when(callSpec.content()).thenReturn(
                "{\"type\":\"UNKNOWN_CATEGORY\",\"priority\":\"HIGH\",\"department\":\"X\"}");

        com.ticket.ai.client.dto.TicketClassifyResult result =
                classifier.classify("x", "y");

        assertThat(result.type()).isEqualTo(com.ticket.ai.enums.TicketClassifyType.OTHER);
    }

    // ---------- Case 3: ChatClient exception ----------

    @Test
    void classify_returns_fallback_when_chat_client_throws() {
        when(callSpec.content()).thenThrow(new RuntimeException("simulated API outage"));

        com.ticket.ai.client.dto.TicketClassifyResult result =
                classifier.classify("x", "y");

        assertThat(result).isNotNull();
        assertThat(result.type()).isEqualTo(com.ticket.ai.enums.TicketClassifyType.OTHER);
        assertThat(result.priority()).isEqualTo("MEDIUM");
        assertThat(result.department()).isEqualTo("待人工分配");
    }

    @Test
    void classify_returns_fallback_when_chat_client_returns_null() {
        when(callSpec.content()).thenReturn(null);

        com.ticket.ai.client.dto.TicketClassifyResult result =
                classifier.classify("x", "y");

        assertThat(result.type()).isEqualTo(com.ticket.ai.enums.TicketClassifyType.OTHER);
    }

    @Test
    void classify_handles_null_request_gracefully() {
        // ClassifyRequest overload：null 入参 → 直接 fallback
        com.ticket.ai.client.dto.TicketClassifyResult result =
                classifier.classify((com.ticket.ai.client.dto.ClassifyRequest) null);

        assertThat(result.type()).isEqualTo(com.ticket.ai.enums.TicketClassifyType.OTHER);
    }
}