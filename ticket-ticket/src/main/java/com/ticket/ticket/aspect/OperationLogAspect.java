package com.ticket.ticket.aspect;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticket.security.context.SecurityContextUtils;
import com.ticket.ticket.entity.OperationLogRecord;
import com.ticket.ticket.mapper.OperationLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Arrays;

/**
 * HTTP 请求审计切面（ticket 05 / spec Phase 3）。
 * <p>
 * 拦截所有标注 {@link OperationLog} 的方法，织入审计逻辑：
 * <ul>
 *     <li>方法执行前 —— 序列化入参 + 抓 IP / UA</li>
 *     <li>方法执行后 —— 计算耗时 + 收集返回码</li>
 *     <li>方法抛异常 —— 同样记审计 + 重新抛（审计不吞业务异常）</li>
 * </ul>
 * <p>
 * <b>失败容忍</b>：审计写入（{@code operationLogMapper.insert}）抛异常时
 * <b>仅 log warn</b>，不外抛 —— 审计挂掉不能阻塞业务（spec AC 隐含要求；
 * 同时也避免因 DB 抖动让所有写端点 5xx）。
 * <p>
 * <b>截断策略</b>：
 * <ul>
 *     <li>{@code params} 截断 2000 字符</li>
 *     <li>{@code userAgent} 截断 500 字符</li>
 * </ul>
 */
@Aspect
@Component
public class OperationLogAspect {

    private static final Logger log = LoggerFactory.getLogger(OperationLogAspect.class);

    /** 入参序列化截断长度（字符） */
    private static final int MAX_PARAMS_LENGTH = 2000;
    /** User-Agent 截断长度（字符） */
    private static final int MAX_USER_AGENT_LENGTH = 500;

    private final OperationLogMapper operationLogMapper;
    private final ObjectMapper objectMapper;

    public OperationLogAspect(OperationLogMapper operationLogMapper,
                              ObjectMapper objectMapper) {
        this.operationLogMapper = operationLogMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 环绕通知 —— 抓执行耗时 + 入参 + 返回码 + 用户/IP/UA。
     * <p>
     * 切入点：{@code @annotation(com.ticket.ticket.aspect.OperationLog)}。
     * 仅 Controller 层（{@code com.ticket.web..controller..}）使用，
     * 但为通用性，不在切点表达式中限定包路径；非 Controller 误用也只是多写一条日志。
     */
    @Around("@annotation(operationLogAnnotation)")
    public Object around(ProceedingJoinPoint joinPoint,
                         OperationLog operationLogAnnotation) throws Throwable {
        long start = System.currentTimeMillis();
        Throwable thrown = null;
        Object result = null;
        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable t) {
            // 审计切面记完异常再外抛 —— 业务异常不该被切面吞
            thrown = t;
            throw t;
        } finally {
            long durationMs = System.currentTimeMillis() - start;
            try {
                recordAudit(joinPoint, operationLogAnnotation, result, thrown, durationMs);
            } catch (Exception auditEx) {
                // 审计失败不能影响业务；只 log warn
                log.warn("写入 operation_log 失败，审计已跳过: method={}, reason={}",
                        methodSignature(joinPoint), auditEx.getMessage());
            }
        }
    }

    /**
     * 组装并写入一条审计日志。
     * <p>
     * 任何异常都在调用方 try-catch，方法自身不抛。
     */
    private void recordAudit(ProceedingJoinPoint joinPoint,
                             OperationLog annotation,
                             Object result,
                             Throwable thrown,
                             long durationMs) {
        OperationLogRecord record = new OperationLogRecord();
        // 当前用户（未登录时为 null —— 匿名端点如 /api/v1/ping 不会标注 @OperationLog，
        // 但即便误标也应优雅降级，不抛）
        Long userId = null;
        String username = SecurityContextUtils.currentUsername();
        // 直接从上下文读 userId 避免重复拿 LoginUser 对象
        if (SecurityContextUtils.currentUser() != null) {
            userId = SecurityContextUtils.currentUser().getUserId();
        }
        record.setUserId(userId);
        record.setUsername(username);

        record.setOperation(annotation.value());
        record.setType(annotation.type());
        record.setMethod(methodSignature(joinPoint));
        record.setParams(serializeArgs(joinPoint));
        record.setDurationMs(durationMs);
        record.setCreateTime(LocalDateTime.now());

        // IP / UA（无 request 时如异步场景降级为 null）
        HttpServletRequest request = currentRequest();
        if (request != null) {
            record.setIp(request.getRemoteAddr());
            String userAgent = request.getHeader("User-Agent");
            if (userAgent != null && userAgent.length() > MAX_USER_AGENT_LENGTH) {
                userAgent = userAgent.substring(0, MAX_USER_AGENT_LENGTH);
            }
            record.setUserAgent(userAgent);
        }

        operationLogMapper.insert(record);
    }

    /**
     * 拼装方法签名 {@code ClassName.methodName}。
     */
    private String methodSignature(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        return method.getDeclaringClass().getName() + "." + method.getName();
    }

    /**
     * 序列化方法入参为 JSON 字符串，截断到 {@link #MAX_PARAMS_LENGTH}。
     * <p>
     * 失败时返回类名字符串（不抛）—— 审计字段不能因为序列化失败而阻塞整次审计。
     */
    private String serializeArgs(ProceedingJoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        if (args == null || args.length == 0) {
            return "[]";
        }
        // 过滤 HttpServletRequest / HttpServletResponse 等非业务参数（避免重复或无用序列化）
        Object[] filtered = Arrays.stream(args)
                .filter(arg -> !(arg instanceof HttpServletRequest))
                .filter(arg -> !(arg instanceof jakarta.servlet.http.HttpServletResponse))
                .toArray();
        if (filtered.length == 0) {
            return "[]";
        }
        try {
            String json = objectMapper.writeValueAsString(filtered);
            if (json.length() > MAX_PARAMS_LENGTH) {
                return json.substring(0, MAX_PARAMS_LENGTH) + "...[truncated]";
            }
            return json;
        } catch (JsonProcessingException ex) {
            log.warn("审计入参序列化失败: {}", ex.getMessage());
            return "[\"<serialize-failed>\"]";
        }
    }

    /**
     * 取当前 HTTP 请求；非 Web 上下文（异步 / 单元测试）返回 null。
     */
    private HttpServletRequest currentRequest() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attrs == null ? null : attrs.getRequest();
        } catch (IllegalStateException ex) {
            // 非 web 环境 RequestContextHolder 可能抛 ISE
            return null;
        }
    }
}
