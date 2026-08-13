import http from '../utils/http'
import type { TicketPriority } from '../utils/ticketState'

/** 工单状态分布统计 */
export interface TicketSummary {
  pending: number
  processing: number
  resolved: number
  closed: number
  total: number
}

/** 趋势点（date 为 YYYY-MM-DD） */
export interface TrendPoint {
  date: string
  count: number
}

/** 优先级分布统计（固定序 HIGH / MEDIUM / LOW） */
export interface PriorityCount {
  priority: TicketPriority
  count: number
}

/** Top 处理人（按已解决工单数） */
export interface TopHandler {
  handlerId: number
  handlerName: string
  resolvedCount: number
}

/** 工单状态分布（饼图数据源） */
export function getTicketSummary(): Promise<TicketSummary> {
  return http.get<TicketSummary>('/v1/stats/tickets/summary').then((res) => res.data)
}

/** 近 N 日工单创建趋势 */
export function getTicketTrend(days = 7): Promise<TrendPoint[]> {
  return http.get<TrendPoint[]>('/v1/stats/tickets/trend', { params: { days } }).then((res) => res.data)
}

/** 优先级分布 */
export function getPriorityStats(): Promise<PriorityCount[]> {
  return http.get<PriorityCount[]>('/v1/stats/tickets/by-priority').then((res) => res.data)
}

/** Top 处理人（默认前 10） */
export function getTopHandlers(limit = 10): Promise<TopHandler[]> {
  return http.get<TopHandler[]>('/v1/stats/tickets/top-handlers', { params: { limit } }).then((res) => res.data)
}
