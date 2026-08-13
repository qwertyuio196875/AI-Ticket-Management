import { describe, expect, it } from 'vitest'
import {
  STATUS_TRANSITIONS,
  STATUS_LABELS,
  STATUS_TAG_TYPES,
  canTransitTo,
  nextStatuses,
  EVENT_LABELS,
  type TicketStatus,
  type TicketLogEvent,
} from '../ticketState'

describe('工单状态机（纯函数）', () => {
  it('状态迁移表：PENDING → PROCESSING/CLOSED；PROCESSING → RESOLVED/CLOSED；RESOLVED → CLOSED；CLOSED 无出口', () => {
    expect(STATUS_TRANSITIONS).toEqual({
      PENDING: ['PROCESSING', 'CLOSED'],
      PROCESSING: ['RESOLVED', 'CLOSED'],
      RESOLVED: ['CLOSED'],
      CLOSED: [],
    })
  })

  it('canTransitTo：合法迁移放行，非法迁移拒绝', () => {
    expect(canTransitTo('PENDING', 'PROCESSING')).toBe(true)
    expect(canTransitTo('PENDING', 'CLOSED')).toBe(true)
    expect(canTransitTo('PROCESSING', 'RESOLVED')).toBe(true)
    expect(canTransitTo('PROCESSING', 'CLOSED')).toBe(true)
    expect(canTransitTo('RESOLVED', 'CLOSED')).toBe(true)
  })

  it('canTransitTo：非法迁移一律拒绝（含回退、跳级、闭合态出口）', () => {
    expect(canTransitTo('PENDING', 'RESOLVED')).toBe(false)
    expect(canTransitTo('PROCESSING', 'PENDING')).toBe(false)
    expect(canTransitTo('RESOLVED', 'PROCESSING')).toBe(false)
    expect(canTransitTo('CLOSED', 'PENDING')).toBe(false)
    expect(canTransitTo('CLOSED', 'CLOSED')).toBe(false)
    expect(canTransitTo('PENDING', 'PENDING')).toBe(false)
  })

  it('nextStatuses 返回当前状态可到达的目标列表', () => {
    expect(nextStatuses('PENDING')).toEqual(['PROCESSING', 'CLOSED'])
    expect(nextStatuses('CLOSED')).toEqual([])
  })

  it('状态中文标签与 tag 类型齐全（4 状态全覆盖）', () => {
    expect(STATUS_LABELS).toEqual({
      PENDING: '待处理',
      PROCESSING: '处理中',
      RESOLVED: '已解决',
      CLOSED: '已关闭',
    })
    const statuses: TicketStatus[] = ['PENDING', 'PROCESSING', 'RESOLVED', 'CLOSED']
    for (const s of statuses) {
      expect(STATUS_LABELS[s]).toBeTruthy()
      expect(STATUS_TAG_TYPES[s]).toBeTruthy()
    }
  })

  it('日志事件类型中文标签齐全（6 类全覆盖）', () => {
    const events: TicketLogEvent[] = ['CREATED', 'UPDATED', 'STATUS_CHANGED', 'ASSIGNED', 'COMMENTED', 'AI_CALLED']
    for (const e of events) {
      expect(EVENT_LABELS[e]).toBeTruthy()
    }
    expect(EVENT_LABELS.CREATED).toBe('创建工单')
    expect(EVENT_LABELS.STATUS_CHANGED).toBe('状态变更')
  })
})
