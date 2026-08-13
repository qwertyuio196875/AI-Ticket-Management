<script setup lang="ts">
/**
 * Dashboard 工作台 /dashboard（stats:view）：
 * 欢迎卡 + 4 张 ECharts 图（状态饼图 / 7 日趋势折线 / 优先级堆叠柱状 / Top10 处理人横向柱状）
 * API 数据 → option 映射抽为纯函数（utils/chartOptions）；加载态 + 空数据态 + 平板单列堆叠
 */
import { computed, onMounted, ref } from 'vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { PieChart, LineChart, BarChart } from 'echarts/charts'
import { TooltipComponent, LegendComponent, GridComponent } from 'echarts/components'
import { useAuthStore } from '../stores/auth'
import {
  getTicketSummary, getTicketTrend, getPriorityStats, getTopHandlers,
  type TicketSummary, type TrendPoint, type PriorityCount, type TopHandler,
} from '../api/stats'
import {
  buildStatusPieOption, buildTrendLineOption, buildPriorityBarOption, buildTopHandlersBarOption,
} from '../utils/chartOptions'

// 按需注册 ECharts 模块（减小打包体积）
use([CanvasRenderer, PieChart, LineChart, BarChart, TooltipComponent, LegendComponent, GridComponent])

const store = useAuthStore()

const loading = ref(true)

const summary = ref<TicketSummary | null>(null)
const trend = ref<TrendPoint[]>([])
const priorityStats = ref<PriorityCount[]>([])
const topHandlers = ref<TopHandler[]>([])

const summaryOption = computed(() => (summary.value ? buildStatusPieOption(summary.value) : undefined))
const trendOption = computed(() => buildTrendLineOption(trend.value))
const priorityOption = computed(() => buildPriorityBarOption(priorityStats.value))
const topHandlerOption = computed(() => buildTopHandlersBarOption(topHandlers.value))

/** 欢迎语昵称：统一走 store.displayName，此处兜底「管理员」 */
const displayName = computed(() => store.displayName || '管理员')

/** 空数据判断：各图数据源无有效数据时显示「暂无数据」占位 */
const summaryEmpty = computed(() => {
  if (!summary.value) return true
  return summary.value.total === 0
})
const trendEmpty = computed(() => trend.value.length === 0 || trend.value.every((p) => p.count === 0))
const priorityEmpty = computed(() => priorityStats.value.every((p) => p.count === 0))
const topHandlerEmpty = computed(() => topHandlers.value.length === 0)

async function loadStats() {
  loading.value = true
  try {
    const [summaryData, trendData, priorityData, topData] = await Promise.all([
      getTicketSummary(),
      getTicketTrend(7),
      getPriorityStats(),
      getTopHandlers(10),
    ])
    summary.value = summaryData
    trend.value = trendData
    priorityStats.value = priorityData
    topHandlers.value = topData
  } catch {
    // 错误已由 http 拦截器提示
  } finally {
    loading.value = false
  }
}

onMounted(loadStats)
</script>

<template>
  <div class="dashboard" v-loading="loading">
    <!-- 欢迎卡 -->
    <section class="welcome-card">
      <div class="welcome-main">
        <h2 class="welcome-title">欢迎回来，{{ displayName }}</h2>
        <p class="welcome-desc">
          这里是 AI 智能工单管理系统的工作台，您可以在「工单列表」中处理团队提交的工单。
        </p>
      </div>
      <div class="welcome-meta">
        <span class="version-tag">v1.0.0</span>
      </div>
    </section>

    <!-- 图表区 -->
    <div class="chart-grid">
      <!-- 状态分布饼图 -->
      <section class="chart-card">
        <div class="chart-header">
          <span class="chart-title">工单状态分布</span>
        </div>
        <div v-if="summaryEmpty" class="chart-empty">暂无数据</div>
        <VChart v-else :option="summaryOption" class="chart" autoresize />
      </section>

      <!-- 7 日趋势折线 -->
      <section class="chart-card">
        <div class="chart-header">
          <span class="chart-title">近 7 日工单趋势</span>
        </div>
        <div v-if="trendEmpty" class="chart-empty">暂无数据</div>
        <VChart v-else :option="trendOption" class="chart" autoresize />
      </section>

      <!-- 优先级堆叠柱状 -->
      <section class="chart-card">
        <div class="chart-header">
          <span class="chart-title">优先级分布</span>
        </div>
        <div v-if="priorityEmpty" class="chart-empty">暂无数据</div>
        <VChart v-else :option="priorityOption" class="chart" autoresize />
      </section>

      <!-- Top10 处理人横向柱状 -->
      <section class="chart-card">
        <div class="chart-header">
          <span class="chart-title">Top 10 处理人</span>
        </div>
        <div v-if="topHandlerEmpty" class="chart-empty">暂无数据</div>
        <VChart v-else :option="topHandlerOption" class="chart" autoresize />
      </section>
    </div>
  </div>
</template>

<style scoped>
.dashboard {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* 欢迎语卡片 */
.welcome-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  background: var(--bg-surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  padding: 28px 32px;
}

.welcome-title {
  font-size: 20px;
  font-weight: 600;
  color: var(--text-primary);
  letter-spacing: -0.2px;
}

.welcome-desc {
  margin-top: 8px;
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.6;
  max-width: 560px;
}

.welcome-meta {
  flex-shrink: 0;
}

.version-tag {
  display: inline-block;
  background: var(--accent-subtle);
  color: var(--accent);
  border-radius: 12px;
  padding: 4px 14px;
  font-size: 12px;
  font-weight: 500;
}

/* 图表网格：桌面 2×2 */
.chart-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
}

.chart-card {
  background: var(--bg-surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  padding: 16px 20px;
  min-width: 0;
}

.chart-header {
  margin-bottom: 8px;
}

.chart-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

.chart {
  height: 280px;
  width: 100%;
}

.chart-empty {
  height: 280px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  color: var(--text-tertiary);
  background: var(--bg-subtle);
  border-radius: var(--radius-sm);
}

/* 响应式：平板与手机单列堆叠 */
@media (max-width: 1023px) {
  .chart-grid {
    grid-template-columns: 1fr;
  }
}
</style>
