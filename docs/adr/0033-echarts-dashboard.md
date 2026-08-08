# 0033 - 工单统计 Dashboard（ECharts）

工单管理后台首页用 **ECharts** 实现统计 Dashboard。

## 图表

- **图 1：状态分布饼图**
  - 4 种状态（PENDING/PROCESSING/RESOLVED/CLOSED）工单数量分布
  - 颜色：PENDING 红色 / PROCESSING 蓝色 / RESOLVED 绿色 / CLOSED 灰色
- **图 2：近 7 天新建工单趋势线图**
  - X 轴日期，Y 轴数量
- **图 3：按优先级堆叠柱状图**
  - 按 priority（P0/P1/P2/P3/P4）分维度
- **图 4：处理人 TOP 10 柱状图**
  - 按 handler_id 统计已解决工单数

## 实现要点

- **前端**：`echarts` + `vue-echarts` 组件
- **后端**：`StatsController` 提供 4 个聚合查询端点
  - `GET /api/v1/stats/tickets/summary` —— 状态分布 + 总数
  - `GET /api/v1/stats/tickets/trend?days=7` —— 趋势
  - `GET /api/v1/stats/tickets/by-priority` —— 优先级分布
  - `GET /api/v1/stats/tickets/top-handlers?limit=10` —— TOP 10
- **聚合查询**：MySQL GROUP BY status / date / priority / handler_id
- **缓存**：Dashboard 数据 Redis 缓存 5 min（高频访问但实时性要求不高）
- **响应式**：窗口 resize 时图表重绘

## 简化

- 不引入 ES 做聚合（工单量级别用 ES 是过度设计）
- 不做实时数据（WebSocket 推送）

## 面试怎么说

"我用 ECharts 实现了工单统计 Dashboard，展示状态分布、近 7 天趋势、按优先级和 TOP10 处理人。后端聚合查询 + Redis 缓存 5 分钟"。

## 影响

- 前端 `package.json` 加 `echarts` + `vue-echarts`
- 后端 `StatsController` 在 ticket-ticket 模块
- 聚合查询 SQL 用 `EXPLAIN` 验证走索引