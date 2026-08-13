import http from '../utils/http'
import type { PageVO } from './tickets'

/** 角色管理查询参数 */
export interface RoleListParams {
  keyword?: string
  pageNum: number
  pageSize: number
}

/** 角色展示对象 */
export interface SysRoleVO {
  id: number
  roleName: string
  roleKey: string
  remark: string
  createTime: string
}

/** 角色保存请求体 */
export interface RoleSaveParams {
  id?: number
  roleName: string
  roleKey: string
  remark?: string
}

/** 分页查询角色列表 */
export function getRoleList(params: RoleListParams): Promise<PageVO<SysRoleVO>> {
  return http.get<PageVO<SysRoleVO>>('/v1/roles', { params }).then((res) => res.data)
}

/** 查询全部角色（分配角色下拉 / 回显用） */
export function getAllRoles(): Promise<SysRoleVO[]> {
  return http.get<SysRoleVO[]>('/v1/roles/all').then((res) => res.data)
}

/** 查询角色详情 */
export function getRole(id: number | string): Promise<SysRoleVO> {
  return http.get<SysRoleVO>(`/v1/roles/${id}`).then((res) => res.data)
}

/** 创建角色 → 返回角色 id */
export function createRole(params: RoleSaveParams): Promise<number> {
  return http.post<number>('/v1/roles', params).then((res) => res.data)
}

/** 更新角色 */
export function updateRole(params: RoleSaveParams): Promise<void> {
  return http.put<void>('/v1/roles', params).then(() => undefined)
}

/** 删除角色 */
export function deleteRole(id: number | string): Promise<void> {
  return http.delete<void>(`/v1/roles/${id}`).then(() => undefined)
}

/** 查询角色已分配的菜单 id 列表 */
export function getRoleMenus(id: number | string): Promise<number[]> {
  return http.get<number[]>(`/v1/roles/${id}/menus`).then((res) => res.data)
}

/** 分配角色菜单 */
export function assignRoleMenus(id: number | string, menuIds: number[]): Promise<void> {
  return http.put<void>(`/v1/roles/${id}/menus`, { menuIds }).then(() => undefined)
}
