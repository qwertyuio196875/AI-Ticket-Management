import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import type { MenuNode } from '../../api/menus'

// mock api 层：auth store 通过 api 模块与后端交互
vi.mock('../../api/auth', () => ({
  login: vi.fn(),
  logout: vi.fn(),
  getMe: vi.fn(),
}))

vi.mock('../../api/menus', () => ({
  getMenuTree: vi.fn(),
}))

import { login, logout, getMe } from '../../api/auth'
import { getMenuTree } from '../../api/menus'
import { useAuthStore, flattenPermissions } from '../auth'
import { TOKEN_KEY } from '../../utils/http'

const mockedLogin = vi.mocked(login)
const mockedLogout = vi.mocked(logout)
const mockedGetMe = vi.mocked(getMe)
const mockedGetMenuTree = vi.mocked(getMenuTree)

/** 构造一个含子节点的菜单树，覆盖 C 型与 F 型节点 */
function sampleMenuTree(): MenuNode[] {
  return [
    {
      id: 1,
      parentId: 0,
      menuName: '工作台',
      menuType: 'C',
      path: '/dashboard',
      permission: 'dashboard:view',
    },
    {
      id: 2,
      parentId: 0,
      menuName: '系统管理',
      menuType: 'C',
      path: '/system',
      children: [
        { id: 21, parentId: 2, menuName: '用户管理', menuType: 'C', path: '/users', permission: 'user:list' },
        { id: 22, parentId: 2, menuName: '新增用户', menuType: 'F', permission: 'user:add' },
      ],
    },
  ]
}

describe('useAuthStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    vi.clearAllMocks()
  })

  it('login 成功后写入 token、userInfo、持久化 localStorage，并拉取菜单树', async () => {
    mockedLogin.mockResolvedValue({
      token: 'jwt-1',
      tokenType: 'Bearer',
      expiresIn: 1800,
      userId: 1,
      username: 'admin',
      nickname: '管理员',
    })
    mockedGetMenuTree.mockResolvedValue(sampleMenuTree())

    const store = useAuthStore()
    await store.login({ username: 'admin', password: 'admin123' })

    expect(mockedLogin).toHaveBeenCalledWith({ username: 'admin', password: 'admin123' })
    expect(store.token).toBe('jwt-1')
    expect(store.userInfo).toEqual({ userId: 1, username: 'admin', nickname: '管理员' })
    expect(localStorage.getItem(TOKEN_KEY)).toBe('jwt-1')
    expect(mockedGetMenuTree).toHaveBeenCalledTimes(1)
    expect(store.menuTree).toHaveLength(2)
  })

  it('fetchMenuTree 将整棵菜单树（含子节点）的非空 permission 扁平化为 permissions', async () => {
    mockedGetMenuTree.mockResolvedValue(sampleMenuTree())

    const store = useAuthStore()
    await store.fetchMenuTree()

    expect(store.menuTree).toHaveLength(2)
    expect(store.permissions).toEqual(['dashboard:view', 'user:list', 'user:add'])
  })

  it('logout 无论后端调用成功与否都清空 state 与 localStorage', async () => {
    localStorage.setItem(TOKEN_KEY, 'jwt-1')
    const store = useAuthStore()
    store.token = 'jwt-1'
    store.userInfo = { userId: 1, username: 'admin', nickname: '管理员' }
    store.menuTree = sampleMenuTree()
    store.permissions = ['dashboard:view']
    mockedLogout.mockRejectedValue(new Error('网络异常'))

    await store.logout()

    expect(mockedLogout).toHaveBeenCalled()
    expect(store.token).toBe('')
    expect(store.userInfo).toBeNull()
    expect(store.menuTree).toEqual([])
    expect(store.permissions).toEqual([])
    expect(localStorage.getItem(TOKEN_KEY)).toBeNull()
  })

  it('init：localStorage 有 token 时恢复 token、重建 userInfo 并拉取菜单', async () => {
    localStorage.setItem(TOKEN_KEY, 'jwt-2')
    mockedGetMe.mockResolvedValue({ userId: 9, username: 'zhang', authorities: ['dashboard:view'] })
    mockedGetMenuTree.mockResolvedValue(sampleMenuTree())

    const store = useAuthStore()
    await store.init()

    expect(store.token).toBe('jwt-2')
    expect(store.userInfo).toEqual({ userId: 9, username: 'zhang', nickname: 'zhang' })
    expect(mockedGetMenuTree).toHaveBeenCalled()
  })

  it('init：localStorage 无 token 时不发起任何请求', async () => {
    const store = useAuthStore()
    await store.init()

    expect(store.token).toBe('')
    expect(mockedGetMe).not.toHaveBeenCalled()
    expect(mockedGetMenuTree).not.toHaveBeenCalled()
  })

  it('init：有 token 但 /auth/me 失败时清空 state 与 localStorage', async () => {
    localStorage.setItem(TOKEN_KEY, 'expired-token')
    mockedGetMe.mockRejectedValue(new Error('401 Unauthorized'))

    const store = useAuthStore()
    await store.init()

    expect(store.token).toBe('')
    expect(store.userInfo).toBeNull()
    expect(localStorage.getItem(TOKEN_KEY)).toBeNull()
  })

  it('hasAnyPermission：权限列表任一命中返回 true，空列表返回 true，均未命中返回 false', () => {
    const store = useAuthStore()
    store.permissions = ['dashboard:view', 'ticket:list']

    expect(store.hasAnyPermission(['dashboard:view'])).toBe(true)
    expect(store.hasAnyPermission(['nope', 'ticket:list'])).toBe(true)
    expect(store.hasAnyPermission([])).toBe(true)
    expect(store.hasAnyPermission(['nope'])).toBe(false)
  })

  it('displayName getter：优先 nickname，降级 username，兜底空串', () => {
    const store = useAuthStore()

    store.userInfo = { userId: 1, username: 'zhang', nickname: '张工' }
    expect(store.displayName).toBe('张工')

    store.userInfo = { userId: 1, username: 'zhang', nickname: '' }
    expect(store.displayName).toBe('zhang')

    store.userInfo = null
    expect(store.displayName).toBe('')
  })

  it('flattenPermissions：对含 children 的节点树扁平化去重非空 permission', () => {
    expect(flattenPermissions(sampleMenuTree())).toEqual(['dashboard:view', 'user:list', 'user:add'])
    expect(flattenPermissions([])).toEqual([])
  })
})
