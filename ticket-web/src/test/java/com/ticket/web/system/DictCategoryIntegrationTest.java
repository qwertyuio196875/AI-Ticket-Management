package com.ticket.web.system;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Map;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ticket 04 — 数据字典 + 工单分类 集成测试（spec Seam 3）。
 * <p>
 * 端到端覆盖 ticket 04 验收标准的最后一条 —— 验证
 * {@code @PreAuthorize("hasAuthority('dict:manage')")} 与
 * {@code @PreAuthorize("hasAuthority('category:manage')")} 在真实 Spring Security 链路下：
 * <ul>
 *     <li>无权限用户（agent_user）调用 POST /dicts → 403</li>
 *     <li>无权限用户（agent_user）调用 POST /ticket-categories → 403</li>
 *     <li>有权限用户（admin）创建/查询 → 200</li>
 *     <li>读端点（GET /dicts/type/priority、GET /ticket-categories）所有已登录用户可访问</li>
 *     <li>ticket 14：GET /ticket-categories/manage 需 category:manage，
 *         精确路径优先于 /{id} 模板匹配（返回全量数组而非被 {id} 吞掉）</li>
 * </ul>
 */
@SpringBootTest
class DictCategoryIntegrationTest {

    private static final String LOGIN_URL = "/api/v1/auth/login";
    private static final String DICTS_URL = "/api/v1/dicts";
    private static final String CATEGORIES_URL = "/api/v1/ticket-categories";

    @Autowired WebApplicationContext webApplicationContext;
    @Autowired ObjectMapper objectMapper;

    private MockMvc mockMvc;

    private MockMvc mockMvc() {
        if (mockMvc == null) {
            mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                    .apply(SecurityMockMvcConfigurers.springSecurity())
                    .build();
        }
        return mockMvc;
    }

    // ---------- dict:manage 权限校验 ----------

    @Test
    void post_dicts_as_agent_user_without_dict_manage_returns_403() throws Exception {
        String agentToken = loginAs("agent_user", "admin123");
        String body = objectMapper.writeValueAsString(Map.of(
                "dictType", "priority",
                "dictValue", "URGENT",
                "dictLabel", "加急",
                "sort", 0,
                "status", 1));

        mockMvc().perform(post(DICTS_URL)
                        .header("Authorization", "Bearer " + agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(equalTo("403")));
    }

    @Test
    void post_dicts_as_admin_succeeds() throws Exception {
        String adminToken = loginAs("admin", "admin123");
        // 用一次性 dict_type（不与种子的 priority / comment_type / status 重名），
        // 避免污染后续 GET /type/priority 的计数断言
        String isolatedType = "test_type_" + System.nanoTime();
        String body = objectMapper.writeValueAsString(Map.of(
                "dictType", isolatedType,
                "dictValue", "URGENT",
                "dictLabel", "加急",
                "sort", 0,
                "status", 1));

        mockMvc().perform(post(DICTS_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(equalTo("200")))
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    @Test
    void get_dicts_by_type_is_accessible_to_any_authenticated_user() throws Exception {
        String agentToken = loginAs("agent_user", "admin123");
        mockMvc().perform(get(DICTS_URL + "/type/priority")
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(3)));
    }

    @Test
    void put_dicts_as_agent_user_without_dict_manage_returns_403() throws Exception {
        // spec AC7：PUT/DELETE 同样需要 403
        String agentToken = loginAs("agent_user", "admin123");
        String body = objectMapper.writeValueAsString(Map.of(
                "id", 1L,
                "dictType", "priority",
                "dictValue", "HIGH",
                "dictLabel", "高",
                "sort", 0,
                "status", 1));

        mockMvc().perform(put(DICTS_URL)
                        .header("Authorization", "Bearer " + agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(equalTo("403")));
    }

    @Test
    void delete_dicts_as_agent_user_without_dict_manage_returns_403() throws Exception {
        // spec AC7：PUT/DELETE 同样需要 403
        String agentToken = loginAs("agent_user", "admin123");
        mockMvc().perform(delete(DICTS_URL + "/1")
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(equalTo("403")));
    }

    // ---------- category:manage 权限校验 ----------

    @Test
    void get_categories_manage_as_admin_returns_full_list() throws Exception {
        // ticket 14 路由验证：/manage 是精确路径，Spring 优先于 /{id} 模板匹配 ——
        // 若被 /{id} 捕获，"manage" 无法解析为 Long 会抛类型转换异常（400/500），
        // 绝不会返回 200 + 数组。断言返回数组（非单条详情）即证明精确路由生效。
        String adminToken = loginAs("admin", "admin123");
        mockMvc().perform(get(CATEGORIES_URL + "/manage")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(equalTo("200")))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(5)));
    }

    @Test
    void get_categories_manage_as_agent_user_without_category_manage_returns_403() throws Exception {
        String agentToken = loginAs("agent_user", "admin123");
        mockMvc().perform(get(CATEGORIES_URL + "/manage")
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(equalTo("403")));
    }

    @Test
    void post_categories_as_agent_user_without_category_manage_returns_403() throws Exception {
        String agentToken = loginAs("agent_user", "admin123");
        String body = objectMapper.writeValueAsString(Map.of(
                "name", "硬件问题_" + System.nanoTime(),
                "description", "显示器、键盘等",
                "sort", 10,
                "status", 1));

        mockMvc().perform(post(CATEGORIES_URL)
                        .header("Authorization", "Bearer " + agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(equalTo("403")));
    }

    @Test
    void post_categories_as_admin_succeeds() throws Exception {
        String adminToken = loginAs("admin", "admin123");
        // status=0（禁用）使 listAllEnabled 不会把新分类计入，避免污染后续 GET 计数
        String body = objectMapper.writeValueAsString(Map.of(
                "name", "硬件问题_" + System.nanoTime(),
                "description", "显示器、键盘等",
                "sort", 10,
                "status", 0));

        mockMvc().perform(post(CATEGORIES_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(equalTo("200")));
    }

    @Test
    void get_categories_is_accessible_to_any_authenticated_user() throws Exception {
        String agentToken = loginAs("agent_user", "admin123");
        mockMvc().perform(get(CATEGORIES_URL)
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(5)));
    }

    @Test
    void put_categories_as_agent_user_without_category_manage_returns_403() throws Exception {
        // spec AC7：PUT/DELETE 同样需要 403
        String agentToken = loginAs("agent_user", "admin123");
        String body = objectMapper.writeValueAsString(Map.of(
                "id", 1L,
                "name", "系统故障",
                "description", "x",
                "sort", 0,
                "status", 1));

        mockMvc().perform(put(CATEGORIES_URL)
                        .header("Authorization", "Bearer " + agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(equalTo("403")));
    }

    @Test
    void delete_categories_as_agent_user_without_category_manage_returns_403() throws Exception {
        // spec AC7：PUT/DELETE 同样需要 403
        String agentToken = loginAs("agent_user", "admin123");
        mockMvc().perform(delete(CATEGORIES_URL + "/1")
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(equalTo("403")));
    }

    // ---------- 辅助 ----------

    private String loginAs(String username, String password) throws Exception {
        String body = mockMvc().perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username, "password", password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode root = objectMapper.readTree(body);
        return root.path("data").path("token").asText();
    }
}
