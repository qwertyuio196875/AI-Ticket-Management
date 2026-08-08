# 工单详情 Redis 缓存（简化版）

`ticket_info` 单条详情在读路径加 Redis 缓存：

- **Key**：`ticket:detail:{ticketId}`
- **Value**：完整 `ticket_info` JSON
- **TTL**：30 分钟 + 随机抖动 ± 5 分钟（防雪崩）
- **更新策略**：**写时失效**（任何 ticket_info 写入都 DEL key，不主动 SET）
- **防击穿**：Redisson 分布式锁包 `TicketService.getById`

## 简化

- 不做布隆过滤器防穿透（成本不值；空值缓存即可）
- 不做缓存预热
- `ticket_log` / 列表 / 统计均**不缓存**（条件维度多，命中率低）

## 面试怎么说

"工单详情读多写少，我加 Redis 缓存，写时失效避免并发写导致脏读；用 Redisson 分布式锁防热点数据击穿"。