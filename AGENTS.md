# AI 智能工单管理系统开发规范

## 一、项目介绍

### 项目名称

AI智能工单管理系统（AI Ticket Management System）

### 项目目标

基于 Java 技术栈，从零开发一个企业级 AI 智能工单管理系统（**单体架构**，6 模块 Maven 多模块）。

**当前阶段目标**：只实现单体架构版本。**禁止**当前阶段引入 Spring Cloud 微服务体系。

### 项目定位

该项目不是简单 CRUD 项目，需要体现真实企业级后端开发能力。

**重点能力**（按面试加分优先级）：

1. Spring Boot 3 多模块架构设计能力
2. Spring Security 6 + JWT 认证授权能力
3. MyBatis Plus + MySQL 设计与优化能力
4. Redis 缓存设计能力（防穿透 / 击穿 / 雪崩）
5. 阿里云 OSS 文件上传能力
6. DeepSeek AI 接口集成能力（RestTemplate）
7. AOP 自定义注解能力（@OperationLog）
8. 工单状态机 + 事件流设计能力

## 二、技术栈要求

### 后端技术（必须使用）

- Java 17+
- Spring Boot 3.x
- Spring MVC
- Spring Security 6
- JWT（单 token，30 分钟过期）
- MyBatis Plus
- MySQL 8
- Redis
- Redisson（分布式锁）
- Apache EasyExcel（导出）
- Knife4j（API 文档）
- RestTemplate（DeepSeek 调用）
- 阿里云 OSS SDK
- Maven

### 前端技术（够用即可）

- Vue 3
- TypeScript
- Vite
- Element Plus
- Pinia
- Axios
- ECharts（统计图）

### AI 技术

直接调用 DeepSeek HTTP API（OpenAI 兼容协议），**不引入** Spring AI 框架。

实现：

- AI 工单分类（创建工单时同步调用）
- AI 智能回复（工单详情页触发）

预留：AI 知识库问答（未来 RAG）

## 三、项目架构设计

### 模块结构（6 模块）

```
ai-ticket-system/
├── ticket-web          ← Controller 层 / API 接口
├── ticket-common       ← 公共工具类 / 常量 / 统一返回结果 / 全局异常 / 公共枚举
├── ticket-security     ← Spring Security 配置 / JWT / 权限拦截
├── ticket-system       ← 用户管理 / 角色管理 / 菜单管理 / 字典管理
├── ticket-ticket       ← 工单 CRUD / 工单分类 / 评论 / 附件 / 状态机 / Redis 缓存 / AOP 日志
└── ticket-ai           ← DeepSeek HTTP 调用 / Prompt / 结果解析
```

### 模块职责

#### ticket-web
- Controller
- API 接口
- 请求参数处理

#### ticket-common
- 公共工具类
- 常量
- 统一返回结果 `Result<T>`
- 全局异常 `@RestControllerAdvice`
- 公共枚举

#### ticket-security
- Spring Security 配置
- JWT 认证（单 token）
- `@PreAuthorize` 权限校验
- JWT 黑名单（Redis）

#### ticket-system
- 用户管理
- 角色管理
- 菜单管理
- 字典管理（type / priority / comment_type 等）

#### ticket-ticket
- 工单分类管理
- 工单 CRUD
- 工单状态机（4 状态 + `canTransitTo()` 集中校验）
- 工单业务日志 `ticket_log`
- 工单多轮对话 `ticket_comment`
- 工单附件（阿里云 OSS）
- Redis 详情缓存
- Redisson 防击穿
- AOP `@OperationLog` 系统审计
- 定时任务（清理 / 日报）
- ECharts 统计聚合
- EasyExcel 导出

#### ticket-ai
- DeepSeek HTTP 调用（RestTemplate）
- AI 工单分类
- AI 智能回复
- AI 记录落库 `ai_ticket_record`
- 失败降级（模板回复）

## 四、代码规范

必须遵循：**阿里巴巴 Java 开发规范**。

代码分层：

```
controller
service / serviceImpl
mapper
entity
dto
vo
config
aspect
```

**Controller**：只负责接收请求、参数校验、调用 Service，**禁止**复杂业务。

**Service**：负责核心业务逻辑。

**Mapper**：负责数据库操作。

**DTO**：请求参数。

**VO**：返回数据。

**Entity**：数据库映射。

## 五、基础工程能力

### 1. 统一返回结果

