import { test, expect, type Page } from '@playwright/test'
import { mockBackend, loginAsAdmin } from './helpers'

// ============================================================
// E2E 工单全链路（后端不可用，page.route 全 mock）：
// 登录 → 工单列表（筛选/导出）→ 新建（AI 分类结果弹窗）→ 详情
// → 评论 → AI 智能回复 → 分配处理人 → 关闭工单
// + 平板 viewport（768×1024）响应式冒烟
// ============================================================

/** 工单域状态（详情 mock 的可变状态，保证操作后页面状态连贯） */
interface TicketDomain {
  status: string
  handlerId: number | null
  comments: Array<Record<string, unknown>>
  assignCount: number
}

function createDomain(): TicketDomain {
  return { status: 'PENDING', handlerId: null, comments: [], assignCount: 0 }
}

/** mock 工单域全部接口（列表 / 详情 / 日志 / 评论 / 附件 / AI / 分配 / 关闭 / 导出 / 新建） */
async function mockTicketDomain(page: Page, domain: TicketDomain) {
  // 字典与分类（筛选下拉）
  await page.route('**/api/v1/dicts/type/status', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      json: {
        code: '200',
        message: 'success',
        data: [
          { id: 1, dictType: 'status', dictValue: 'PENDING', dictLabel: '待处理', sort: 1, status: 1, remark: '', createTime: '' },
          { id: 2, dictType: 'status', dictValue: 'PROCESSING', dictLabel: '处理中', sort: 2, status: 1, remark: '', createTime: '' },
          { id: 3, dictType: 'status', dictValue: 'RESOLVED', dictLabel: '已解决', sort: 3, status: 1, remark: '', createTime: '' },
          { id: 4, dictType: 'status', dictValue: 'CLOSED', dictLabel: '已关闭', sort: 4, status: 1, remark: '', createTime: '' },
        ],
      },
    })
  })
  await page.route('**/api/v1/dicts/type/priority', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      json: {
        code: '200',
        message: 'success',
        data: [
          { id: 5, dictType: 'priority', dictValue: 'HIGH', dictLabel: '高', sort: 1, status: 1, remark: '', createTime: '' },
          { id: 6, dictType: 'priority', dictValue: 'MEDIUM', dictLabel: '中', sort: 2, status: 1, remark: '', createTime: '' },
          { id: 7, dictType: 'priority', dictValue: 'LOW', dictLabel: '低', sort: 3, status: 1, remark: '', createTime: '' },
        ],
      },
    })
  })
  await page.route('**/api/v1/ticket-categories', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      json: {
        code: '200',
        message: 'success',
        data: [
          { id: 1, name: '网络问题', description: '网络连接相关', sort: 1, status: 1, createTime: '' },
          { id: 2, name: '硬件故障', description: '设备硬件问题', sort: 2, status: 1, createTime: '' },
        ],
      },
    })
  })

  // 用户列表（处理人筛选 / 分配下拉，admin 有 user:manage）
  await page.route('**/api/v1/users*', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      json: {
        code: '200',
        message: 'success',
        data: {
          total: 2,
          pageNum: 1,
          pageSize: 50,
          records: [
            { id: 3, username: 'zhangsan', nickname: '张三', status: 1, createTime: '2026-01-01T00:00:00' },
            { id: 4, username: 'lisi', nickname: '李四', status: 1, createTime: '2026-01-01T00:00:00' },
          ],
        },
      },
    })
  })

  // 导出：mock blob + Content-Disposition（注册在通用 tickets 路由之前）
  await page.route('**/api/v1/tickets/export*', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
      headers: { 'content-disposition': 'attachment; filename="tickets-20260813.xlsx"' },
      body: Buffer.from('mock-xlsx-content'),
    })
  })

  // 详情子资源（logs / comments / attachments / ai-reply / assign / close）
  // 注意：glob 中 * 不匹配 /，子路径需用 ** 才能覆盖 /100/logs 等
  await page.route('**/api/v1/tickets/100**', async (route) => {
    const url = route.request().url()
    const method = route.request().method()

    if (url.includes('/logs')) {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        json: {
          code: '200',
          message: 'success',
          data: [
            {
              id: 1, ticketId: 100, eventType: 'CREATED', operatorId: 1, operatorName: '超级管理员',
              content: 'title=无法连接公司内网, type=网络问题, priority=HIGH', createTime: '2026-08-13T09:00:00',
            },
            ...(domain.assignCount > 0
              ? [{
                  id: 2, ticketId: 100, eventType: 'ASSIGNED', operatorId: 1, operatorName: '超级管理员',
                  content: 'handlerId=3, 处理人=张三', createTime: '2026-08-13T09:05:00',
                }]
              : []),
          ],
        },
      })
      return
    }

    if (url.includes('/comments')) {
      if (method === 'POST') {
        const body = route.request().postDataJSON() as { content?: string; commentType?: string }
        domain.comments.push({
          id: 11 + domain.comments.length,
          ticketId: 100,
          content: body?.content ?? '',
          commentType: body?.commentType ?? 'AGENT',
          creatorId: 1,
          creatorName: '超级管理员',
          parentId: null,
          createTime: '2026-08-13T09:10:00',
        })
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          json: { code: '200', message: 'success', data: 11 },
        })
      } else {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          json: { code: '200', message: 'success', data: domain.comments },
        })
      }
      return
    }

    if (url.includes('/attachments')) {
      if (method === 'POST') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          json: {
            code: '200',
            message: 'success',
            data: { id: 1, ticketId: 100, fileName: 'screenshot.png', size: 1024, mimeType: 'image/png', uploaderId: 1, uploadTime: '2026-08-13T09:15:00' },
          },
        })
      } else {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          json: { code: '200', message: 'success', data: [] },
        })
      }
      return
    }

    if (url.includes('/ai-reply')) {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        json: {
          code: '200',
          message: 'success',
          data: {
            reply: '建议先检查本机网卡与 DNS 配置，再尝试 ping 网关；若仍无法连接，请提供抓包结果以便进一步排查。',
            recordId: 7,
            fallback: false,
          },
        },
      })
      return
    }

    if (url.includes('/assign')) {
      domain.handlerId = 3
      domain.assignCount += 1
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        json: { code: '200', message: 'success', data: null },
      })
      return
    }

    if (url.includes('/close')) {
      domain.status = 'CLOSED'
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        json: { code: '200', message: 'success', data: null },
      })
      return
    }

    // GET /v1/tickets/100 详情（状态随操作变化）
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      json: {
        code: '200',
        message: 'success',
        data: {
          id: 100,
          ticketNo: 'TK2026081300000100',
          title: '无法连接公司内网',
          content: '早上开始无法连接公司内网，同事的网络正常，怀疑是网卡驱动问题。',
          type: '网络问题',
          priority: 'HIGH',
          status: domain.status,
          creatorId: 1,
          handlerId: domain.handlerId,
          createTime: '2026-08-13T09:00:00',
          updateTime: '2026-08-13T09:00:00',
        },
      },
    })
  })

  // 工单列表 + 新建（注册在 export / 100 之后避免误匹配）
  await page.route('**/api/v1/tickets*', async (route) => {
    if (route.request().method() === 'POST') {
      // 新建工单：返回 id
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        json: { code: '200', message: 'success', data: 100 },
      })
      return
    }
    // 分页列表
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      json: {
        code: '200',
        message: 'success',
        data: {
          total: 1,
          pageNum: 1,
          pageSize: 20,
          records: [
            {
              id: 100,
              ticketNo: 'TK2026081300000100',
              title: '无法连接公司内网',
              content: '早上开始无法连接公司内网',
              type: '网络问题',
              priority: 'HIGH',
              status: 'PENDING',
              creatorId: 1,
              handlerId: null,
              createTime: '2026-08-13T09:00:00',
              updateTime: '2026-08-13T09:00:00',
            },
          ],
        },
      },
    })
  })
}

