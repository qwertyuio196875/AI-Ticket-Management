import { test, expect } from '@playwright/test'
import { mockBackend, loginAsAdmin } from './helpers'

// ============================================================
// E2E 登录主流程（后端不可用，通过 page.route 拦截 mock 全部 API）
// ============================================================

test.describe('登录主流程（mock 后端）', () => {
  test('登录 → 守卫重定向 dashboard → 菜单渲染 → 登出全链路', async ({ page }) => {
    await mockBackend(page)

    // 1. 打开登录页：标题「欢迎登录」可见
    await page.goto('/login')
    await expect(page.getByText('欢迎登录')).toBeVisible()
    await page.screenshot({ path: 'e2e/screenshots/login-desktop.png' })

    // 2. 登录并落地 dashboard（helper 内已断言顶栏昵称）
    await loginAsAdmin(page)

    // 3. 侧边栏渲染 C 型菜单（真实权限串 mock）
    const sidebar = page.locator('.app-aside .el-menu')
    await expect(sidebar.getByText('Dashboard')).toBeVisible()
    await expect(sidebar.getByText('工单列表')).toBeVisible()
    await expect(sidebar.getByText('字典管理')).toBeVisible()
    await expect(sidebar.getByText('用户管理')).toBeVisible()

    // 4. F 型按钮权限（新建工单等）不得渲染进侧边栏
    await expect(sidebar.getByText('新建工单')).toHaveCount(0)
    await expect(sidebar.getByText('删除工单')).toHaveCount(0)

    // 5. 截图 dashboard
    await page.screenshot({ path: 'e2e/screenshots/dashboard.png' })

    // 6. 点击右上角用户区 → 下拉「退出登录」→ 点击
    await page.locator('.user-entry').click()
    await page.getByText('退出登录').click()

    // 7. 回到登录页，localStorage token 清空
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
