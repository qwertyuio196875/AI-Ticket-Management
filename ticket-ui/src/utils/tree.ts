/**
 * 平铺列表 → 树（通用实现，纯函数，可单测）：
 * - 找不到父节点（孤儿）的节点兜底为顶级，避免数据丢失
 * - 可选同级排序（递归作用于每层 children）
 * - 不修改传入数组
 * 供菜单树（menuTree.ts）与评论树（commentTree.ts）共用。
 */

/** 树节点：原条目字段 + children */
export type TreeNode<T> = T & { children: TreeNode<T>[] }

export interface BuildTreeParams<T, K> {
  /** 节点主键提取 */
  getId: (item: T) => K
  /** 父节点主键提取（null / undefined 视为顶级） */
  getParentId: (item: T) => K | null | undefined
  /** 顶级判定；默认 parentId 为 null / undefined 即顶级（菜单等场景 parentId=0 也需视为顶级，由调用方传入） */
  isTopLevel?: (parentId: K | null | undefined) => boolean
  /** 同级排序（可选，递归作用于每层 children） */
  sortChildren?: (a: TreeNode<T>, b: TreeNode<T>) => number
}

export function buildTree<T, K>(items: T[], params: BuildTreeParams<T, K>): TreeNode<T>[] {
  const { getId, getParentId, isTopLevel, sortChildren } = params

  const nodes = new Map<K, TreeNode<T>>()
  for (const item of items) {
    nodes.set(getId(item), { ...item, children: [] })
  }

  const roots: TreeNode<T>[] = []
  for (const node of nodes.values()) {
    const parentId = getParentId(node)
    const topLevel = isTopLevel ? isTopLevel(parentId) : parentId == null
    const parent = topLevel ? undefined : parentId != null ? nodes.get(parentId) : undefined
    if (parent) {
      parent.children.push(node)
    } else {
      roots.push(node)
    }
  }

  if (sortChildren) {
    const sortRecursively = (list: TreeNode<T>[]) => {
      list.sort(sortChildren)
      list.forEach((item) => sortRecursively(item.children))
    }
    sortRecursively(roots)
  }
  return roots
}
