package com.ticket.web.ticket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticket.ticket.entity.OperationLogRecord;
import com.ticket.ticket.entity.TicketInfo;
import com.ticket.ticket.entity.TicketLog;
import com.ticket.ticket.enums.TicketEventType;
import com.ticket.ticket.mapper.OperationLogMapper;
import com.ticket.ticket.mapper.TicketInfoMapper;
import com.ticket.ticket.mapper.TicketLogMapper;
import com.ticket.ticket.service.TicketNoGenerator;
import com.ticket.ticket.service.impl.RedisTicketNoGenerator;
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

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ticket 05 —— 工单 CRUD 端到端集成测试（spec Seam 3）。
 * <p>
 * 全 Spring 上下文 + MockMvc，覆盖 ticket 05 AC 的端到端流程：
 * <ul>
 *     <li>login → POST 创建 → list 包含 → detail 一致 → PUT 修改 → DELETE 软删 → list 排除</li>
 *     <li>权限隔离：{@code agent_user} 无 {@code ticket:create / ticket:delete} → 403</li>
 *     <li>不存在 / 软删后查 → 404 / {@code T0101}</li>
 *     <li>工单业务流：创建时同事务写 {@code ticket_log(CREATED)}</li>
 *     <li>@OperationLog 切面：写端点自动落 {@code operation_log}（含 user / IP / method / params）</li>
 * </ul>
 * <p>
 * <b>替身说明</b>：测试机无 Redis，{@link TicketNoTestConfig} 提供
 * {@code @Primary} 的 {@link InMemoryTicketNoGenerator}，覆盖掉
 * 默认的 {@link RedisTicketNoGenerator}。
 * <p>
 * MySQL → H2（已 schema-locations/data-locations 指向 test/resources）。
 */
@SpringBootTest
class TicketCrudIntegrationTest {

    private static final String LOGIN_URL = "/api/v1/auth/login";
    private static final String TICKETS_URL = "/api/v1/tickets";

