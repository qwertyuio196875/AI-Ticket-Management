import { describe, expect, it } from 'vitest'
import { buildCommentTree, type CommentNode } from '../commentTree'
import type { TicketCommentVO } from '../../api/tickets'

/** 构造评论 VO 的快捷函数 */
function c(id: number, parentId: number | null, createTime = `2026-01-0${id} 10:00:00`): TicketCommentVO {
  return {
    id,
    ticketId: 1,
    content: `评论${id}`,
    commentType: 'AGENT',
    creatorId: 1,
    creatorName: '张三',
    parentId,
    createTime,
  }
}

/** 断言节点树形状（递归） */
function shapeOf(nodes: CommentNode[]): unknown[] {
  return nodes.map((n) => [n.id, shapeOf(n.children)])
}

describe('buildCommentTree 评论树组装（纯函数）', () => {
  it('无 parentId（或 null）的评论全部为顶级节点', () => {
    const tree = buildCommentTree([c(1, null), c(2, null), c(3, null)])
    expect(shapeOf(tree)).toEqual([
      [1, []],
      [2, []],
      [3, []],
    ])
  })

  it('一层回复：parentId 命中父节点时挂到对应子级', () => {
    const tree = buildCommentTree([c(1, null), c(2, 1), c(3, 1), c(4, null)])
    expect(shapeOf(tree)).toEqual([
      [1, [[2, []], [3, []]]],
      [4, []],
    ])
  })

  it('多层嵌套：回复的回复递归组装', () => {
    const tree = buildCommentTree([c(1, null), c(2, 1), c(3, 2)])
    expect(shapeOf(tree)).toEqual([[1, [[2, [[3, []]]]]]])
  })

  it('孤儿节点（parentId 指向不存在的节点）兜底为顶级，避免评论丢失', () => {
    const tree = buildCommentTree([c(1, null), c(2, 99)])
    expect(shapeOf(tree)).toEqual([
      [1, []],
      [2, []],
    ])
  })

  it('保持输入顺序（后端已按 create_time ASC，前端不再排序）', () => {
    const tree = buildCommentTree([c(1, null), c(3, null), c(2, 1)])
    expect(shapeOf(tree)).toEqual([
      [1, [[2, []]]],
      [3, []],
    ])
  })

  it('空数组返回空树', () => {
    expect(buildCommentTree([])).toEqual([])
  })
})
