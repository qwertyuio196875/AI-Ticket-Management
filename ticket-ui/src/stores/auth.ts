import { defineStore } from 'pinia'
import { login as apiLogin, logout as apiLogout, getMe, type LoginParams } from '../api/auth'
import { getMenuTree, type MenuNode } from '../api/menus'
import { TOKEN_KEY } from '../utils/http'

/** 前端会话中的用户信息（/auth/me 不含 nickname 时降级为 username） */
export interface UserInfo {
  userId: number
  username: string
  nickname: string
}

/**
 * 认证状态管理：token / 用户信息 / 菜单树 / 权限点。
 * token 持久化到 localStorage，登录成功、应用启动（init）时都会拉取菜单树。
 */
export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: '',
    userInfo: null as UserInfo | null,
    menuTree: [] as MenuNode[],
    permissions: [] as string[],
  }),

  getters: {
    /** 是否已登录（存在 token） */
    isLoggedIn: (state) => !!state.token,
    /** 展示名：优先 nickname，降级 username，兜底空串 */
    displayName: (state) => state.userInfo?.nickname || state.userInfo?.username || '',
  },

  actions: {
    /**
     * 登录：调用登录接口 → 保存 token 与用户信息 → 持久化 → 拉取菜单树与权限
     */
    async login(params: LoginParams) {
      const vo = await apiLogin(params)
      this.token = vo.token
      this.userInfo = { userId: vo.userId, username: vo.username, nickname: vo.nickname }
      localStorage.setItem(TOKEN_KEY, vo.token)
      await this.fetchMenuTree()
    },

    /**
     * 拉取菜单树并扁平化权限点
     */
    async fetchMenuTree() {
      const tree = await getMenuTree()
      this.menuTree = tree
      this.permissions = flattenPermissions(tree)
    },

    /**
     * 登出：尽力调用后端登出接口；无论成败都清空本地登录态
     */
    async logout() {
      try {
        await apiLogout()
      } catch {
        // 登出接口失败不阻塞本地清理（如网络异常时仍可退出）
      } finally {
        this.clearState()
      }
    },

    /**
     * 应用启动时恢复登录态：localStorage 有 token → 重建用户信息 + 菜单权限；
     * 无 token 或恢复失败 → 保持/清空未登录状态
     */
    async init() {
      const token = localStorage.getItem(TOKEN_KEY)
      if (!token) return
      this.token = token
      try {
        const me = await getMe()
        this.userInfo = { userId: me.userId, username: me.username, nickname: me.username }
        await this.fetchMenuTree()
      } catch {
        this.clearState()
      }
    },

    /**
     * 清空全部登录态（state + localStorage）
     */
    clearState() {
      this.token = ''
      this.userInfo = null
      this.menuTree = []
      this.permissions = []
      localStorage.removeItem(TOKEN_KEY)
    },

    /**
     * 判断是否拥有给定权限点列表中的任意一个；空列表视为放行
     */
    hasAnyPermission(list: string[]): boolean {
      if (!list || list.length === 0) return true
      return list.some((p) => this.permissions.includes(p))
    },
  },
})

/**
 * 扁平化菜单树（含子节点）中的所有非空 permission 字段
 */
export function flattenPermissions(nodes: MenuNode[]): string[] {
  const result: string[] = []
  const walk = (items: MenuNode[]) => {
    for (const item of items) {
      if (item.permission) result.push(item.permission)
      if (item.children && item.children.length > 0) walk(item.children)
    }
  }
  walk(nodes)
  return result
}
