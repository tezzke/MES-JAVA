import { createApp } from 'vue';
import { createPinia } from 'pinia';
import ElementPlus from 'element-plus';
import zhCn from 'element-plus/es/locale/lang/zh-cn';
import 'element-plus/dist/index.css';
import App from './App.vue';
import router from './router';
import './styles/main.css';
import { onUnauthorized } from './api/http';
import { useAuthStore } from './stores/auth';

/**
 * 前端入口:装配 Pinia(状态)、Router(路由)、Element Plus(UI 组件)。
 */
const app = createApp(App);
app.use(createPinia());
app.use(router);
app.use(ElementPlus, { locale: zhCn });

onUnauthorized(() => {
  useAuthStore().clear();
  if (router.currentRoute.value.name !== 'login') {
    void router.replace({ name: 'login', query: { redirect: router.currentRoute.value.fullPath } });
  }
});

app.mount('#app');
