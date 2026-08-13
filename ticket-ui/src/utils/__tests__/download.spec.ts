// @vitest-environment happy-dom
// 下载能力依赖 DOM（创建 <a> 触发保存）与 Blob，按文件启用 happy-dom；其余测试保持全局 node 环境
import { beforeEach, describe, expect, it, vi } from 'vitest'

// vi.hoisted：axios 的 create 返回的下载实例在此构造，供模块加载与用例断言共享。
// 注意：requestUseSpy 的注册调用发生在模块加载期，beforeEach 不能 clearAllMocks（否则记录被清空）
const { fakeGet, requestUseSpy } = vi.hoisted(() => {
  const fakeGet = vi.fn()
  const requestUseSpy = vi.fn()
  const responseUseSpy = vi.fn()
  const fakeInstance = {
    get: fakeGet,
    interceptors: {
      request: { use: requestUseSpy },
      response: { use: responseUseSpy },
    },
  }
  return { fakeGet, requestUseSpy, fakeInstance }
})

vi.mock('axios', () => ({
  default: {
    create: vi.fn(() => ({
      get: fakeGet,
      interceptors: {
        request: { use: requestUseSpy },
        response: { use: () => undefined },
      },
    })),
  },
}))

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
}))

import { ElMessage } from 'element-plus'
import { download, requestInterceptor, TOKEN_KEY } from '../http'

/** 注入 URL.createObjectURL / revokeObjectURL（happy-dom 未完整实现时兜底 stub） */
function stubObjectUrl() {
  const createObjectURL = vi.fn(() => 'blob:mock-download')
  const revokeObjectURL = vi.fn()
  Object.assign(URL, { createObjectURL, revokeObjectURL })
  return { createObjectURL, revokeObjectURL }
}

/** 捕获 download 内部创建的 <a> 元素（download 完成后元素会被 remove，无法用 DOM 查询） */
function captureAnchor() {
  const createElementSpy = vi.spyOn(document, 'createElement')
  const clickSpy = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
  const anchor = () =>
    createElementSpy.mock.results.map((r) => r.value).find((el) => el instanceof HTMLAnchorElement)
  return { anchor, clickSpy }
}

describe('download 文件下载', () => {
  beforeEach(() => {
    // 清理上个用例 spyOn 残留（document.createElement / click 为复用同一 spy）；
    // 模块加载期 requestUseSpy 的注册调用由 vi.fn() 承载，restoreAllMocks 不影响
    vi.restoreAllMocks()
    fakeGet.mockClear()
    vi.mocked(ElMessage.error).mockClear()
    localStorage.clear()
  })

  it('下载实例复用请求拦截器（自动附加 Authorization），请求使用 responseType blob', async () => {
    localStorage.setItem(TOKEN_KEY, 'jwt-download')
    const { createObjectURL, revokeObjectURL } = stubObjectUrl()
    const { anchor } = captureAnchor()
    fakeGet.mockResolvedValue({
      data: new Blob(['xlsx-bytes'], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' }),
      headers: { 'content-disposition': 'attachment; filename="tickets-20250101.xlsx"' },
    })

    await download('/v1/tickets/export', { params: { status: 'PENDING' } })

    // 拦截器注册：下载实例确实复用了 requestInterceptor（其附加 Authorization 行为由 http.spec 覆盖）
    expect(requestUseSpy).toHaveBeenCalledWith(requestInterceptor)
    // 请求参数：固定 blob + 透传 params
    expect(fakeGet).toHaveBeenCalledWith('/v1/tickets/export', {
      params: { status: 'PENDING' },
      responseType: 'blob',
    })
    // 触发浏览器保存：创建对象 URL → <a download> 点击 → 释放
    expect(createObjectURL).toHaveBeenCalledTimes(1)
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:mock-download')
    expect(anchor()?.getAttribute('download')).toBe('tickets-20250101.xlsx')
    expect(anchor()?.getAttribute('href')).toBe('blob:mock-download')
  })

  it('Content-Disposition 缺失时按 URL 末段兜底文件名', async () => {
    stubObjectUrl()
    const { anchor } = captureAnchor()
    fakeGet.mockResolvedValue({ data: new Blob(['x']), headers: {} })

    await download('/v1/tickets/export')

    expect(anchor()?.getAttribute('download')).toBe('export')
  })

  it('下载失败且响应体是业务 JSON 时提示后端中文错误', async () => {
    stubObjectUrl()
    const errorBlob = new Blob([JSON.stringify({ code: 'T0999', message: '导出失败：暂无工单数据' })], {
      type: 'application/json',
    })
    fakeGet.mockRejectedValue({ response: { data: errorBlob } })

    await download('/v1/tickets/export')

    expect(ElMessage.error).toHaveBeenCalledWith('导出失败：暂无工单数据')
  })

  it('下载失败且响应体不是业务 JSON 时提示通用中文错误', async () => {
    stubObjectUrl()
    fakeGet.mockRejectedValue({ response: { data: new Blob(['<html>500</html>']) } })

    await download('/v1/tickets/export')

    expect(ElMessage.error).toHaveBeenCalledWith('文件下载失败，请稍后重试')
  })
})
