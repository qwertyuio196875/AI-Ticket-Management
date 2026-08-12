# AI 智能工单管理系统

基于 Spring Boot 3 + Vue 3 的企业级单体工单管理系统，6 模块 Maven 多模块架构。面向企业内部员工，从报障到解决形成完整事件流，配合 DeepSeek AI 做自动分类与智能回复。

## 技术栈

**后端**：Java 17 / Spring Boot 3.2 / Spring MVC / Spring Security 6 / JWT（单 token，30 分钟）/ MyBatis Plus / MySQL 8 / Redis / Redisson / Apache EasyExcel / Knife4j / Spring AI 1.x `ChatClient`（DeepSeek 集成）/ 阿里云 OSS SDK。

**前端**：Vue 3 / TypeScript / Vite / Element Plus / Pinia / Axios / ECharts。

**AI**：Spring AI 1.x `ChatClient` + DeepSeek OpenAI 兼容端点。HTTP 拼体 / 超时 / 重试由 Spring AI 内部托管，业务层仅暴露 `TicketClassifier` + `TicketReplier` 两个 interface；Prompt 用 `resources/prompts/*.st` + `PromptTemplate` 占位渲染。

## 模块结构

- `ticket-web`：Controller 层，依赖其他所有业务模块
- `ticket-common`：公共工具 / 常量 / `Result<T>` / 全局异常 / 枚举
- `ticket-security`：Spring Security 配置 / JWT / `@PreAuthorize` 权限拦截 / 黑名单
- `ticket-system`：用户 / 角色 / 菜单 RBAC / 数据字典 / 工单分类
- `ticket-ticket`：工单 CRUD / 4 状态状态机 / 多轮对话 / 附件 / Redis 缓存 / AOP 日志
- `ticket-ai`：Spring AI `ChatClient` 封装 / `TicketClassifier` + `TicketReplier` 双 interface / Prompt 模板 / 失败降级双层防线 / `ai_ticket_record` 落库

## 核心能力

- **认证授权**：JWT 单 token + 两层 RBAC（菜单权限 + 操作权限 `@PreAuthorize`）
- **工单核心**：4 状态状态机（`PENDING → PROCESSING → RESOLVED → CLOSED`，迁移集中在 `TicketStatus.canTransitTo`），工单业务事件流（`ticket_log` 状态变更流水），工单多轮对话（CUSTOMER / AGENT / INTERNAL 三种评论类型）
- **AI 集成**：工单创建时 Spring AI `ChatClient` 同步发起分类 + 2s 短超时降默认分类（`OTHER / MEDIUM / 待人工分配`），真分类后台落 `ai_ticket_record`；工单详情页"AI 智能回复"按钮同步调用，多轮对话历史自维护 `List<Message>`；失败走双层防线（AI 层捕获 + 调用方兜底），不阻塞主流程
- **缓存防护**：Redis 详情缓存 + 空值防穿透 + Redisson 分布式锁防击穿 + 随机 TTL 防雪崩
- **AOP 审计**：自定义 `@OperationLog` 注解 + AOP 在 Controller 边界自动切，记录调用者 / API / 入参出参 / IP / UA
- **可观测**：MySQL 联合索引 `(status, handler_id, create_time)` + EXPLAIN 验证

## 开发进度

按 7 阶段逐步推进：项目初始化 → 认证授权 → 工单核心 → AI 集成 → 性能与体验 → 基础工程 → 收尾。当前已完成阶段 1–5（ticket 1–10）：项目骨架、Spring Security 6 认证、RBAC、字典/分类、工单 CRUD、状态机分配关闭、多轮对话、Spring AI `ChatClient` + DeepSeek 集成（见 [ADR-0028](docs/adr/0028-spring-ai-deepseek-http.md)）、Redis 详情缓存 + Redisson 分布式锁 + MySQL 联合索引、EasyExcel 工单导出 + ECharts Dashboard 4 图聚合。下一阶段推进 Knife4j / Spring Task / 阿里云 OSS / Vue 3 前端 / 部署上线（ticket 11+）。

## 简历项目描述

> 基于 Spring Boot 3 + MyBatis Plus + Redis + 阿里云 OSS 的企业级 AI 智能工单管理系统（单体架构，6 模块 Maven 多模块）。实现了 JWT 单点登录 + 两层 RBAC 权限控制 + 工单状态机 + 业务事件流 + AOP 自定义注解审计 + Redis 缓存防击穿 + EasyExcel 导出 + ECharts 统计 + Spring Task 定时任务 + Knife4j 接口文档；集成 DeepSeek AI 实现工单自动分类和智能回复，AI 失败优雅降级不阻塞主流程。前端 Vue 3 + Element Plus，Docker 部署阿里云 ECS。
