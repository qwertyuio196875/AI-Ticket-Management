package com.ticket.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * DemoController 集成 smoke test（tracer bullet 阶段 — 详见 ticket 01）。
 * <p>
 * 通过 {@code @SpringBootTest} 加载完整应用上下文，验证：
 * <ul>
 *     <li>正常响应路径：{@code /api/v1/ping} → {@code Result.success("pong")}</li>
 *     <li>DTO 校验路径：{@code /api/v1/echo} 接受 {@code @Valid} body，缺字段返回 400 + 业务码 "C0400"</li>
 * </ul>
 * <p>
 * 全局异常处理对 {@link com.ticket.common.exception.BusinessException} 的包装
 * 由 {@link com.ticket.common.exception.GlobalExceptionHandler} 自身保证，
 * 不在 Controller 测试中重复。
 */
@SpringBootTest
class DemoControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    private MockMvc mockMvc() {
        if (mockMvc == null) {
            // 让 @RestControllerAdvice（GlobalExceptionHandler）参与
            mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        }
        return mockMvc;
    }

    @Test
    void ping_returns_result_wrapping_pong() throws Exception {
        mockMvc().perform(get("/api/v1/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(equalTo("200")))
                .andExpect(jsonPath("$.message").value(equalTo("success")))
                .andExpect(jsonPath("$.data").value(equalTo("pong")));
    }

    @Test
    void echo_with_valid_body_returns_success_with_payload() throws Exception {
        DemoController.EchoRequest body = new DemoController.EchoRequest("hello", 18);

        mockMvc().perform(post("/api/v1/echo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(equalTo("200")))
                .andExpect(jsonPath("$.data.name").value(equalTo("hello")))
                .andExpect(jsonPath("$.data.age").value(equalTo(18)));
    }

    @Test
    void echo_with_missing_name_returns_400_via_global_handler() throws Exception {
        // 故意缺 name 字段，@NotBlank 触发 MethodArgumentNotValidException
        String invalidBody = "{\"age\":18}";

        mockMvc().perform(post("/api/v1/echo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(equalTo("C0400")))
                .andExpect(jsonPath("$.message").value(containsString("参数校验失败")));
    }
}