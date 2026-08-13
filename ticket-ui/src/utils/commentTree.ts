import type { TicketCommentVO } from '../api/tickets'
import { buildTree } from './tree'

/** 评论树节点：原 VO 字段 + 子节点列表 */
export type CommentNode = TicketCommentVO & { children: CommentNode[] }

/**
 * 把后端返回的平铺评论列表（create_time ASC）组装成回复树（基于通用 buildTree）：
 * - parentId 为 null / 0 / 找不到父节点的评论 → 顶级节点（孤儿兜底，避免评论丢失）
 * - 其余挂到父节点 children，保持输入顺序（评论树不做排序）
 */
export function buildCommentTree(comments: TicketCommentVO[]): CommentNode[] {
  return buildTree<TicketCommentVO, number>(comments, {
    getId: (comment) => comment.id,
    getParentId: (comment) => comment.parentId,
    isTopLevel: (parentId) => parentId == null || parentId === 0,
  })
}
