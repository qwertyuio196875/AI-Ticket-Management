<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Fold, Expand, Monitor, ArrowDown, SwitchButton } from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'
import SidebarMenuItem from '../components/SidebarMenuItem.vue'

const router = useRouter()
const route = useRoute()
const store = useAuthStore()

/** 侧边栏折叠状态（桌面模式，折叠宽度 64px） */
const isCollapse = ref(false)
/** 窄屏抽屉模式可见性（< 1024px） */
const drawerVisible = ref(false)
/** 是否为窄屏：侧边栏切换为抽屉 */
const isMobile = ref(false)

/** 窗口尺寸监听：窄屏切换抽屉模式，回到桌面时收起抽屉 */
function handleResize() {
  isMobile.value = window.innerWidth < 1024
  if (!isMobile.value) drawerVisible.value = false
}

onMounted(() => {
  handleResize()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
})

/** 切换侧边栏：窄屏打开抽屉，桌面折叠 / 展开 */
function toggleSidebar() {
  if (isMobile.value) {
    drawerVisible.value = true
  } else {
    isCollapse.value = !isCollapse.value
  }
}

/** 面包屑：首页 / 当前页（当前页取路由 meta.title） */
const breadcrumbs = computed(() => {
  const items: Array<{ title: string; path: string }> = [{ title: '首页', path: '/' }]
  if (route.meta.title) {
    items.push({ title: route.meta.title as string, path: route.path })
  }
  return items
})

/** 顶栏展示的用户名：统一走 store.displayName（nickname → username → 空串），此处兜底「未登录」 */
const displayName = computed(() => store.displayName || '未登录')

/** 头像占位文字：取用户名首字符 */
const avatarText = computed(() => (displayName.value ? displayName.value.charAt(0) : '工'))

/** 用户下拉菜单命令 */
function handleCommand(command: string) {
  if (command === 'logout') void handleLogout()
}

/** 登出：清理登录态后返回登录页 */
async function handleLogout() {
  await store.logout()
  ElMessage.success('已退出登录')
  router.push('/login')
}
</script>

<template>
  <el-container class="app-layout">
    <!-- 桌面侧边栏（窄屏由 CSS 隐藏，切换为抽屉） -->
    <el-aside class="app-aside" :width="isCollapse ? '64px' : '256px'">
      <div class="app-logo" @click="router.push('/')">
        <div class="app-logo-icon">
          <el-icon :size="20"><Monitor /></el-icon>
        </div>
        <span v-show="!isCollapse" class="app-logo-text">AI 工单管理系统</span>
      </div>

      <el-menu
        class="app-menu"
        :default-active="route.path"
        :collapse="isCollapse"
        :collapse-transition="false"
        router
      >
        <SidebarMenuItem v-for="item in store.menuTree" :key="item.id" :node="item" />
      </el-menu>
    </el-aside>

    <!-- 窄屏抽屉：渲染同一份菜单树，点击菜单后自动收起 -->
    <el-drawer
      v-model="drawerVisible"
      direction="ltr"
      size="256px"
      :with-header="false"
      class="app-drawer"
    >
      <div class="app-logo">
        <div class="app-logo-icon">
          <el-icon :size="20"><Monitor /></el-icon>
        </div>
        <span class="app-logo-text">AI 工单管理系统</span>
      </div>
      <el-menu class="app-menu" :default-active="route.path" router @select="drawerVisible = false">
        <SidebarMenuItem v-for="item in store.menuTree" :key="item.id" :node="item" />
      </el-menu>
    </el-drawer>

    <el-container class="app-body">
      <!-- 顶栏：折叠开关 + 面包屑 | 用户信息与退出 -->
      <el-header class="app-header" height="56px">
        <div class="header-left">
          <button class="hamburger" type="button" aria-label="切换侧边栏" @click="toggleSidebar">
            <el-icon :size="18">
              <component :is="isCollapse && !isMobile ? Expand : Fold" />
            </el-icon>
          </button>

          <el-breadcrumb separator="/">
            <el-breadcrumb-item v-for="item in breadcrumbs" :key="item.path" :to="item.path">
              {{ item.title }}
            </el-breadcrumb-item>
          </el-breadcrumb>
        </div>

        <div class="header-right">
          <el-dropdown trigger="click" @command="handleCommand">
            <div class="user-entry">
              <el-avatar :size="28" class="user-avatar">{{ avatarText }}</el-avatar>
              <span class="user-name">{{ displayName }}</span>
              <el-icon :size="12" class="user-caret"><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout">
                  <el-icon><SwitchButton /></el-icon>
                  <span class="dropdown-item-text">退出登录</span>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="app-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
