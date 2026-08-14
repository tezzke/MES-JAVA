import { createRouter, createWebHistory, type RouteLocationNormalized } from 'vue-router';
import { useAuthStore } from '../stores/auth';

/**
 * 前端路由:每个业务模块一个页面,模块之间完全独立,
 * 新增业务页面只需:src/views 加一个 .vue + 此处加一条路由。
 * 页面组件全部懒加载,首屏只加载当前模块。
 */
const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('../views/LoginView.vue'),
      meta: { title: '登录', public: true },
    },
    {
      path: '/403',
      name: 'forbidden',
      component: () => import('../views/ForbiddenView.vue'),
      meta: { title: '无权访问', public: true },
    },
    {
      path: '/',
      component: () => import('../layouts/MainLayout.vue'),
      children: [
        { path: '', name: 'dashboard', component: () => import('../views/DashboardView.vue'), meta: { title: '生产总览', permission: 'TELEMETRY_READ' } },
        { path: 'factory', name: 'factory', component: () => import('../views/Factory3DView.vue'), meta: { title: '3D 车间', permission: 'TELEMETRY_READ' } },
        { path: 'history', name: 'history', component: () => import('../views/HistoryView.vue'), meta: { title: '历史曲线', permission: 'TELEMETRY_READ' } },
        { path: 'alarms', name: 'alarms', component: () => import('../views/AlarmsView.vue'), meta: { title: 'SQLite 报警', permission: 'ALARM_READ' } },
        { path: 'traceability', name: 'traceability', component: () => import('../views/TraceabilityView.vue'), meta: { title: '实时扫码追溯', permission: 'BARCODE_READ' } },
        { path: 'system/modbus-probe', name: 'modbus-probe', component: () => import('../views/ModbusProbeView.vue'), meta: { title: 'Modbus 现场探针', permission: 'MODBUS_PROBE' } },
        { path: 'system/users', name: 'users', component: () => import('../views/system/UsersView.vue'), meta: { title: '用户管理', permission: 'USER_READ' } },
        { path: 'system/roles', name: 'roles', component: () => import('../views/system/RolesView.vue'), meta: { title: '角色管理', permission: 'ROLE_READ' } },
        { path: 'system/menus', name: 'menus', component: () => import('../views/system/MenusView.vue'), meta: { title: '菜单管理', permission: 'MENU_READ' } },
        { path: 'system/audits', name: 'audits', component: () => import('../views/system/AuditsView.vue'), meta: { title: '审计日志', permission: 'AUDIT_READ' } },
        { path: 'production/master', name: 'master', component: () => import('../views/production/MasterDataView.vue'), meta: { title: '生产主数据', permission: 'MASTER_READ' } },
        { path: 'production/work-orders', name: 'orders', component: () => import('../views/production/WorkOrdersView.vue'), meta: { title: '生产工单', permission: 'PRODUCTION_READ' } },
        { path: 'production/trace', name: 'production-trace', component: () => import('../views/production/ProductionTraceView.vue'), meta: { title: '生产追溯', permission: 'TRACE_READ' } },
        { path: 'alarm-actions', name: 'alarm-actions', component: () => import('../views/production/AlarmActionsView.vue'), meta: { title: '报警处置', permission: 'ALARM_ACTION_READ' } },
      ],
    },
    { path: '/work-orders', redirect: '/production/work-orders' },
    { path: '/master', redirect: '/production/master' },
    { path: '/modbus-probe', redirect: '/system/modbus-probe' },
    { path: '/production/orders', redirect: '/production/work-orders' },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
});

function requestedPath(to: RouteLocationNormalized) {
  return typeof to.query.redirect === 'string' ? to.query.redirect : '/';
}

router.beforeEach(async (to) => {
  const auth = useAuthStore();
  if (to.meta.public) {
    if (to.name === 'login' && (auth.authenticated || await auth.bootstrap())) return requestedPath(to);
    return true;
  }
  if (!(auth.authenticated || await auth.bootstrap())) {
    return { name: 'login', query: { redirect: to.fullPath } };
  }
  if (!auth.has(to.meta.permission as string | undefined)) return { name: 'forbidden' };
  return true;
});

export default router;
