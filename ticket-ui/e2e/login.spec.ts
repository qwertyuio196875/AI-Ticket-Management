import { test, expect, type Page } from '@playwright/test'

// ============================================================
// E2E 登录主流程（后端不可用，通过 page.route 拦截 mock 全部 API）
// ============================================================

/** 贴近真实种子的菜单树：C 型页面菜单 + F 型按钮权限 + 嵌套子菜单 */
const menuTree = [
  {
    id: 1, parentId: 0, menuName: 'Dashboard', menuType: 'C', path: '/dashboard',
    component: 'Dashboard', icon: 'dashboard', sort: 1, visible: true,
    permission: 'dashboard:view', children: [],
  },
  {
    id: 2, parentId: 0, menuName: '用户管理', menuType: 'C', path: '/users',
    component: 'User', icon: 'user', sort: 2, visible: true,
    permission: 'user:list', children: [],
  },
  {
    id: 3, parentId: 0, menuName: '角色管理', menuType: 'C', path: '/roles',
    component: 'Role', icon: 'role', sort: 3, visible: true,
    permission: 'role:list', children: [],
  },
  {
    id: 4, parentId: 0, menuName: '菜单管理', menuType: 'C', path: '/menus',
    component: 'Menu', icon: 'menu', sort: 4, visible: true,
    permission: 'menu:list', children: [],
  },
  {
    id: 5, parentId: 0, menuName: '工单列表', menuType: 'C', path: '/tickets',
    component: 'Ticket', icon: 'ticket', sort: 5, visible: true,
    permission: 'ticket:list',
    children: [
      // C 型子菜单：验证嵌套子菜单渲染
      {
        id: 51, parentId: 5, menuName: '我的工单', menuType: 'C', path: '/my-tickets',
        component: 'MyTicket', sort: 1, visible: true,
        permission: 'ticket:my', children: [],
      },
      // F 型按钮权限：无 path，侧边栏不得渲染
      {
        id: 52, parentId: 5, menuName: '新建工单', menuType: 'F', path: '',
        permission: 'ticket:create', children: [],
      },
    ],
  },
  {
    id: 6, parentId: 0, menuName: '字典管理', menuType: 'C', path: '/dicts',
    component: 'Dict', icon: 'dict', sort: 6, visible: true,
    permission: 'dict:list', children: [],
  },
  {
    id: 7, parentId: 0, menuName: '工单分类', menuType: 'C', path: '/ticket-categories',
    component: 'Category', icon: 'category', sort: 7, visible: true,
    permission: 'category:list', children: [],
  },
]

/** 拦截并 mock 全部后端 API（登录 / 菜单 / 登出 / 当前用户） */
async function mockBackend(page: Page) {
  // 登录：返回模拟 JWT 与会话信息
  await page.route('**/api/v1/auth/login', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      json: {
        code: 200,
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
      json: { code: 200, message: 'success', data: menuTree },
    })
  })

  // 登出
  await page.route('**/api/v1/auth/logout', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      json: { code: 200, message: 'success', data: null },
    })
  })

  // 当前用户（页面刷新恢复登录态时会调用）
  await page.route('**/api/v1/auth/me', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      json: {
        code: 200,
        message: 'success',
        data: { userId: 1, username: 'admin', authorities: ['dashboard:view'] },
      },
    })
  })
}

test.describe('登录主流程（mock 后端）', () => {
  test('登录 → 守卫重定向 dashboard → 菜单渲染 → 登出全链路', async ({ page }) => {
    await mockBackend(page)

    // 1. 打开登录页：标题「欢迎登录」可见
    await page.goto('/login')
    await expect(page.getByText('欢迎登录')).toBeVisible()
    await page.screenshot({ path: 'e2e/screenshots/login-desktop.png' })

    // 2. 填写账号密码并提交
    await page.getByPlaceholder('工号 / 用户名').fill('admin')
    await page.getByPlaceholder('密码').fill('admin123')
    await page.getByRole('button', { name: /登\s*录/ }).click()

    // 3. 守卫 + 菜单加载 + 重定向，落地 /dashboard
    await page.waitForURL('**/dashboard')

    // 4. 顶栏显示登录用户昵称（.user-name；Dashboard 页另有「欢迎回来」文案，故用精确定位）
    await expect(page.locator('.user-name')).toHaveText('超级管理员')

    // 5. 侧边栏渲染 C 型菜单
    const sidebar = page.locator('.app-aside .el-menu')
    await expect(sidebar.getByText('Dashboard')).toBeVisible()
    await expect(sidebar.getByText('工单列表')).toBeVisible()
    await expect(sidebar.getByText('字典管理')).toBeVisible()

    // 6. F 型按钮权限（新建工单）不得渲染进侧边栏
    await expect(sidebar.getByText('新建工单')).toHaveCount(0)

    // 7. 展开「工单列表」子菜单：C 型子菜单「我的工单」渲染，F 型仍不渲染
    await sidebar.getByText('工单列表').click()
    await expect(sidebar.getByText('我的工单')).toBeVisible()
    await expect(sidebar.getByText('新建工单')).toHaveCount(0)

    // 8. 截图 dashboard
    await page.screenshot({ path: 'e2e/screenshots/dashboard.png' })

    // 9. 点击右上角用户区 → 下拉「退出登录」→ 点击
    await page.locator('.user-entry').click()
    await page.getByText('退出登录').click()

    // 10. 回到登录页，localStorage token 清空
    await page.waitForURL('**/login')
    const token = await page.evaluate(() => localStorage.getItem('ai-ticket.token'))
    expect(token).toBeNull()
  })

  test('窄屏（480px）登录页：品牌面板隐藏、登录卡片可见', async ({ page }) => {
    await page.setViewportSize({ width: 480, height: 800 })
    await mockBackend(page)

    await page.goto('/login')
    // 窄屏下左侧品牌面板不可见，右侧登录卡片正常渲染
    await expect(page.locator('.brand-panel')).not.toBeVisible()
    await expect(page.locator('.login-card')).toBeVisible()
    await expect(page.getByText('欢迎登录')).toBeVisible()
    await page.screenshot({ path: 'e2e/screenshots/login-narrow.png' })
  })
})
