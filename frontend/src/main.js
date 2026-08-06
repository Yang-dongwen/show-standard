import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router from './router/index.js'
import './styles/global.css'

const app = createApp(App)
app.use(ElementPlus, {
  locale: zhCn,
  size: 'default',
  zIndex: 3000
})
app.use(router)
app.mount('#app')
