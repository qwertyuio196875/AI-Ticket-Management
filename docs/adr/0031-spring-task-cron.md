# 0031 - 定时任务（Spring Task）

使用 Spring 自带 `@Scheduled` 实现定时任务，不引入独立调度框架。

## 任务列表

- **任务 1：清理过期 JWT 黑名单**
  - `@Scheduled(cron = "0 0 3 * * ?")`，每天凌晨 3 点
  - 清理 Redis 中过期的 blacklist key
- **任务 2：生成工单日报统计**
  - `@Scheduled(cron = "0 0 8 * * ?")`，每天早上 8 点
  - 统计昨日新建 / 已完成 / 平均处理时长
  - 落 `daily_ticket_stats` 表
- **任务 3：清理 30 天前已软删工单**（未来扩展）
  - `@Scheduled(cron = "0 0 4 * * ?")`，每天凌晨 4 点

## 简化

- 不引入 Quartz / XXL-Job
- 不做分布式任务调度（单节点足够）
- 不做任务监控告警（`task_execution_log` 表 + 日志即可）

## 面试怎么说

"我用 Spring Task 实现定时任务，包括清理过期数据、生成每日统计报表。简单场景不需要上 Quartz，@Scheduled 注解就够了"。

## 影响

- 主类加 `@EnableScheduling`
- 任务执行日志：`task_execution_log` 表（task_name / start_time / end_time / status / error）
- 未来多节点部署需要 ShedLock 防重复执行