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

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ticket 03 RBAC 集成测试（spec Seam 3）。
 * <p>
 * 端到端覆盖 ticket 03 验收标准的最后一条 —— 验证
 * {@code @PreAuthorize("hasAuthority('user:manage')")} 在真实 Spring Security 链路下：
 * <ul>
 *     <li>无权限用户（agent_user，仅 ticket:view）调用 POST /users → 403</li>
 *     <li>有权限用户（admin，全菜单）调用 POST /users → 200</li>
 *     <li>菜单树端点按角色过滤：admin 看到 5 个，agent_user 看到 2 个</li>
 * </ul>
 * <p>
 * 数据由 {@code src/test/resources/db/h2/data.sql} 提供：
 * admin (id=1) 挂 admin 角色，agent_user (id=3) 挂 agent 角色。
 */
@SpringBootTest
class RbacIntegrationTest {

    private static final String LOGIN_URL = "/api/v1/auth/login";
    private static final String USERS_URL = "/api/v1/users";
    private static final String ROLES_URL = "/api/v1/roles";
    private static final String MENUS_TREE_URL = "/api/v1/menus/tree";

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

    // ---------- @PreAuthorize 验证 ----------

    @Test
    void post_users_as_agent_user_without_user_manage_returns_403() throws Exception {
        String agentToken = loginAs("agent_user", "admin123");
        String body = objectMapper.writeValueAsString(Map.of(
                "username", "ghost",
                "password", "x",
                "nickname", "n",
                "status", 1));

        mockMvc().perform(post(USERS_URL)
                        .header("Authorization", "Bearer " + agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(equalTo("403")));
    }

    @Test
    void post_users_as_admin_with_user_manage_succeeds() throws Exception {
        String adminToken = loginAs("admin", "admin123");
        String body = objectMapper.writeValueAsString(Map.of(
                "username", "newbie_" + System.nanoTime(),
                "password", "admin123",
                "nickname", "新员工",
                "status", 1));

        mockMvc().perform(post(USERS_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(equalTo("200")))
                .andExpect(jsonPath("$.data").value(greaterThanOrEqualTo(1)));
    }

    // ---------- 用户列表 + 菜单树按角色过滤 ----------

    @Test
    void list_users_as_admin_returns_paged_results() throws Exception {
        String adminToken = loginAs("admin", "admin123");
        mockMvc().perform(get(USERS_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .param("pageNum", "1")
                        .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.records", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    void menu_tree_for_admin_returns_all_five_seeded_menus() throws Exception {
        String adminToken = loginAs("admin", "admin123");
        mockMvc().perform(get(MENUS_TREE_URL)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(5)));
    }

    @Test
    void menu_tree_for_agent_user_returns_only_ticket_and_dashboard() throws Exception {
        String agentToken = loginAs("agent_user", "admin123");
        mockMvc().perform(get(MENUS_TREE_URL)
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)));
    }

    @Test
    void me_endpoint_exposes_authorities_after_rbac_is_wired() throws Exception {
        // ticket 02 阶段 authorities 为空；ticket 03 接入 sys_menu.permission 后
        // 应该能拿到 agent_user 的 ticket:view / dashboard:view
        String agentToken = loginAs("agent_user", "admin123");
        mockMvc().perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authorities", hasSize(2)));
    }

    // ---------- 角色列表 ----------

    @Test
    void list_roles_as_admin_returns_seeded_admin_and_agent() throws Exception {
        String adminToken = loginAs("admin", "admin123");
        mockMvc().perform(get(ROLES_URL + "/all")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)));
    }

    @Test
    void post_roles_as_agent_user_returns_403() throws Exception {
        // role:manage 权限校验 —— agent_user 没有这条权限
        String agentToken = loginAs("agent_user", "admin123");
        String body = objectMapper.writeValueAsString(Map.of(
                "roleName", "测试",
                "roleKey", "tester",
                "remark", "n"));
        mockMvc().perform(post(ROLES_URL)
                        .header("Authorization", "Bearer " + agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
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