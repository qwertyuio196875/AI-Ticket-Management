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
6. Spring AI 1.x `ChatClient` 集成能力（DeepSeek OpenAI 兼容）
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
- Spring AI 1.x `ChatClient`（DeepSeek 集成）
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

集成 **Spring AI 1.x** + **DeepSeek**（OpenAI 兼容协议）：用 `spring-ai-openai-spring-boot-starter` 配 `spring.ai.openai.base-url=https://api.deepseek.com`，业务层通过 `ChatClient.call(prompt)` 调用；HTTP 拼体 / 超时 / 重试由 Spring AI 内部托管。详见 [§十一](#十一spring-ai-模块deepseek-集成) 与 [ADR-0028](docs/adr/0028-spring-ai-deepseek-http.md)。

实现：

- AI 工单分类（创建工单时同步发起 + 2s 短超时降级；真结果后台落 `ai_ticket_record`）
- AI 智能回复（工单详情页"AI 智能回复"按钮同步调用）

失败降级（双层防线）：

- 分类失败 → 默认 `OTHER / MEDIUM / 待人工分配`，**不阻塞工单创建**
- 回复失败 → 内置模板回复

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
└── ticket-ai           ← Spring AI ChatClient 封装 / 两个业务 interface / Prompt 模板 / 失败降级 / 落 `ai_ticket_record`
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
- Spring AI 1.x `ChatClient` 封装（DeepSeek OpenAI 兼容端点）
- 业务 interface：`TicketClassifier` + `TicketReplier`（外部模块只依赖 interface）
- AI 工单分类（创建工单同步 + 2s 短超时降级）
- AI 智能回复（多轮对话历史自维护 `List<Message>`）
- Prompt 模板（`resources/prompts/*.st` + `PromptTemplate` 占位渲染）
- AI 记录落库 `ai_ticket_record`（含 `error_log`）
- 失败降级（双层防线：AI 层捕获 + 调用方兜底）

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
字段：id / ticket_id / call_type / model / prompt_version / response_content / error_log / success / create_time
- call_type：CLASSIFY（创建工单时自动分类）/ REPLY（智能回复）
- success：1 成功 / 0 失败；失败时 error_log 记录异常摘要

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

## 十一、Spring AI 模块（DeepSeek 集成）

AI 模块用 **Spring AI 1.x `ChatClient`** + **DeepSeek OpenAI 兼容协议** 实现。HTTP 拼体 / 超时 / 重试由 Spring AI 内部托管；本项目层只暴露业务 interface、Prompt 模板与失败降级。详细设计见 [ADR-0028](docs/adr/0028-spring-ai-deepseek-http.md)。

### 调用方式

- Spring AI 自动注入 `ChatClient` Bean（由 `spring-ai-openai-spring-boot-starter` 提供）。
- `DeepSeekClassifier` / `DeepSeekReplier` 内部 `chatClient.call(prompt)` 调 DeepSeek。
- 多轮对话历史由业务层从 `ticket_comment` 表查后自维护 `List<Message>`，**不**引入 Spring AI `ChatMemory` 双层抽象。

### 配置

`application.yml`：

```yaml
spring:
  ai:
    openai:
      api-key: ${DEEPSEEK_API_KEY}      # 仅环境变量，无默认值（fail-fast）
      base-url: https://api.deepseek.com
      chat:
        options:
          model: deepseek-chat
          temperature: 0.3
          max-tokens: 2048
          timeout: 30s
```

API Key 由开发者自己注册 DeepSeek 账号、配到环境变量 `DEEPSEEK_API_KEY=sk-xxxxxxxx`，**不进 Git**。自 Spring AI 1.0 GA 起，本项目从历史的 `ai.deepseek.*` 自定义字段统一迁移到 `spring.ai.openai.*`。

### Prompt 管理

模板放在 `ticket-ai/src/main/resources/prompts/`：

- `classify.st`：工单分类 Prompt（强约束 JSON 输出：`type / priority / department`）
- `reply.st`：工单智能回复 Prompt（工单基本信息 + 历史对话占位渲染）

运行时通过 `org.springframework.ai.chat.prompt.PromptTemplate` 读取 + `${var}` 占位符替换。

### 功能

- **AI 工单分类**：标题 + 内容 → `TicketClassifyResult { type, priority, department }`，类型严格枚举（`NETWORK/HARDWARE/SOFTWARE/ACCOUNT/OTHER` 等）。
- **AI 智能回复**：工单基本信息 + 多轮对话历史 → 排查思路 + 解决方案。

### 同步性

- 创建工单同步发起分类 + 2s 短超时降级：超时 → 默认分类立即落库，真分类结果后台落 `ai_ticket_record`。
- AI 智能回复按钮同步调用，30s 等待上限。

### 失败降级（双层防线）

- **第一层**：`DeepSeekClassifier` / `DeepSeekReplier` 内部 `try-catch` 接住 Spring AI 异常 → 写 `ai_ticket_record.error_log` → 返回业务兜底值（分类 = `OTHER / MEDIUM / 待人工分配`，回复 = 模板回复）。
- **第二层**：调用方（`ticket-ticket`）拿到兜底值后继续推进业务主流程，**不阻塞工单创建**。

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
- AI 服务（mock `ChatClient`，覆盖 happy / 异常 JSON / 超时降级 / 模板渲染四类路径）
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

### 第四阶段：AI 集成（Spring AI 改造）
- Spring AI 1.x `ChatClient` 封装（DeepSeek OpenAI 兼容端点）
- 业务 interface 双拆分：`TicketClassifier` + `TicketReplier`
- AI 工单分类（创建工单同步发起 + 2s 短超时降级）
- AI 智能回复（多轮对话历史自维护 `List<Message>`）
- Prompt 模板：`resources/prompts/*.st` + `PromptTemplate` 占位符渲染
- `ai_ticket_record` 落库（含 `error_log`）
- 失败降级双层防线 + 7 个单测用例覆盖

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
8. 所有 Markdown 格式的文档（含本文件、`CONTEXT.md`、`docs/adr/**`、`docs/agents/**`、README、commit message、代码注释里的设计说明等）**必须**用中文书写；面向用户的产品文案与面向开发者的技术文档一致。

   - **代码内英文标识符**（类名 / 方法名 / 变量名 / 包名 / 常量名 / 数据库字段名 / HTTP 路径 / 业务码）保持英文 —— 它们是程序接口，不是文档。
   - **Javadoc / 行内注释**遵循同样的规则：注释文本用中文，技术术语保留英文（如「JWT」「BCrypt」「Redis」「缓存击穿」）。
   - **对外文案**（用户看得到的提示语、`Result.message`、异常 message、Swagger/Knife4j 注解描述）**必须**中文。
   - **commit message** 中文书写，参照 ticket 1 / ticket 2 的写法（feat(ticket2): JWT + Spring Security 6 认证授权）。
   - **例外**：纯工具性脚本里临时打印的英文日志可不改；外部开源依赖的英文配置（`application.yml` 的固定字段名、Spring 注解参数等）保持原样。

## 最终目标

完成基于 Spring Boot 3 + Vue 3 + MySQL + Redis + DeepSeek + 阿里云 OSS 的企业级 AI 智能工单管理系统单体版本。

## Agent skills

### Issue tracker

Issues live as Markdown files under `.scratch/<feature>/`. See `docs/agents/issue-tracker.md`.

### Triage labels

The five canonical triage roles, each label string equal to its name: `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: one `CONTEXT.md` plus `docs/adr/` at the repo root. See `docs/agents/domain.md`.