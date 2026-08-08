# 工单状态机（简化版）

四种状态：`PENDING / PROCESSING / RESOLVED / CLOSED`

合法迁移集中在 `TicketStatus.canTransitTo(next)` 静态方法：

```
PENDING      →  PROCESSING   （分配）
PENDING      →  CLOSED       （关闭）
PROCESSING   →  RESOLVED     （完成）
PROCESSING   →  CLOSED       （关闭）
RESOLVED     →  CLOSED       （手动关闭）
```

## 简化

- 删掉 RESOLVED 7 天重开（复杂度不值）
- 删掉 PROCESSING 退回 PENDING（同上）
- 非法迁移抛业务异常 `TICKET_INVALID_TRANSITION`（见 ADR-0009）

## 面试怎么说

"我用枚举集中维护状态迁移规则，避免 if-else 散落各处；所有状态修改前走 `canTransitTo` 校验，非法迁移抛业务异常，每次状态变更写 `ticket_log` 流水"。