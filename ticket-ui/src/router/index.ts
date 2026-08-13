import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { TOKEN_KEY } from '../utils/http'
import { resolveNavigation } from './guard'

/**
 * 路由表：
 * - /login：登录页（AuthLayout 容器）
 * - /：主布局（DefaultLayout），requiresAuth，默认重定向 /dashboard
 * - catch-all：404 中文占位页
 */
export const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    component: () => import('../layouts/AuthLayout.vue'),
    children: [
      {
        path: '',
        name: 'Login',
        component: () => import('../views/LoginView.vue'),
        meta: { title: '登录' },
      },
    ],
  },
  {
    path: '/',
    component: () => import('../layouts/DefaultLayout.vue'),
    redirect: '/dashboard',
    meta: { requiresAuth: true },
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('../views/DashboardView.vue'),
        meta: { title: '工作台', requiresAuth: true, permissions: ['dashboard:view'] },
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('../views/NotFoundView.vue'),
    meta: { title: '页面不存在' },
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

/**
 * 全局前置守卫：无 token / 未登录 / 权限不足时按规则重定向
 */
router.beforeEach(async (to) => {
  const store = useAuthStore()
  const target = await resolveNavigation(to, {
    hasToken: () => !!localStorage.getItem(TOKEN_KEY),
    isMenuLoaded: () => store.menuTree.length > 0,
    ensureMenuLoaded: () => store.fetchMenuTree(),
    hasPermission: (permissions) => store.hasAnyPermission(permissions),
    // 权限不足时的回落路径：取菜单树中第一个可导航页面；无菜单时兜底 /dashboard
    fallbackPath: () => store.menuTree.find((n) => n.menuType === 'C' && n.path)?.path || '/dashboard',
  })
  return target ?? true
})

export default router
