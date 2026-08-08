# 业务异常码（简化版）

业务异常用**枚举**集中定义：

```java
public enum BusinessExceptionCode {
    TICKET_NOT_FOUND("T0101", "工单不存在"),
    TICKET_INVALID_TRANSITION("T0102", "状态非法迁移"),
    USER_NOT_FOUND("S0101", "用户不存在"),
    ROLE_NOT_FOUND("S0201", "角色不存在"),
    AI_UNAVAILABLE("A0101", "AI 服务不可用");

    private final String code;
    private final String message;
}
```

## 简化

- 不做 i18n 同步（前端直接用 `Result.message` 字段）
- 不做模块化字符串注册中心同步导出（项目小用不到）
- 全局异常处理 `@RestControllerAdvice` 统一包装 `Result.error(code, message)`

## 面试怎么说

"我设计了业务异常码体系（模块前缀 + 序号），enum 集中定义，全局异常处理统一包装返回，前端按 code 做错误提示"。