package com.ticket.ticket.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticket.ticket.entity.OperationLogRecord;
import com.ticket.ticket.mapper.OperationLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link OperationLogAspect} 单元测试（ticket 05 AC 末条）。
 * <p>
 * 纯 JUnit 5 + Mockito，不启动 Spring 上下文，毫秒级运行。
 * <p>
 * <b>覆盖</b>：
 * <ul>
 *     <li>成功路径 —— 验证 {@code operation_log} 写入 user / method / params / IP / UA / duration 正确</li>
 *     <li>异常路径 —— 业务异常被外抛，审计仍正常写入</li>
 *     <li>审计写入失败 —— 业务不被阻塞（异常吞掉）</li>
 *     <li>无登录态 —— 审计以空 user 写入</li>
 *     <li>无 Web 上下文 —— 审计以空 IP / UA 写入</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class OperationLogAspectTest {

    @Mock
    private OperationLogMapper operationLogMapper;

    @Mock
    private ProceedingJoinPoint joinPoint;

    private OperationLogAspect aspect;

    @BeforeEach
    void clearContext() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
        // 显式构造 —— @InjectMocks 找不到 raw ObjectMapper 字段注入（5.x 的 strict 行为），
        // 直接 new 出来最稳。
        aspect = new OperationLogAspect(operationLogMapper, new ObjectMapper());
    }

    @AfterEach
    void afterEach() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    /**
     * 成功路径：抓到 user / method / params / IP / UA / duration。
     */
    @Test
    void writes_audit_log_with_all_fields_on_success() throws Throwable {
        // given
        loginAs(42L, "alice", "ticket:create");
        bindRequest("203.0.113.7", "JUnit/5.0");

        Method method = SampleController.class.getMethod("create", String.class, int.class);
        stubJoinPoint(method, new Object[]{"hello", 7}, "ok", null);

        com.ticket.ticket.aspect.OperationLog annotation =
                method.getAnnotation(com.ticket.ticket.aspect.OperationLog.class);

        // when
        Object result = aspect.around(joinPoint, annotation);

        // then
        assertThat(result).isEqualTo("ok");
        ArgumentCaptor<OperationLogRecord> captor = ArgumentCaptor.forClass(OperationLogRecord.class);
        verify(operationLogMapper, times(1)).insert(captor.capture());
        OperationLogRecord rec = captor.getValue();
        assertThat(rec.getUserId()).isEqualTo(42L);
        assertThat(rec.getUsername()).isEqualTo("alice");
        assertThat(rec.getOperation()).isEqualTo("创建工单");
        assertThat(rec.getType()).isEqualTo("TICKET");
        assertThat(rec.getMethod()).isEqualTo(SampleController.class.getName() + ".create");
        assertThat(rec.getIp()).isEqualTo("203.0.113.7");
        assertThat(rec.getUserAgent()).isEqualTo("JUnit/5.0");
        assertThat(rec.getParams()).contains("hello").contains("7");
        assertThat(rec.getDurationMs()).isNotNull().isGreaterThanOrEqualTo(0L);
        assertThat(rec.getCreateTime()).isNotNull();
    }

    /**
     * 业务异常路径：异常被外抛，审计仍写入。
     */
    @Test
    void writes_audit_log_when_business_throws_and_rethrows() throws Throwable {
        // given
        loginAs(99L, "bob", "ticket:create");
        bindRequest("127.0.0.1", "test");

        Method method = SampleController.class.getMethod("create", String.class, int.class);
        RuntimeException boom = new RuntimeException("biz error");
        stubJoinPoint(method, new Object[]{"x", 1}, null, boom);

        com.ticket.ticket.aspect.OperationLog annotation =
                method.getAnnotation(com.ticket.ticket.aspect.OperationLog.class);

        // when + then: 异常被外抛
        RuntimeException thrown = null;
        try {
            aspect.around(joinPoint, annotation);
        } catch (RuntimeException ex) {
            thrown = ex;
        }
        assertThat(thrown).isSameAs(boom);

        // 但审计仍然写入
        verify(operationLogMapper, times(1)).insert(any(OperationLogRecord.class));
    }

    /**
     * 审计写入失败：业务结果不受影响（异常被吞）。
     */
    @Test
    void business_result_unaffected_when_audit_insert_fails() throws Throwable {
        // given
        loginAs(1L, "admin", "ticket:create");
        bindRequest("10.0.0.1", "ua");

        Method method = SampleController.class.getMethod("create", String.class, int.class);
        stubJoinPoint(method, new Object[]{"hi", 0}, "ok", null);

        // mapper.insert 抛异常 —— 审计失败
        org.mockito.Mockito.doThrow(new RuntimeException("DB down"))
                .when(operationLogMapper).insert(any(OperationLogRecord.class));

        com.ticket.ticket.aspect.OperationLog annotation =
                method.getAnnotation(com.ticket.ticket.aspect.OperationLog.class);

        // when —— 业务结果正常返回
        Object result = aspect.around(joinPoint, annotation);

        // then
        assertThat(result).isEqualTo("ok");
        verify(operationLogMapper, times(1)).insert(any(OperationLogRecord.class));
    }

    /**
     * 无登录态：审计以空 user / username 写入。
     */
    @Test
    void writes_audit_log_with_null_user_when_unauthenticated() throws Throwable {
        // 无 SecurityContextHolder 设置
        bindRequest("127.0.0.1", "ua");

        Method method = SampleController.class.getMethod("create", String.class, int.class);
        stubJoinPoint(method, new Object[]{"a", 0}, "ok", null);

        com.ticket.ticket.aspect.OperationLog annotation =
                method.getAnnotation(com.ticket.ticket.aspect.OperationLog.class);

        aspect.around(joinPoint, annotation);

        ArgumentCaptor<OperationLogRecord> captor = ArgumentCaptor.forClass(OperationLogRecord.class);
        verify(operationLogMapper).insert(captor.capture());
        assertThat(captor.getValue().getUserId()).isNull();
        assertThat(captor.getValue().getUsername()).isEqualTo("");
    }

    /**
     * 无 Web 上下文：审计 IP / UA 为 null。
     */
    @Test
    void writes_audit_log_with_null_ip_and_ua_when_no_request() throws Throwable {
        // 不绑 RequestContextHolder
        loginAs(1L, "u", "p");

        Method method = SampleController.class.getMethod("create", String.class, int.class);
        stubJoinPoint(method, new Object[]{"a", 0}, "ok", null);

        com.ticket.ticket.aspect.OperationLog annotation =
                method.getAnnotation(com.ticket.ticket.aspect.OperationLog.class);

        aspect.around(joinPoint, annotation);

        ArgumentCaptor<OperationLogRecord> captor = ArgumentCaptor.forClass(OperationLogRecord.class);
        verify(operationLogMapper).insert(captor.capture());
        assertThat(captor.getValue().getIp()).isNull();
        assertThat(captor.getValue().getUserAgent()).isNull();
    }

    /**
     * 入参包含 HttpServletRequest 时，过滤掉再序列化。
     */
    @Test
    void filters_servlet_objects_from_serialized_params() throws Throwable {
        // given
        loginAs(1L, "u", "p");
        bindRequest("127.0.0.1", "ua");

        Method method = SampleController.class.getMethod("echoWithRequest", HttpServletRequest.class, String.class);
        MockHttpServletRequest req = new MockHttpServletRequest();
        stubJoinPoint(method, new Object[]{req, "payload"}, "ok", null);

        com.ticket.ticket.aspect.OperationLog annotation =
                method.getAnnotation(com.ticket.ticket.aspect.OperationLog.class);

        aspect.around(joinPoint, annotation);

        ArgumentCaptor<OperationLogRecord> captor = ArgumentCaptor.forClass(OperationLogRecord.class);
        verify(operationLogMapper).insert(captor.capture());
        String params = captor.getValue().getParams();
        assertThat(params).contains("payload").doesNotContain("MockHttpServletRequest");
    }

    // ---------- helpers ----------

    /**
     * 一次性把 joinPoint 的所有 stubbing 配齐，避免嵌套 {@code when()} 触发 Mockito 的
     * "UnfinishedStubbing" 严格模式告警。
     * <p>
     * <b>调用顺序很关键</b>：先 {@code mock(MethodSignature.class)} 完成内层 stub，
     * 再用准备好的 signature 去 stubbing {@code joinPoint.getSignature()}。
     */
    private void stubJoinPoint(Method method, Object[] args, Object proceedReturn, RuntimeException proceedThrow) throws Throwable {
        MethodSignature sig = mock(MethodSignature.class);
        when(sig.getMethod()).thenReturn(method);
        when(joinPoint.getSignature()).thenReturn(sig);
        when(joinPoint.getArgs()).thenReturn(args);
        if (proceedThrow != null) {
            when(joinPoint.proceed()).thenThrow(proceedThrow);
        } else {
            when(joinPoint.proceed()).thenReturn(proceedReturn);
        }
    }

    private void loginAs(Long userId, String username, String permission) {
        com.ticket.security.user.LoginUser principal = com.ticket.security.user.LoginUser.fromToken(
                userId, username, List.of(permission));
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority(permission)));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private void bindRequest(String ip, String userAgent) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr(ip);
        req.addHeader("User-Agent", userAgent);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));
    }

    /**
     * 内部样例 controller —— 仅为反射取 method 注解用，不参与 Spring 装配。
     */
    static class SampleController {
        @com.ticket.ticket.aspect.OperationLog(value = "创建工单", type = "TICKET")
        public String create(String title, int n) {
            return "ok";
        }

        @com.ticket.ticket.aspect.OperationLog(value = "回显", type = "TICKET")
        public String echoWithRequest(HttpServletRequest request, String payload) {
            return "ok";
        }
    }
}