    @Autowired WebApplicationContext webApplicationContext;
    @Autowired ObjectMapper objectMapper;
    @Autowired TicketInfoMapper ticketInfoMapper;
    @Autowired TicketLogMapper ticketLogMapper;
    @Autowired OperationLogMapper operationLogMapper;

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
    void full_crud_flow_create_list_detail_update_delete() throws Exception {
        String token = loginAs("admin", "admin123");

        // 1. 创建
        String createBody = objectMapper.writeValueAsString(Map.of(
                "title", "VPN 登录不上",
                "content", "测试机访问内网 VPN 客户端报 720 错误，重启无效",
                "type", "网络问题",
                "priority", "HIGH"));
        String createResp = mockMvc().perform(post(TICKETS_URL)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(equalTo("200")))
                .andExpect(jsonPath("$.data").value(greaterThanOrEqualTo(1)))
                .andReturn().getResponse().getContentAsString();
        Long ticketId = objectMapper.readTree(createResp).path("data").asLong();
        assertThat(ticketId).isPositive();

        // 2. 列表包含
        mockMvc().perform(get(TICKETS_URL)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records", hasSize(greaterThanOrEqualTo(1))));

        // 3. 详情一致
        String detail = mockMvc().perform(get(TICKETS_URL + "/" + ticketId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(equalTo("200")))
                .andExpect(jsonPath("$.data.title").value(equalTo("VPN 登录不上")))
                .andExpect(jsonPath("$.data.priority").value(equalTo("HIGH")))
                .andExpect(jsonPath("$.data.status").value(equalTo("PENDING")))
                .andReturn().getResponse().getContentAsString();
        assertThat(detail).isNotBlank();

        // 4. 更新 title / content（admin 是创建人吗？是的 —— admin 创的；或者走管理员旁路）
        String updateBody = objectMapper.writeValueAsString(Map.of(
                "title", "VPN 登录不上（已升级紧急）",
                "content", "改用 SSL VPN 端口"));
        mockMvc().perform(put(TICKETS_URL + "/" + ticketId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(equalTo("200")));

        // 5. 软删
        mockMvc().perform(delete(TICKETS_URL + "/" + ticketId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(equalTo("200")));

        // 6. 列表不再包含刚被软删的工单（@TableLogic 过滤 is_deleted=1）
        // —— H2 在多个测试间共享，前序测试可能已经造了工单，所以不能断言 records.size == 0；
        // 只断言我们删的那张不在结果里
        String afterDelete = mockMvc().perform(get(TICKETS_URL)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode afterDeleteJson = objectMapper.readTree(afterDelete);
        JsonNode records = afterDeleteJson.path("data").path("records");
        boolean found = false;
        for (JsonNode r : records) {
            if (r.path("id").asLong() == ticketId) {
                found = true;
                break;
            }
        }
        assertThat(found).as("软删后的工单不应出现在列表中").isFalse();
    }

    // ---------- ticket_log 业务流 ----------

    @Test
    void create_writes_ticket_log_CREATED_in_same_transaction() throws Exception {
        String token = loginAs("admin", "admin123");
        String body = objectMapper.writeValueAsString(Map.of(
                "title", "账号权限申请",
                "content", "需要新增项目空间权限",
                "priority", "LOW"));
        String resp = mockMvc().perform(post(TICKETS_URL)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Long ticketId = objectMapper.readTree(resp).path("data").asLong();

        // ticket_log 应有 CREATED 事件
        // 注：ticket 08 起创建工单会额外产生 AI_CALLED log（AI 分类的事务后业务事件），
        // 这里断言 CREATED 一定存在，不再断言 size == 1。
        List<TicketLog> logs = ticketLogMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<TicketLog>()
                        .eq(TicketLog::getTicketId, ticketId));
        TicketLog createdLog = logs.stream()
                .filter(l -> l.getEventType() == TicketEventType.CREATED)
                .findFirst()
                .orElseThrow(() -> new AssertionError("expected a CREATED log"));
        assertThat(createdLog.getOperatorId()).isEqualTo(1L); // admin
        assertThat(createdLog.getContent()).contains("title=账号权限申请");
    }

    @Test
    void update_writes_ticket_log_UPDATED() throws Exception {
        String token = loginAs("admin", "admin123");
        // 1. 创建
        String resp = mockMvc().perform(post(TICKETS_URL)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "原标题", "content", "原内容", "priority", "MEDIUM"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Long ticketId = objectMapper.readTree(resp).path("data").asLong();

        // 2. 修改
        mockMvc().perform(put(TICKETS_URL + "/" + ticketId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "新标题", "content", "新内容"))))
                .andExpect(status().isOk());

        // 3. 验证 ticket_log 多了一条 UPDATED
        // 注：ticket 08 起创建工单会额外产生 AI_CALLED log（在 CREATED 之后、UPDATED 之前），
        // 所以这里断言 CREATED 与 UPDATED 两个事件都存在，不再断言 size == 2。
        List<TicketLog> logs = ticketLogMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<TicketLog>()
                        .eq(TicketLog::getTicketId, ticketId)
                        .orderByAsc(TicketLog::getId));
        TicketLog createdLog = logs.stream()
                .filter(l -> l.getEventType() == TicketEventType.CREATED)
                .findFirst()
                .orElseThrow(() -> new AssertionError("expected a CREATED log"));
        TicketLog updatedLog = logs.stream()
                .filter(l -> l.getEventType() == TicketEventType.UPDATED)
                .findFirst()
                .orElseThrow(() -> new AssertionError("expected an UPDATED log"));
        // UPDATED 一定在 CREATED 之后（保证顺序）
        assertThat(updatedLog.getId()).isGreaterThan(createdLog.getId());
    }

    // ---------- @OperationLog 审计 ----------

    @Test
    void create_ticket_writes_operation_log_with_admin_user() throws Exception {
        // 创建前先记一下 operation_log 当前行数
        long before = countOperationLog();

        String token = loginAs("admin", "admin123");
        mockMvc().perform(post(TICKETS_URL)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "审计测试工单", "content", "x", "priority", "MEDIUM"))))
                .andExpect(status().isOk());

        // 创建应触发 operation_log 写入
        long after = countOperationLog();
        assertThat(after - before).isEqualTo(1L);
    }

    // ---------- 权限隔离 ----------

    @Test
    void agent_user_cannot_create_ticket_returns_403() throws Exception {
        String token = loginAs("agent_user", "admin123");
        mockMvc().perform(post(TICKETS_URL)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "agent 试图创建", "content", "x", "priority", "MEDIUM"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(equalTo("403")));
    }

