import { http } from './http';
import type {
  AlarmRecord,
  BarcodeRecord,
  DeviceMeta,
  DeviceSnapshot,
  PagedResult,
  SystemInfo,
  TelemetryRecord,
} from './types';

/**
 * REST API 封装:所有 HTTP 请求集中在本文件,方便统一处理错误与调整路径。
 * baseURL 用相对路径:开发时经 Vite proxy 转发,生产时与后端同源。
 */
export const mesApi = {
  /** 获取设备档案列表(页面加载时调用一次) */
  getDevices: () => http.get<DeviceMeta[]>('/api/devices').then((r) => r.data),

  /** 获取全部设备最新快照(实时通道连上前的初始数据) */
  getSnapshots: () => http.get<DeviceSnapshot[]>('/api/devices/snapshots').then((r) => r.data),

  /** 查询历史曲线 */
  getHistory: (params: {
    deviceId: string;
    pointName: string;
    from?: string;
    to?: string;
    maxPoints?: number;
  }) => http.get<TelemetryRecord[]>('/api/telemetry/history', { params }).then((r) => r.data),

  /** 当前激活报警 */
  getActiveAlarms: () => http.get<AlarmRecord[]>('/api/alarms/active').then((r) => r.data),

  /** 历史报警分页查询 */
  queryAlarms: (params: {
    deviceId?: string;
    from?: string;
    to?: string;
    page?: number;
    pageSize?: number;
  }) => http.get<PagedResult<AlarmRecord>>('/api/alarms', { params }).then((r) => r.data),

  /** 扫码记录分页查询(条码追溯) */
  queryBarcodes: (params: {
    keyword?: string;
    deviceId?: string;
    from?: string;
    to?: string;
    page?: number;
    pageSize?: number;
  }) => http.get<PagedResult<BarcodeRecord>>('/api/barcodes', { params }).then((r) => r.data),

  /** 最近扫码记录(实时扫码列表的初始数据) */
  getRecentBarcodes: (limit = 20) =>
    http.get<BarcodeRecord[]>('/api/barcodes/recent', { params: { limit } }).then((r) => r.data),

  /** 系统信息 */
  getSystemInfo: () => http.get<SystemInfo>('/api/system/info').then((r) => r.data),
};
