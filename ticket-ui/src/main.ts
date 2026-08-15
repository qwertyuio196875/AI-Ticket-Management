import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import './styles/index.css'
import App from './App.vue'
import router from './router'
import permission from './directives/permission'
import { useAuthStore } from './stores/auth'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(router)
app.use(ElementPlus, { locale: zhCn })
// 权限指令：v-permission="['ticket:create']"，缺权限时移除元素
app.directive('permission', permission)
app.mount('#app')

// 应用启动时尝试恢复登录态（localStorage 有 token 时重建用户信息与菜单权限；
// 守卫也会在导航时兜底处理未登录 / 权限不足）
void useAuthStore().init()
