# 0030 - 工单列表 Excel 导出

工单管理后台提供**工单列表导出 Excel**功能，使用 Apache EasyExcel。

## 实现要点

- **依赖**：`com.alibaba:easyexcel`
- **导出范围**：根据当前筛选条件（状态、优先级、处理人、时间范围）导出全部数据
- **格式**：`.xlsx`，单 sheet，多列（工单号 / 标题 / 类型 / 优先级 / 状态 / 创建人 / 处理人 / 创建时间）
- **API**：`GET /api/v1/tickets/export`（带筛选条件 query 参数）
- **样式**：表头加粗、数据行按状态着色
- **字段映射**：`@ExcelProperty("工单号")` 注解标注 VO 字段
- **列宽自适应**：`@ColumnWidth(20)`

## 简化

- 不做异步导出（大数据量场景用不到）
- 不做多 sheet
- 不做模板填充

## 面试怎么说

"我用 EasyExcel 实现了工单列表的 Excel 导出，支持筛选条件同步导出。它比 Apache POI 内存友好，几万行不爆内存"。