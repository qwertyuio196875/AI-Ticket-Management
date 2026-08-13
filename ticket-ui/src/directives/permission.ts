import type { Directive } from 'vue'
import { useAuthStore } from '../stores/auth'

/**
 * v-permission 指令：`v-permission="['ticket:create']"`。
 * 用户缺少全部所列权限时移除元素；空列表视为放行。
 * 判定逻辑抽为纯函数 shouldRemoveElement 便于单测。
 */

/** 是否应移除元素（纯函数）：required 为空放行；用户权限任一命中放行；否则移除 */
export function shouldRemoveElement(userPermissions: string[], required: string[] | undefined): boolean {
  if (!required || required.length === 0) return false
  return !required.some((p) => userPermissions.includes(p))
}

export const permission: Directive<HTMLElement, string[]> = {
  mounted(el, binding) {
    const store = useAuthStore()
    if (shouldRemoveElement(store.permissions, binding.value)) {
      el.remove()
    }
  },
}

export default permission
