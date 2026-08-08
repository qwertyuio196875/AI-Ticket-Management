# RBAC 两层权限（简化版）

RBAC 按两层实现，实习项目够用：

1. **菜单权限**（`sys_menu`）：决定前端路由 + 侧边栏可见性
2. **操作权限**（`sys_menu.permission` 字符串，如 `ticket:create`）：按钮级，后端 `@PreAuthorize` 校验

## 为什么

数据权限（按部门过滤）是企业级复杂特性，实习项目用不到——简化为两层足够讲清 RBAC 原理，面试官问起来"用户-角色-菜单"三表关系清晰。

## 影响

- 不做 `data_scope` 字段、不做 AOP 切面注入
- `sys_menu.permission` 字符串作为 `@PreAuthorize("hasAuthority('ticket:create')")` 的判断依据
- 权限字符串硬编码在 `@PreAuthorize` 注解里（项目规模小，常量类反而过度）