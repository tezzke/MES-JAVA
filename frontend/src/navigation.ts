/** 侧栏导航：按制造业务分组，并统一成车间现场用语。 */

export interface NavItem {
  path: string;
  name: string;
  permissionCode?: string;
  sortOrder: number;
}

export interface NavGroup {
  id: string;
  label: string;
  items: NavItem[];
}

const MONITOR_ITEMS: NavItem[] = [
  { path: '/', name: '产线总览', permissionCode: 'TELEMETRY_READ', sortOrder: 1 },
  { path: '/factory', name: '数字车间', permissionCode: 'TELEMETRY_READ', sortOrder: 2 },
  { path: '/history', name: '过程趋势', permissionCode: 'TELEMETRY_READ', sortOrder: 3 },
  { path: '/alarms', name: '设备报警', permissionCode: 'ALARM_READ', sortOrder: 4 },
  { path: '/traceability', name: '条码追溯', permissionCode: 'BARCODE_READ', sortOrder: 5 },
];

const EXECUTION_ITEMS: NavItem[] = [
  { path: '/production/master', name: '工艺主数据', permissionCode: 'MASTER_READ', sortOrder: 40 },
  { path: '/production/work-orders', name: '生产工单', permissionCode: 'PRODUCTION_READ', sortOrder: 50 },
  { path: '/production/trace', name: '批次追溯', permissionCode: 'TRACE_READ', sortOrder: 60 },
  { path: '/alarm-actions', name: '异常处置', permissionCode: 'ALARM_ACTION_READ', sortOrder: 70 },
];

const SYSTEM_ITEMS: NavItem[] = [
  { path: '/system/modbus-probe', name: '现场通讯', permissionCode: 'MODBUS_PROBE', sortOrder: 6 },
  { path: '/system/users', name: '人员账号', permissionCode: 'USER_READ', sortOrder: 10 },
  { path: '/system/roles', name: '岗位权限', permissionCode: 'ROLE_READ', sortOrder: 20 },
  { path: '/system/menus', name: '功能导航', permissionCode: 'MENU_READ', sortOrder: 30 },
  { path: '/system/audits', name: '操作审计', permissionCode: 'AUDIT_READ', sortOrder: 40 },
];

const DISPLAY_NAMES = new Map(
  [...MONITOR_ITEMS, ...EXECUTION_ITEMS, ...SYSTEM_ITEMS].map((item) => [item.path, item.name]),
);

export const PAGE_COPY: Record<string, { title: string; subtitle: string }> = {
  '/': { title: '产线总览', subtitle: '按工段查看设备状态、产量与现场报警' },
  '/factory': { title: '数字车间', subtitle: '自动巡视各车间与设备，便于对外展示' },
  '/history': { title: '过程趋势', subtitle: '回看设备工艺参数随时间的变化' },
  '/alarms': { title: '设备报警', subtitle: '未复位报警与历史报警履历' },
  '/traceability': { title: '条码追溯', subtitle: '工位扫码流水与单件流转轨迹' },
  '/system/modbus-probe': { title: '现场通讯', subtitle: '诊断设备柜 Modbus 连通与寄存器读数' },
  '/system/users': { title: '人员账号', subtitle: '维护操作员账号，并分配岗位权限' },
  '/system/roles': { title: '岗位权限', subtitle: '按岗位配置可访问的车间功能' },
  '/system/menus': { title: '功能导航', subtitle: '维护侧栏菜单与对应权限码' },
  '/system/audits': { title: '操作审计', subtitle: '关键写操作与登录记录，便于事后追溯' },
  '/production/master': { title: '工艺主数据', subtitle: '物料、产品、工艺路线与工位档案' },
  '/production/work-orders': { title: '生产工单', subtitle: '下达工单、扫码开工、报工与完工' },
  '/production/trace': { title: '批次追溯', subtitle: '按批次或条码回看生产事件' },
  '/alarm-actions': { title: '异常处置', subtitle: '确认、指派并关闭现场报警' },
};

export const BUILT_IN_MENUS: NavItem[] = [...MONITOR_ITEMS, ...EXECUTION_ITEMS, ...SYSTEM_ITEMS];

export function displayName(path: string, fallback: string) {
  return DISPLAY_NAMES.get(path) ?? fallback;
}

export function groupMenus(items: NavItem[]): NavGroup[] {
  const byPath = new Map(items.map((item) => [item.path, item]));
  const known = new Set(BUILT_IN_MENUS.map((item) => item.path));
  const extra = items
    .filter((item) => !known.has(item.path))
    .sort((a, b) => a.sortOrder - b.sortOrder);
  return [
    { id: 'monitor', label: '生产监控', items: pick(byPath, MONITOR_ITEMS) },
    { id: 'execution', label: '生产执行', items: pick(byPath, EXECUTION_ITEMS) },
    { id: 'system', label: '系统管理', items: pick(byPath, SYSTEM_ITEMS) },
    { id: 'other', label: '其他', items: extra },
  ].filter((group) => group.items.length > 0);
}

function pick(byPath: Map<string, NavItem>, catalog: NavItem[]) {
  return catalog
    .map((item) => byPath.get(item.path))
    .filter((item): item is NavItem => item != null);
}
