# AI 智能工单管理系统

> 基于 Spring Boot 3 + Vue 3 + Spring AI 的企业内部工单管理系统，覆盖工单从创建到关闭的完整生命周期，并以 DeepSeek 提供自动分类与回复建议辅助。AI 异常时双层降级，不阻塞主流程。

## 📖 项目简介

本项目面向**企业内部员工报障**场景，解决「工单流程冗长、分类依赖人工、客服响应慢」的问题。系统覆盖工单 **创建 → 自动分类 → 分配 → 多轮对话 → 解决 → 关闭** 的全生命周期，并基于 Spring AI + DeepSeek 实现创建时的自动分类与详情页的 AI 智能回复建议。

设计上强调**工程韧性**与**实现一致性**：

- **状态机集中校验** —— 合法状态迁移与非法迁移拒绝规则集中在一个静态方法
- **AI 双层降级** —— Spring AI / DeepSeek 异常时，业务层拿到兜底值继续推进，工单创建不阻塞
- **Redis 三类缓存防护** —— 空值防穿透、分布式锁防击穿、TTL 抖动防雪崩
- **AOP 自定义注解审计** —— 关键操作由切面统一记录，Controller 层不写一行日志代码
- **Spring Security 6 + JWT** —— 无状态认证 + Redis 黑名单主动失效

采用 6 模块 Maven 多模块架构，Docker 单镜像部署。

## ✨ 核心功能

### 1. 工单全生命周期管理

**业务问题**：工单在不同处理人之间流转，状态分散在 Service if-else 中难以维护。

**实现**：内置 4 状态状态机（`PENDING → PROCESSING → RESOLVED → CLOSED`），5 条合法迁移集中在 `TicketStatus.canTransitTo()` 静态方法校验，非法迁移抛统一业务异常 `T0102`。每次状态变更在同事务中写 `ticket_log` 业务事件流，保证主记录与变更流水严格一致。

### 2. AI 智能辅助

**业务问题**：报障工单类型、优先级、归属部门需要人工判定，回复需要参考历史对话。

**实现**：集成 Spring AI 1.x `ChatClient` + DeepSeek（OpenAI 兼容端点），业务层只依赖 `TicketClassifier` / `TicketReplier` 两个 interface，对 Spring AI 解耦。工单创建时同步发起分类；详情页"AI 智能回复"按钮基于多轮对话历史生成回复建议；所有调用落 `ai_ticket_record` 审计表（含成功结果 / 异常摘要）。

### 3. AI 双层降级

**业务问题**：AI 服务不可用时不能拖垮主流程。

**实现**：

- **第一层**（`DeepSeekClassifier` / `DeepSeekReplier` 内部）：`try-catch` 接住 Spring AI 异常，写 `ai_ticket_record.error_log`，返回默认分类 / 兜底回复
- **第二层**（`ticket-ticket` 业务层）：拿到兜底值后继续推进，工单创建不阻塞、不回滚

### 4. Redis 三类缓存防护

**业务问题**：缓存三大经典问题（穿透 / 击穿 / 雪崩）。

**实现**：

| 场景   | 方案                                           |
| ---- | -------------------------------------------- |
| 缓存穿透 | DB miss 时写 `__EMPTY__` 空值标记（短 TTL），命中直接抛业务异常 |
| 缓存击穿 | Redisson 分布式锁 + 双重检查，锁竞争失败降级直接查 DB           |
| 缓存雪崩 | TTL = 30min ± 5min 随机抖动                      |

写操作通过 `TransactionSynchronizationManager` 注册 after-commit 钩子，事务回滚不清缓存；事务提交后才删缓存，避免读到中间状态。

### 5. 权限与安全

**业务问题**：菜单级 + 按钮级权限统一管理，登出后 token 立即失效。

**实现**：

- **Spring Security 6** 无状态认证（`SessionCreationPolicy.STATELESS`）
- **JWT 单 token**，30 分钟过期
- **Redis 黑名单** 主动登出，剩余有效期自动清理
- **菜单权限**（`sys_menu`）控制前端路由与侧边栏
- **操作权限**（`sys_menu.permission`）通过 `@PreAuthorize("hasAuthority('ticket:create')")` 在方法边界拦截
- **BCrypt** 自适应哈希存储密码

### 6. AOP 操作审计

