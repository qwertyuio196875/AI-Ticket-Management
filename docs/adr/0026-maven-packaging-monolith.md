# Maven 多模块打包策略（单体模式）

当前阶段采用**单体打包**：所有业务模块聚合为一个 Spring Boot fat jar，单一入口启动。

**模块角色**：
- `ticket-web`：**唯一入口模块**，含 `main` 方法 + `application.yml`
- `ticket-ticket` / `ticket-ai` / `ticket-system` / `ticket-log` / `ticket-statistics` / `ticket-security`：业务模块，作为 `ticket-web` 的 `compile` 依赖，编译进同一个 fat jar
- `ticket-common`：共享库，普通 jar，被所有业务模块依赖
- 根模块：`<packaging>pom</packaging>`

**构建产物**：`ai-ticket-system-1.0.0.jar`（fat jar，含所有依赖）。
**启动**：`java -jar ai-ticket-system-1.0.0.jar`（Dockerfile 见 ADR-0018）。

**未来微服务化**：仅需拆 `pom.xml` 模块边界（每个业务模块独立 `<packaging>` 与 main class），业务代码零改动。当前阶段**不预留 main class**（避免过度设计），微服务化时再分。

## 为什么

单体优先（用户明确）：当前目标是企业级后台能力验证，**过早预留微服务 main class** 是 YAGNI —— 写出来没人用，反而增加维护负担。当前阶段每个模块清晰分离 + 严格依赖单向（ADR-0008），未来拆服务时只搬运模块边界，不重构业务代码。

## 影响

- `ticket-web/pom.xml` 必须 `<dependency>` 所有业务模块
- 业务模块 pom.xml**不要**加 `spring-boot-maven-plugin`（避免 repackage 冲突）
- Docker 镜像单文件打整个 fat jar（见 ADR-0018）
- 模块间调用走 Spring Bean 注入（普通方法调用），不走 HTTP / Feign
- 单元测试每个模块独立跑（MyBatis-Plus 单测用 H2 或 Testcontainers）