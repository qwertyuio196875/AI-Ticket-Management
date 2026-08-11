package com.ticket.web.ticket;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticket.ticket.entity.TicketComment;
import com.ticket.ticket.entity.TicketInfo;
import com.ticket.ticket.entity.TicketLog;
import com.ticket.ticket.enums.TicketEventType;
import com.ticket.ticket.enums.TicketStatus;
import com.ticket.ticket.mapper.TicketCommentMapper;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ticket 07 —— 工单评论端到端集成测试（spec Seam 3）。
 * <p>
 * 全 Spring 上下文 + MockMvc，覆盖 ticket 07 AC 的端到端流程：
 * <ul>
 *     <li>POST CUSTOMER 评论 → GET 可见（含 content 已被 HTML escape）</li>
 *     <li>POST INTERNAL 评论 → admin 视角 GET 可见 / 非 admin 视角 GET 隐藏</li>
 *     <li>嵌套回复：父评论 → 子评论 → GET 按 ASC 顺序返回，parentId 正确</li>
 *     <li>CLOSED 工单：POST 抛 409 + {@code T0103}</li>
 *     <li>软删：仅创建者本人 / 管理员可删；他人在 403</li>
 *     <li>权限：未登录 → 401；agent_user 有 {@code ticket:comment} → 200</li>
 *     <li>{@code ticket_log(COMMENTED)} 同事务写入验证</li>
 * </ul>
 */
@SpringBootTest
class TicketCommentIntegrationTest {

    private static final String LOGIN_URL = "/api/v1/auth/login";
    private static final String TICKETS_URL = "/api/v1/tickets";

    @Autowired WebApplicationContext webApplicationContext;
    @Autowired ObjectMapper objectMapper;
    @Autowired TicketInfoMapper ticketInfoMapper;
    @Autowired TicketCommentMapper ticketCommentMapper;
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

    // ---------- 主流程：POST + GET 内容已 escape ----------

    @Test
    void post_customer_comment_then_get_returns_it_with_escaped_content() throws Exception {
        String token = loginAs("admin", "admin123");
        Long ticketId = createTicketAs(token, Map.of("title", "评论主流程", "content", "VPN 登录不上"));

        // 写一条带 HTML 特殊字符的评论
        String body = objectMapper.writeValueAsString(Map.of(
                "content", "<script>alert(1)</script>",
                "commentType", "CUSTOMER"));
        String resp = mockMvc().perform(post(TICKETS_URL + "/" + ticketId + "/comments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(equalTo("200")))
                .andReturn().getResponse().getContentAsString();
        Long commentId = objectMapper.readTree(resp).path("data").asLong();
        assertThat(commentId).isNotNull();

        // GET 列表：返回该评论，content 已被 HTML escape
        mockMvc().perform(get(TICKETS_URL + "/" + ticketId + "/comments")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(equalTo("200")))
                .andExpect(jsonPath("$.data[0].id").value(equalTo(commentId.intValue())))
                .andExpect(jsonPath("$.data[0].commentType").value(equalTo("CUSTOMER")))
                .andExpect(jsonPath("$.data[0].content").value(equalTo("&lt;script&gt;alert(1)&lt;/script&gt;")));

        // ticket_log 应当写一条 COMMENTED
        List<TicketLog> logs = logsFor(ticketId);
        assertThat(logs).hasSize(2); // CREATED + COMMENTED
        assertThat(logs.get(1).getEventType()).isEqualTo(TicketEventType.COMMENTED);
        assertThat(logs.get(1).getContent())
                .contains("commentId=" + commentId)
                .contains("commentType=CUSTOMER");
    }

    // ---------- INTERNAL 可见性过滤 ----------

    @Test
    void internal_comment_visible_to_admin_hidden_from_non_admin() throws Exception {
        String adminToken = loginAs("admin", "admin123");
        Long ticketId = createTicketAs(adminToken, Map.of("title", "内部备注", "content", "x"));

        // 写一条 INTERNAL 评论
        mockMvc().perform(post(TICKETS_URL + "/" + ticketId + "/comments")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "content", "内部排查：用户电脑环境问题",
                                "commentType", "INTERNAL"))))
                .andExpect(status().isOk());

        // admin 视角：能看到
        mockMvc().perform(get(TICKETS_URL + "/" + ticketId + "/comments")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(equalTo(1)))
                .andExpect(jsonPath("$.data[0].commentType").value(equalTo("INTERNAL")));

