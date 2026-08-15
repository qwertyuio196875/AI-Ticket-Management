import { describe, expect, it } from 'vitest'
import { buildTicketQueryParams, type TicketFilterForm } from '../ticketQuery'

describe('buildTicketQueryParams 工单查询参数构造（纯函数）', () => {
  it('筛选条件全空时仅保留分页参数', () => {
    expect(buildTicketQueryParams({}, 1, 20)).toEqual({ pageNum: 1, pageSize: 20 })
    expect(buildTicketQueryParams({ status: '', priority: undefined, type: '', handlerId: null, dateRange: null }, 2, 10)).toEqual({
      pageNum: 2,
      pageSize: 10,
    })
  })

  it('日期范围拆分为 dateFrom / dateTo 两个参数', () => {
    const params = buildTicketQueryParams(
      { dateRange: ['2026-01-01', '2026-01-31'] } as TicketFilterForm,
      1,
      20,
    )
    expect(params).toEqual({
      pageNum: 1,
      pageSize: 20,
      dateFrom: '2026-01-01',
      dateTo: '2026-01-31',
    })
  })

  it('全部筛选条件齐备时完整透传（status/priority/type/handlerId/日期）', () => {
    const params = buildTicketQueryParams(
      {
        status: 'PROCESSING',
        priority: 'HIGH',
        type: '网络故障',
        handlerId: 7,
        dateRange: ['2026-01-01', '2026-01-31'],
      },
      3,
      50,
    )
    expect(params).toEqual({
      pageNum: 3,
      pageSize: 50,
      status: 'PROCESSING',
      priority: 'HIGH',
      type: '网络故障',
      handlerId: 7,
      dateFrom: '2026-01-01',
      dateTo: '2026-01-31',
    })
  })

  it('空字符串 / null / undefined 等空值一律不进入查询参数', () => {
    const params = buildTicketQueryParams(
      { status: '', priority: undefined, type: '  ', handlerId: null, dateRange: null } as TicketFilterForm,
      1,
      20,
    )
    expect(params).toEqual({ pageNum: 1, pageSize: 20 })
  })
})
