import { http } from './http';
import type { PagedResult } from './types';

export interface UserProfile {
  id: number;
  username: string;
  displayName: string;
  enabled: boolean;
  roles: string[];
  permissions: string[];
}

export interface Role {
  id: number;
  code: string;
  name: string;
  permissions: string[];
}

export interface Menu {
  id: number;
  parentId: number | null;
  name: string;
  path: string;
  permissionCode: string | null;
  sortOrder: number;
  enabled: boolean;
}

export interface AuditEvent {
  id: number;
  actor: string;
  action: string;
  targetType: string;
  targetId: string | null;
  result: string;
  correlationId: string;
  clientIp: string;
  occurredAt: string;
}

export const authApi = {
  login: (body: { username: string; password: string }) =>
    http.post<UserProfile>('/api/auth/login', body).then((r) => r.data),
  logout: () => http.post<void>('/api/auth/logout'),
  me: () => http.get<UserProfile>('/api/auth/me').then((r) => r.data),
};

export const systemApi = {
  users: (page: number, size: number) =>
    http.get<PagedResult<UserProfile>>('/api/system-management/users', { params: { page, size } }).then((r) => r.data),
  saveUser: (id: number | null, body: { username: string; displayName: string; password: string; enabled: boolean }) =>
    id == null
      ? http.post('/api/system-management/users', body)
      : http.put(`/api/system-management/users/${id}`, body),
  deleteUser: (id: number) => http.delete(`/api/system-management/users/${id}`),
  assignUserRoles: (id: number, roleIds: number[]) =>
    http.put(`/api/system-management/users/${id}/roles`, roleIds),
  roles: () => http.get<Role[]>('/api/system-management/roles').then((r) => r.data),
  permissions: () => http.get<string[]>('/api/system-management/permissions').then((r) => r.data),
  saveRole: (id: number | null, body: { code: string; name: string }) =>
    id == null
      ? http.post('/api/system-management/roles', body)
      : http.put(`/api/system-management/roles/${id}`, body),
  deleteRole: (id: number) => http.delete(`/api/system-management/roles/${id}`),
  assignRolePermissions: (id: number, permissions: string[]) =>
    http.put(`/api/system-management/roles/${id}/permissions`, permissions),
  menus: () => http.get<Menu[]>('/api/system-management/menus').then((r) => r.data),
  saveMenu: (id: number | null, body: Omit<Menu, 'id'>) =>
    id == null
      ? http.post('/api/system-management/menus', body)
      : http.put(`/api/system-management/menus/${id}`, body),
  deleteMenu: (id: number) => http.delete(`/api/system-management/menus/${id}`),
  audits: (page: number, size: number) =>
    http.get<PagedResult<AuditEvent>>('/api/system-management/audits', { params: { page, size } }).then((r) => r.data),
};
