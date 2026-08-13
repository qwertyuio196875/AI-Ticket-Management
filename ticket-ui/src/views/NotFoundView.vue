<script setup lang="ts">
// 404 占位页：大号代码 + 中文提示 + 返回首页
</script>

<template>
  <div class="not-found">
    <div class="not-found-code">404</div>
    <p class="not-found-desc">您访问的页面不存在，可能已被移动或删除</p>
    <el-button type="primary" class="not-found-btn" @click="$router.push('/')">返回首页</el-button>
  </div>
</template>

<style scoped>
.not-found {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: var(--bg-base);
  padding: 24px;
  text-align: center;
}

.not-found-code {
  font-size: 96px;
  font-weight: 700;
  letter-spacing: 4px;
  color: var(--accent);
  background: var(--accent-subtle);
  border-radius: 16px;
  padding: 8px 40px;
  line-height: 1.1;
  user-select: none;
}

.not-found-desc {
  margin-top: 24px;
  font-size: 14px;
  color: var(--text-secondary);
}

.not-found-btn {
  margin-top: 28px;
  border-radius: 6px;
}
</style>
