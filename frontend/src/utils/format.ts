import type { DeviceStatus } from '../api/types';

/**
 * 展示层通用工具:状态文案/颜色映射、时间格式化。
 * 所有页面共用一份映射,保证全站配色语义一致。
 */

/** 设备状态 → 中文文案 */
export const statusText: Record<DeviceStatus, string> = {
  Running: '运行',
  Standby: '待机',
  Alarm: '报警',
  Offline: '离线',
};

/** 设备状态 → 主题色(与 3D 场景、状态标签共用) */
export const statusColor: Record<DeviceStatus, string> = {
  Running: '#16a34a',
  Standby: '#d97706',
  Alarm: '#dc2626',
  Offline: '#6b7280',
};

/** UTC ISO 字符串 → 本地时间 "MM-dd HH:mm:ss" */
export function formatTime(iso: string | null | undefined): string {
  if (!iso) return '-';
  const date = new Date(iso);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(
    date.getMinutes(),
  )}:${pad(date.getSeconds())}`;
}

/** 数值展示:整数不带小数,小数保留 2 位(避免浮点尾数刷屏) */
export function formatValue(value: number | null | undefined): string {
  if (value == null || Number.isNaN(value)) return '-';
  return Number.isInteger(value) ? String(value) : value.toFixed(2);
}

/** Date → 后端可解析的 ISO 字符串 */
export function toIso(date: Date): string {
  return date.toISOString();
}
