package com.ticket.ticket.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * ticket-ticket 模块的 MyBatis 配置（ticket 05）。
 * <p>
 * 启动类在 {@code com.ticket.web}，MP 的自动 mapper 扫描只覆盖启动类所在包，
 * 扫不到本模块的 {@code com.ticket.ticket.mapper} —— 故在此显式声明。
 * <p>
 * 与 {@code ticket-system} 模块各自声明 {@code @MapperScan}，避免
 * 集中配置反过来耦合所有模块的包结构。
 * <p>
 * <b>{@code MybatisPlusInterceptor} 不在本类重复声明</b>：{@code ticket-system}
 * 模块已经注册了一个全局的分页 / 多租户拦截器，本模块的所有 mapper 自动复用。
 * 重复 {@code @Bean} 会触发 {@code BeanDefinitionOverrideException}。
 */
@Configuration("ticketMyBatisConfig")
@MapperScan("com.ticket.ticket.mapper")
public class MyBatisConfig {
}