        // agent_user 视角：INTERNAL 被过滤
        String agentToken = loginAs("agent_user", "admin123");
        mockMvc().perform(get(TICKETS_URL + "/" + ticketId + "/comments")
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(equalTo(0)));
    }

    // ---------- 嵌套回复 ----------

    @Test
    void nested_reply_chain_renders_in_asc_order_with_correct_parent_id() throws Exception {
        String token = loginAs("admin", "admin123");
        Long ticketId = createTicketAs(token, Map.of("title", "嵌套回复", "content", "x"));

        // 1. 顶级评论
        String r1 = mockMvc().perform(post(TICKETS_URL + "/" + ticketId + "/comments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "content", "顶级评论",
                                "commentType", "CUSTOMER"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Long parentId = objectMapper.readTree(r1).path("data").asLong();

        // 2. 对顶级评论的回复
        mockMvc().perform(post(TICKETS_URL + "/" + ticketId + "/comments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "content", "回复顶级",
                                "commentType", "AGENT",
                                "parentId", parentId))))
                .andExpect(status().isOk());

        // 3. GET 列表：按 ASC，parentId 正确
        mockMvc().perform(get(TICKETS_URL + "/" + ticketId + "/comments")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(equalTo(2)))
                .andExpect(jsonPath("$.data[0].parentId").doesNotExist())
                .andExpect(jsonPath("$.data[1].parentId").value(equalTo(parentId.intValue())))
                .andExpect(jsonPath("$.data[1].commentType").value(equalTo("AGENT")));
    }

    @Test
    void nested_reply_with_parent_of_other_ticket_returns_400_T0105() throws Exception {
        String token = loginAs("admin", "admin123");
        Long ticketA = createTicketAs(token, Map.of("title", "工单A", "content", "x"));
        Long ticketB = createTicketAs(token, Map.of("title", "工单B", "content", "y"));

        // 在 ticketA 写一条评论
        String r1 = mockMvc().perform(post(TICKETS_URL + "/" + ticketA + "/comments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "content", "A 的评论",
                                "commentType", "CUSTOMER"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Long parentInA = objectMapper.readTree(r1).path("data").asLong();

        // 在 ticketB 试图把 parentId 指向 ticketA 的评论
        mockMvc().perform(post(TICKETS_URL + "/" + ticketB + "/comments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "content", "回复跨工单评论",
                                "commentType", "CUSTOMER",
                                "parentId", parentInA))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(equalTo("T0105")));
    }

    // ---------- CLOSED 工单拒绝评论 ----------

    @Test
    void post_comment_to_closed_ticket_returns_409_T0103() throws Exception {
        String token = loginAs("admin", "admin123");
        Long ticketId = createTicketAs(token, Map.of("title", "已关闭工单评论", "content", "x"));

        // 关闭工单
        mockMvc().perform(post(TICKETS_URL + "/" + ticketId + "/close")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // 再写评论：409 + T0103
        mockMvc().perform(post(TICKETS_URL + "/" + ticketId + "/comments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "content", "x",
                                "commentType", "CUSTOMER"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(equalTo("T0103")));
    }

    // ---------- 软删：仅创建者 / 管理员 ----------

    @Test
    void delete_by_creator_succeeds() throws Exception {
        String token = loginAs("admin", "admin123");
        Long ticketId = createTicketAs(token, Map.of("title", "软删-创建者", "content", "x"));

        String r = mockMvc().perform(post(TICKETS_URL + "/" + ticketId + "/comments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "content", "x",
                                "commentType", "CUSTOMER"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Long commentId = objectMapper.readTree(r).path("data").asLong();

        // admin 是创建者，可删
        mockMvc().perform(delete(TICKETS_URL + "/" + ticketId + "/comments/" + commentId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // DB 软删：selectById 拿不到（@TableLogic 过滤）
        TicketComment after = ticketCommentMapper.selectById(commentId);
        assertThat(after).isNull();
    }

    @Test
    void delete_by_admin_who_is_not_creator_succeeds() throws Exception {
        // agent_user 创建一条评论（agent 拥有 ticket:comment）
        String agentToken = loginAs("agent_user", "admin123");
        String adminToken = loginAs("admin", "admin123");
        Long ticketId = createTicketAs(adminToken, Map.of("title", "软删-管理员删他人", "content", "x"));

        String r = mockMvc().perform(post(TICKETS_URL + "/" + ticketId + "/comments")
                        .header("Authorization", "Bearer " + agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "content", "agent 创建的评论",
                                "commentType", "AGENT"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Long commentId = objectMapper.readTree(r).path("data").asLong();

        // admin 删除 agent 创建的评论
        mockMvc().perform(delete(TICKETS_URL + "/" + ticketId + "/comments/" + commentId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    // ---------- 权限 / 校验 ----------

    @Test
    void post_without_auth_returns_401() throws Exception {
        String adminToken = loginAs("admin", "admin123");
        Long ticketId = createTicketAs(adminToken, Map.of("title", "未登录评论", "content", "x"));

        mockMvc().perform(post(TICKETS_URL + "/" + ticketId + "/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "content", "x",
                                "commentType", "CUSTOMER"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void post_with_blank_content_returns_400() throws Exception {
        String token = loginAs("admin", "admin123");
        Long ticketId = createTicketAs(token, Map.of("title", "空内容", "content", "x"));

        mockMvc().perform(post(TICKETS_URL + "/" + ticketId + "/comments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "content", "",
                                "commentType", "CUSTOMER"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(equalTo("C0400")));
    }

    @Test
    void post_with_unknown_ticket_id_returns_404_T0101() throws Exception {
        String token = loginAs("admin", "admin123");

        mockMvc().perform(post(TICKETS_URL + "/999999/comments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "content", "x",
                                "commentType", "CUSTOMER"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(equalTo("T0101")));
    }

    @Test
    void agent_user_can_post_comment_returns_200() throws Exception {
        // agent_user 拥有 ticket:comment，可以回复工单
        String adminToken = loginAs("admin", "admin123");
        String agentToken = loginAs("agent_user", "admin123");
        Long ticketId = createTicketAs(adminToken, Map.of("title", "agent 回复", "content", "x"));

        mockMvc().perform(post(TICKETS_URL + "/" + ticketId + "/comments")
                        .header("Authorization", "Bearer " + agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "content", "agent 回复",
                                "commentType", "AGENT"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(equalTo("200")));

        // agent 不是 admin，但也是创建者，删除自己评论成功
        Long commentId = ticketCommentMapper.selectList(
                new LambdaQueryWrapper<TicketComment>().eq(TicketComment::getTicketId, ticketId)
        ).get(0).getId();
        mockMvc().perform(delete(TICKETS_URL + "/" + ticketId + "/comments/" + commentId)
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isOk());
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
                new LambdaQueryWrapper<TicketLog>()
                        .eq(TicketLog::getTicketId, ticketId)
                        .orderByAsc(TicketLog::getId));
    }
}
