package com.ticket.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * AI 智能工单管理系统 — 应用启动入口。
 * <p>
 * {@code @ComponentScan} 覆盖所有 6 个模块的基础包 {@code com.ticket}，
 * 以便各业务模块（ticket-security / ticket-system / ticket-ticket / ticket-ai）的
 * {@code @RestControllerAdvice}、{@code @Configuration} 等被自动扫描注册。
 */
@SpringBootApplication
@ComponentScan(basePackages = "com.ticket")
public class AiTicketSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiTicketSystemApplication.class, args);
    }
}