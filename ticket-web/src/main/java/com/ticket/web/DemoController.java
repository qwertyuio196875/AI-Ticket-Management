package com.ticket.web;

import com.ticket.common.result.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 演示控制器（tracer bullet 阶段 — 详见 ticket 01）。
 * <p>
 * 暴露 tracer bullet 阶段要求的最少端点：
 * <ul>
 *     <li>{@code GET /api/v1/ping} — 占位 / 健康检查，返回 {@code Result.success("pong")}</li>
 *     <li>{@code POST /api/v1/echo} — 接受 {@code @Valid @RequestBody}，验证参数校验链路</li>
 * </ul>
 * <p>
 * 业务异常与兜底异常的包装已通过 {@code ticket-common} 中的
 * {@link com.ticket.common.exception.GlobalExceptionHandler} 全局生效，
 * 不在 Controller 中重复测试。
 */
@RestController
@RequestMapping("/api/v1")
public class DemoController {

    @GetMapping("/ping")
    public Result<String> ping() {
        return Result.success("pong");
    }

    @PostMapping("/echo")
    public Result<EchoResponse> echo(@Valid @RequestBody EchoRequest request) {
        return Result.success(new EchoResponse(request.getName(), request.getAge()));
    }

    /**
     * 回显请求 DTO — 含 {@code @NotBlank} 与 {@code @Min} 校验。
     */
    public static class EchoRequest {
        @NotBlank(message = "name 不能为空")
        private String name;

        @Min(value = 0, message = "age 不能小于 0")
        private Integer age;

        public EchoRequest() {
        }

        public EchoRequest(String name, Integer age) {
            this.name = name;
            this.age = age;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Integer getAge() {
            return age;
        }

        public void setAge(Integer age) {
            this.age = age;
        }
    }

    /**
     * 回显响应 DTO — 字段名承载其内容（避免与 request 字段语义错配）。
     */
    public static class EchoResponse {
        private String name;
        private Integer age;

        public EchoResponse() {
        }

        public EchoResponse(String name, Integer age) {
            this.name = name;
            this.age = age;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Integer getAge() {
            return age;
        }

        public void setAge(Integer age) {
            this.age = age;
        }
    }
}