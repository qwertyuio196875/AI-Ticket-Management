<script setup lang="ts">
import { computed } from 'vue'
import { TrendCharts } from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'

const store = useAuthStore()

/** 欢迎语昵称：统一走 store.displayName，此处兜底「管理员」 */
const displayName = computed(() => store.displayName || '管理员')
</script>

<template>
  <!-- 工作台占位页：欢迎语 + 统计图表建设中（图表由后续 ticket 实现） -->
  <div class="dashboard">
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

    <section class="placeholder-card">
      <div class="placeholder-icon">
        <el-icon :size="28"><TrendCharts /></el-icon>
      </div>
      <h3 class="placeholder-title">统计图表建设中</h3>
      <p class="placeholder-desc">
        工单数量趋势、状态分布、处理时长等统计看板将在后续版本开放，敬请期待。
      </p>
    </section>
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

/* 统计占位卡片 */
.placeholder-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  background: var(--bg-surface);
  border: 1px dashed var(--border-default);
  border-radius: var(--radius-md);
  padding: 64px 24px;
}

.placeholder-icon {
  width: 56px;
  height: 56px;
  border-radius: 14px;
  background: var(--accent-subtle);
  color: var(--accent);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 16px;
}

.placeholder-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}

.placeholder-desc {
  margin-top: 8px;
  font-size: 13px;
  color: var(--text-tertiary);
  max-width: 420px;
}
</style>
