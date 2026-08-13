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
        meta: { title: '工作台', requiresAuth: true, permissions: ['stats:view'] },
      },
      // 工单域：create 声明在 :id 之前，避免 create 被当成参数
      {
        path: 'tickets',
        name: 'TicketList',
        component: () => import('../views/TicketListView.vue'),
        meta: { title: '工单列表', requiresAuth: true, permissions: ['ticket:view'] },
      },
      {
        path: 'tickets/create',
        name: 'TicketCreate',
        component: () => import('../views/TicketCreateView.vue'),
        meta: { title: '新建工单', requiresAuth: true, permissions: ['ticket:create'] },
      },
      {
        path: 'tickets/:id',
        name: 'TicketDetail',
        component: () => import('../views/TicketDetailView.vue'),
        meta: { title: '工单详情', requiresAuth: true, permissions: ['ticket:view'] },
      },
      // 系统管理域
      {
        path: 'system/users',
        name: 'UserManage',
        component: () => import('../views/UserManageView.vue'),
        meta: { title: '用户管理', requiresAuth: true, permissions: ['user:manage'] },
      },
      {
        path: 'system/roles',
        name: 'RoleManage',
        component: () => import('../views/RoleManageView.vue'),
        meta: { title: '角色管理', requiresAuth: true, permissions: ['role:manage'] },
      },
      {
        path: 'system/menus',
        name: 'MenuManage',
        component: () => import('../views/MenuManageView.vue'),
        meta: { title: '菜单管理', requiresAuth: true, permissions: ['menu:manage'] },
      },
      {
        path: 'system/dicts',
        name: 'DictManage',
        component: () => import('../views/DictManageView.vue'),
        meta: { title: '数据字典', requiresAuth: true, permissions: ['dict:manage'] },
      },
      {
        path: 'system/ticket-categories',
        name: 'TicketCategoryManage',
        component: () => import('../views/TicketCategoryView.vue'),
        meta: { title: '工单分类', requiresAuth: true, permissions: ['category:manage'] },
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
