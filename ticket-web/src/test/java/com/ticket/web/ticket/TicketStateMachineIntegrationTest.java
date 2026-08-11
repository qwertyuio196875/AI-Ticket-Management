package com.ticket.web.ticket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticket.ticket.entity.TicketInfo;
import com.ticket.ticket.entity.TicketLog;
import com.ticket.ticket.enums.TicketEventType;
import com.ticket.ticket.enums.TicketStatus;
import com.ticket.ticket.mapper.TicketInfoMapper;
import com.ticket.ticket.mapper.TicketLogMapper;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ticket 06 —— 工单状态机端到端集成测试（spec Seam 3）。
 * <p>
 * 全 Spring 上下文 + MockMvc，覆盖 ticket 06 AC 的端到端流程：
 * <ul>
 *     <li>端到端主流程：创建 → 分配（PENDING→PROCESSING） → 标记解决（PROCESSING→RESOLVED） →
 *         关闭（RESOLVED→CLOSED），逐步验证 ticket_info.status 与 ticket_log 流水完整性</li>
 *     <li>非法迁移：CLOSED → PROCESSING 触发 409 + {@code T0102}</li>
 *     <li>handler 不存在 / 已禁用：分配抛 404 + {@code S0101}</li>
 *     <li>权限隔离：agent_user 无 {@code ticket:assign / ticket:close / ticket:update} → 403</li>
 *     <li>PATCH /status / PUT /assign / POST /close 三个端点的 HTTP 方法 / 路径对齐 AC</li>
 * </ul>
 *
 * <h2>关于 TicketNoGenerator 替身</h2>
 *
 * 测试机无 Redis —— 直接复用 {@code TicketCrudIntegrationTest} 提供的
 * {@code @Primary} {@code InMemoryTicketNoGenerator}。
 * <p>
 * 但 H2 内存库在多个 {@code @SpringBootTest} 类间共享 + JUnit 5 默认每个 test
 * method 创建新的测试类实例 + 该 InMemoryTicketNoGenerator 的 sequence map
 * 原先是实例级 —— 三者叠加会让 sequence 每次从 1 重启，与 {@code TicketCrudIntegrationTest}
 * 写过的 ticket_no 撞 {@code uk_ticket_no} 唯一索引。
 * <p>
 * 解决方案（落在 {@code TicketCrudIntegrationTest}，属测试基础设施改进）：
 * 把 {@code InMemoryTicketNoGenerator.SEQUENCES} 改为 {@code static}，
 * 跨测试方法 / 跨测试类共享计数器。本测试类无需自己注册 bean —— 直接依赖
 * 共享的 {@code @Primary} 即可，避免与已有 bean 冲突。
 */
@SpringBootTest
class TicketStateMachineIntegrationTest {

    private static final String LOGIN_URL = "/api/v1/auth/login";
    private static final String TICKETS_URL = "/api/v1/tickets";

    @Autowired WebApplicationContext webApplicationContext;
    @Autowired ObjectMapper objectMapper;
    @Autowired TicketInfoMapper ticketInfoMapper;
    @Autowired TicketLogMapper ticketLogMapper;

    private MockMvc mockMvc;

    private MockMvc mockMvc() {
        if (mockMvc == null) {
            mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                    .apply(SecurityMockMvcConfigurers.springSecurity())
                    .build();
        }
        return mockMvc;
    }

    // ---------- 端到端主流程 ----------

