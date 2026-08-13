# =============================================================
# AI 智能工单管理系统 — 单阶段镜像（ticket 15，见 spec Phase 7）
#
# 设计要点（面试可讲）：
#   1. 单阶段构建：fat jar 由 maven 在宿主机构建，镜像只做运行时，
#      无需在镜像内重复打包（见 spec Out of Scope：不做多阶段）。
#   2. eclipse-temurin:17-jre-alpine：只带 JRE 的 slim 基础镜像，
#      相比 jdk 版 / debian 版体积更小、攻击面更小。
#   3. 非 root 运行：容器内新建 app 用户降权，避免 root 进程逃逸风险。
#   4. -XX:MaxRAMPercentage=75.0：容器环境无法用 Xmx 硬编码，
#      按容器内存上限的百分比分配堆，适配不同规格的 ECS。
# =============================================================
FROM eclipse-temurin:17-jre-alpine

# 非 root 用户（busybox addgroup/adduser）
RUN addgroup -S app && adduser -S app -G app

WORKDIR /app

# ticket-web 的 repackage fat jar（6 模块打进一个可执行 jar）
COPY ticket-web/target/ticket-web-1.0.0.jar app.jar

USER app

EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
