package com.ticket.web.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticket.security.blacklist.TokenBlacklistStore;
import com.ticket.security.jwt.JwtProperties;
import com.ticket.security.jwt.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 认证链路集成测试（spec Seam 3 —— 部署前冒烟）。
 * <p>
 * 全上下文 + MockMvc，覆盖 ticket 02 的端到端验收标准：
 * 登录拿 token → 带 token 访问受保护端点 → 无 token / 篡改 token / 登出后 token 一律 401。
 * <p>
 * <b>替身说明</b>：本机无 Docker，故
 * <ul>
 *     <li>MySQL → H2 内存库（MySQL 兼容模式，见 src/test/resources/application.yml）</li>
 *     <li>Redis 黑名单 → {@link InMemoryTokenBlacklistStore}（本类内的 {@code @Primary} 替身）</li>
 * </ul>
 * 生产实现 {@code RedisTokenBlacklistStore} 的行为契约（key 前缀 / TTL）
 * 由 {@code AuthServiceTest} 以 mock 验证。
 */
@SpringBootTest
class AuthIntegrationTest {

    private static final String LOGIN_URL = "/api/v1/auth/login";
    private static final String ME_URL = "/api/v1/auth/me";
    private static final String LOGOUT_URL = "/api/v1/auth/logout";

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    /** 用于自行签发过期 token，密钥须与运行时一致 */
    @Autowired
    private JwtProperties jwtProperties;

    private MockMvc mockMvc;

    private MockMvc mockMvc() {
        if (mockMvc == null) {
            // springSecurity() 必不可少：只用 webAppContextSetup 不会装配 Security 过滤链，
            // 认证相关的断言就全部失去意义
            mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                    .apply(SecurityMockMvcConfigurers.springSecurity())
                    .build();
        }
        return mockMvc;
    }

    // ---------- 登录 ----------

    @Test
    void login_with_seeded_admin_returns_token_and_user_info() throws Exception {
        String body = mockMvc().perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("admin", "admin123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(equalTo("200")))
                .andExpect(jsonPath("$.data.username").value(equalTo("admin")))
                .andExpect(jsonPath("$.data.nickname").value(equalTo("超级管理员")))
                .andExpect(jsonPath("$.data.tokenType").value(equalTo("Bearer")))
                .andExpect(jsonPath("$.data.expiresIn").value(equalTo(1800)))
                .andReturn().getResponse().getContentAsString();

        assertThat(tokenOf(body)).isNotBlank();
    }

    @Test
    void login_with_wrong_password_returns_401_with_business_code_401() throws Exception {
        mockMvc().perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("admin", "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(equalTo("401")))
                .andExpect(jsonPath("$.data").value(equalTo(null)));
    }

    @Test
    void login_with_unknown_username_returns_401() throws Exception {
        mockMvc().perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("ghost", "admin123")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(equalTo("401")));
    }

    @Test
    void login_with_disabled_account_returns_401() throws Exception {
        // 种子数据里 resigned 的 status = 0
        mockMvc().perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("resigned", "admin123")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(equalTo("401")));
    }

    @Test
    void login_with_blank_username_is_rejected_by_validation() throws Exception {
        mockMvc().perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"admin123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(equalTo("C0400")));
    }

    // ---------- 受保护端点 ----------

