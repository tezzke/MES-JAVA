import { describe, expect, it } from 'vitest';
import { displayName, groupMenus } from './navigation';

describe('navigation', () => {
  it('uses manufacturing labels for built-in pages', () => {
    expect(displayName('/alarms', 'SQLite 报警')).toBe('设备报警');
    expect(displayName('/system/users', '用户管理')).toBe('人员账号');
  });

  it('groups visible menus by shop-floor function', () => {
    const groups = groupMenus([
      { path: '/', name: '产线总览', permissionCode: 'TELEMETRY_READ', sortOrder: 1 },
      { path: '/production/work-orders', name: '生产工单', permissionCode: 'PRODUCTION_READ', sortOrder: 50 },
      { path: '/system/users', name: '人员账号', permissionCode: 'USER_READ', sortOrder: 10 },
    ]);
    expect(groups.map((group) => group.label)).toEqual(['生产监控', '生产执行', '系统管理']);
  });
});
