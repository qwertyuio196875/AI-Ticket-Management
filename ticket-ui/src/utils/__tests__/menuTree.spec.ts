import { describe, expect, it } from 'vitest'
import { buildMenuTree, type MenuTreeItem } from '../menuTree'
import type { SysMenuVO } from '../../api/menus'

/** 构造平铺菜单 VO（默认 C 型菜单） */
function m(id: number, parentId: number, menuName: string, sort = 0, overrides: Partial<SysMenuVO> = {}): SysMenuVO {
  return {
    id,
    parentId,
    menuName,
    menuType: 'C',
    path: `/${menuName}`,
    component: menuName,
    icon: '',
    sort,
    visible: 1,
    permission: `${menuName}:view`,
    createTime: '2026-01-01T00:00:00',
    ...overrides,
  }
}

/** 断言树形状：[[id, [[childId, [...]]]], ...] */
function shapeOf(nodes: MenuTreeItem[]): unknown[] {
  return nodes.map((n) => [n.id, shapeOf(n.children)])
}

describe('buildMenuTree 菜单平铺 → 树（纯函数）', () => {
  it('parentId=0 的节点为顶级，其余按 parentId 挂载', () => {
    const tree = buildMenuTree([m(1, 0, '系统管理'), m(11, 1, '用户管理'), m(12, 1, '角色管理'), m(2, 0, '工单管理')])
    expect(shapeOf(tree)).toEqual([
      [1, [[11, []], [12, []]]],
      [2, []],
    ])
  })

  it('多层嵌套：目录 → 子菜单 → 孙节点递归组装', () => {
    const tree = buildMenuTree([m(1, 0, '系统'), m(11, 1, '权限'), m(111, 11, '按钮1')])
    expect(shapeOf(tree)).toEqual([[1, [[11, [[111, []]]]]]])
  })

  it('同级节点按 sort 升序排列', () => {
    const tree = buildMenuTree([m(1, 0, 'A', 30), m(2, 0, 'B', 10), m(3, 0, 'C', 20)])
    expect(tree.map((n) => n.id)).toEqual([2, 3, 1])
  })

  it('孤儿节点（parentId 找不到父）兜底为顶级，避免菜单丢失', () => {
    const tree = buildMenuTree([m(1, 0, '正常'), m(2, 99, '孤儿')])
    expect(shapeOf(tree)).toEqual([
      [1, []],
      [2, []],
    ])
  })

  it('空数组返回空树', () => {
    expect(buildMenuTree([])).toEqual([])
  })

  it('不修改传入的原始数组（纯函数）', () => {
    const input = [m(1, 0, 'A', 2), m(2, 1, 'B', 1)]
    buildMenuTree(input)
    expect(input).toHaveLength(2)
    expect(input[0].sort).toBe(2)
  })
})
