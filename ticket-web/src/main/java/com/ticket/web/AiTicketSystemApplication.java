package com.ticket.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * AI 智能工单管理系统 — 应用启动入口。
 * <p>
 * {@code @ComponentScan} 覆盖所有 6 个模块的基础包 {@code com.ticket}，
 * 以便各业务模块（ticket-security / ticket-system / ticket-ticket / ticket-ai）的
 * {@code @RestControllerAdvice}、{@code @Configuration} 等被自动扫描注册。
 *
 * <p><b>{@code @EnableScheduling}</b>：ticket 11 引入，激活所有
 * {@code @Scheduled} 定时任务（{@code JwtBlacklistCleanupTask} /
 * {@code DailyTicketStatsTask}，详见 ADR-0031）。
 */
@SpringBootApplication
@EnableScheduling
@ComponentScan(basePackages = "com.ticket")
public class AiTicketSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiTicketSystemApplication.class, args);
    }
}