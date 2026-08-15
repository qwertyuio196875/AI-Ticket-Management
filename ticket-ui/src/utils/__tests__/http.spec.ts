import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { AxiosError, InternalAxiosRequestConfig } from 'axios'

// 模拟 Element Plus 消息提示
vi.mock('element-plus', () => ({
  ElMessage: {
    success: vi.fn(),
    error: vi.fn(),
    warning: vi.fn(),
  },
}))

// 模拟 auth store 与 router（http.ts 内部动态导入，避免循环依赖）
// vi.hoisted 保证 mock factory 引用外部 mock fn 时不受 hoisting 顺序影响
const { clearStateMock, routerReplaceMock } = vi.hoisted(() => ({
  clearStateMock: vi.fn(),
  routerReplaceMock: vi.fn(),
}))

vi.mock('../../stores/auth', () => ({
  useAuthStore: () => ({ clearState: clearStateMock }),
}))

vi.mock('../../router', () => ({
  default: { replace: routerReplaceMock },
}))

import { ElMessage } from 'element-plus'
import {
  requestInterceptor,
  responseInterceptor,
  responseErrorHandler,
  parseFilenameFromDisposition,
  TOKEN_KEY,
  type Result,
} from '../http'

function makeConfig(url = '/v1/tickets'): InternalAxiosRequestConfig {
  return {
    url,
    method: 'post',
    headers: {},
  } as InternalAxiosRequestConfig
}

function makeResponse(code: string, message: string, data: unknown = null) {
  return {
    data: { code, message, data },
    status: 200,
    statusText: 'OK',
    headers: {},
    config: makeConfig(),
  }
}

function makeError(
  status: number | undefined,
  message: string,
  hasRequest: boolean,
  url = '/v1/tickets',
): AxiosError<Result> {
  return {
    isAxiosError: true,
    // 顶层 config 供 error.config?.url 判断请求来源（与 axios 运行时结构一致）
    config: makeConfig(url),
    response: status === undefined ? undefined : {
      status,
      statusText: 'Error',
      headers: {},
      config: makeConfig(url),
      data: { code: status, message },
    },
    request: hasRequest ? {} : undefined,
    message,
  } as unknown as AxiosError<Result>
}

describe('http 拦截器', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  describe('requestInterceptor（请求拦截器）', () => {
    it('有 token 时自动附加 Authorization: Bearer 头', () => {
      localStorage.setItem(TOKEN_KEY, 'jwt-token-abc')
      const config = requestInterceptor(makeConfig())
      expect(config.headers.Authorization).toBe('Bearer jwt-token-abc')
    })

    it('无 token 时不附加 Authorization 头', () => {
      const config = requestInterceptor(makeConfig())
      expect(config.headers.Authorization).toBeUndefined()
    })
  })

  describe('responseInterceptor（响应拦截器）', () => {
    it('Result.code === "200" 时返回整个 Result（response.data）', () => {
      const response = makeResponse('200', 'success', { token: 't1' })
      const result = responseInterceptor(response)
      expect(result).toEqual({ code: '200', message: 'success', data: { token: 't1' } })
    })

    it('Result.code !== "200" 时提示后端错误消息并 reject', async () => {
      const response = makeResponse('T0101', '用户名或密码错误')
      const promise = responseInterceptor(response)
      await expect(promise).rejects.toThrow('用户名或密码错误')
      expect(ElMessage.error).toHaveBeenCalledWith('用户名或密码错误')
    })
  })

  describe('parseFilenameFromDisposition（Content-Disposition 文件名解析）', () => {
    it('解析 filename="xxx" 形式（RFC 5987 之外的普通形式）', () => {
      expect(parseFilenameFromDisposition('attachment; filename="tickets-20250101.xlsx"')).toBe(
        'tickets-20250101.xlsx',
      )
    })

    it('解析 filename 无引号形式', () => {
      expect(parseFilenameFromDisposition('attachment; filename=tickets.xlsx')).toBe('tickets.xlsx')
    })

    it('优先解析 filename*=UTF-8\'\' 编码形式并做 URL 解码（含中文）', () => {
      const disposition = "attachment; filename=\"tickets.xlsx\"; filename*=UTF-8''%E5%B7%A5%E5%8D%95.xlsx"
      expect(parseFilenameFromDisposition(disposition)).toBe('工单.xlsx')
    })

    it('无 Content-Disposition 或无法解析时返回 null', () => {
      expect(parseFilenameFromDisposition(undefined)).toBeNull()
      expect(parseFilenameFromDisposition('')).toBeNull()
      expect(parseFilenameFromDisposition('inline')).toBeNull()
    })
  })

  describe('responseErrorHandler（响应错误处理）', () => {
    it('HTTP 401 时清空 store、移除本地 token、跳转登录页并提示', async () => {
      localStorage.setItem(TOKEN_KEY, 'expired-token')
      const error = makeError(401, 'Unauthorized', true)

      await expect(responseErrorHandler(error)).rejects.toBeTruthy()

      // 清理职责委托给 clearState()（其内部会 removeItem，拦截器不再重复），
      // localStorage 清理行为由 auth store 单测覆盖
      expect(clearStateMock).toHaveBeenCalled()
      expect(routerReplaceMock).toHaveBeenCalledWith('/login')
      expect(ElMessage.error).toHaveBeenCalled()
    })

    it('登录接口 401（凭证错误）→ 静默 reject，不提示、不跳转、不清 store', async () => {
      const error = makeError(401, '用户名或密码错误', true, '/v1/auth/login')

      await expect(responseErrorHandler(error)).rejects.toBeTruthy()

      expect(ElMessage.error).not.toHaveBeenCalled()
      expect(routerReplaceMock).not.toHaveBeenCalled()
      expect(clearStateMock).not.toHaveBeenCalled()
    })

    it('后端业务错误（如 500）时提示后端返回的 message', async () => {
      const error = makeError(500, '服务器内部错误', true)
      await expect(responseErrorHandler(error)).rejects.toBeTruthy()
      expect(ElMessage.error).toHaveBeenCalledWith('服务器内部错误')
      expect(routerReplaceMock).not.toHaveBeenCalled()
    })

    it('网络错误（无 response）时提示中文网络异常消息并 reject', async () => {
      const error = makeError(undefined, 'Network Error', false)
      await expect(responseErrorHandler(error)).rejects.toBeTruthy()
      expect(ElMessage.error).toHaveBeenCalledWith('网络异常，请稍后重试')
    })
  })
})
