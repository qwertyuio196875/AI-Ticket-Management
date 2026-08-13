import type { RouteLocationNormalized, RouteLocationRaw } from 'vue-router'

/**
 * 守卫执行所需的上下文（由 router 装配时注入，便于独立单测）
 */
export interface GuardContext {
  /** 当前是否有 token */
  hasToken: () => boolean
  /** 菜单树是否已加载 */
  isMenuLoaded: () => boolean
  /** 拉取菜单树（401 时拦截器会清空登录态） */
  ensureMenuLoaded: () => Promise<unknown>
  /** 是否拥有给定权限点列表中的任意一个 */
  hasPermission: (permissions: string[]) => boolean
  /** 权限不足时的回落路径（如菜单树中第一个可导航页面），避免硬编码导致死循环 */
  fallbackPath: () => string
}

/**
 * 导航守卫核心决策函数（纯逻辑，可独立单测）：
 * - 无 token 访问 requiresAuth 路由 → 重定向 /login?redirect={fullPath}
 * - 已登录访问 /login → 重定向 /
 * - 有 token 但菜单未加载 → 先拉取菜单；失败（token 被清）→ 兜底回登录页
 * - meta.permissions 权限不足 → 重定向 fallbackPath（与当前路径相同则放行，防止死循环）
 * - 其余情况返回 null 表示放行
 */
export async function resolveNavigation(
  to: RouteLocationNormalized,
  ctx: GuardContext,
): Promise<RouteLocationRaw | null> {
  const requiresAuth = to.meta.requiresAuth === true
  const hasToken = ctx.hasToken()

  if (!requiresAuth) {
    // 公开页：已登录访问 /login 时直接回首页
    if (to.path === '/login' && hasToken) return '/'
    return null
  }

  // 未登录访问受保护页
  if (!hasToken) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  // 已登录但菜单尚未加载：先拉取菜单与权限
  if (!ctx.isMenuLoaded()) {
    await ctx.ensureMenuLoaded()
  }

  // 拉取菜单失败（如 401，拦截器已清空登录态）：兜底回登录页
  if (!ctx.hasToken()) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  // 权限校验：meta.permissions 任一命中才放行
  const required = to.meta.permissions as string[] | undefined
  if (required && required.length > 0 && !ctx.hasPermission(required)) {
    const fallback = ctx.fallbackPath()
    // 回落路径即当前路径（如当前页恰好是用户唯一可访问页）时直接放行，避免无限重定向
    if (fallback === to.path) return null
    return fallback
  }

  return null
}
