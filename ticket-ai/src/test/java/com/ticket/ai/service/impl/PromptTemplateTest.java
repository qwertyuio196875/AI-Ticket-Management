package com.ticket.ai.service.impl;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.core.io.ClassPathResource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prompt 模板渲染单测（ticket 08）。
 * <p>
 * spec AC 要求："{@code classify.st} renders {@code ${ticketTitle}} and
 * {@code ${ticketContent}} correctly"。
 * <p>
 * <b>覆盖范围</b>：仅验证占位符渲染正确性。<b>不</b>测试 Spring AI 调用链
 * （由 {@link DeepSeekClassifierTest} 覆盖）。
 * <p>
 * <b>为什么写在 impl 包下而不是 service 包？</b>当前项目内没有专门的 prompt 包；
 * 这两个 prompt 资源文件被 {@code DeepSeekClassifier} / {@code DeepSeekReplier}
 * 各自加载，把测试放在 impl 包下便于就近查找。
 */
class PromptTemplateTest {

    @Test
    void classify_prompt_renders_ticket_title_and_content() {
        // 1. 加载 resources/prompts/classify.st
        PromptTemplate template = new PromptTemplate(
                new ClassPathResource("prompts/classify.st"));

        // 2. 渲染占位符
        String rendered = template.render(Map.of(
                "ticketTitle", "VPN 登录不上",
                "ticketContent", "测试机访问内网 VPN 客户端报 720 错误"));

        // 3. 断言占位符被替换为实际工单信息
        assertThat(rendered)
                .contains("VPN 登录不上")
                .contains("测试机访问内网 VPN 客户端报 720 错误")
                // 反向断言：占位符不能残留
                .doesNotContain("${ticketTitle}")
                .doesNotContain("${ticketContent}");
    }
}