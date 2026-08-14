import { http } from './http';
import type { PagedResult } from './types';

export const MASTER_TYPES = [
  'MATERIAL', 'PRODUCT', 'BOM', 'BOM_ITEM', 'WORKSHOP', 'LINE', 'STATION',
  'EQUIPMENT_BINDING', 'OPERATION', 'ROUTE', 'ROUTE_STEP',
] as const;
export type MasterType = (typeof MASTER_TYPES)[number];

export interface MasterItem {
  id: number;
  type: MasterType;
  code: string;
  name: string;
  referenceId: number | null;
  secondaryReferenceId: number | null;
  sequence: number | null;
  quantity: number | null;
  attributes: Record<string, string>;
  enabled: boolean;
  version: number;
}

export type WorkOrderStatus = 'DRAFT' | 'RELEASED' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED';
export interface WorkOrder {
  id: number;
  orderNo: string;
  productId: number;
  routeId: number;
  batchNo: string;
  plannedQuantity: number;
  goodQuantity: number;
  badQuantity: number;
  status: WorkOrderStatus;
  version: number;
  createdAt: string;
}

export interface ProductionTask {
  id: number;
  workOrderId: number;
  routeStepId: number;
  operationId: number;
  stationId: number | null;
  sequence: number;
  plannedQuantity: number;
  goodQuantity: number;
  badQuantity: number;
  status: 'READY' | 'IN_PROGRESS' | 'COMPLETED';
  version: number;
}

export interface WorkOrderProgress {
  workOrderId: number;
  status: WorkOrderStatus;
  plannedQuantity: number;
  goodQuantity: number;
  badQuantity: number;
  completedTasks: number;
  totalTasks: number;
  completionPercent: number;
}

export interface TraceEvent {
  id: number;
  workOrderId: number;
  taskId: number | null;
  batchNo: string;
  barcode: string;
  eventType: string;
  goodQuantity: number | null;
  badQuantity: number | null;
  occurredAt: string;
}

export interface AlarmAction {
  id: number;
  sourceAlarmId: number;
  status: 'NEW' | 'ACKNOWLEDGED' | 'ASSIGNED' | 'RESOLVED' | 'CLOSED';
  assignee: string | null;
  resolution: string | null;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export const masterApi = {
  list: (type: MasterType, page: number, size: number) =>
    http.get<PagedResult<MasterItem>>(`/api/master/${type}`, { params: { page, size } }).then((r) => r.data),
  detail: (id: number) => http.get<MasterItem>(`/api/master/detail/${id}`).then((r) => r.data),
  save: (type: MasterType, id: number | null, body: Omit<MasterItem, 'id' | 'type'>) =>
    id == null ? http.post(`/api/master/${type}`, body) : http.put(`/api/master/${type}/${id}`, body),
};

export const productionApi = {
  orders: (page: number, size: number) =>
    http.get<PagedResult<WorkOrder>>('/api/production/work-orders', { params: { page, size } }).then((r) => r.data),
  order: (id: number) => http.get<WorkOrder>(`/api/production/work-orders/${id}`).then((r) => r.data),
  saveOrder: (id: number | null, body: Pick<WorkOrder, 'orderNo' | 'productId' | 'routeId' | 'batchNo' | 'plannedQuantity' | 'version'>) =>
    id == null ? http.post('/api/production/work-orders', body) : http.put(`/api/production/work-orders/${id}`, body),
  deleteOrder: (id: number, version: number) => http.delete(`/api/production/work-orders/${id}`, { params: { version } }),
  releaseOrder: (id: number, version: number) => http.post(`/api/production/work-orders/${id}/release`, { version }),
  tasks: (id: number) => http.get<ProductionTask[]>(`/api/production/work-orders/${id}/tasks`).then((r) => r.data),
  progress: (id: number) => http.get<WorkOrderProgress>(`/api/production/work-orders/${id}/progress`).then((r) => r.data),
  startTask: (id: number, version: number, barcode: string) =>
    http.post(`/api/production/tasks/${id}/start`, { version, barcode }, { headers: { 'Idempotency-Key': crypto.randomUUID() } }),
  reportTask: (id: number, body: { taskVersion: number; orderVersion: number; good: number; bad: number; barcode: string }) =>
    http.post(`/api/production/tasks/${id}/report`, body, { headers: { 'Idempotency-Key': crypto.randomUUID() } }),
  completeTask: (id: number, version: number, barcode: string) =>
    http.post(`/api/production/tasks/${id}/complete`, { version, barcode }, { headers: { 'Idempotency-Key': crypto.randomUUID() } }),
  trace: (params: { batchNo?: string; barcode?: string; page: number; size: number }) =>
    http.get<PagedResult<TraceEvent>>('/api/production/trace', { params }).then((r) => r.data),
};

export const alarmActionApi = {
  list: (page: number, size: number) =>
    http.get<PagedResult<AlarmAction>>('/api/alarm-actions', { params: { page, size } }).then((r) => r.data),
  acknowledge: (sourceAlarmId: number) => http.post(`/api/alarm-actions/source/${sourceAlarmId}/acknowledge`),
  assign: (id: number, version: number, assignee: string) =>
    http.post(`/api/alarm-actions/${id}/assign`, { version, assignee }),
  resolve: (id: number, version: number, resolution: string) =>
    http.post(`/api/alarm-actions/${id}/resolve`, { version, resolution }),
  close: (id: number, version: number) => http.post(`/api/alarm-actions/${id}/close`, { version }),
};
