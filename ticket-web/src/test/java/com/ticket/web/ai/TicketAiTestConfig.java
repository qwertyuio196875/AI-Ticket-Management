package com.ticket.web.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 集成测试环境的 ChatClient 替身（ticket 08）。
 * <p>
 * 测试机无真实 DeepSeek key，避免任何真实 HTTP 请求。
 * 替身返回 null（ChatClient 的链式调用 stub 失败时由 DeepSeekClassifier / DeepSeekReplier
 * 走兜底路径，符合"AI 失败不阻塞主流程"的契约）。
 */
@TestConfiguration
public class TicketAiTestConfig {

    @Bean
    @Primary
    public ChatClient testChatClient() {
        return org.mockito.Mockito.mock(ChatClient.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
    }
}
