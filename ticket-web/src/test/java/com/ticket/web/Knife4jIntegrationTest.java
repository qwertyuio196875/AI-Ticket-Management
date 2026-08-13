package com.ticket.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ticket 11 / Knife4j 集成测试（spec AC）。
 * <p>
 * 端到端验证：
 * <ul>
 *     <li>{@code /v3/api-docs} 返回 OpenAPI JSON，包含 endpoints from all modules</li>
 *     <li>{@code /doc.html} 返回 200（Knife4j UI 入口）</li>
 *     <li>5 个分组（auth / system / ticket / ai / stats）都能在 OpenAPI 里查到</li>
 * </ul>
 *
 * <p><b>安全</b>：doc 路径在 SecurityConfig 已加 permitAll，无需登录即可访问。
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = true)
@TestPropertySource(properties = {
        "spring.main.allow-bean-definition-overriding=true",
        // Knife4j production 模式关闭会让 UI 不可用；测试环境强制开启
        "knife4j.production=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class Knife4jIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void api_docs_returns_openapi_json_with_paths_from_all_modules() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();
        String body = result.getResponse().getContentAsString();
        JsonNode root = objectMapper.readTree(body);

        // 1. 顶层 OpenAPI 字段存在
        assertThat(root.path("openapi").asText()).isNotBlank();
        assertThat(root.path("info").path("title").asText()).isNotBlank();

        // 2. paths 非空（覆盖多模块 endpoints）
        JsonNode paths = root.path("paths");
        assertThat(paths.size()).isGreaterThan(0);

        // 3. 至少包含各模块的关键端点
        String allPaths = paths.toString();
        assertThat(allPaths)
                .as("应包含 auth 模块端点")
                .contains("/api/v1/auth/login")
                .as("应包含 system 模块端点")
                .containsAnyOf("/api/v1/users", "/api/v1/roles")
                .as("应包含 ticket 模块端点")
                .containsAnyOf("/api/v1/tickets", "/api/v1/tickets/{id}")
                .as("应包含 stats 模块端点")
                .containsAnyOf("/api/v1/stats/tickets/summary", "/api/v1/stats/tickets/trend");
    }

    @Test
    void api_docs_groups_endpoint_exposes_five_groups() throws Exception {
        // Knife4j 在 /v3/api-docs/group 下提供按 groupName 索引的子 doc
        // 我们检查默认 doc 至少包含 5 个 tag（auth / system / ticket / ai / stats）
        MvcResult result = mockMvc.perform(get("/v3/api-docs").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());

        // tags 列表 —— 每个 @Tag(name = "...") 一项
        JsonNode tags = root.path("tags");
        boolean hasAuth = false, hasSystem = false, hasTicket = false, hasAi = false, hasStats = false;
        for (JsonNode tag : tags) {
            String name = tag.path("name").asText();
            if (name.contains("auth")) hasAuth = true;
            if (name.contains("系统") || name.equalsIgnoreCase("system")) hasSystem = true;
            if (name.contains("工单") || name.equalsIgnoreCase("ticket")) hasTicket = true;
            if (name.contains("AI") || name.equalsIgnoreCase("ai")) hasAi = true;
            if (name.contains("统计") || name.equalsIgnoreCase("stats")) hasStats = true;
        }
        assertThat(hasAuth).as("应有 auth 分组").isTrue();
        assertThat(hasSystem).as("应有 system 分组").isTrue();
        assertThat(hasTicket).as("应有 ticket 分组").isTrue();
        assertThat(hasAi).as("应有 ai 分组").isTrue();
        assertThat(hasStats).as("应有 stats 分组").isTrue();
    }

    @Test
    void doc_html_is_accessible_returns_200_with_html() throws Exception {
        mockMvc.perform(get("/doc.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
    }

    @Test
    void swagger_ui_path_is_accessible() throws Exception {
        // SpringDoc 默认 /swagger-ui/index.html；Knife4j 接管 /doc.html 后通常 redirect
        // 这里只校验两个路径在 SecurityConfig 已放行，不强制 200（具体版本 redirect 行为可能不同）
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().is(org.hamcrest.Matchers.anyOf(
                        org.hamcrest.Matchers.equalTo(200),
                        org.hamcrest.Matchers.equalTo(302),
                        org.hamcrest.Matchers.equalTo(301))));
    }

    @Test
    void api_docs_paths_count_is_above_minimum_threshold() throws Exception {
        // 防止漏标 @Tag 后端点被剔除：合理的 5 分组项目至少 20 个端点
        MvcResult result = mockMvc.perform(get("/v3/api-docs").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode paths = objectMapper.readTree(result.getResponse().getContentAsString()).path("paths");
        assertThat(paths.size()).isGreaterThan(15);
    }

    @Test
    void schema_definitions_include_response_types() throws Exception {
        // @Schema(description = "...") 标注会让 Result.data 字段暴露在 components/schemas
        MvcResult result = mockMvc.perform(get("/v3/api-docs").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode schemas = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("components").path("schemas");

        // Result<T> 是泛型 —— Springdoc 通常以 ParameterizedType 形式出现，
        // 命名形如 "ResultDto_LoginVO" 或 "PageVO_SysUserVO"。检查任一 Result* schema 即可。
        boolean hasResultLikeSchema = false;
        java.util.Iterator<String> fieldNames = schemas.fieldNames();
        while (fieldNames.hasNext()) {
            String name = fieldNames.next();
            // Springdoc 的 Result<T> 在 OpenAPI 中会展开为 Result + 实体 schema 两份
            // 我们要求至少有 "Result" 这个 schema 暴露（部分版本会命名为 "ResultObject"）
            if (name.contains("Result") || name.equalsIgnoreCase("ResultObject")) {
                hasResultLikeSchema = true;
                break;
            }
        }
        assertThat(hasResultLikeSchema).as("components.schemas 应暴露 Result 或类似泛型展开名").isTrue();
    }

    @Test
    void api_docs_with_auth_login_path_has_post_operation() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode paths = objectMapper.readTree(result.getResponse().getContentAsString()).path("paths");
        JsonNode loginPath = paths.path("/api/v1/auth/login");
        assertThat(loginPath.isMissingNode()).as("auth/login 端点应存在").isFalse();
        assertThat(loginPath.has("post")).as("auth/login 端点应有 POST 方法").isTrue();
    }
}