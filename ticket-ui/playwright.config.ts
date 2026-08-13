import { defineConfig } from '@playwright/test'

/**
 * Playwright E2E 测试配置：
 * - 复用（或自起）Vite dev server，端口 5210（避开 Windows 保留端口范围 5110-5209）
 * - 后端 Java 服务本机不可用，E2E 统一通过 page.route 拦截 mock 后端 API
 * - 截图：失败自动截图 + 用例内显式截图留档到 e2e/screenshots/
 */
export default defineConfig({
  testDir: './e2e',
  outputDir: './e2e/screenshots',
  screenshot: 'only-on-failure',
  timeout: 30_000,
  use: {
    baseURL: 'http://localhost:5210',
    viewport: { width: 1280, height: 800 },
  },
  webServer: {
    command: 'npm run dev -- --port 5210 --strictPort',
    url: 'http://localhost:5210',
    reuseExistingServer: true,
  },
})