所有接口统一格式：
```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

### 2. 全局异常处理

`@RestControllerAdvice` 统一处理：
- 参数异常
- 业务异常（`BusinessException`）
- 系统异常

### 3. 参数校验

Hibernate Validator：`@NotBlank` / `@NotNull` / `@Size`。

### 4. 日志系统

SLF4J + Logback：
- 请求日志（Controller AOP）
- 异常日志（全局异常处理）
- 业务日志（关键节点显式记录）

## 六、用户权限系统

实现 RBAC（两层）。

**数据库设计**：

#### 用户表 sys_user
字段：id / username / password / nickname / status / create_time

#### 角色表 sys_role
字段：id / role_name / role_key

#### 菜单权限表 sys_menu
字段：id / menu_name / permission / path / component / icon / sort / parent_id

#### 关系表
sys_user_role / sys_role_menu

## 七、工单管理模块

项目核心业务。

### 工单表 ticket_info
字段：
```
id / ticket_no / title / content / type / priority / status /
creator_id / handler_id / create_time / update_time
```

状态：`PENDING / PROCESSING / RESOLVED / CLOSED`（合法迁移见 ADR-0005）

### 工单分类表 ticket_category
字段：id / name / description / sort

### 工单评论表 ticket_comment
字段：id / ticket_id / content / comment_type / creator_id / parent_id / create_time

### 工单附件表 ticket_attachment
字段：id / ticket_id / file_url / file_name / size / mime_type / uploader_id / upload_time

### AI 记录表 ai_ticket_record
字段：id / ticket_id / type / priority / department / reply_content / error_log / create_time

### 功能

**用户（内部员工）**：
- 创建工单
- 查看工单
- 工单多轮对话
- 查看处理进度

**管理员**：
- 查询工单
- 分配工单
- 修改状态
- 关闭工单
- Excel 导出
- 统计 Dashboard

## 八、工单流程日志

### ticket_log 业务日志
工单维度，记录：
- 状态变化（待处理 → 处理中）
- 分配（分配给某员工）
- 评论
- AI 调用

### @OperationLog 系统审计日志
HTTP 请求维度，AOP 自动切，记录：
- 调用者
- API
- 入参出参
- IP / UA

## 九、MySQL 优化要求

### 1. 索引优化

针对工单查询：
- 状态 `status` 单列索引
- 处理人 `handler_id` 单列索引
- 创建时间 `create_time` 单列索引
- `(status, handler_id, create_time)` 联合索引（最左匹配）

### 2. SQL 慢查询优化

开启 MySQL 慢查询日志，用 `EXPLAIN` 分析：
- SQL 执行计划
- 是否走索引
- 优化前后对比

## 十、Redis 缓存设计

使用场景：**工单详情查询**（读多写少）。

```
请求 → Redis 缓存
       ↓ 不存在
       Redisson 分布式锁（防击穿）
       ↓
       MySQL 查询 → 写入 Redis
       TTL 30min ± 5min 随机（防雪崩）
```

策略：
- **防穿透**：空值缓存（不存在也缓存短 TTL）
- **防击穿**：Redisson 分布式锁
- **防雪崩**：随机过期时间

## 十一、Spring AI 模块（DeepSeek HTTP）

### 调用方式
RestTemplate 直接调 `https://api.deepseek.com/chat/completions`（OpenAI 兼容协议）

### 配置
`application.yml` 配 `ai.deepseek.api-key=${DEEPSEEK_API_KEY}`

API Key 由开发者自己注册 DeepSeek 账号、配到环境变量。

### 功能
- **AI 工单分类**：标题 + 内容 → type / priority / department
- **AI 智能回复**：工单详情 → 排查步骤 + 解决方案

### 失败降级
- 分类失败：返回默认分类 `OTHER / MEDIUM / 待人工分配`，**不阻塞工单创建**
- 回复失败：返回模板回复

## 十二、自定义注解和 AOP

### 1. 操作日志注解 @OperationLog
```java
@OperationLog("创建工单")
```
AOP 自动记录：用户 / 方法 / 参数 / 时间。

### 2. 简化（不做的）
- 不做 `@RateLimit` 限流注解
- 不做 `@PermissionCheck` 自定义权限注解（用 Spring Security 的 `@PreAuthorize` 即可）

## 十三、单元测试要求

测试核心 Service：
- 工单状态机迁移（各种合法 / 非法情况）
- 工单创建 / 分配 / 关闭
- AI 服务（mock HTTP 调用）
- 权限校验

技术：
- JUnit 5
- Mockito
- SpringBootTest

**简化（不做的）**：
- 不强制覆盖率门槛
- 不做 Controller 详尽单测（集成测试即可）

## 十四、开发流程（7 阶段）

### 第一阶段：项目初始化
- Spring Boot 3 初始化
- Maven 6 模块搭建
- 数据库配置
- 基础框架（Result / Exception / 日志）

### 第二阶段：认证授权
- 用户 / 角色 / 菜单 RBAC
- JWT 单 token 登录
- 字典管理 `sys_dict`
- 工单分类管理 `ticket_category`

### 第三阶段：工单核心
- 工单 CRUD
- 4 状态状态机
- 工单业务日志 `ticket_log`
- 工单多轮对话 `ticket_comment`
- AOP `@OperationLog`

### 第四阶段：AI 集成
- DeepSeek HTTP 调用封装
- AI 工单分类
- AI 智能回复
- `ai_ticket_record` 落库

### 第五阶段：性能与体验
- Redis 详情缓存
- Redisson 分布式锁
- MySQL 联合索引 + EXPLAIN 验证
- EasyExcel 工单导出
- ECharts 统计 Dashboard

### 第六阶段：基础工程
- Knife4j API 文档
- Spring Task 定时任务（日报统计）
- 阿里云 OSS 附件上传
- Vue3 前端基础 CRUD

### 第七阶段：收尾
- 阿里云 ECS 部署
- Dockerfile
- JUnit 单测核心 Service
- 简历项目描述润色

## 十五、开发原则

1. 优先保证代码质量，**不追求快速堆功能**。
2. 所有模块**低耦合**。
3. 设计时考虑未来微服务拆分（**当前不实现**）。
4. **不允许** Controller 存在复杂业务。
5. 核心业务**必须**添加注释。
6. 每完成一个模块**必须**保证项目可以运行。
7. 遇到复杂功能**必须**先设计方案，再编码。

## 最终目标

完成基于 Spring Boot 3 + Vue 3 + MySQL + Redis + DeepSeek + 阿里云 OSS 的企业级 AI 智能工单管理系统单体版本。

## Agent skills

### Issue tracker

Issues live as Markdown files under `.scratch/<feature>/`. See `docs/agents/issue-tracker.md`.

### Triage labels

The five canonical triage roles, each label string equal to its name: `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: one `CONTEXT.md` plus `docs/adr/` at the repo root. See `docs/agents/domain.md`.