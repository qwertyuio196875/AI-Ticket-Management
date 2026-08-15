import type { EChartsOption } from 'echarts'
import type { PriorityCount, TicketSummary, TopHandler, TrendPoint } from '../api/stats'
import { PRIORITY_LABELS, STATUS_COLORS, STATUS_LABELS, STATUS_SUMMARY_FIELDS } from './ticketState'

/**
 * Dashboard 4 张图的 API 数据 → ECharts option 映射（纯函数，可单测）。
 * 视觉延续 Win11 极简：主色 --accent(#0078d4)，克制阴影，无多余装饰。
 * 状态颜色 / 统计字段映射统一收敛在 ticketState.ts（单一来源）。
 */

/** 优先级 → 柱状配色 */
const PRIORITY_COLORS: Record<string, string> = {
  HIGH: '#c42b1c',
  MEDIUM: '#d83b01',
  LOW: '#0078d4',
}

/** 全局文本色与轴线色（对齐 CSS token） */
const TEXT_COLOR = '#605e5c'
const AXIS_COLOR = '#d4d4d4'

/** 状态分布饼图（summary） */
export function buildStatusPieOption(summary: TicketSummary): EChartsOption {
  const statuses = ['PENDING', 'PROCESSING', 'RESOLVED', 'CLOSED'] as const
  const data = statuses.map((status) => ({
    name: STATUS_LABELS[status],
    value: summary[STATUS_SUMMARY_FIELDS[status]],
    itemStyle: { color: STATUS_COLORS[status] },
  }))

  return {
    tooltip: { trigger: 'item', formatter: '{b}: {c} 张（{d}%）' },
    legend: { bottom: 0, textStyle: { color: TEXT_COLOR, fontSize: 12 } },
    series: [
      {
        type: 'pie',
        radius: ['42%', '68%'],
        center: ['50%', '44%'],
        itemStyle: { borderRadius: 6, borderColor: '#ffffff', borderWidth: 2 },
        label: { color: TEXT_COLOR, fontSize: 12 },
        data,
      },
    ],
  }
}

/** 近 N 日工单趋势折线图（trend） */
export function buildTrendLineOption(points: TrendPoint[]): EChartsOption {
  return {
    tooltip: { trigger: 'axis' },
    grid: { left: 40, right: 16, top: 24, bottom: 28 },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: points.map((p) => p.date),
      axisLine: { lineStyle: { color: AXIS_COLOR } },
      axisLabel: { color: TEXT_COLOR, fontSize: 11 },
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      splitLine: { lineStyle: { color: '#f0f0f0' } },
      axisLabel: { color: TEXT_COLOR, fontSize: 11 },
    },
    series: [
      {
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 6,
        data: points.map((p) => p.count),
        lineStyle: { color: '#0078d4', width: 2 },
        itemStyle: { color: '#0078d4', borderColor: '#ffffff', borderWidth: 2 },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0, y: 0, x2: 0, y2: 1,
            colorStops: [
              { offset: 0, color: 'rgba(0, 120, 212, 0.18)' },
              { offset: 1, color: 'rgba(0, 120, 212, 0.02)' },
            ],
          },
        },
      },
    ],
  }
}

/**
 * 优先级分布堆叠柱状图（by-priority，固定序 HIGH/MEDIUM/LOW）：
 * x 轴单分类「全部工单」，三个优先级各一个系列（data 单元素）且 stack:'total'，
 * 三色段在同一个类目上纵向堆叠，图例区分优先级。
 */
export function buildPriorityBarOption(list: PriorityCount[]): EChartsOption {
  const byPriority = new Map(list.map((item) => [item.priority, item.count]))
  const order = ['HIGH', 'MEDIUM', 'LOW'] as const

  return {
    tooltip: { trigger: 'axis' },
    legend: { bottom: 0, textStyle: { color: TEXT_COLOR, fontSize: 12 } },
    grid: { left: 40, right: 16, top: 24, bottom: 44 },
    xAxis: {
      type: 'category',
      data: ['全部工单'],
      axisLine: { lineStyle: { color: AXIS_COLOR } },
      axisLabel: { color: TEXT_COLOR, fontSize: 11 },
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      splitLine: { lineStyle: { color: '#f0f0f0' } },
      axisLabel: { color: TEXT_COLOR, fontSize: 11 },
    },
    series: order.map((p) => ({
      name: PRIORITY_LABELS[p],
      type: 'bar' as const,
      stack: 'total',
      barWidth: 56,
      itemStyle: { color: PRIORITY_COLORS[p], borderRadius: 0 },
      data: [byPriority.get(p) ?? 0],
    })),
  }
}

/** Top N 处理人横向柱状图（top-handlers） */
export function buildTopHandlersBarOption(list: TopHandler[]): EChartsOption {
  return {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 16, right: 40, top: 16, bottom: 24, containLabel: true },
    xAxis: {
      type: 'value',
      minInterval: 1,
      splitLine: { lineStyle: { color: '#f0f0f0' } },
      axisLabel: { color: TEXT_COLOR, fontSize: 11 },
    },
    yAxis: {
      type: 'category',
      inverse: true,
      data: list.map((h) => h.handlerName || `#${h.handlerId}`),
      axisLine: { lineStyle: { color: AXIS_COLOR } },
      axisLabel: { color: TEXT_COLOR, fontSize: 11 },
    },
    series: [
      {
        type: 'bar',
        barMaxWidth: 18,
        itemStyle: { color: '#0078d4', borderRadius: [0, 6, 6, 0] },
        data: list.map((h) => h.resolvedCount),
      },
    ],
  }
}