**业务问题**：Controller 层手动记录日志，重复且易遗漏。

**实现**：自定义 `@OperationLog` 注解 + AOP 切 Controller 边界，自动记录调用者 / API / IP / User-Agent / 请求参数 / 返回结果到 `operation_log` 表，参数超长自动截断。

### 7. 数据导出与附件

- **EasyExcel**：工单列表 .xlsx 导出，基于流式 API 写入 `OutputStream`
- **阿里云 OSS**：私有 Bucket 签名 URL 上传 / 下载附件；`aliyun.oss.enabled` 默认 false，无 OSS 账号时降级本地文件系统（`./tmp/oss/`）

### 8. 运营可视化

ECharts Dashboard 提供状态分布 / 优先级分布 / 趋势折线 / TopN 处理人 4 张统计图，Redis 5min 聚合缓存。

## 🛠 技术栈

| 层次         | 技术选型                                                                  |
|:---------- |:--------------------------------------------------------------------- |
| **后端框架**   | Java 17, Spring Boot 3.2, Spring MVC, Spring Security 6               |
| **AI 集成**  | Spring AI 1.x `ChatClient` + DeepSeek（OpenAI 兼容端点）                    |
| **数据访问**   | MyBatis Plus, MySQL 8（InnoDB）, H2（测试）                                 |
| **缓存与分布式** | Redis, Redisson（分布式锁 + 看门狗）                                           |
| **工具库**    | Apache EasyExcel（流式导出）, Knife4j（API 文档）, 阿里云 OSS SDK                  |
| **定时与调度**  | Spring Task（`@Scheduled`，日报聚合 + 黑名单清理）                                |
| **测试**     | JUnit 5, Mockito, Spring Boot Test, MockMvc                           |
| **前端框架**   | Vue 3, TypeScript, Vite, Element Plus, Pinia, Axios                   |
| **前端可视化**  | ECharts（4 图 Dashboard）                                                |
| **E2E 测试** | Playwright（mock 后端）                                                   |
| **部署运维**   | Maven 多模块单 fat jar, Docker（eclipse-temurin:17-jre-alpine）, Nginx 反向代理 |

## 🧩 模块结构

项目采用 Maven 多模块架构，单向依赖，业务模块间禁止互相调用：

```
ticket-web          Controller 层 / 启动类 / application.yml
ticket-common       Result<T> / 业务异常 / 全局异常处理 / 公共枚举
ticket-security     Spring Security 6 / JWT / Redis 黑名单 / @PreAuthorize
ticket-system       用户 / 角色 / 菜单 / 数据字典 / 工单分类（RBAC）
ticket-ticket       工单 CRUD / 4 状态机 / 评论 / 附件 / Redis 缓存 / AOP 日志
ticket-ai           Spring AI ChatClient / TicketClassifier / TicketReplier
```

依赖方向：`ticket-web` → 业务模块 → `ticket-common`；`ticket-common` → （无依赖）。

## 📐 系统架构

### 整体架构

![系统架构图](docs/images/architecture.png)

> 客户端 → Nginx → `ticket-web`（业务 Controller） → 业务模块 → MySQL / Redis / Redisson / 阿里云 OSS / DeepSeek API。

### 工单状态机

![工单状态机图](docs/images/state-machine.png)

> 4 状态 + 5 条合法迁移；非法迁移抛业务异常 `T0102`；迁移规则集中在 `TicketStatus.canTransitTo` 静态方法校验；状态变更与 `ticket_log` 同事务写入。

## 🧪 测试

使用 JUnit 5 + Mockito + Spring Boot Test（MockMvc + H2 内存数据库）覆盖核心业务路径与接口契约：

- **状态机迁移**：合法 / 非法迁移、异常码一致性
- **AI 双层降级**：Spring AI 异常 → 兜底值；JSON 解析失败 → 兜底值
- **Redis 缓存三类防护**：穿透（空值标记）、击穿（锁重试）、雪崩（TTL 抖动）；事务回滚不清缓存
- **JWT 与黑名单**：签名验证、过期、黑名单命中拒绝
- **RBAC**：未登录、token 无权限、token 已登出 三类 401 / 403 路径
- **AOP 审计**：注解命中、参数截断、异常路径仍写日志
- **EasyExcel 导出**：空集合写 header + 空 sheet；筛选条件边界