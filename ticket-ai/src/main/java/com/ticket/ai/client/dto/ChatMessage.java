package com.ticket.ai.client.dto;

/**
 * 业务中性的多轮对话消息（ticket 08）。
 * <p>
 * Spring AI 的 {@code org.springframework.ai.chat.messages.Message} 类型不暴露给业务模块；
 * 业务模块只用本 record，{@code ticket-ai} 内部转换为 Spring AI 类型。
 */
public record ChatMessage(ChatRole role, String content) {

    /**
     * 消息角色枚举。
     */
    public enum ChatRole {
        /** 客户 / 工单创建人 消息 */
        USER,
        /** 助手 / AI 消息 */
        ASSISTANT,
        /** 系统 Prompt（一般不用，业务层传 history 时不需要） */
        SYSTEM
    }

    public static ChatMessage user(String content) {
        return new ChatMessage(ChatRole.USER, content);
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage(ChatRole.ASSISTANT, content);
    }

    public static ChatMessage system(String content) {
        return new ChatMessage(ChatRole.SYSTEM, content);
    }
}
