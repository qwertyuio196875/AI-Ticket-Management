import { expect, type Page } from '@playwright/test'

// ============================================================
// E2E 公共 helpers：菜单树 / 基础后端 mock / 登录辅助
// 供 login.spec 与 ticket-flow.spec 共用（本文件不含 test()，不会被收集执行）
// ============================================================

/** 贴近真实种子的菜单树：C 型页面菜单 + F 型按钮权限（全部真实权限串） */
export const menuTree = [
  {
    id: 1, parentId: 0, menuName: 'Dashboard', menuType: 'C', path: '/dashboard',
    component: 'Dashboard', icon: 'dashboard', sort: 1, visible: true,
    permission: 'stats:view', children: [],
  },
  {
    id: 2, parentId: 0, menuName: '用户管理', menuType: 'C', path: '/system/users',
    component: 'UserList', icon: 'user', sort: 2, visible: true,
    permission: 'user:manage', children: [],
  },
  {
    id: 3, parentId: 0, menuName: '角色管理', menuType: 'C', path: '/system/roles',
    component: 'RoleList', icon: 'role', sort: 3, visible: true,
    permission: 'role:manage', children: [],
  },
  {
    id: 4, parentId: 0, menuName: '菜单管理', menuType: 'C', path: '/system/menus',
    component: 'MenuList', icon: 'menu', sort: 4, visible: true,
    permission: 'menu:manage', children: [],
  },
  {
    id: 5, parentId: 0, menuName: '工单列表', menuType: 'C', path: '/tickets',
    component: 'TicketList', icon: 'ticket', sort: 5, visible: true,
    permission: 'ticket:view',
    children: [
      // F 型按钮权限：无 path，侧边栏不得渲染；权限串进入 permissions 供 v-permission 判定
      { id: 51, parentId: 5, menuName: '新建工单', menuType: 'F', path: '', permission: 'ticket:create', children: [] },
      { id: 52, parentId: 5, menuName: '删除工单', menuType: 'F', path: '', permission: 'ticket:delete', children: [] },
      { id: 53, parentId: 5, menuName: '分配工单', menuType: 'F', path: '', permission: 'ticket:assign', children: [] },
      { id: 54, parentId: 5, menuName: '关闭工单', menuType: 'F', path: '', permission: 'ticket:close', children: [] },
      { id: 55, parentId: 5, menuName: '评论工单', menuType: 'F', path: '', permission: 'ticket:comment', children: [] },
      { id: 56, parentId: 5, menuName: '上传附件', menuType: 'F', path: '', permission: 'ticket:upload', children: [] },
      { id: 57, parentId: 5, menuName: '状态流转', menuType: 'F', path: '', permission: 'ticket:update', children: [] },
      { id: 58, parentId: 5, menuName: 'AI 回复', menuType: 'F', path: '', permission: 'ai:invoke', children: [] },
    ],
  },
  {
    id: 6, parentId: 0, menuName: '字典管理', menuType: 'C', path: '/system/dicts',
    component: 'DictList', icon: 'dict', sort: 6, visible: true,
    permission: 'dict:manage', children: [],
  },
  {
    id: 7, parentId: 0, menuName: '工单分类', menuType: 'C', path: '/system/ticket-categories',
    component: 'TicketCategoryList', icon: 'category', sort: 7, visible: true,
    permission: 'category:manage', children: [],
  },
]

/** 拦截并 mock 基础后端 API（登录 / 菜单 / 登出 / 当前用户 / Dashboard 统计） */
export async function mockBackend(page: Page) {
  // 登录：返回模拟 JWT 与会话信息
  await page.route('**/api/v1/auth/login', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      json: {
        code: '200',
        message: 'success',
        data: {
          token: 'mock-jwt-token',
          tokenType: 'Bearer',
          expiresIn: 1800,
          userId: 1,
          username: 'admin',
          nickname: '超级管理员',
        },
      },
    })
  })

  // 菜单树
  await page.route('**/api/v1/menus/tree', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      json: { code: '200', message: 'success', data: menuTree },
    })
  })

  // 登出
  await page.route('**/api/v1/auth/logout', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      json: { code: '200', message: 'success', data: null },
    })
  })

  // 当前用户（页面刷新恢复登录态时会调用）
  await page.route('**/api/v1/auth/me', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      json: {
        code: '200',
        message: 'success',
        data: { userId: 1, username: 'admin', authorities: ['stats:view'] },
      },
    })
  })

  // Dashboard 统计 4 接口（登录后守卫重定向 /dashboard 会立即加载）
  await page.route('**/api/v1/stats/tickets/summary', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      json: {
        code: '200',
        message: 'success',
        data: { pending: 3, processing: 5, resolved: 8, closed: 2, total: 18 },
      },
    })
  })
  await page.route('**/api/v1/stats/tickets/trend*', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      json: {
        code: '200',
        message: 'success',
        data: [
          { date: '2026-08-07', count: 2 },
          { date: '2026-08-08', count: 3 },
          { date: '2026-08-09', count: 1 },
          { date: '2026-08-10', count: 4 },
          { date: '2026-08-11', count: 2 },
          { date: '2026-08-12', count: 5 },
          { date: '2026-08-13', count: 1 },
        ],
      },
    })
  })
  await page.route('**/api/v1/stats/tickets/by-priority', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      json: {
        code: '200',
        message: 'success',
        data: [
          { priority: 'HIGH', count: 4 },
          { priority: 'MEDIUM', count: 6 },
          { priority: 'LOW', count: 2 },
        ],
      },
    })
  })
  await page.route('**/api/v1/stats/tickets/top-handlers*', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      json: {
        code: '200',
        message: 'success',
        data: [
          { handlerId: 3, handlerName: '张三', resolvedCount: 12 },
          { handlerId: 4, handlerName: '李四', resolvedCount: 7 },
        ],
      },
    })
  })
}

/** 登录辅助：填写账号密码并提交，等待进入 dashboard */
export async function loginAsAdmin(page: Page) {
  await page.goto('/login')
  await page.getByPlaceholder('工号 / 用户名').fill('admin')
  await page.getByPlaceholder('密码').fill('admin123')
  await page.getByRole('button', { name: /登\s*录/ }).click()
  await page.waitForURL('**/dashboard')
  await expect(page.locator('.user-name')).toHaveText('超级管理员')
}
