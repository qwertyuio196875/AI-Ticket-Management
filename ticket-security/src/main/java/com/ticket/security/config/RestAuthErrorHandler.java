package com.ticket.security.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticket.common.exception.BusinessExceptionCode;
import com.ticket.common.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Spring Security 异常出口 —— 把认证 / 授权失败也包装成统一的 {@link Result}。
 * <p>
 * 必须单独处理：Security 的过滤器链在 DispatcherServlet <b>之前</b>，
 * 抛出的异常到不了 {@code @RestControllerAdvice}，
 * 默认会返回 Spring Boot 的 {@code /error} HTML/JSON —— 与全站 {@code Result} 格式不一致。
 * <p>
 * 两个出口：
 * <ul>
 *     <li>401 {@link #commence}：未认证（无 token / 过期 / 篡改 / 已登出）</li>
 *     <li>403 {@link #handle}：已认证但权限不足（{@code @PreAuthorize} 落地在 ticket 03）</li>
 * </ul>
 */
@Component
public class RestAuthErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(RestAuthErrorHandler.class);

    private static final String UNAUTHORIZED_MESSAGE = "未认证或登录已失效，请重新登录";

    private final ObjectMapper objectMapper;

    public RestAuthErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 未认证 → HTTP 401 */
    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        log.debug("未认证访问 [{} {}]: {}",
                request.getMethod(), request.getRequestURI(), authException.getMessage());
        write(response, BusinessExceptionCode.AUTH_UNAUTHORIZED, UNAUTHORIZED_MESSAGE);
    }

    /** 权限不足 → HTTP 403 */
    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        log.warn("权限不足 [{} {}]: {}",
                request.getMethod(), request.getRequestURI(), accessDeniedException.getMessage());
        write(response, BusinessExceptionCode.AUTH_FORBIDDEN, BusinessExceptionCode.AUTH_FORBIDDEN.getMessage());
    }

    private void write(HttpServletResponse response, BusinessExceptionCode code, String message) throws IOException {
        response.setStatus(code.getHttpStatus().value());
        // 必须显式指定 UTF-8，否则中文提示乱码
        response.setContentType("application/json;charset=UTF-8");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), Result.error(code.getCode(), message));
    }
}
