<script setup lang="ts">
import { computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useAuthStore } from '../stores/auth';
import { useRealtimeStore } from '../stores/realtime';
import { BUILT_IN_MENUS, displayName, groupMenus, PAGE_COPY, type NavItem } from '../navigation';

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();
const realtime = useRealtimeStore();

const visibleMenus = computed(() => {
  const catalog: NavItem[] = [
    ...auth.menus
      .filter((item) => item.enabled)
      .map((item) => ({
        path: item.path,
        name: displayName(item.path, item.name),
        permissionCode: item.permissionCode ?? undefined,
        sortOrder: item.sortOrder,
      })),
    ...BUILT_IN_MENUS,
  ];
  return groupMenus(
    [...new Map(catalog.map((item) => [item.path, item])).values()].filter((item) =>
      auth.has(item.permissionCode),
    ),
  );
});

const pageCopy = computed(
  () => PAGE_COPY[route.path] ?? { title: String(route.meta.title ?? ''), subtitle: '' },
);

async function logout() {
  await auth.logout();
  await router.replace('/login');
}
</script>

<template>
  <div class="layout">
    <aside class="sidebar">
      <div class="brand">
        <div class="brand-mark">MES</div>
        <div class="brand-copy">
          <strong>制造执行系统</strong>
          <span>车间现场 · 工单 · 追溯</span>
        </div>
      </div>
      <nav class="nav">
        <section v-for="group in visibleMenus" :key="group.id" class="nav-group">
          <div class="nav-group-label">{{ group.label }}</div>
          <router-link
            v-for="item in group.items"
            :key="item.path"
            :to="item.path"
            class="nav-item"
            :class="{ active: route.path === item.path }"
          >
            {{ item.name }}
          </router-link>
        </section>
      </nav>
      <div class="sidebar-footer">
        <span class="conn-dot" :class="{ on: realtime.connected }"></span>
        <div>
          <div class="conn-label">{{ realtime.connected ? '现场数据接通' : '现场数据中断' }}</div>
          <div class="conn-hint">实时通道</div>
        </div>
      </div>
    </aside>
    <section class="main">
      <header class="topbar">
        <div>
          <div class="page-title">{{ pageCopy.title }}</div>
          <div v-if="pageCopy.subtitle" class="page-sub">{{ pageCopy.subtitle }}</div>
        </div>
        <div class="account">
          <span v-if="auth.canMonitor" class="alarm-chip" :class="{ hot: realtime.activeAlarmList.length }">
            未复位报警 {{ realtime.activeAlarmList.length }}
          </span>
          <span class="operator">{{ auth.profile?.displayName || auth.profile?.username }}</span>
          <el-button link type="primary" @click="logout">退出系统</el-button>
        </div>
      </header>
      <main class="content"><router-view /></main>
    </section>
  </div>
</template>

<style scoped>
.layout {
  display: flex;
  height: 100%;
  background: var(--bg-page);
}
.sidebar {
  width: 232px;
  flex-shrink: 0;
  background: var(--bg-sidebar);
  border-right: 1px solid var(--border);
  display: flex;
  flex-direction: column;
}
.brand {
  min-height: 72px;
  padding: 16px 16px 14px;
  display: flex;
  align-items: center;
  gap: 12px;
  border-bottom: 1px solid var(--border);
  background: #ffffff;
}
.brand-mark {
  width: 38px;
  height: 38px;
  border-radius: 6px;
  display: grid;
  place-items: center;
  background: #1c1d1f;
  color: #ffffff;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.06em;
}
.brand-copy {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.brand-copy strong {
  font-size: 14px;
}
.brand-copy span {
  color: var(--text-sub);
  font-size: 11px;
}
.nav {
  flex: 1;
  overflow: auto;
  padding: 12px 10px 16px;
}
.nav-group + .nav-group {
  margin-top: 16px;
}
.nav-group-label {
  padding: 4px 12px 8px;
  color: #9aa1ab;
  font-size: 11px;
  letter-spacing: 0.08em;
}
.nav-item {
  display: block;
  padding: 8px 12px;
  margin-bottom: 3px;
  color: #3a3d42;
  text-decoration: none;
  border-radius: 6px;
  border-left: 3px solid transparent;
}
.nav-item:hover {
  background: #eef0f3;
  color: var(--text-main);
}
.nav-item.active {
  background: #ffffff;
  color: var(--text-main);
  font-weight: 650;
  border-left-color: var(--accent);
  box-shadow: var(--shadow-panel);
}
.sidebar-footer {
  padding: 12px 16px 16px;
  color: var(--text-sub);
  border-top: 1px solid var(--border);
  background: #ffffff;
  display: flex;
  align-items: center;
  gap: 10px;
}
.conn-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #c4c8ce;
  flex-shrink: 0;
}
.conn-dot.on {
  background: var(--ok);
  box-shadow: 0 0 0 3px rgba(22, 163, 74, 0.16);
}
.conn-label {
  font-size: 12px;
  color: var(--text-main);
}
.conn-hint {
  font-size: 11px;
}
.main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.topbar {
  min-height: 64px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 22px;
  background: #ffffff;
  border-bottom: 1px solid var(--border);
}
.page-title {
  font-size: 18px;
  font-weight: 650;
}
.page-sub {
  margin-top: 2px;
  font-size: 12px;
  color: var(--text-sub);
}
.account {
  display: flex;
  align-items: center;
  gap: 16px;
  color: var(--text-sub);
}
.operator {
  color: var(--text-main);
}
.alarm-chip {
  padding: 4px 10px;
  border-radius: 99px;
  border: 1px solid var(--border);
  background: #f7f8fa;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}
.alarm-chip.hot {
  color: var(--danger);
  border-color: #fecaca;
  background: #fef2f2;
}
.content {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}
</style>
