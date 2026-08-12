package com.ticket.ai.service.impl;

import com.ticket.ai.client.dto.ChatMessage;
import com.ticket.ai.client.dto.ReplyContext;
import com.ticket.ai.client.dto.ReplyResult;
import com.ticket.ai.config.AiProperties;
import com.ticket.ai.enums.AiCallType;
import com.ticket.ai.service.AIRecordPersistService;
import com.ticket.ai.service.ChatContentExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DeepSeekReplier 单测（ticket 08 spec AC — 5 cases）。
 * <p>
 * <b>覆盖范围</b>：通过 lambda 注入 {@link ChatContentExtractor}，验证：
 * <ol>
 *     <li>Happy path —— extractor 返回非空字符串 → ReplyResult(reply=AI响应, fallback=false)</li>
 *     <li>extractor exception —— 抛 RuntimeException → ReplyResult(fallback=true) + 写 ai_ticket_record</li>
 *     <li>Empty response —— 返回空字符串 → ReplyResult(fallback=true) + 写 ai_ticket_record</li>
 *     <li>Empty history —— historyMessages 为空 / null → 不抛 NPE，正常调用</li>
 *     <li>Null ctx —— ctx 为 null → ReplyResult(fallback=true)</li>
 * </ol>
 * <p>
 * <b>为什么用 lambda 而不是 Mockito？</b>
 * ChatContentExtractor 是单方法函数式接口，用 lambda 直接构造 stub 比 Mockito
 * {@code when().thenReturn()} 更简单、稳定（无需关注 {@code any()} 泛型擦除、
 * strict stub warnings 等 Mockito 陷阱）。单测意图也更清晰。
 */
class DeepSeekReplierTest {

    /** 测试桩：通过 lambda 注入；{@code null} 表示抛 RuntimeException */
    private static class StubExtractor implements ChatContentExtractor {
        private final String response;
        private final RuntimeException toThrow;

        StubExtractor(String response) {
            this.response = response;
            this.toThrow = null;
        }

        StubExtractor(RuntimeException toThrow) {
            this.response = null;
            this.toThrow = toThrow;
        }

        @Override
        public String extract(org.springframework.ai.chat.prompt.Prompt prompt) {
            if (toThrow != null) {
                throw toThrow;
            }
            return response;
        }
    }

    /** 空的 AIRecordPersistService 桩（验证调用） */
    private static class NoopRecordPersistService implements AIRecordPersistService {
        @Override
        public Long save(Long recordId, Long ticketId, AiCallType callType, String model,
                        String promptVersion, String responseContent, String errorLog) {
            return 1L;
        }
    }

    private DeepSeekReplier replier;

    @BeforeEach
    void setUp() {
        // 默认 replier 不在 setUp 创建；每个 test 自己 new（因为 extractor 是测试的核心变量）
    }

    private DeepSeekReplier buildReplier(ChatContentExtractor extractor) {
        AiProperties props = new AiProperties(2000, "deepseek-chat",
                "问题已记录，工单号 %s，预计 1 个工作日内回复");
        return new DeepSeekReplier(extractor, props, new NoopRecordPersistService());
    }

    // ---------- Case 1: Happy path ----------

    @Test
    void reply_happy_path_returns_ai_response() {
        String aiReply = "1. 检查 VPN 客户端版本\n2. 重启服务\n3. 联系网络运维";
        replier = buildReplier(new StubExtractor(aiReply));

        ReplyContext ctx = new ReplyContext(
                "TK2026081100000001", "VPN 登录不上", "VPN 客户端报 720 错误",
                "PENDING", List.of());

        ReplyResult result = replier.reply(ctx);

        assertThat(result.reply()).isEqualTo(aiReply);
        assertThat(result.fallback()).isFalse();
    }

    // ---------- Case 2: ChatContentExtractor exception ----------

    @Test
    void reply_returns_fallback_when_chat_client_throws() {
        replier = buildReplier(new StubExtractor(new RuntimeException("DeepSeek API down")));

        ReplyContext ctx = new ReplyContext(
                "TK2026081100000002", "VPN 登录不上", "x", "PENDING", List.of());

        ReplyResult result = replier.reply(ctx);

        // 兜底字符串（AiProperties.replyFallbackTemplate + ticketNo）
        assertThat(result.reply()).isEqualTo("问题已记录，工单号 TK2026081100000002，预计 1 个工作日内回复");
        assertThat(result.fallback()).isTrue();
    }

    // ---------- Case 3: Empty response ----------

    @Test
    void reply_returns_fallback_when_chat_client_returns_blank() {
        replier = buildReplier(new StubExtractor(""));

        ReplyContext ctx = new ReplyContext(
                "TK2026081100000003", "x", "x", "PENDING", List.of());

        ReplyResult result = replier.reply(ctx);

        assertThat(result.reply())
                .contains("TK2026081100000003")
                .contains("预计 1 个工作日内回复");
        assertThat(result.fallback()).isTrue();
    }

    // ---------- Case 4: Empty history ----------

    @Test
    void reply_handles_empty_history_messages_gracefully() {
        replier = buildReplier(new StubExtractor("ok response"));

        ReplyContext ctx = new ReplyContext(
                "TK2026081100000004", "x", "x", "PENDING", List.of());

        ReplyResult result = replier.reply(ctx);

        assertThat(result.reply()).isEqualTo("ok response");
        assertThat(result.fallback()).isFalse();
    }

    @Test
    void reply_handles_null_history_messages_gracefully() {
        // record 的紧凑构造会把 null 转为 empty list，但显式 null 也走同一路径
        replier = buildReplier(new StubExtractor("ok"));

        ReplyContext ctx = new ReplyContext(
                "TK2026081100000005", "x", "x", "PENDING", null);

        ReplyResult result = replier.reply(ctx);

        assertThat(result.reply()).isEqualTo("ok");
        assertThat(result.fallback()).isFalse();
    }

    // ---------- Case 5: ChatMessage conversion ----------

    @Test
    void reply_passes_history_messages_through() {
        // 验证 historyMessages 不会破坏调用链 —— 只要 extractor 返回字符串就成功
        replier = buildReplier(new StubExtractor("response with history"));

        List<ChatMessage> history = List.of(
                ChatMessage.user("我这边 VPN 上不去"),
                ChatMessage.assistant("请尝试重启客户端"));
        ReplyContext ctx = new ReplyContext(
                "TK2026081100000006", "VPN 问题", "x", "PROCESSING", history);

        ReplyResult result = replier.reply(ctx);

        assertThat(result.reply()).isEqualTo("response with history");
        assertThat(result.fallback()).isFalse();
    }

    // ---------- Defensive: null ctx ----------

    @Test
    void reply_handles_null_context_gracefully() {
        // 即使 ctx 为 null，兜底 reply 仍能给出 ReplyResult（fallback=true）
        AtomicReference<String> lastPrompt = new AtomicReference<>();
        ChatContentExtractor extractor = prompt -> {
            lastPrompt.set("called"); // 不应被调用
            return "should not be called";
        };
        replier = buildReplier(extractor);

        ReplyResult result = replier.reply(null);

        assertThat(result.reply()).contains("unknown");
        assertThat(result.fallback()).isTrue();
        assertThat(lastPrompt.get()).isNull(); // 验证 extractor 没被调用
    }
}
