# 测试摘要（ticket 15）

> 运行日期：2026-08-13 · 命令：`mvn -B test`（6 模块全量）· 结果：**BUILD SUCCESS**

## 总览

| 模块 | 测试数 | 失败 | 错误 |
|---|---|---|---|
| ticket-common | 8 | 0 | 0 |
| ticket-security | 29 | 0 | 0 |
| ticket-system | 39 | 0 | 0 |
| ticket-ticket | 184 | 0 | 0 |
| ticket-ai | 17 | 0 | 0 |
| ticket-web | 81 | 0 | 0 |
| **合计** | **358** | **0** | **0** |

## 验收标准对照（ticket 15 AC）

| 验收项 | 要求 | 实测 | 结论 |
|---|---|---|---|
| 状态机测试 | > 10 | **62**（`TicketStatusTest` 53 参数化用例 + `TicketStateMachineIntegrationTest` 9 集成用例） | ✅ |
| 工单 CRUD 测试 | > 5 | **11**（`TicketCrudIntegrationTest`） | ✅ |
| 认证测试 | > 5 | **36**（`AuthServiceTest` 10 + `JwtUtilTest` 13 + `AuthIntegrationTest` 13） | ✅ |

## 关键测试清单

### 工单状态机（62）

- `TicketStatusTest`（ticket-ticket，纯 JUnit 5 单测）：5 条合法迁移（ADR-0005）+ 静态方法等价 + null 入参非法 + 非法迁移笛卡尔积全集 + 自迁非法 + CLOSED 终态 + `requireTransitTo` 抛 `T0102` / 合法不抛
- `TicketStateMachineIntegrationTest`（ticket-web，MockMvc 集成）：PENDING→PROCESSING、PENDING→CLOSED、PROCESSING→RESOLVED、PROCESSING→CLOSED、RESOLVED→CLOSED 及非法迁移被拒

### 工单 CRUD（11）

- `TicketCrudIntegrationTest`（ticket-web）：创建 / 列表过滤 / 详情 / 修改 / 删除 / 权限校验（`ticket:create` / `ticket:update` / `ticket:delete` / 创建人或管理员规则）

### 认证授权（36）

- `AuthServiceTest`（ticket-security）：登录成功 / 密码错误 / 用户禁用 / 登出入黑名单
- `JwtUtilTest`（ticket-security）：签发 / 解析 / 过期 / 签名篡改 / 空 token
- `AuthIntegrationTest`（ticket-web）：登录拿 token / `/api/v1/auth/me` / 登出后黑名单拦截 / `/api/v1/ping` 免鉴权

### 其他核心

- AI（17）：`DeepSeekClassifierTest` / `DeepSeekReplierTest`（mock `ChatClient`，成功 / JSON 异常 / 超时降级 / 模板渲染四类路径）+ `PromptTemplateTest`
- 缓存（ticket-ticket）：`TicketCacheServiceImplTest`（命中 / miss / Redisson 锁 / 空值防穿透 / TTL 抖动）
- RBAC：`MenuTreeAssemblerTest` / `SysUserServiceImplTest` / `RbacIntegrationTest`
- 导出 / 统计 / OSS / 定时任务：`TicketExportServiceImplTest` / `TicketStatsServiceImplTest` / `AliyunOssServiceTest` / `DailyTicketStatsTaskTest` 等

## 说明

- 测试架构遵循 spec Testing Decisions：Service 单测主力（Seam 1，Mockito）+ 集成冒烟（Seam 3，MockMvc + H2），不追求覆盖率门槛。
- 参数化用例数量按 Surefire 报告口径（`Tests run:` 展开后计数）。