/* ============ 整体骨架 ============ */
.app-layout {
  height: 100vh;
}

.app-body {
  min-width: 0;
}

/* ============ 侧边栏 ============ */
.app-aside {
  display: flex;
  flex-direction: column;
  background: var(--bg-surface);
  border-right: 1px solid var(--border-subtle);
  transition: width 0.2s ease;
  overflow: hidden;
}

.app-logo {
  height: 56px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 16px;
  cursor: pointer;
  border-bottom: 1px solid var(--border-subtle);
  overflow: hidden;
  white-space: nowrap;
  user-select: none;
}

.app-logo-icon {
  width: 28px;
  height: 28px;
  flex-shrink: 0;
  border-radius: 6px;
  background: var(--accent-subtle);
  color: var(--accent);
  display: flex;
  align-items: center;
  justify-content: center;
}

.app-logo-text {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}

/* 菜单 Win11 化：透明底、轻 hover、强调色选中态 */
.app-menu {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  border-right: none;
  background: transparent;
  --el-menu-text-color: var(--text-secondary);
  --el-menu-hover-text-color: var(--text-primary);
  --el-menu-active-color: var(--accent);
  --el-menu-bg-color: transparent;
  --el-menu-hover-bg-color: var(--bg-subtle);
  --el-menu-item-height: 44px;
  --el-menu-sub-item-height: 40px;
  --el-menu-base-level-padding: 12px;
  --el-menu-icon-width: 22px;
  padding: 8px;
}

/* 注意：.el-menu-item / .el-sub-menu__title / .el-menu--popup 是 el-menu
   组件内部渲染的元素，需用 :deep() 穿透 scoped，否则选中态与圆角覆盖不生效 */
.app-menu :deep(.el-menu-item),
.app-menu :deep(.el-sub-menu__title) {
  border-radius: 6px;
  margin-bottom: 2px;
}

.app-menu :deep(.el-menu-item.is-active) {
  background-color: var(--accent-subtle);
  font-weight: 500;
}

.app-menu:not(.el-menu--collapse) {
  width: 100%;
}

/* 折叠模式下弹出子菜单的圆角与阴影 */
.app-menu :deep(.el-menu--popup) {
  border-radius: 6px;
  box-shadow: var(--shadow-md);
}

/* ============ 顶栏 ============ */
.app-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  background: var(--bg-surface);
  border-bottom: 1px solid var(--border-subtle);
  padding: 0 20px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 14px;
  min-width: 0;
}

/* 折叠 / 抽屉开关按钮 */
.hamburger {
  width: 32px;
  height: 32px;
  flex-shrink: 0;
  border: none;
  background: transparent;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-secondary);
  cursor: pointer;
  transition: background-color 0.15s ease, color 0.15s ease;
}

.hamburger:hover {
  background: var(--bg-subtle);
  color: var(--text-primary);
}

.hamburger:active {
  background: var(--border-subtle);
}

/* 面包屑 */
.app-header :deep(.el-breadcrumb__inner),
.app-header :deep(.el-breadcrumb__inner a) {
  color: var(--text-secondary);
  font-weight: 400;
}

.app-header :deep(.el-breadcrumb__item:last-child .el-breadcrumb__inner) {
  color: var(--text-primary);
}

/* 用户信息入口 */
.header-right {
  display: flex;
  align-items: center;
  flex-shrink: 0;
}

.user-entry {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 8px;
  border-radius: 6px;
  cursor: pointer;
  transition: background-color 0.15s ease;
  outline: none;
}

.user-entry:hover {
  background: var(--bg-subtle);
}

.user-avatar {
  background: var(--accent);
  color: #ffffff;
  font-size: 13px;
  flex-shrink: 0;
}

.user-name {
  font-size: 13px;
  color: var(--text-primary);
  max-width: 160px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-caret {
  color: var(--text-tertiary);
}

.dropdown-item-text {
  margin-left: 6px;
}

/* ============ 内容区 ============ */
.app-main {
  background: var(--bg-base);
  padding: 24px;
  overflow-y: auto;
}

/* ============ 响应式：窄屏侧边栏切换为抽屉 ============ */
@media (max-width: 1023px) {
  .app-aside {
    display: none;
  }
}
</style>