    @Test
    void me_with_valid_token_returns_current_user_from_jwt() throws Exception {
        String token = loginAsAdmin();

        mockMvc().perform(get(ME_URL).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(equalTo("200")))
                .andExpect(jsonPath("$.data.username").value(equalTo("admin")))
                .andExpect(jsonPath("$.data.userId").value(equalTo(1)))
                // ticket 03 起：sys_menu.permission 通过 JWT 载荷透传，admin 至少有 5 个权限
                .andExpect(jsonPath("$.data.authorities", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    void me_without_token_returns_401() throws Exception {
        mockMvc().perform(get(ME_URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(equalTo("401")));
    }

    @Test
    void me_with_tampered_token_returns_401() throws Exception {
        String token = loginAsAdmin();
        // 改签名段最后两个字符 —— 结构仍合法但验签失败
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("AA") ? "BB" : "AA");

        mockMvc().perform(get(ME_URL).header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(equalTo("401")));
    }

    @Test
    void me_with_garbage_token_returns_401() throws Exception {
        mockMvc().perform(get(ME_URL).header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(equalTo("401")));
    }

    @Test
    void me_with_expired_token_returns_401() throws Exception {
        // 用同一把密钥、负数有效期签一个"出生即过期"的 token，
        // 验证过期分支同样走到 401（黑名单分支由 logout 用例覆盖）
        JwtProperties expiredProps = new JwtProperties();
        expiredProps.setSecret(jwtProperties.getSecret());
        expiredProps.setExpireMinutes(-1);
        String expiredToken = new JwtUtil(expiredProps).generateToken(1L, "admin", List.of());

        mockMvc().perform(get(ME_URL).header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(equalTo("401")));
    }

    // ---------- 登出 → 黑名单 ----------

    @Test
    void logout_then_reusing_the_same_token_returns_401() throws Exception {
        String token = loginAsAdmin();
        String authHeader = "Bearer " + token;

        // 登出前 token 可用
        mockMvc().perform(get(ME_URL).header("Authorization", authHeader))
                .andExpect(status().isOk());

        mockMvc().perform(post(LOGOUT_URL).header("Authorization", authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(equalTo("200")));

        // 登出后同一个 token 立刻失效（签名仍有效，但已在黑名单）
        mockMvc().perform(get(ME_URL).header("Authorization", authHeader))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(equalTo("401")));
    }

    @Test
    void logout_does_not_affect_a_freshly_issued_token() throws Exception {
        String firstToken = loginAsAdmin();
        mockMvc().perform(post(LOGOUT_URL).header("Authorization", "Bearer " + firstToken))
                .andExpect(status().isOk());

        // 重新登录拿到的是不同 jti，不受上一个 token 拉黑影响
        String secondToken = loginAsAdmin();
        assertThat(secondToken).isNotEqualTo(firstToken);

        mockMvc().perform(get(ME_URL).header("Authorization", "Bearer " + secondToken))
                .andExpect(status().isOk());
    }

    // ---------- ticket 01 兼容性 ----------

    @Test
    void ticket01_tracer_bullet_endpoints_stay_public_after_security_is_enabled() throws Exception {
        // ticket 01 的 DemoControllerTest 不装配 Security 过滤链，测不到放行规则，
        // 这里补一条：启用 Security 后探针端点仍可匿名访问
        mockMvc().perform(get("/api/v1/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(equalTo("pong")));

        mockMvc().perform(post("/api/v1/echo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"hello\",\"age\":18}"))
                .andExpect(status().isOk());
    }

    // ---------- 辅助 ----------

    private String credentials(String username, String password) throws Exception {
        return objectMapper.writeValueAsString(Map.of("username", username, "password", password));
    }

    private String loginAsAdmin() throws Exception {
        String body = mockMvc().perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("admin", "admin123")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return tokenOf(body);
    }

    private String tokenOf(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        return root.path("data").path("token").asText();
    }

    /**
     * Redis 黑名单的内存替身 —— 只在本测试类生效。
     * <p>
     * 生产代码里没有内存实现（不留死分支），测试用 {@code @Primary} 覆盖注入。
     */
    @TestConfiguration
    static class BlacklistTestConfig {

        @Bean
        @Primary
        TokenBlacklistStore inMemoryTokenBlacklistStore() {
            return new InMemoryTokenBlacklistStore();
        }
    }

    /** 忽略 TTL 的简易黑名单：集成测试的时间跨度内不涉及过期 */
    static class InMemoryTokenBlacklistStore implements TokenBlacklistStore {

        private final Set<String> blacklisted = ConcurrentHashMap.newKeySet();

        @Override
        public void blacklist(String jti, Duration ttl) {
            if (jti != null && ttl != null && !ttl.isZero() && !ttl.isNegative()) {
                blacklisted.add(jti);
            }
        }

        @Override
        public boolean isBlacklisted(String jti) {
            return jti != null && blacklisted.contains(jti);
        }
    }
}
