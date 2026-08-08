# 0032 - API 文档：Knife4j

集成 **Knife4j**（Swagger 增强版）自动生成 API 文档。

## 实现要点

- **依赖**：`com.github.xiaoymin:knife4j-openapi3-jakarta-spring-boot-starter`
- **访问路径**：
  - 本地：`http://localhost:8080/doc.html`
  - Staging：`https://staging-api.example.com/doc.html`
- **分组**：每个模块独立 Docket bean（auth / system / ticket / ai）
- **Controller 注解完整**：`@Tag` / `@Operation` / `@Parameter` / `@ApiResponse`
- **DTO / VO**：`@Schema(description = "...")` 标注字段含义

## 简化

- 生产环境通过 `knife4j.production=true` 关闭 UI（安全基线）
- 不做接口调试权限控制（开发期用，生产期关闭）
- 不做接口版本切换

## 面试怎么说

"我集成了 Knife4j 自动生成 API 文档，按模块分组，每个 Controller 都有完整的注解描述"。

## 影响

- `application.yml` 配置 `springdoc.swagger-ui.path=/swagger-ui.html`
- Knife4j 与 Spring Security 集成：放行 `/doc.html` / `/v3/api-docs/**` 路径
- 前端开发期直接用 Knife4j UI 调试接口，无需 Postman