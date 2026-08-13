import http from '../utils/http'
import type { PageVO } from './tickets'

/** 用户管理查询参数 */
export interface UserListParams {
  keyword?: string
  status?: number
  pageNum: number
  pageSize: number
}

/** 用户展示对象 */
export interface SysUserVO {
  id: number
  username: string
  nickname: string
  status: number
  createTime: string
}

/** 用户保存请求体（password 编辑时留空 = 不修改密码） */
export interface UserSaveParams {
  id?: number
  username: string
  password?: string
  nickname?: string
  status: number
}

/** 分页查询用户列表 */
export function getUserList(params: UserListParams): Promise<PageVO<SysUserVO>> {
  return http.get<PageVO<SysUserVO>>('/v1/users', { params }).then((res) => res.data)
}

/** 查询用户详情 */
export function getUser(id: number | string): Promise<SysUserVO> {
  return http.get<SysUserVO>(`/v1/users/${id}`).then((res) => res.data)
}

/** 创建用户 → 返回用户 id */
export function createUser(params: UserSaveParams): Promise<number> {
  return http.post<number>('/v1/users', params).then((res) => res.data)
}

/** 更新用户 */
export function updateUser(params: UserSaveParams): Promise<void> {
  return http.put<void>('/v1/users', params).then(() => undefined)
}

/** 删除用户 */
export function deleteUser(id: number | string): Promise<void> {
  return http.delete<void>(`/v1/users/${id}`).then(() => undefined)
}

/** 查询用户已分配的角色 id 列表 */
export function getUserRoles(id: number | string): Promise<number[]> {
  return http.get<number[]>(`/v1/users/${id}/roles`).then((res) => res.data)
}

/** 分配用户角色 */
export function assignUserRoles(id: number | string, roleIds: number[]): Promise<void> {
  return http.put<void>(`/v1/users/${id}/roles`, { roleIds }).then(() => undefined)
}
