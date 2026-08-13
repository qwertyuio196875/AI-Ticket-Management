package com.ticket.web.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Knife4j / Springdoc OpenAPI 配置 (ticket 11 / ADR-0032)。
// 5 个 GroupedOpenApi 分组: auth / system / ticket / ai / stats。
// 注: Springdoc 3.x 的分组 API 是 GroupedOpenApi (Swagger 2 / SpringFox 时代的术语是
// Docket, 本项目用 Springdoc 故统一称 GroupedOpenApi)。
// 安全方案: Bearer JWT - Knife4j 顶部 Authorize 按钮填入 token 即可调用受保护端点。
// 生产环境: 通过 knife4j.production=true 关闭 UI (不影响 /v3/api-docs)。
@Configuration
public class Knife4jConfig {

    private static final String BEARER_KEY = "bearer-jwt";

    @Bean
    public OpenAPI ticketSystemOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("AI 智能工单管理系统 API")
                        .description("企业内部 AI 智能工单管理系统 - 单体架构 6 模块 Maven 多模块")
                        .version("1.0.0")
                        .contact(new Contact().name("ticket-system").email("dev@example.com"))
                        .license(new License().name("Apache 2.0").url("https://www.apache.org/licenses/LICENSE-2.0")))
                .components(new Components()
                        .addSecuritySchemes(BEARER_KEY,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("在请求头 Authorization: Bearer {token} 携带 JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_KEY));
    }

    // 5 个 GroupedOpenApi 分组

    /** 认证授权分组 (登录 / 登出 / 当前用户)。 */
    @Bean
    public GroupedOpenApi authGroup() {
        return GroupedOpenApi.builder()
                .group("auth")
                .displayName("认证授权")
                .pathsToMatch("/api/v1/auth/**")
                .build();
    }

    /** 系统管理分组 (用户 / 角色 / 菜单 / 数据字典 / 工单分类)。 */
    @Bean
    public GroupedOpenApi systemGroup() {
        return GroupedOpenApi.builder()
                .group("system")
                .displayName("系统管理")
                .pathsToMatch(
                        "/api/v1/users/**",
                        "/api/v1/roles/**",
                        "/api/v1/menus/**",
                        "/api/v1/dicts/**",
                        "/api/v1/ticket-categories/**")
                .build();
    }

    /**
     * 工单主业务分组 (CRUD / 状态机 / 评论 / 导出)。
     * <p>
     * 显式 pathsToExclude 掉 ai-reply 子路径，避免 ticket 组与 ai 组重复收录。
     */
    @Bean
    public GroupedOpenApi ticketGroup() {
        return GroupedOpenApi.builder()
                .group("ticket")
                .displayName("工单管理")
                .pathsToMatch("/api/v1/tickets", "/api/v1/tickets/**")
                .pathsToExclude("/api/v1/tickets/*/ai-reply")
                .build();
    }

    /** AI 分组 (智能回复)。 */
    @Bean
    public GroupedOpenApi aiGroup() {
        return GroupedOpenApi.builder()
                .group("ai")
                .displayName("AI 智能")
                .pathsToMatch("/api/v1/tickets/*/ai-reply")
                .build();
    }

    /** 统计分组 (Dashboard)。 */
    @Bean
    public GroupedOpenApi statsGroup() {
        return GroupedOpenApi.builder()
                .group("stats")
                .displayName("数据统计")
                .pathsToMatch("/api/v1/stats/**")
                .build();
    }
}