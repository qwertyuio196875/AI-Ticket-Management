import http, { download } from '../utils/http'
import type { TicketListParams } from '../utils/ticketQuery'
import type { CommentType, TicketPriority, TicketStatus } from '../utils/ticketState'

/** 分页返回结构（后端 PageVO） */
export interface PageVO<T> {
  total: number
  pageNum: number
  pageSize: number
  records: T[]
}

/** 工单展示对象 */
export interface TicketVO {
  id: number
  ticketNo: string
  title: string
  content: string
  type: string
  priority: TicketPriority
  status: TicketStatus
  creatorId: number
  handlerId: number | null
  createTime: string
  updateTime: string
}

/** 创建工单请求体（type 留空由 AI 自动分类，2s 短超时降级，不阻塞创建） */
export interface TicketCreateParams {
  title: string
  content: string
  type?: string
  priority?: TicketPriority
}

/** 编辑工单请求体 */
export interface TicketUpdateParams {
  title: string
  content: string
}

/** 状态流转请求体 */
export interface TicketStatusChangeParams {
  targetStatus: TicketStatus
  reason?: string
}

/** 分配处理人请求体 */
export interface TicketAssignParams {
  handlerId: number
  reason?: string
}

/** 工单评论展示对象 */
export interface TicketCommentVO {
  id: number
  ticketId: number
  content: string
  commentType: CommentType
  creatorId: number
  creatorName: string
  parentId: number | null
  createTime: string
}

/** 新增评论请求体 */
export interface TicketCommentCreateParams {
  content: string
  commentType: CommentType
  parentId?: number
}

/** AI 智能回复结果 */
export interface TicketAiReplyVO {
  reply: string
  recordId?: number
  /** true 表示 AI 不可用，返回的是内置模板建议 */
  fallback: boolean
}

/** 工单附件展示对象 */
export interface TicketAttachmentVO {
  id: number
  ticketId: number
  fileName: string
  size: number
  mimeType: string
  uploaderId: number
  uploadTime: string
  downloadUrl?: string
}

/** 工单时间线事件展示对象 */
export interface TicketLogVO {
  id: number
  ticketId: number
  eventType: string
  operatorId: number | null
  operatorName: string | null
  content: string
  createTime: string
}

/** 创建工单 → 返回工单 id */
export function createTicket(params: TicketCreateParams): Promise<number> {
  return http.post<number>('/v1/tickets', params).then((res) => res.data)
}

/** 分页查询工单列表 */
export function getTicketList(params: TicketListParams): Promise<PageVO<TicketVO>> {
  return http.get<PageVO<TicketVO>>('/v1/tickets', { params }).then((res) => res.data)
}

/** 查询工单详情 */
export function getTicket(id: number | string): Promise<TicketVO> {
  return http.get<TicketVO>(`/v1/tickets/${id}`).then((res) => res.data)
}

/** 编辑工单标题 / 内容（后端校验创建人或管理员） */
export function updateTicket(id: number | string, params: TicketUpdateParams): Promise<void> {
  return http.put<void>(`/v1/tickets/${id}`, params).then(() => undefined)
}

/** 删除工单 */
export function deleteTicket(id: number | string): Promise<void> {
  return http.delete<void>(`/v1/tickets/${id}`).then(() => undefined)
}

/** 工单状态流转（PENDING→PROCESSING 等） */
export function changeTicketStatus(id: number | string, params: TicketStatusChangeParams): Promise<void> {
  return http.patch<void>(`/v1/tickets/${id}/status`, params).then(() => undefined)
}

/** 分配处理人（PENDING 时后端自动转 PROCESSING） */
export function assignTicket(id: number | string, params: TicketAssignParams): Promise<void> {
  return http.put<void>(`/v1/tickets/${id}/assign`, params).then(() => undefined)
}

/** 关闭工单 */
export function closeTicket(id: number | string): Promise<void> {
  return http.post<void>(`/v1/tickets/${id}/close`).then(() => undefined)
}

/** 导出工单列表为 .xlsx（触发浏览器下载） */
export function exportTickets(params: TicketListParams): Promise<void> {
  return download('/v1/tickets/export', { params })
}

/** 新增评论 → 返回评论 id */
export function createComment(
  id: number | string,
  params: TicketCommentCreateParams,
): Promise<number> {
  return http.post<number>(`/v1/tickets/${id}/comments`, params).then((res) => res.data)
}

/** 查询工单评论（create_time ASC，后端已按权限过滤 INTERNAL） */
export function getComments(id: number | string): Promise<TicketCommentVO[]> {
  return http.get<TicketCommentVO[]>(`/v1/tickets/${id}/comments`).then((res) => res.data)
}

/** 删除评论 */
export function deleteComment(id: number | string, commentId: number): Promise<void> {
  return http.delete<void>(`/v1/tickets/${id}/comments/${commentId}`).then(() => undefined)
}

/** AI 智能回复（同步调用，30s 等待上限；fallback=true 时为模板兜底） */
export function getAiReply(id: number | string): Promise<TicketAiReplyVO> {
  return http.post<TicketAiReplyVO>(`/v1/tickets/${id}/ai-reply`).then((res) => res.data)
}

/** 上传工单附件（multipart，field=file） */
export function uploadAttachment(id: number | string, file: File): Promise<TicketAttachmentVO> {
  const formData = new FormData()
  formData.append('file', file)
  return http
    .post<TicketAttachmentVO>(`/v1/tickets/${id}/attachments`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    .then((res) => res.data)
}

/** 查询工单附件列表 */
export function getAttachments(id: number | string): Promise<TicketAttachmentVO[]> {
  return http.get<TicketAttachmentVO[]>(`/v1/tickets/${id}/attachments`).then((res) => res.data)
}

/** 删除附件 */
export function deleteAttachment(id: number | string, attachmentId: number): Promise<void> {
  return http.delete<void>(`/v1/tickets/${id}/attachments/${attachmentId}`).then(() => undefined)
}

/** 查询工单时间线日志（create_time ASC） */
export function getTicketLogs(id: number | string): Promise<TicketLogVO[]> {
  return http.get<TicketLogVO[]>(`/v1/tickets/${id}/logs`).then((res) => res.data)
}
