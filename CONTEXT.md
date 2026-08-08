# AI 智能工单管理系统

企业内部使用的 AI 辅助工单管理系统。核心是工单从创建到关闭的完整事件流，配合 AI 做分类、回复和（后续）知识库问答。当前阶段为单体架构，但模块边界按未来微服务拆分设计。

## Language

**User（用户）**：
仅指企业内部员工（运维、客服、管理员等），通过 `sys_user` 登录后台操作。外部客户**不登录系统**；客户档案以轻量形式存在于工单的 `creator_id` 关联中，由内部员工代客录入。
_Avoid_: 客户、customer（指代外部时）、account

**Ticket（工单）**：
一次从报障到解决的**完整事件流**，以 `ticket_info` 为主记录、以 `ticket_log` 为状态变更流水。不是内部协作的"待办任务"。
_Avoid_: 任务、task、order

**Handler（处理人）**：
工单当前的具体负责人，必须是某个 `sys_user`。`handler_id` 永远是用户 ID，不是部门 ID。
_Avoid_: assignee、负责人（含义过宽）

**AssignDept（派单部门）**：
工单被派往的业务部门，存于 `assign_dept_id`。与 `handler_id` 解耦——派单时可以只指定部门（待分配）、或同时指定部门和处理人。
_Avoid_: 处理部门（与 handler 混淆）、部门

**Permission（权限）**：
权限系统分为**三层独立维度**，互不重叠：
- **菜单权限**（`sys_menu`）：决定前端路由与侧边栏的可见性。
- **操作权限**（`sys_menu.permission` 字符串，如 `ticket:create`）：按钮级，由后端 `@PreAuthorize` 校验。
- **数据权限**（`sys_role.data_scope` 1=全部/2=本部门/3=本人）：决定列表查询的过滤范围，由 AOP 切面统一注入。

_Avoid_: "权限"指代不清时（必须明确说"菜单/操作/数据"哪一类）

**AIClassification（AI 分类结果）**：
AI 在工单创建时给出的类型/优先级/处理部门建议，落库为 `ai_ticket_record` 的**不可变快照**。后续若管理员修改了 `ticket_info` 的对应字段，以 `ticket_info` 为准；AI 回复与统计也以 `ticket_info` 当前值为输入，而非 AI 原分类。
_Avoid_: AI 分类（不带"结果"二字时易被理解为仍在生效）

**TicketStatus（工单状态）**：
四种状态的枚举。Java 枚举名（`PENDING/PROCESSING/RESOLVED/CLOSED`）同时作为 DB 存储值和跨服务传输值；中文（待处理/处理中/已完成/已关闭）仅在 VO 层翻译给前端。合法迁移由 `TicketStatus.canTransitTo(next)` 集中维护。
_Avoid_: 用 0/1/2/3 等 magic number 存储