import http from '../utils/http'

/** 登录请求参数 */
export interface LoginParams {
  username: string
  password: string
}

/** 登录成功返回的用户会话信息 */
export interface LoginVO {
  token: string
  tokenType: string
  expiresIn: number
  userId: number
  username: string
  nickname: string
}

/** 当前登录用户信息（/auth/me） */
export interface UserInfoVO {
  userId: number
  username: string
  authorities: string[]
}

/** 登录：换取 token 与会话信息 */
export function login(params: LoginParams): Promise<LoginVO> {
  return http.post<LoginVO>('/v1/auth/login', params).then((res) => res.data)
}

/** 登出：使服务端 token 失效 */
export function logout(): Promise<void> {
  return http.post<void>('/v1/auth/logout').then(() => undefined)
}

/** 获取当前登录用户信息 */
export function getMe(): Promise<UserInfoVO> {
  return http.get<UserInfoVO>('/v1/auth/me').then((res) => res.data)
}
