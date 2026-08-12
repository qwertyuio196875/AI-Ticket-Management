package com.ticket.ai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticket.ai.client.TicketClassifier;
import com.ticket.ai.client.TicketReplier;
import com.ticket.ai.service.AIRecordPersistService;
import com.ticket.ai.service.ChatContentExtractor;
import com.ticket.ai.service.impl.DeepSeekClassifier;
import com.ticket.ai.service.impl.DeepSeekReplier;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ticket-ai 模块 Spring 配置（ticket 08）。
 * <p>
 * 当前 ticket 08 的 bean wiring 集中在这里：
 * <ul>
 *     <li>{@link AiProperties} 由 {@code @EnableConfigurationProperties} 绑定</li>
 *     <li>{@link com.ticket.ai.mapper.AiTicketRecordMapper} 由 {@code @MapperScan} 注册</li>
 *     <li>{@link TicketClassifier} bean —— {@code DeepSeekClassifier} 实现</li>
 *     <li>{@link TicketReplier} bean —— {@code DeepSeekReplier} 实现</li>
 *     <li>{@link ChatContentExtractor} bean —— lambda 适配器（不暴露 ChatClient 给业务代码）</li>
 * </ul>
 * <p>
 * <b>设计原则</b>：业务模块（ticket-ticket）只依赖 {@link TicketClassifier} /
 * {@link TicketReplier} / {@link AIRecordPersistService} 这几个 interface，
 * 看不到 {@link ChatClient} 等 Spring AI 内部类型。本配置类的 ChatContentExtractor
 * 适配层就是这条边界的"翻译器"。
 * <p>
 * <b>为什么用 {@code @MapperScan} 而不是仅 {@code @Mapper}？</b>
 * {@code AiTicketSystemApplication}（启动类）位于 {@code com.ticket.web} 包，
 * Spring 默认只扫描该包及其子包。{@code com.ticket.ai.mapper} 不在扫描范围内，
 * 仅靠 {@code @Mapper} 注解无法被注册为 Spring Bean —— 集成测试启动时会报
 * {@code NoSuchBeanDefinitionException}。{@code @MapperScan} 在配置类显式声明
 * 扫描范围，跨模块装配最稳。
 */
@Configuration
@MapperScan("com.ticket.ai.mapper")
@EnableConfigurationProperties(AiProperties.class)
public class AiConfig {

    /**
     * ChatClient → ChatContentExtractor 适配层。
     * <p>
     * ChatClient Bean 由 Spring AI 自动装配（spring-ai-openai-spring-boot-starter）；
     * 这里把它包成函数式接口，让 {@link DeepSeekReplier} 不感知 Spring AI 类型。
     */
    @Bean
    public ChatContentExtractor chatContentExtractor(ChatClient chatClient) {
        return prompt -> chatClient.prompt(prompt).call().content();
    }

    @Bean
    public TicketClassifier ticketClassifier(ChatClient chatClient,
                                            AiProperties aiProperties,
                                            AIRecordPersistService recordPersistService,
                                            ObjectMapper objectMapper) {
        return new DeepSeekClassifier(chatClient, aiProperties, recordPersistService, objectMapper);
    }

    @Bean
    public TicketReplier ticketReplier(ChatContentExtractor chatContentExtractor,
                                      AiProperties aiProperties,
                                      AIRecordPersistService recordPersistService) {
        return new DeepSeekReplier(chatContentExtractor, aiProperties, recordPersistService);
    }
}