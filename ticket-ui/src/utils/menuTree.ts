import type { SysMenuVO } from '../api/menus'
import { buildTree } from './tree'

/** 菜单树节点：平铺 VO 字段 + children（供 el-table 树形表格 / el-tree 使用） */
export type MenuTreeItem = SysMenuVO & { children: MenuTreeItem[] }

/**
 * 把后端平铺菜单列表（GET /v1/menus）组装成树（基于通用 buildTree）：
 * - parentId 为 0 / 找不到父节点的节点 → 顶级（孤儿兜底）
 * - 同级节点按 sort 升序排列（缺省 0），递归排序子级
 */
export function buildMenuTree(menus: SysMenuVO[]): MenuTreeItem[] {
  return buildTree<SysMenuVO, number>(menus, {
    getId: (menu) => menu.id,
    getParentId: (menu) => menu.parentId,
    isTopLevel: (parentId) => parentId == null || parentId === 0,
    sortChildren: (a, b) => (a.sort ?? 0) - (b.sort ?? 0),
  })
}
