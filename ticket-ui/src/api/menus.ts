import http from '../utils/http'

/** 菜单类型：C=目录/菜单（有 path 可导航）、F=按钮权限（无 path）、M=菜单目录（仅分组） */
export type MenuType = 'C' | 'F' | 'M'

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

/** 获取当前用户可见的菜单树 */
export function getMenuTree(): Promise<MenuNode[]> {
  return http.get<MenuNode[]>('/v1/menus/tree').then((res) => res.data)
}