    @Test
    void full_state_machine_flow_create_assign_resolve_close() throws Exception {
        String adminToken = loginAs("admin", "admin123");

        // 1. 创建工单（默认 PENDING）
        Long ticketId = createTicketAs(adminToken, Map.of(
                "title", "状态机端到端测试",
                "content", "走完整生命周期",
                "priority", "HIGH"));
        TicketInfo created = ticketInfoMapper.selectById(ticketId);
        assertThat(created.getStatus()).isEqualTo(TicketStatus.PENDING);
        assertThat(created.getHandlerId()).isNull();

        // 2. 分配给 handler=agent_user (id=3)
        mockMvc().perform(put(TICKETS_URL + "/" + ticketId + "/assign")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "handlerId", 3,
                                "reason", "分配给 agent_user"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(equalTo("200")));

        TicketInfo afterAssign = ticketInfoMapper.selectById(ticketId);
        assertThat(afterAssign.getStatus()).isEqualTo(TicketStatus.PROCESSING);
        assertThat(afterAssign.getHandlerId()).isEqualTo(3L);

        List<TicketLog> logsAfterAssign = logsFor(ticketId);
        assertThat(logsAfterAssign).hasSize(3); // CREATED + ASSIGNED + STATUS_CHANGED
        assertThat(logsAfterAssign.get(1).getEventType()).isEqualTo(TicketEventType.ASSIGNED);
        assertThat(logsAfterAssign.get(1).getContent())
                .contains("handlerId=3")
                .contains("reason=分配给 agent_user");
        assertThat(logsAfterAssign.get(2).getEventType()).isEqualTo(TicketEventType.STATUS_CHANGED);
        assertThat(logsAfterAssign.get(2).getContent())
                .contains("from=PENDING").contains("to=PROCESSING");

        // 3. 标记解决：PATCH /status with target=RESOLVED
        mockMvc().perform(patch(TICKETS_URL + "/" + ticketId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "targetStatus", "RESOLVED",
                                "reason", "已解决"))))
                .andExpect(status().isOk());

        TicketInfo afterResolve = ticketInfoMapper.selectById(ticketId);
        assertThat(afterResolve.getStatus()).isEqualTo(TicketStatus.RESOLVED);

        List<TicketLog> logsAfterResolve = logsFor(ticketId);
        assertThat(logsAfterResolve.get(3).getEventType()).isEqualTo(TicketEventType.STATUS_CHANGED);
        assertThat(logsAfterResolve.get(3).getContent())
                .contains("from=PROCESSING").contains("to=RESOLVED").contains("reason=已解决");

        // 4. 关闭工单：POST /close
        mockMvc().perform(post(TICKETS_URL + "/" + ticketId + "/close")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        TicketInfo afterClose = ticketInfoMapper.selectById(ticketId);
        assertThat(afterClose.getStatus()).isEqualTo(TicketStatus.CLOSED);

        List<TicketLog> logsAfterClose = logsFor(ticketId);
        assertThat(logsAfterClose).hasSize(5); // + STATUS_CHANGED (close)
        TicketLog closeLog = logsAfterClose.get(4);
        assertThat(closeLog.getEventType()).isEqualTo(TicketEventType.STATUS_CHANGED);
        assertThat(closeLog.getContent())
                .contains("from=RESOLVED").contains("to=CLOSED").contains("reason=closed");
    }

    @Test
    void changeStatus_illegal_transition_returns_409_T0102() throws Exception {
        String token = loginAs("admin", "admin123");

        // 创建后直接关闭（合法：PENDING → CLOSED）
        Long ticketId = createTicketAs(token, Map.of("title", "非法迁移测试", "content", "x", "priority", "MEDIUM"));
        mockMvc().perform(post(TICKETS_URL + "/" + ticketId + "/close")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // 此时工单是 CLOSED，尝试 CLOSED → PROCESSING（非法）—— ticket 06 AC 末条
        mockMvc().perform(patch(TICKETS_URL + "/" + ticketId + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "targetStatus", "PROCESSING"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(equalTo("T0102")));
    }

    @Test
    void assign_with_nonexistent_handler_returns_404_S0101() throws Exception {
        String token = loginAs("admin", "admin123");
        Long ticketId = createTicketAs(token, Map.of("title", "handler 缺失", "content", "x"));

        mockMvc().perform(put(TICKETS_URL + "/" + ticketId + "/assign")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("handlerId", 99999))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(equalTo("S0101")));
    }

    @Test
    void assign_with_disabled_handler_returns_404_S0101() throws Exception {
        String token = loginAs("admin", "admin123");
        Long ticketId = createTicketAs(token, Map.of("title", "handler 禁用", "content", "x"));

        // sys_user id=2 是 'resigned' (status=0)
        mockMvc().perform(put(TICKETS_URL + "/" + ticketId + "/assign")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("handlerId", 2))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(equalTo("S0101")));
    }

    @Test
    void changeStatus_on_nonexistent_ticket_returns_404_T0101() throws Exception {
        String token = loginAs("admin", "admin123");
        mockMvc().perform(patch(TICKETS_URL + "/999999/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("targetStatus", "PROCESSING"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(equalTo("T0101")));
    }

    // ---------- 权限隔离 ----------

    @Test
    void agent_user_cannot_change_status_returns_403() throws Exception {
        String adminToken = loginAs("admin", "admin123");
        Long ticketId = createTicketAs(adminToken, Map.of("title", "agent 试图改状态", "content", "x"));

        String agentToken = loginAs("agent_user", "admin123");
        mockMvc().perform(patch(TICKETS_URL + "/" + ticketId + "/status")
                        .header("Authorization", "Bearer " + agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("targetStatus", "PROCESSING"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(equalTo("403")));
    }

    @Test
    void agent_user_cannot_assign_returns_403() throws Exception {
        String adminToken = loginAs("admin", "admin123");
        Long ticketId = createTicketAs(adminToken, Map.of("title", "agent 试图分配", "content", "x"));

        String agentToken = loginAs("agent_user", "admin123");
        mockMvc().perform(put(TICKETS_URL + "/" + ticketId + "/assign")
                        .header("Authorization", "Bearer " + agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("handlerId", 3))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(equalTo("403")));
    }

    @Test
    void agent_user_cannot_close_returns_403() throws Exception {
        String adminToken = loginAs("admin", "admin123");
        Long ticketId = createTicketAs(adminToken, Map.of("title", "agent 试图关闭", "content", "x"));

        String agentToken = loginAs("agent_user", "admin123");
        mockMvc().perform(post(TICKETS_URL + "/" + ticketId + "/close")
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(equalTo("403")));
    }

    @Test
    void changeStatus_with_blank_targetStatus_returns_400() throws Exception {
        String token = loginAs("admin", "admin123");
        Long ticketId = createTicketAs(token, Map.of("title", "targetStatus 空值校验", "content", "x"));

        // targetStatus 字段为 null —— @NotNull 触发 400
        mockMvc().perform(patch(TICKETS_URL + "/" + ticketId + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(equalTo("C0400")));
    }

    // ---------- helpers ----------

    private Long createTicketAs(String token, Map<String, ?> fields) throws Exception {
        String resp = mockMvc().perform(post(TICKETS_URL)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(fields)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).path("data").asLong();
    }

    private String loginAs(String username, String password) throws Exception {
        String resp = mockMvc().perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username, "password", password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode root = objectMapper.readTree(resp);
        return root.path("data").path("token").asText();
    }

    private List<TicketLog> logsFor(Long ticketId) {
        return ticketLogMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<TicketLog>()
                        .eq(TicketLog::getTicketId, ticketId)
                        .orderByAsc(TicketLog::getId));
    }
}
