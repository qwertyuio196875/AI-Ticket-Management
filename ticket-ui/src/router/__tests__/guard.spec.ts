import { describe, expect, it, vi } from 'vitest'
import type { RouteLocationNormalized } from 'vue-router'
import { resolveNavigation, type GuardContext } from '../guard'

/** 构造最小 RouteLocationNormalized 测试对象 */
function makeTo(overrides: Partial<RouteLocationNormalized> = {}): RouteLocationNormalized {
  return {
    path: '/tickets',
    fullPath: '/tickets',
    query: {},
    params: {},
    hash: '',
    matched: [],
    meta: {},
    name: undefined,
    redirectedFrom: undefined,
    ...overrides,
  } as unknown as RouteLocationNormalized
}

/** 构造守卫上下文，默认放行全部检查 */
function makeCtx(overrides: Partial<GuardContext> = {}): GuardContext {
  return {
    hasToken: () => true,
    isMenuLoaded: () => true,
    ensureMenuLoaded: vi.fn().mockResolvedValue(undefined),
    hasPermission: () => true,
    fallbackPath: () => '/dashboard',
    ...overrides,
  }
}

describe('resolveNavigation 路由守卫', () => {
  it('requiresAuth 路由且无 token → 重定向 /login 并携带 redirect 查询参数', async () => {
    const to = makeTo({ path: '/tickets', fullPath: '/tickets', meta: { requiresAuth: true } })
    const ctx = makeCtx({ hasToken: () => false })

    const target = await resolveNavigation(to, ctx)

    expect(target).toEqual({ path: '/login', query: { redirect: '/tickets' } })
  })

  it('已登录访问公开登录页 /login → 重定向到首页 /', async () => {
    const to = makeTo({ path: '/login', fullPath: '/login', meta: {} })

    const target = await resolveNavigation(to, makeCtx({ hasToken: () => true }))

    expect(target).toBe('/')
  })

  it('未登录访问 /login → 放行', async () => {
    const to = makeTo({ path: '/login', fullPath: '/login', meta: {} })

    const target = await resolveNavigation(to, makeCtx({ hasToken: () => false }))

    expect(target).toBeNull()
  })

  it('requiresAuth + 有 token + 权限不足 → 采用 fallbackPath 返回值重定向', async () => {
    const to = makeTo({
      path: '/users',
      fullPath: '/users',
      meta: { requiresAuth: true, permissions: ['user:list'] },
    })
    const fallbackPath = vi.fn().mockReturnValue('/tickets')
    const ctx = makeCtx({ hasPermission: () => false, fallbackPath })

    const target = await resolveNavigation(to, ctx)

    expect(fallbackPath).toHaveBeenCalled()
    expect(target).toBe('/tickets')
  })

  it('权限不足但 fallbackPath 与当前路径相同 → 放行，避免重定向死循环', async () => {
    const to = makeTo({
      path: '/dashboard',
      fullPath: '/dashboard',
      meta: { requiresAuth: true, permissions: ['dashboard:view'] },
    })
    const ctx = makeCtx({ hasPermission: () => false, fallbackPath: () => '/dashboard' })

    const target = await resolveNavigation(to, ctx)

    expect(target).toBeNull()
  })

  it('requiresAuth + 有 token + 权限满足 → 放行', async () => {
    const to = makeTo({
      path: '/users',
      fullPath: '/users',
      meta: { requiresAuth: true, permissions: ['user:list'] },
    })
    const ctx = makeCtx({ hasPermission: () => true })

    const target = await resolveNavigation(to, ctx)

    expect(target).toBeNull()
  })

  it('requiresAuth + 有 token + 菜单未加载 → 先调用 ensureMenuLoaded 再放行', async () => {
    const to = makeTo({ path: '/dashboard', fullPath: '/dashboard', meta: { requiresAuth: true } })
    const ensureMenuLoaded = vi.fn().mockResolvedValue(undefined)
    const ctx = makeCtx({ isMenuLoaded: () => false, ensureMenuLoaded })

    const target = await resolveNavigation(to, ctx)

    expect(ensureMenuLoaded).toHaveBeenCalledTimes(1)
    expect(target).toBeNull()
  })

  it('ensureMenuLoaded 失败导致 token 被清空（如 401）→ 兜底重定向 /login', async () => {
    const to = makeTo({ path: '/dashboard', fullPath: '/dashboard', meta: { requiresAuth: true } })
    const ensureMenuLoaded = vi.fn().mockRejectedValue(new Error('401'))
    const ctx = makeCtx({
      hasToken: () => false,
      isMenuLoaded: () => false,
      ensureMenuLoaded,
    })

    const target = await resolveNavigation(to, ctx)

    expect(target).toEqual({ path: '/login', query: { redirect: '/dashboard' } })
  })

  it('requiresAuth + 有 token + meta 未声明 permissions → 放行', async () => {
    const to = makeTo({ path: '/dashboard', fullPath: '/dashboard', meta: { requiresAuth: true } })

    const target = await resolveNavigation(to, makeCtx())

    expect(target).toBeNull()
  })
})
