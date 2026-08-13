/**
 * 工单分类拖拽排序重算（纯函数，可单测）：
 * 输入按 sort 升序的列表与拖拽起止索引，输出需要调用 PUT 的 { id, sort } 变更列表
 * （仅 sort 实际变化的项），sort 重算为 1..n 连续值。不修改传入数组。
 */

export interface SortableItem {
  id: number
  sort: number
}

export function recalculateCategorySort<T extends SortableItem>(
  items: T[],
  from: number,
  to: number,
): Array<{ id: number; sort: number }> {
  if (from === to || items.length === 0) return []

  const next = [...items]
  const [moved] = next.splice(from, 1)
  // 拖拽到列表末尾：目标索引等价于移动后长度
  const clampedTo = Math.min(to, next.length)
  next.splice(clampedTo, 0, moved)

  const changes: Array<{ id: number; sort: number }> = []
  next.forEach((item, index) => {
    const newSort = index + 1
    if (item.sort !== newSort) {
      changes.push({ id: item.id, sort: newSort })
    }
  })
  return changes
}
