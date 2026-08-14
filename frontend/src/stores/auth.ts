import { computed, ref } from 'vue';
import { defineStore } from 'pinia';
import { authApi, systemApi, type Menu, type UserProfile } from '../api/management';
import { clearCsrfToken } from '../api/http';
import { useRealtimeStore } from './realtime';

const MONITOR_PERMISSIONS = ['TELEMETRY_READ', 'ALARM_READ', 'BARCODE_READ'];

export const useAuthStore = defineStore('auth', () => {
  const profile = ref<UserProfile | null>(null);
  const menus = ref<Menu[]>([]);
  const bootstrapped = ref(false);
  const authenticated = computed(() => profile.value != null);
  const permissions = computed(() => new Set(profile.value?.permissions ?? []));
  const canMonitor = computed(() => MONITOR_PERMISSIONS.some((permission) => permissions.value.has(permission)));

  function has(permission?: string | string[]) {
    if (!permission) return authenticated.value;
    const required = Array.isArray(permission) ? permission : [permission];
    return required.every((code) => permissions.value.has(code));
  }

  async function loadMenus() {
    menus.value = has('MENU_READ') ? await systemApi.menus() : [];
  }

  async function initializeRealtime() {
    if (canMonitor.value) await useRealtimeStore().init();
  }

  async function login(username: string, password: string) {
    profile.value = await authApi.login({ username, password });
    bootstrapped.value = true;
    await Promise.all([loadMenus(), initializeRealtime()]);
  }

  async function me() {
    profile.value = await authApi.me();
    return profile.value;
  }

  async function bootstrap() {
    if (bootstrapped.value) return authenticated.value;
    try {
      await me();
      await Promise.all([loadMenus(), initializeRealtime()]);
    } catch {
      profile.value = null;
    } finally {
      bootstrapped.value = true;
    }
    return authenticated.value;
  }

  function clear() {
    useRealtimeStore().dispose();
    profile.value = null;
    menus.value = [];
    bootstrapped.value = true;
    clearCsrfToken();
  }

  async function logout() {
    try {
      await authApi.logout();
    } finally {
      clear();
    }
  }

  return { profile, menus, bootstrapped, authenticated, permissions, canMonitor, has, login, logout, me, bootstrap, clear };
});