test.describe('工单全链路（mock 后端）', () => {
  test('admin 登录 → 列表筛选导出 → 新建（AI 分类）→ 详情评论/AI/分配/关闭', async ({ page }) => {
    await mockBackend(page)
    const domain = createDomain()
    await mockTicketDomain(page, domain)

    await loginAsAdmin(page)

    // ============ 1. 工单列表 ============
    await page.locator('.app-aside .el-menu').getByText('工单列表').click()
    await page.waitForURL('**/tickets')
    await expect(page.getByText('无法连接公司内网')).toBeVisible()
    await expect(page.getByText('TK2026081300000100')).toBeVisible()
    await page.screenshot({ path: 'e2e/screenshots/ticket-list.png' })

    // 筛选：选状态「处理中」→ 查询（mock 不区分参数，验证交互可用）
    await page.locator('.filter-card .el-select').first().click()
    await page.getByRole('option', { name: '处理中' }).click()
    await page.getByRole('button', { name: '查询' }).click()

    // 导出：断言 download 事件 + 文件名来自 Content-Disposition
    const downloadPromise = page.waitForEvent('download')
    await page.getByRole('button', { name: '导出' }).click()
    const download = await downloadPromise
    expect(download.suggestedFilename()).toBe('tickets-20260813.xlsx')

    // 新建工单
    await page.getByRole('button', { name: '新建工单' }).click()
    await page.waitForURL('**/tickets/create')

    // ============ 2. 新建工单 → AI 分类结果弹窗 ============
    await page.getByPlaceholder('一句话概括问题（1-100 字）').fill('无法连接公司内网')
    await page.locator('.create-form textarea').fill('早上开始无法连接公司内网，同事的网络正常，怀疑是网卡驱动问题。')
    await page.getByRole('button', { name: '提交工单' }).click()

    // 分类结果对话框：AI 分类 / 优先级 / 状态
    await expect(page.getByText('工单创建成功')).toBeVisible()
    await expect(page.getByText('AI 已完成工单分类，以下是识别结果：')).toBeVisible()
    await expect(page.getByText('TK2026081300000100')).toBeVisible()
    await page.screenshot({ path: 'e2e/screenshots/ticket-create-result.png' })

    // 确认 → 跳转详情
    await page.getByRole('button', { name: '查看工单详情' }).click()
    await page.waitForURL('**/tickets/100')

    // ============ 3. 工单详情 ============
    await expect(page.getByText('TK2026081300000100')).toBeVisible()
    // 状态 tag：PENDING → 待处理
    await expect(page.getByText('待处理')).toBeVisible()
    // 时间线：创建事件
    await expect(page.getByText('创建工单')).toBeVisible()
    await page.screenshot({ path: 'e2e/screenshots/ticket-detail.png' })

    // 评论（AGENT 类型）
    await page.locator('.comment-editor textarea').fill('已收到，正在排查网络问题')
    await page.getByRole('button', { name: '发送评论' }).click()
    await expect(page.getByText('已收到，正在排查网络问题')).toBeVisible()
    // 评论 tag 显示「客服」（对齐字典 comment_type：AGENT=客服；与输入框下拉选中项区分，用 class 精确定位）
    await expect(page.locator('.comment-type-tag', { hasText: '客服' })).toBeVisible()

    // AI 智能回复
    await page.getByRole('button', { name: 'AI 智能回复' }).click()
    await expect(page.getByText(/建议先检查本机网卡与 DNS 配置/)).toBeVisible()
    await page.getByRole('button', { name: '知道了' }).click()

    // 分配处理人：张三
    await page.getByRole('button', { name: '分配处理人' }).click()
    await page.locator('.el-dialog').getByRole('combobox').click()
    await page.getByRole('option', { name: '张三' }).click()
    await page.getByRole('button', { name: '确认分配' }).click()
    // 分配后详情刷新：信息卡「处理人」显示张三（user:manage 映射；排除分配对话框中的同名文本）
    await expect(
      page.locator('.info-item').filter({ hasText: '处理人' }).getByText('张三'),
    ).toBeVisible()

    // 关闭工单（PENDING 的流转按钮组只含「开始处理」；关闭走独立按钮）
    await page.getByRole('button', { name: '关闭工单' }).click()
    await page.getByRole('button', { name: '关闭', exact: true }).click()
    // 关闭后状态变已关闭
    await expect(page.getByText('已关闭')).toBeVisible()
    await page.screenshot({ path: 'e2e/screenshots/ticket-detail-closed.png' })
  })

  test('平板（768×1024）详情页与用户管理页：可操作且无横向溢出', async ({ page }) => {
    await page.setViewportSize({ width: 768, height: 1024 })
    await mockBackend(page)
    const domain = createDomain()
    await mockTicketDomain(page, domain)
    await loginAsAdmin(page)

    // 直接进入详情页（模拟从列表进入）
    await page.goto('/tickets/100')
    await expect(page.getByText('TK2026081300000100')).toBeVisible()

    // 详情页无横向溢出：文档宽度不超过视口
    const noHorizontalOverflow = await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    )
    expect(noHorizontalOverflow).toBe(true)

    // 平板下可操作：发评论
    await page.locator('.comment-editor textarea').fill('平板端评论测试')
    await page.getByRole('button', { name: '发送评论' }).click()
    await expect(page.getByText('平板端评论测试')).toBeVisible()
    await page.screenshot({ path: 'e2e/screenshots/ticket-detail-tablet.png' })

    // 管理页响应式冒烟：抽屉模式点「用户管理」→ 表格渲染且无横向溢出
    await page.locator('.hamburger').click()
    await page.locator('.app-drawer .el-menu').getByText('用户管理').click()
    await page.waitForURL('**/system/users')
    await expect(page.getByText('新建用户')).toBeVisible()
    const noAdminOverflow = await page.evaluate(
      () => document.scrollingElement!.scrollWidth <= document.documentElement.clientWidth,
    )
    expect(noAdminOverflow).toBe(true)
    await page.screenshot({ path: 'e2e/screenshots/users-tablet.png' })
  })
})
