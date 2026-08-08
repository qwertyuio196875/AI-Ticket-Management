# AI 智能工单管理系统（学习项目版）

企业内部使用的 AI 辅助工单管理系统（**单体架构**，6 模块 Maven 多模块）。核心是工单从创建到关闭的完整事件流，配合 DeepSeek AI 做分类和回复。用于 Java 后端学习 + 第一份实习面试。

## Language

**User（用户）**：
仅指企业内部员工（运维、客服、管理员），通过 `sys_user` 登录后台。外部客户**不登录系统**。
_Avoid_: 客户、customer（指代外部时）、account

**Ticket（工单）**：
一次从报障到解决的**完整事件流**，以 `ticket_info` 为主记录、以 `ticket_log` 为状态变更流水。
_Avoid_: 任务、task、order

**Handler（处理人）**：
工单当前的具体负责人，`handler_id` 是 `sys_user.id`。

**Permission（权限）**：
两层实现——
- **菜单权限**（`sys_menu`）：前端路由 + 侧边栏可见性
- **操作权限**（`sys_menu.permission` 字符串）：按钮级，`@PreAuthorize` 校验

_Avoid_: 提"数据权限"（本项目不实现）

**AIClassification（AI 分类结果）**：
DeepSeek 在工单创建时给出的 type / priority / department 建议，落 `ai_ticket_record`。失败时返回默认分类（OTHER / MEDIUM / 待人工分配），**不阻塞工单创建**。

**TicketStatus（工单状态）**：
四种状态枚举：`PENDING / PROCESSING / RESOLVED / CLOSED`。枚举名同时作为 DB 存储值。合法迁移由 `TicketStatus.canTransitTo(next)` 集中维护（见 ADR-0005）。

**TicketLog（工单业务日志）**：
工单维度的事件流，记录状态迁移、分配、内容修改、AI 调用。**与 `ticket_info` 同事务**（见 ADR-0012）。

**OperationLog（系统审计日志）**：
HTTP 请求维度的审计流，由 AOP 在 Controller 边界自动切，记录调用者、API、入参出参、IP、UA。

**TicketCache（工单缓存）**：
`ticket_info` 读路径有 Redis 缓存（见 ADR-0004）。key = `ticket:detail:{ticketId}`，TTL 30min ± 5min 抖动，写时失效。

**TicketNo（工单编号）**：
格式 `TK{yyyyMMdd}{9 位 sequence}`（见 ADR-0006），Redis INCR 每日重置。

**ErrorCode（业务异常码）**：
枚举 `BusinessExceptionCode` 集中定义（见 ADR-0009），格式 `模块前缀 + 序号`，全局异常处理统一包装。

**TicketComment（工单评论 / 回复）**：
工单的多轮对话载体。`ticket_comment` 表存回复内容、类型（`CUSTOMER` / `AGENT` / `INTERNAL`）、创建人、parent_id（支持嵌套回复，见 ADR-0034）。**与 `ticket_info` 不同事务**——评论失败不回滚工单主流程。

**TicketCategory（工单分类）**：
管理员可配置的工单分类字典。`ticket_category` 表存分类名 / 描述 / 排序号。AI 分类结果（`AIClassification.type`）应与字典对齐——AI 返回未知分类时落 `OTHER`。

**DataDict（数据字典）**：
将 `TicketStatus`、`TicketPriority` 等枚举的可选项抽到 `sys_dict` 表管理。type / priority / comment_type 等字段名固定，value 从字典查。便于运营调整而无需改代码。

## 模块结构（6 模块）

```
ai-ticket-system/
├── ticket-web          ← Controller 层
├── ticket-common       ← Result / Exception / 常量 / 工具
├── ticket-security     ← Spring Security 配置 + JWT + 权限拦截
├── ticket-system       ← 用户 / 角色 / 菜单 / 字典 RBAC
├── ticket-ticket       ← 工单 / 工单分类 / 评论 / 附件 / 状态机 / Redis缓存 / AOP 日志
└── ticket-ai           ← DeepSeek HTTP 调用 + Prompt + 结果解析
```

## 核心业务实体（7 个，与苍穹外卖同量级）

| 表 | 实体 | 说明 |
|---|---|---|
| `sys_user` / `sys_role` / `sys_menu` / `sys_role_menu` | RBAC | 4 张表，user-role-menu 多对多 |
| `sys_dict` | 数据字典 | type / priority / comment_type 等可选项 |
| `ticket_category` | 工单分类 | 管理员配置 |
| `ticket_info` | 工单主表 | 标题 / 内容 / 状态 / 处理人 |
| `ticket_log` | 工单业务日志 | 状态变更流水 |
| `ticket_comment` | 工单评论 | 客户 / 客服 / 内部备注三类型 |
| `ticket_attachment` | 工单附件 | OSS file_url + 元数据 |
| `ai_ticket_record` | AI 记录 | 分类 / 回复 + error_log |
| `daily_ticket_stats` | 每日统计 | 定时任务写入 |

## 技术栈

- Java 17 + Spring Boot 3.x + Spring MVC
- Spring Security 6 + JWT（单 token 30min）
- MyBatis Plus + MySQL 8
- Redis + Redisson
- Apache EasyExcel（Excel 导出，见 ADR-0030）
- Spring Task（@Scheduled 定时任务，见 ADR-0031）
- Knife4j（API 文档，见 ADR-0032）
- DeepSeek HTTP API（RestTemplate，见 ADR-0028）
- 阿里云 OSS（aliyun-sdk-oss，见 ADR-0014）
- 前端：Vue 3 + Element Plus + Vite + Axios + Pinia + ECharts（统计图，见 ADR-0033）