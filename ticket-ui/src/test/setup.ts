import { vi } from 'vitest'

// vitest node 环境下模拟浏览器 localStorage（Token 持久化依赖）。
// 进程级一次性注入：所有测试文件共享同一实例，各 spec 通过 beforeEach(localStorage.clear()) 保证隔离。
const storage = new Map<string, string>()

vi.stubGlobal('localStorage', {
  getItem: (key: string) => (storage.has(key) ? storage.get(key)! : null),
  setItem: (key: string, value: string) => {
    storage.set(key, String(value))
  },
  removeItem: (key: string) => {
    storage.delete(key)
  },
  clear: () => {
    storage.clear()
  },
  key: (index: number) => Array.from(storage.keys())[index] ?? null,
  get length() {
    return storage.size
  },
})
