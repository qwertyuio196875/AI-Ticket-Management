# 简历项目描述（ticket 15）

> 用法：直接复制到简历「项目经历」板块；面试时配合 spec.md「差异化亮点」逐条讲解。

## 项目描述（1-2 段，面试口径）

基于 **Spring Boot 3** + MyBatis Plus + **Redis** + 阿里云 **OSS** 的企业级 AI 智能工单管理系统（单体架构，6 模块 Maven 多模块）。实现了 **JWT** 单点登录（单 token 30 分钟过期 + Redis 黑名单）+ 两层 **RBAC** 权限控制（菜单权限 + `@PreAuthorize` 操作权限）+ 工单 4 状态状态机（迁移校验集中 `TicketStatus.canTransitTo`）+ 工单业务事件流（`ticket_log` 全生命周期审计）+ AOP 自定义注解 `@OperationLog` 系统审计 + Redis 三层缓存防护（空值防穿透 / Redisson 分布式锁防击穿 / 随机 TTL 防雪崩）+ EasyExcel 工单导出 + ECharts 统计 Dashboard + Spring Task 定时任务（日报 / 黑名单清理）+ Knife4j 接口文档；并针对 MySQL 工单查询设计 `(status, handler_id, create_time)` 联合索引，用 `EXPLAIN` 验证走索引。

核心差异化：集成 **DeepSeek AI**（Spring AI 1.x `ChatClient`，OpenAI 兼容协议）实现工单创建时自动分类（同步发起 + 2s 短超时）与工单详情「AI 智能回复」（多轮对话历史自维护），失败走双层降级（AI 层捕获 + 调用方兜底）**不阻塞主流程**，所有 AI 调用落 `ai_ticket_record` 审计表。前端 Vue 3 + TypeScript + Element Plus + Pinia，Docker 单阶段镜像（非 root 运行）部署于阿里云 ECS，nginx 反向代理 + SSL 终止。全模块 JUnit 5 + Mockito 共 **358** 个测试用例（状态机 62 / CRUD 11 / 认证 36）。

## 面试讲解要点（可选背诵）

1. **AI 集成**：参考项目都没做，集成 DeepSeek 是最大差异化。
2. **状态机集中校验**：避免 if-else 散落各处，非法迁移抛统一业务码 `T0102`。
3. **业务事件流**：`ticket_log` 与工单主表同事务，是工单生命周期完整审计。
4. **AI 降级策略**：失败不阻塞主流程，体现"工程韧性"。
5. **三层防御缓存**：穿透（空值缓存）+ 击穿（Redisson 锁）+ 雪崩（随机 TTL）。
6. **AOP 自定义注解**：`@OperationLog` 切 Controller 边界，记录调用者 / API / 入参出参 / IP / UA。

## 关键技术词（简历关键词栏用）

Spring Boot 3 · Spring Security 6 · JWT · RBAC · MyBatis Plus · MySQL 8 · Redis · Redisson · EasyExcel · ECharts · Spring Task · Knife4j · Spring AI 1.x · DeepSeek · 阿里云 OSS · Docker · Vue 3 · Element Plus · TypeScript
