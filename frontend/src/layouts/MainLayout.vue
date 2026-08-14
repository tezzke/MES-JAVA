<script setup lang="ts">
import { computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useAuthStore } from '../stores/auth';
import { useRealtimeStore } from '../stores/realtime';

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();
const realtime = useRealtimeStore();
const builtIn = [
  { path: '/', name: '生产总览', permissionCode: 'TELEMETRY_READ', sortOrder: 1 },
  { path: '/factory', name: '3D 车间', permissionCode: 'TELEMETRY_READ', sortOrder: 2 },
  { path: '/history', name: '历史曲线', permissionCode: 'TELEMETRY_READ', sortOrder: 3 },
  { path: '/alarms', name: 'SQLite 报警', permissionCode: 'ALARM_READ', sortOrder: 4 },
  { path: '/traceability', name: '实时扫码追溯', permissionCode: 'BARCODE_READ', sortOrder: 5 },
  { path: '/system/modbus-probe', name: 'Modbus 现场探针', permissionCode: 'MODBUS_PROBE', sortOrder: 6 },
];

const visibleMenus = computed(() => {
  const merged = [...builtIn, ...auth.menus.filter((item) => item.enabled)];
  return [...new Map(merged.map((item) => [item.path, item])).values()]
    .filter((item) => auth.has(item.permissionCode ?? undefined))
    .sort((a, b) => a.sortOrder - b.sortOrder);
});

async function logout() {
  await auth.logout();
  await router.replace('/login');
}
</script>

<template>
  <div class="layout">
    <aside class="sidebar">
      <div class="logo"><strong>MES</strong><span>生产管理系统</span></div>
      <el-menu router :default-active="route.path" class="nav-menu">
        <el-menu-item v-for="item in visibleMenus" :key="item.path" :index="item.path">
          {{ item.name }}
        </el-menu-item>
      </el-menu>
      <div class="sidebar-footer">
        <span class="conn-dot" :class="{ on: realtime.connected }"></span>
        {{ realtime.connected ? '实时通道已连接' : '实时通道断开' }}
      </div>
    </aside>
    <section class="main">
      <header class="topbar">
        <span class="page-title">{{ route.meta.title }}</span>
        <div class="account">
          <span>{{ auth.profile?.displayName }}</span>
          <span v-if="auth.canMonitor">激活报警 {{ realtime.activeAlarmList.length }} 条</span>
          <el-button link type="primary" @click="logout">退出</el-button>
        </div>
      </header>
      <main class="content"><router-view /></main>
    </section>
  </div>
</template>

<style scoped>
.layout{display:flex;height:100%}.sidebar{width:220px;flex-shrink:0;background:var(--bg-panel);border-right:1px solid var(--border);display:flex;flex-direction:column}
.logo{height:58px;padding:0 18px;display:flex;align-items:center;gap:10px;border-bottom:1px solid var(--border)}.logo strong{color:var(--accent);font-size:20px}.logo span{color:var(--text-sub)}
.nav-menu{flex:1;border-right:0;background:transparent;overflow:auto}.sidebar-footer{padding:14px 16px;color:var(--text-sub);border-top:1px solid var(--border);font-size:12px}
.conn-dot{display:inline-block;width:8px;height:8px;border-radius:50%;background:#ef4444;margin-right:8px}.conn-dot.on{background:#22c55e;box-shadow:0 0 6px #22c55e}
.main{flex:1;display:flex;flex-direction:column;min-width:0}.topbar{height:58px;display:flex;align-items:center;justify-content:space-between;padding:0 20px;background:var(--bg-panel);border-bottom:1px solid var(--border)}
.page-title{font-size:16px;font-weight:600}.account{display:flex;align-items:center;gap:18px;color:var(--text-sub)}.content{flex:1;min-height:0;overflow:hidden}
</style>
