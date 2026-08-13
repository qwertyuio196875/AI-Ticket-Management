<script setup lang="ts">
// 登录布局：浅色底 + 路由出口，登录页卡片布局由 LoginView 自行完成
</script>

<template>
  <div class="auth-layout">
    <router-view />
  </div>
</template>

<style scoped>
.auth-layout {
  min-height: 100vh;
  background: var(--bg-base);
}
</style>
