// @vitest-environment happy-dom
// 指令挂载测试需要真实 DOM，按文件启用 happy-dom；判定逻辑本身是 node 可测的纯函数
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createApp, h, resolveDirective, withDirectives } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from '../../stores/auth'
import { permission, shouldRemoveElement } from '../permission'

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
}))

/** 挂载一个带 v-permission 指令的按钮到 DOM，返回容器元素 */
function mountButton(userPermissions: string[], required: string[]) {
  setActivePinia(createPinia())
  const store = useAuthStore()
  store.permissions = userPermissions

  const host = document.createElement('div')
  document.body.appendChild(host)

  const app = createApp({
    render() {
      const dir = resolveDirective('permission')
      return withDirectives(h('button', { class: 'perm-btn' }, '新建'), [[dir!, required]])
    },
  })
  app.directive('permission', permission)
  app.mount(host)
  return host
}

describe('permission 指令', () => {
  beforeEach(() => {
    document.body.innerHTML = ''
  })

  it('用户拥有所需权限时元素保留渲染', () => {
    const host = mountButton(['ticket:create', 'ticket:view'], ['ticket:create'])
    expect(host.querySelector('.perm-btn')).not.toBeNull()
  })

  it('用户缺少全部所需权限时元素被移除', () => {
    const host = mountButton(['ticket:view'], ['ticket:create'])
    expect(host.querySelector('.perm-btn')).toBeNull()
  })

  it('空权限列表（放行）时元素始终保留', () => {
    const host = mountButton([], [])
    expect(host.querySelector('.perm-btn')).not.toBeNull()
  })
})

describe('shouldRemoveElement 判定纯函数', () => {
  it('空权限要求一律放行（返回 false）', () => {
    expect(shouldRemoveElement([], [])).toBe(false)
    expect(shouldRemoveElement(['ticket:view'], [])).toBe(false)
    expect(shouldRemoveElement([], undefined)).toBe(false)
  })

  it('任一权限命中放行，全部缺失才移除', () => {
    expect(shouldRemoveElement(['ticket:create'], ['ticket:create'])).toBe(false)
    expect(shouldRemoveElement(['ticket:view'], ['ticket:create', 'ticket:assign'])).toBe(true)
    expect(shouldRemoveElement(['ticket:view', 'ticket:assign'], ['ticket:create', 'ticket:assign'])).toBe(false)
  })
})
