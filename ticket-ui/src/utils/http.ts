import axios, {
  type AxiosError,
  type AxiosRequestConfig,
  type AxiosResponse,
  type InternalAxiosRequestConfig,
} from 'axios'
import { ElMessage } from 'element-plus'

/** localStorage 中 Token 的存储键 */
export const TOKEN_KEY = 'ai-ticket.token'

/** 后端统一返回结构（code 为字符串：成功 "200"，业务错误为 "T0101" 等业务码） */
export interface Result<T = unknown> {
  code: string
  message: string
  data: T
}

/** 类型安全的请求客户端：拦截器已把响应归一化为 Result<T> */
export interface HttpClient {
  get<T>(url: string, config?: AxiosRequestConfig): Promise<Result<T>>
  post<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<Result<T>>
  put<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<Result<T>>
  patch<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<Result<T>>
  delete<T>(url: string, config?: AxiosRequestConfig): Promise<Result<T>>
}

/** 从 localStorage 读取 Token */
export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

/**
 * 请求拦截器：存在 Token 时自动附加 Authorization 头
 */
export function requestInterceptor(config: InternalAxiosRequestConfig): InternalAxiosRequestConfig {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
}

/**
 * 响应拦截器：Result.code === "200" 时透传整个 Result；
 * code !== "200" 时提示后端错误消息并 reject（后端返回的业务失败）
 */
export function responseInterceptor(response: AxiosResponse<Result>): Result | Promise<never> {
  const result = response.data
  if (result.code !== '200') {
    ElMessage.error(result.message)
    return Promise.reject<never>(new Error(result.message))
  }
  return result
}

/**
 * 从 Content-Disposition 响应头解析下载文件名（纯函数，可单测）：
 * - 优先解析 RFC 5987 编码形式 filename*=UTF-8''xxx 并做 URL 解码（支持中文文件名）
 * - 其次解析普通形式 filename="xxx" / filename=xxx
 * - 解析失败或缺失时返回 null（由调用方兜底默认文件名）
 */
export function parseFilenameFromDisposition(disposition: string | null | undefined): string | null {
  if (!disposition) return null
  const encoded = disposition.match(/filename\*=UTF-8''([^;]+)/i)
  if (encoded?.[1]) {
    try {
      return decodeURIComponent(encoded[1])
    } catch {
      // 编码内容非法时降级尝试普通形式
    }
  }
  const plain = disposition.match(/filename="?([^";]+)"?/i)
  return plain?.[1] ?? null
}

/**
 * 响应错误处理器：
 * - 登录接口 401（凭证错误）：用户本就在登录页，静默 reject，由调用方提示「登录失败」
 * - 其他 HTTP 401：会话过期，清理登录态并跳转登录页
 * - 其他 HTTP 错误：提示后端 message；网络错误：提示中文网络异常。
 * 注意：store 与 router 采用函数内动态 import，避免与 auth store / router 循环依赖。
 */
export async function responseErrorHandler(error: AxiosError<Result>): Promise<never> {
  if (error.response) {
    const { status, data } = error.response
    if (status === 401 && error.config?.url?.includes('/v1/auth/login')) {
      // 登录请求凭证错误：不清状态、不跳转、不提示（避免与 LoginView 的「登录失败」双提示）
      return Promise.reject(error)
    }
    if (status === 401) {
      // 动态导入，避免模块顶层循环依赖
      const [{ useAuthStore }, { default: router }] = await Promise.all([
        import('../stores/auth'),
        import('../router'),
      ])
      // clearState 内部已移除 localStorage token，无需重复 removeItem
      useAuthStore().clearState()
      router.replace('/login')
      ElMessage.error('登录已过期，请重新登录')
    } else {
      ElMessage.error(data?.message || '请求失败，请稍后重试')
    }
  } else {
    ElMessage.error('网络异常，请稍后重试')
  }
  return Promise.reject(error)
}

const instance = axios.create({ baseURL: '/api', timeout: 15000 })

instance.interceptors.request.use(requestInterceptor)
instance.interceptors.response.use(
  // 拦截器按契约返回整个 Result（response.data），与 axios 类型签名不一致处用断言收敛
  responseInterceptor as unknown as (response: AxiosResponse) => AxiosResponse | Promise<AxiosResponse>,
  responseErrorHandler,
)

// 运行时拦截器已把响应归一化为 Result<T>，这里通过类型断言向 api 层暴露安全签名
const http: HttpClient = {
  get<T>(url: string, config?: AxiosRequestConfig) {
    return instance.get(url, config) as Promise<Result<T>>
  },
  post<T>(url: string, data?: unknown, config?: AxiosRequestConfig) {
    return instance.post(url, data, config) as Promise<Result<T>>
  },
  put<T>(url: string, data?: unknown, config?: AxiosRequestConfig) {
    return instance.put(url, data, config) as Promise<Result<T>>
  },
  patch<T>(url: string, data?: unknown, config?: AxiosRequestConfig) {
    return instance.patch(url, data, config) as Promise<Result<T>>
  },
  delete<T>(url: string, config?: AxiosRequestConfig) {
    return instance.delete(url, config) as Promise<Result<T>>
  },
}

/** 文件下载专用 axios 实例：独立超时（导出大文件耗时更长），复用请求拦截器自动附加 Token */
const downloadInstance = axios.create({ baseURL: '/api', timeout: 60000 })
downloadInstance.interceptors.request.use(requestInterceptor)

/**
 * 下载文件（Excel 导出等 blob 接口使用）：
 * - 独立 axios 实例，自动带 Authorization 头，responseType 固定为 blob（不经过业务拦截器归一化）
 * - 文件名优先取 Content-Disposition，缺失时按 URL 末段兜底
 * - 下载失败且响应体是后端业务 JSON（Result 包装）时提示后端 message，否则提示通用中文错误
 */
export async function download(url: string, config: AxiosRequestConfig = {}): Promise<void> {
  try {
    const response = await downloadInstance.get<Blob>(url, { ...config, responseType: 'blob' })
    const disposition = response.headers?.['content-disposition']
    const fallbackName = url.split('/').pop()?.split('?')[0] || 'download'
    const filename = parseFilenameFromDisposition(disposition) ?? fallbackName

    // 创建对象 URL 并触发浏览器保存
    const objectUrl = URL.createObjectURL(response.data)
    const link = document.createElement('a')
    link.href = objectUrl
    link.download = filename
    document.body.appendChild(link)
    link.click()
    link.remove()
    URL.revokeObjectURL(objectUrl)
  } catch (error) {
    // 下载失败：响应体为业务 JSON 时给出后端明确错误，否则提示网络异常
    const payload = (error as AxiosError).response?.data
    if (typeof Blob !== 'undefined' && payload instanceof Blob) {
      try {
        const result = JSON.parse(await payload.text()) as Partial<Result>
        if (result.message) {
          ElMessage.error(result.message)
          return
        }
      } catch {
        // 非 JSON 错误体 → 走下方通用提示
      }
    }
    ElMessage.error('文件下载失败，请稍后重试')
  }
}

export default http
