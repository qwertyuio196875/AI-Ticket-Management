import { describe, expect, it } from 'vitest'
import { recalculateCategorySort, type SortableItem } from '../categorySort'

/** 构造带 sort 的排序项 */
function s(id: number, sort: number): SortableItem {
  return { id, sort }
}

describe('recalculateCategorySort 分类拖拽排序重算（纯函数）', () => {
  it('向下拖拽（0 → 2）：移动项与中间项 sort 重算为连续值', () => {
    const items = [s(1, 1), s(2, 2), s(3, 3), s(4, 4)]
    const changes = recalculateCategorySort(items, 0, 2)
    expect(changes).toEqual([
      { id: 2, sort: 1 },
      { id: 3, sort: 2 },
      { id: 1, sort: 3 },
    ])
  })

  it('向上拖拽（3 → 0）：移动项与前方项 sort 重算', () => {
    const items = [s(1, 1), s(2, 2), s(3, 3), s(4, 4)]
    const changes = recalculateCategorySort(items, 3, 0)
    expect(changes).toEqual([
      { id: 4, sort: 1 },
      { id: 1, sort: 2 },
      { id: 2, sort: 3 },
      { id: 3, sort: 4 },
    ])
  })

  it('拖拽到原位（from === to）返回空变更', () => {
    const items = [s(1, 1), s(2, 2)]
    expect(recalculateCategorySort(items, 1, 1)).toEqual([])
  })

  it('sort 已为连续值时仅移动项变化（其余项不变）', () => {
    const items = [s(1, 1), s(2, 2), s(3, 3)]
    const changes = recalculateCategorySort(items, 2, 0)
    expect(changes).toEqual([
      { id: 3, sort: 1 },
      { id: 1, sort: 2 },
      { id: 2, sort: 3 },
    ])
  })

  it('空数组返回空变更', () => {
    expect(recalculateCategorySort([], 0, 1)).toEqual([])
  })

  it('不修改传入数组（纯函数）', () => {
    const items = [s(1, 1), s(2, 2), s(3, 3)]
    recalculateCategorySort(items, 0, 2)
    expect(items.map((i) => i.sort)).toEqual([1, 2, 3])
  })
})
