package com.ticket.ai.service;

import org.springframework.ai.chat.prompt.Prompt;

/**
 * Spring AI ChatClient 内容提取器（ticket 08）。
 * <p>
 * 把 Spring AI ChatClient 的 chain 调用（{@code prompt(prompt).call().content()}）
 * 抽象成单方法函数式接口，让 {@link com.ticket.ai.service.impl.DeepSeekReplier}
 * 只依赖这一层——既不感知 Spring AI 类型，也让单测 mock 变得 trivial
 * （不需要 stub ChatClient chain 的 3 段）。
 * <p>
 * <b>由谁实现？</b>Spring 配置层提供（详见
 * {@code com.ticket.ai.config.AiConfig} —— Spring 注入 ChatClient Bean，
 * 用 lambda 把 {@code prompt → client.prompt(prompt).call().content()} 包成实现）。
 *
 * @see com.ticket.ai.service.impl.DeepSeekReplier
 */
@FunctionalInterface
public interface ChatContentExtractor {

    /**
     * 同步调用 AI，返回内容字符串。
     * <p>
     * 失败由实现层抛 RuntimeException；调用方负责 catch + 兜底。
     */
    String extract(Prompt prompt);
}