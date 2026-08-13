import axios, {
  type AxiosError,
  type AxiosRequestConfig,
  type AxiosResponse,
  type InternalAxiosRequestConfig,
} from 'axios'
import { ElMessage } from 'element-plus'

/** localStorage 中 Token 的存储键 */
export const TOKEN_KEY = 'ai-ticket.token'

/** 后端统一返回结构 */
export interface Result<T = unknown> {
  code: number
  message: string
  data: T
}

/** 类型安全的请求客户端：拦截器已把响应归一化为 Result<T> */
export interface HttpClient {
  get<T>(url: string, config?: AxiosRequestConfig): Promise<Result<T>>
  post<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<Result<T>>
  put<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<Result<T>>
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
 * 响应拦截器：Result.code === 200 时透传整个 Result；
 * code !== 200 时提示后端错误消息并 reject（后端返回的业务失败）
 */
export function responseInterceptor(response: AxiosResponse<Result>): Result | Promise<never> {
  const result = response.data
  if (result.code !== 200) {
    ElMessage.error(result.message)
    return Promise.reject<never>(new Error(result.message))
  }
  return result
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
  delete<T>(url: string, config?: AxiosRequestConfig) {
    return instance.delete(url, config) as Promise<Result<T>>
  },
}

export default http
