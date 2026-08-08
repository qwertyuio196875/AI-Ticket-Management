# ticket_no 生成策略

工单编号格式固定为 **`TK{yyyyMMdd}{9 位 zero-padded sequence}`**，例：`TK2026080800000001`。

生成方式：每次创建工单前先 `INCR ticket:no:seq:{yyyyMMdd}`（Redis），拿到 sequence 后拼成完整 ticket_no 写入 `ticket_info.ticket_no`。**每日 0 点重置**（Redis key 带日期，过期即可，新一天的 key 自动从 1 开始）。

## 为什么

- **可读性**：运营/客服看到 `TK20260808...` 一眼知道是哪天的工单，无需查库。
- **可排序**：字符串字典序与时间顺序一致，列表默认排序无需额外计算。
- **唯一性**：Redis 单点 INCR 保证原子性，无需分布式协调器。
- **不上 Snowflake**：当前 QPS 量级（企业内工单系统，并发远低于 Redis 单 key 写入上限）INCR 足够；Snowflake 的 worker-id 配置是当前阶段不必要的负担。

## 影响

- Redis 必须开启持久化（AOF + RDB），避免 Redis 宕机导致 sequence 丢失/重复。
- sequence 用 `Long` 类型；9 位 zero-padded 假设日单量 < 10 亿，远超企业内工单系统极限。
- 跨日边界：00:00:00.000 ~ 00:00:00.999 跨天的工单，sequence 归零但日期是新一天，**新工单**不会与昨日最后一张冲突；同一天内 sequence 永远唯一。
- 未来拆微服务后，sequence 生成由 ticket-service 自持，不需要跨服务协调。