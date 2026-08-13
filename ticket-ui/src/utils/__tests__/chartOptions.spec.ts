import { describe, expect, it } from 'vitest'
import type { PriorityCount, TicketSummary, TopHandler, TrendPoint } from '../../api/stats'
import {
  buildPriorityBarOption,
  buildStatusPieOption,
  buildTopHandlersBarOption,
  buildTrendLineOption,
} from '../chartOptions'

function summary(overrides: Partial<TicketSummary> = {}): TicketSummary {
  return { pending: 3, processing: 5, resolved: 8, closed: 2, total: 18, ...overrides }
}

function trend(points: Array<[string, number]>): TrendPoint[] {
  return points.map(([date, count]) => ({ date, count }))
}

describe('ECharts option 映射（纯函数）', () => {
  describe('buildStatusPieOption 状态分布饼图', () => {
    it('按固定状态序输出 4 片数据，value 取 summary 对应字段', () => {
      const option = buildStatusPieOption(summary())
      const series = option.series as Array<{ type: string; data: Array<{ name: string; value: number }> }>
      expect(series[0].type).toBe('pie')
      expect(series[0].data.map((d) => d.name)).toEqual(['待处理', '处理中', '已解决', '已关闭'])
      expect(series[0].data.map((d) => d.value)).toEqual([3, 5, 8, 2])
    })

    it('值为 0 的状态也保留在图中（不丢片）', () => {
      const option = buildStatusPieOption(summary({ processing: 0 }))
      const series = option.series as Array<{ data: Array<{ value: number }> }>
      expect(series[0].data.map((d) => d.value)).toEqual([3, 0, 8, 2])
    })
  })

  describe('buildTrendLineOption 趋势折线图', () => {
    it('x 轴为日期、series 为计数', () => {
      const option = buildTrendLineOption(trend([['2026-08-06', 1], ['2026-08-07', 3]]))
      expect((option.xAxis as { data: string[] }).data).toEqual(['2026-08-06', '2026-08-07'])
      const series = option.series as Array<{ type: string; data: number[] }>
      expect(series[0].type).toBe('line')
      expect(series[0].data).toEqual([1, 3])
    })

    it('空数据返回空 x 轴与空系列，页面可安全渲染空态', () => {
      const option = buildTrendLineOption([])
      expect((option.xAxis as { data: string[] }).data).toEqual([])
      expect((option.series as Array<{ data: number[] }>)[0].data).toEqual([])
    })
  })

  describe('buildPriorityBarOption 优先级堆叠柱状图', () => {
    it('x 轴单分类「全部工单」，三个优先级系列按固定序堆叠（stack: total）', () => {
      const list: PriorityCount[] = [
        { priority: 'HIGH', count: 4 },
        { priority: 'MEDIUM', count: 6 },
        { priority: 'LOW', count: 2 },
      ]
      const option = buildPriorityBarOption(list)
      expect((option.xAxis as { data: string[] }).data).toEqual(['全部工单'])
      const series = option.series as Array<{ type: string; name: string; stack: string; data: number[] }>
      expect(series).toHaveLength(3)
      expect(series.map((s) => s.name)).toEqual(['高', '中', '低'])
      expect(series.every((s) => s.type === 'bar' && s.stack === 'total')).toBe(true)
      expect(series.map((s) => s.data)).toEqual([[4], [6], [2]])
    })

    it('输入顺序变化时仍按固定序（HIGH/MEDIUM/LOW）输出，缺失补 0', () => {
      const list: PriorityCount[] = [
        { priority: 'LOW', count: 2 },
        { priority: 'HIGH', count: 4 },
      ]
      const option = buildPriorityBarOption(list)
      const series = option.series as Array<{ data: number[] }>
      expect(series.map((s) => s.data)).toEqual([[4], [0], [2]])
    })
  })

  describe('buildTopHandlersBarOption Top10 处理人横向柱状图', () => {
    it('y 轴为处理人姓名（横向）、x 轴为已解决数，按输入序', () => {
      const list: TopHandler[] = [
        { handlerId: 1, handlerName: '张三', resolvedCount: 12 },
        { handlerId: 2, handlerName: '李四', resolvedCount: 7 },
      ]
      const option = buildTopHandlersBarOption(list)
      // 横向柱状：yAxis 是类目轴，xAxis 是数值轴
      expect((option.yAxis as { type: string; data: string[] }).data).toEqual(['张三', '李四'])
      expect((option.xAxis as { type: string }).type).toBe('value')
      const series = option.series as Array<{ type: string; data: number[] }>
      expect(series[0].type).toBe('bar')
      expect(series[0].data).toEqual([12, 7])
    })

    it('空数据返回空轴，页面显示"暂无数据"占位', () => {
      const option = buildTopHandlersBarOption([])
      expect((option.yAxis as { data: string[] }).data).toEqual([])
      expect((option.series as Array<{ data: number[] }>)[0].data).toEqual([])
    })
  })
})