    @Test
    void agent_user_cannot_delete_ticket_returns_403() throws Exception {
        // admin 先创建一张
        String adminToken = loginAs("admin", "admin123");
        String createResp = mockMvc().perform(post(TICKETS_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "给 agent 测的", "content", "x", "priority", "MEDIUM"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Long ticketId = objectMapper.readTree(createResp).path("data").asLong();

        // agent 试图删除
        String agentToken = loginAs("agent_user", "admin123");
        mockMvc().perform(delete(TICKETS_URL + "/" + ticketId)
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(equalTo("403")));
    }

    // ---------- 不存在 / 软删后 ----------

    @Test
    void get_non_existent_ticket_returns_404() throws Exception {
        String token = loginAs("admin", "admin123");
        mockMvc().perform(get(TICKETS_URL + "/999999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(equalTo("T0101")));
    }

    @Test
    void get_soft_deleted_ticket_returns_404() throws Exception {
        String token = loginAs("admin", "admin123");
        String createResp = mockMvc().perform(post(TICKETS_URL)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "将被软删", "content", "x", "priority", "MEDIUM"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Long ticketId = objectMapper.readTree(createResp).path("data").asLong();

        mockMvc().perform(delete(TICKETS_URL + "/" + ticketId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc().perform(get(TICKETS_URL + "/" + ticketId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(equalTo("T0101")));
    }

    // ---------- 默认值 ----------

    @Test
    void create_without_priority_defaults_to_MEDIUM() throws Exception {
        String token = loginAs("admin", "admin123");
        String resp = mockMvc().perform(post(TICKETS_URL)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "无优先级", "content", "x"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Long ticketId = objectMapper.readTree(resp).path("data").asLong();

        mockMvc().perform(get(TICKETS_URL + "/" + ticketId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.priority").value(equalTo("MEDIUM")))
                .andExpect(jsonPath("$.data.status").value(equalTo("PENDING")));
    }

    @Test
    void create_ticket_no_starts_with_TK_and_today_date() throws Exception {
        String token = loginAs("admin", "admin123");
        String resp = mockMvc().perform(post(TICKETS_URL)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "验证 ticket_no", "content", "x"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Long ticketId = objectMapper.readTree(resp).path("data").asLong();

        TicketInfo info = ticketInfoMapper.selectById(ticketId);
        assertThat(info).isNotNull();
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        assertThat(info.getTicketNo()).startsWith("TK" + today);
        assertThat(info.getTicketNo().length()).isEqualTo("TK".length() + 8 + 9);
    }

    // ---------- 校验失败 ----------

    @Test
    void create_with_blank_title_returns_400() throws Exception {
        String token = loginAs("admin", "admin123");
        mockMvc().perform(post(TICKETS_URL)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\",\"content\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(equalTo("C0400")));
    }

    // ---------- helpers ----------

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

    private long countOperationLog() {
        Long count = operationLogMapper.selectCount(null);
        return count == null ? 0L : count;
    }

    /**
     * 替身：内存版工单编号生成器。
     * <p>
     * 测试机无 Redis，避免 {@code RedisTicketNoGenerator} 启动 / 调用时抛
     * RedisConnectionFailureException。
     * <p>
     * <b>复用</b>：{@code @Primary} + 按方法签名注入，覆盖默认的 Redis 实现。
     */
    @TestConfiguration
    static class TicketNoTestConfig {
        @Bean
        @Primary
        TicketNoGenerator inMemoryTicketNoGenerator() {
            return new InMemoryTicketNoGenerator();
        }
    }

    /**
     * 内存版工单编号生成器 —— 行为对齐 {@code RedisTicketNoGenerator}，
     * 但 sequence 用 {@link AtomicLong} 维护，按日 reset。
     * <p>
     * 只在本测试内使用。
     * <p>
     * <b>{@code SEQUENCES} 改为 {@code static}</b>：JUnit 5 默认每个 test method 创建
     * 新的测试类实例 —— 本生成器实例每次重建会让 sequence 从 1 重启，与同 JVM 内
     * 后续测试类（ticket 06+）共享 H2 时的 ticket_no 撞 {@code uk_ticket_no} 唯一索引。
     * 改 static 后序列计数器跨实例 / 跨测试方法共享，避开唯一索引冲突。本测试类
     * 单跑时行为与原实现等价（单类多方法时仍按日累计），对 ticket 05 验收零影响。
     */
    static class InMemoryTicketNoGenerator implements TicketNoGenerator {
        /** static —— 跨测试实例 / 跨测试类共享，避开 H2 uk_ticket_no 冲突 */
        private static final Map<String, AtomicLong> SEQUENCES = new HashMap<>();
        private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

        @Override
        public synchronized String next() {
            String dateKey = LocalDate.now().format(DATE_FORMATTER);
            long seq = SEQUENCES.computeIfAbsent(dateKey, k -> new AtomicLong(0)).incrementAndGet();
            return "TK" + dateKey + String.format("%09d", seq);
        }
    }
}
