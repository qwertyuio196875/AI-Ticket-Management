# 0034 - 工单多轮对话（ticket_comment）

工单支持**客服与客户多轮对话**，类似苍穹外卖的订单详情对话。

## 实体设计

`ticket_comment` 表：
```
id              bigint PK
ticket_id       bigint      -- 关联 ticket_info
content         text        -- 回复内容
comment_type    varchar     -- CUSTOMER / AGENT / INTERNAL
creator_id      bigint      -- 创建人（sys_user.id）
parent_id       bigint NULL -- 父评论 id，支持嵌套回复
create_time     datetime
```

**comment_type 三种类型**：
- `CUSTOMER`：客户发（通过内部员工代发）
- `AGENT`：客服 / 运维发
- `INTERNAL`：内部备注（仅内部可见，**不发给客户**）

## API

- `POST /api/v1/tickets/{id}/comments`：新增回复
- `GET /api/v1/tickets/{id}/comments`：获取对话列表（按时间正序）
- `DELETE /api/v1/tickets/{id}/comments/{commentId}`：删除（仅创建者本人或管理员）

## UI

工单详情页底部显示对话流，支持回复、@提及、嵌套显示。

## 简化（不做的）

- 不做实时推送（WebSocket）——前端每次进入详情页拉一次
- 不做已读未读
- 不做评论表情 / 文件附件
- 不做评论搜索

## 面试怎么说

"工单支持多轮对话，对话流落 ticket_comment 表。区分客户回复、客服回复、内部备注三种类型，支持嵌套回复。前端工单详情页底部展示对话流"。

## 影响

- `ticket_comment` 是 ticket-ticket 模块内的子实体
- 列表查询：`WHERE ticket_id = ? ORDER BY create_time ASC`
- 评论权限：客服可见所有类型，客户代理（代客录入场景）只看 CUSTOMER + AGENT
- 评论写操作产生 `ticket_log` 流水（event=COMMENTED）
- 评论内容 XSS 过滤（HTML 转义）