<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock, Cpu } from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const store = useAuthStore()

const form = reactive({
  username: '',
  password: '',
})
const loading = ref(false)

/** 登录：成功后进入首页，失败提示错误 */
async function handleLogin() {
  if (!form.username || !form.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    await store.login({ username: form.username, password: form.password })
    ElMessage.success('登录成功')
    router.push('/')
  } catch {
    ElMessage.error('登录失败，请检查用户名或密码')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <!-- 登录页：左侧品牌面板 + 右侧表单卡片（Win11 极简，窄屏隐藏品牌面板） -->
  <div class="login-container">
    <aside class="brand-panel">
      <div class="brand-logo">
        <div class="brand-logo-icon">
          <el-icon :size="18"><Cpu /></el-icon>
        </div>
        <span>AI 智能工单管理系统</span>
      </div>

      <div class="brand-hero">
        <h1>AI 智能工单</h1>
        <p>企业级运维协作平台 · 让 AI 协助你的团队更快响应、精准分类、智能回复。</p>
        <div class="brand-features">
          <span class="feature-tag">AI 自动分类</span>
          <span class="feature-tag">智能回复</span>
          <span class="feature-tag">RBAC 权限</span>
          <span class="feature-tag">单体架构</span>
        </div>
      </div>

      <div class="brand-footer">v1.0.0 · 企业版</div>
    </aside>

    <main class="form-panel">
      <div class="login-card">
        <div class="login-title">欢迎登录</div>
        <div class="login-subtitle">请输入您的工号与密码</div>

        <el-form class="login-form" @submit.prevent="handleLogin">
          <el-form-item class="form-group">
            <el-input
              v-model="form.username"
              placeholder="工号 / 用户名"
              size="large"
              :prefix-icon="User"
              clearable
              @keyup.enter="handleLogin"
            />
          </el-form-item>

          <el-form-item class="form-group">
            <el-input
              v-model="form.password"
              type="password"
              placeholder="密码"
              size="large"
              :prefix-icon="Lock"
              show-password
              @keyup.enter="handleLogin"
            />
          </el-form-item>

          <el-button type="primary" class="login-btn" :loading="loading" @click="handleLogin">
            登 录
          </el-button>
        </el-form>

        <div class="login-footer">© 2026 AI 工单管理系统 · Powered by Spring Boot 3</div>
      </div>
    </main>
  </div>
</template>

<style scoped>
.login-container {
  display: flex;
  min-height: 100vh;
  background: var(--bg-surface);
}

/* ============ 左侧品牌面板（Win11 极简） ============ */
.brand-panel {
  flex: 1.1;
  background: var(--bg-surface);
  padding: 56px 64px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  border-right: 1px solid var(--border-subtle);
  position: relative;
  overflow: hidden;
}

/* 一个非常微妙的装饰圆 — Win11 Mica 风格的轻盈感 */
.brand-panel::before {
  content: '';
  position: absolute;
  top: -200px;
  right: -200px;
  width: 500px;
  height: 500px;
  background: radial-gradient(circle, var(--accent-subtle) 0%, transparent 70%);
  pointer-events: none;
}

.brand-logo {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
  position: relative;
  z-index: 1;
}

.brand-logo-icon {
  width: 32px;
  height: 32px;
  background: var(--accent-subtle);
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--accent);
}

.brand-hero {
  position: relative;
  z-index: 1;
  max-width: 420px;
}

.brand-hero h1 {
  font-size: 32px;
  font-weight: 600;
  color: var(--text-primary);
  line-height: 1.25;
  margin-bottom: 12px;
  letter-spacing: -0.4px;
}

.brand-hero p {
  font-size: 14px;
  color: var(--text-secondary);
  line-height: 1.65;
}

.brand-features {
  display: flex;
  gap: 6px;
  margin-top: 24px;
  flex-wrap: wrap;
}

.feature-tag {
  background: var(--bg-subtle);
  border: 1px solid var(--border-subtle);
  padding: 4px 12px;
  border-radius: 12px;
  font-size: 12px;
  color: var(--text-secondary);
}

.brand-footer {
  font-size: 12px;
  color: var(--text-tertiary);
  position: relative;
  z-index: 1;
}

/* ============ 右侧表单面板 ============ */
.form-panel {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px;
  background: var(--bg-surface);
}

.login-card {
  width: 100%;
  max-width: 340px;
}

.login-title {
  font-size: 22px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 4px;
}

.login-subtitle {
  font-size: 13px;
  color: var(--text-secondary);
  margin-bottom: 32px;
}

.form-group {
  margin-bottom: 14px;
}

.login-form .el-form-item {
  margin-bottom: 14px;
}

/* 登录按钮：满宽、Win11 圆角与主色覆盖 */
.login-btn {
  width: 100%;
  height: 36px;
  font-size: 14px;
  font-weight: 500;
  border-radius: 6px !important;
  margin-top: 10px;
}

.login-btn.el-button--primary {
  --el-button-bg-color: var(--accent);
  --el-button-border-color: var(--accent);
  --el-button-hover-bg-color: var(--accent-hover);
  --el-button-hover-border-color: var(--accent-hover);
  --el-button-active-bg-color: var(--accent-pressed);
  --el-button-active-border-color: var(--accent-pressed);
}

.login-footer {
  text-align: center;
  margin-top: 28px;
  font-size: 11px;
  color: var(--text-tertiary);
}

/* ============ Element Plus 输入框 Win11 化覆盖 ============ */
/* 注意：el-input 的根元素（.el-input--large）可被 scoped 命中，
   但 __wrapper / __inner 是子组件内部元素，必须用 :deep() 穿透 scoped，否则覆盖不生效 */
.el-input--large {
  --el-input-height: 36px;
}

.login-form :deep(.el-input__wrapper) {
  background: var(--bg-subtle) !important;
  border-radius: 6px !important;
  box-shadow: 0 0 0 1px var(--border-default) inset !important;
  transition: box-shadow 0.15s ease, background-color 0.15s ease;
}

.login-form :deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px var(--accent) inset !important;
}

.login-form :deep(.el-input.is-focus .el-input__wrapper),
.login-form :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px var(--accent) inset !important;
  background: var(--bg-surface) !important;
}

.login-form :deep(.el-input__inner) {
  color: var(--text-primary) !important;
}

.login-form :deep(.el-input__inner::placeholder) {
  color: var(--text-tertiary) !important;
}

/* ============ 响应式：窄屏隐藏品牌面板 ============ */
@media (max-width: 900px) {
  .brand-panel {
    display: none;
  }

  .form-panel {
    flex: 1;
  }
}
</style>
