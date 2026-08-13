import http from '../utils/http'

/** 菜单类型：M=目录（仅分组）、C=菜单（有 path 可导航）、F=按钮权限（无 path）——与后端 SysMenu 枚举一致 */
export type MenuType = 'M' | 'C' | 'F'

/** 菜单树节点（对应后端 SysMenuTreeVO） */
export interface MenuNode {
  id: number
  parentId: number
  menuName: string
  menuType: MenuType
  path?: string
  component?: string
  icon?: string
  sort?: number
  visible?: boolean
  permission?: string
  createTime?: string
  children?: MenuNode[]
}

/** 菜单管理平铺 VO（对应后端 SysMenuVO，GET /v1/menus） */
export interface SysMenuVO {
  id: number
  parentId: number
  menuName: string
  menuType: MenuType
  path?: string
  component?: string
  icon?: string
  sort: number
  visible: number
  permission?: string
  createTime: string
}

/** 菜单保存请求体（parentId 顶级为 0） */
export interface MenuSaveParams {
  id?: number
  parentId: number
  menuName: string
  menuType: MenuType
  path?: string
  component?: string
  icon?: string
  sort?: number
  visible: number
  permission?: string
}

/** 获取当前用户可见的菜单树（登录即可，侧边栏用） */
export function getMenuTree(): Promise<MenuNode[]> {
  return http.get<MenuNode[]>('/v1/menus/tree').then((res) => res.data)
}

/** 全量平铺菜单列表（menu:manage，管理页用） */
export function getMenuList(): Promise<SysMenuVO[]> {
  return http.get<SysMenuVO[]>('/v1/menus').then((res) => res.data)
}

/** 按 id 查询菜单详情（menu:manage） */
export function getMenu(id: number | string): Promise<SysMenuVO> {
  return http.get<SysMenuVO>(`/v1/menus/${id}`).then((res) => res.data)
}

/** 创建菜单 → 返回 id */
export function createMenu(params: MenuSaveParams): Promise<number> {
  return http.post<number>('/v1/menus', params).then((res) => res.data)
}

/** 更新菜单 */
export function updateMenu(params: MenuSaveParams): Promise<void> {
  return http.put<void>('/v1/menus', params).then(() => undefined)
}

/** 删除菜单（后端级联删除子节点） */
export function deleteMenu(id: number | string): Promise<void> {
  return http.delete<void>(`/v1/menus/${id}`).then(() => undefined)
}
