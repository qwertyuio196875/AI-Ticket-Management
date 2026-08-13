/**
 * 工单状态机（纯函数模块，与后端 ADR-0005 保持一致）：
 * PENDING → [PROCESSING, CLOSED]；PROCESSING → [RESOLVED, CLOSED]；RESOLVED → [CLOSED]；CLOSED → []
 * 同时提供状态 / 日志事件的中文标签与 tag 类型映射，供列表、详情、时间线复用。
 */

/** 工单状态枚举（与后端 TicketStatus 一致） */
export type TicketStatus = 'PENDING' | 'PROCESSING' | 'RESOLVED' | 'CLOSED'

/** 状态迁移表：key 为当前状态，value 为可到达的目标状态列表 */
export const STATUS_TRANSITIONS: Record<TicketStatus, TicketStatus[]> = {
  PENDING: ['PROCESSING', 'CLOSED'],
  PROCESSING: ['RESOLVED', 'CLOSED'],
  RESOLVED: ['CLOSED'],
  CLOSED: [],
}

/** 状态中文标签 */
export const STATUS_LABELS: Record<TicketStatus, string> = {
  PENDING: '待处理',
  PROCESSING: '处理中',
  RESOLVED: '已解决',
  CLOSED: '已关闭',
}

/** 状态 → Element Plus tag 类型（列表 / 详情徽标用） */
export const STATUS_TAG_TYPES: Record<TicketStatus, 'info' | 'primary' | 'success' | 'danger'> = {
  PENDING: 'info',
  PROCESSING: 'primary',
  RESOLVED: 'success',
  CLOSED: 'danger',
}

/** 全部状态（保序），供筛选下拉 / 状态选项使用 */
export const ALL_STATUSES: TicketStatus[] = ['PENDING', 'PROCESSING', 'RESOLVED', 'CLOSED']

/** 状态 → 统计字段名（TicketSummary 的 camelCase 字段，图表映射单一来源） */
export type SummaryField = 'pending' | 'processing' | 'resolved' | 'closed'

export const STATUS_SUMMARY_FIELDS: Record<TicketStatus, SummaryField> = {
  PENDING: 'pending',
  PROCESSING: 'processing',
  RESOLVED: 'resolved',
  CLOSED: 'closed',
}

/** 状态 → 图表配色（与 Win11 状态 tag 语义一致：灰/蓝/绿/红） */
export const STATUS_COLORS: Record<TicketStatus, string> = {
  PENDING: '#8a8886',
  PROCESSING: '#0078d4',
  RESOLVED: '#107c10',
  CLOSED: '#c42b1c',
}

/** 判断 target 是否为 current 的合法下一步 */
export function canTransitTo(current: TicketStatus, target: TicketStatus): boolean {
  return STATUS_TRANSITIONS[current]?.includes(target) ?? false
}

/** 返回当前状态可到达的目标列表（由 canTransitTo 派生，保证语义单一来源） */
export function nextStatuses(current: TicketStatus): TicketStatus[] {
  return ALL_STATUSES.filter((target) => canTransitTo(current, target))
}

/** 工单日志事件类型（与后端 TicketLogEvent 一致） */
export type TicketLogEvent = 'CREATED' | 'UPDATED' | 'STATUS_CHANGED' | 'ASSIGNED' | 'COMMENTED' | 'AI_CALLED'

/** 日志事件中文标签（详情页时间线） */
export const EVENT_LABELS: Record<TicketLogEvent, string> = {
  CREATED: '创建工单',
  UPDATED: '编辑工单',
  STATUS_CHANGED: '状态变更',
  ASSIGNED: '分配处理人',
  COMMENTED: '新增评论',
  AI_CALLED: 'AI 调用',
}

/** 优先级枚举与中文标签（与后端 /dicts/type/priority 种子数据对齐） */
export type TicketPriority = 'HIGH' | 'MEDIUM' | 'LOW'

export const PRIORITY_LABELS: Record<TicketPriority, string> = {
  HIGH: '高',
  MEDIUM: '中',
  LOW: '低',
}

export const PRIORITY_TAG_TYPES: Record<TicketPriority, 'danger' | 'warning' | 'info'> = {
  HIGH: 'danger',
  MEDIUM: 'warning',
  LOW: 'info',
}

/** 评论类型与中文标签（与后端 /dicts/type/comment_type 种子数据对齐：AGENT=客服、CUSTOMER=客户、INTERNAL=内部） */
export type CommentType = 'CUSTOMER' | 'AGENT' | 'INTERNAL'

export const COMMENT_TYPE_LABELS: Record<CommentType, string> = {
  CUSTOMER: '客户',
  AGENT: '客服',
  INTERNAL: '内部备注',
}
