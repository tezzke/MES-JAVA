/**
 * 与后端 DTO 一一对应的 TypeScript 类型定义。
 * 后端 Java 枚举按名称序列化(如 "Running"),因此这里都是字符串字面量联合类型。
 */

/** 设备状态(与后端 DeviceStatus 枚举一致) */
export type DeviceStatus = 'Offline' | 'Standby' | 'Running' | 'Alarm';

/** 数据质量(与后端 DataQuality 枚举一致) */
export type DataQuality = 'Good' | 'Uncertain' | 'Bad';

/** 报警等级(与后端 AlarmLevel 枚举一致) */
export type AlarmLevel = 'Info' | 'Warning' | 'Error';

/** 3D 场景坐标 */
export interface Position3D {
  x: number;
  y: number;
  z: number;
  rotationY: number;
}

/** 点位定义(设备档案的一部分) */
export interface PointMeta {
  name: string;
  displayName: string;
  unit: string;
  isStatus: boolean;
  isCounter: boolean;
  alarmHigh: number | null;
  alarmLow: number | null;
}

/** 设备档案(GET /api/devices) */
export interface DeviceMeta {
  deviceId: string;
  name: string;
  type: string;
  line: string;
  ipAddress: string;
  port: number;
  position: Position3D;
  points: PointMeta[];
}

// ==================== 厂房模型(GET /api/plant,对应后端 plant.json)====================

/** XZ 平面上的矩形范围 */
export interface Bounds {
  minX: number;
  maxX: number;
  minZ: number;
  maxZ: number;
}

/** 建筑轴线(label 为图纸轴号,如 "3-F") */
export interface AxisLine {
  label: string;
  value: number;
}

/** 轴网:柱子尺寸 + 两个方向的轴线 */
export interface AxisGrid {
  columnSize: number;
  columnHeight: number;
  axesX: AxisLine[];
  axesZ: AxisLine[];
}

/** 一段墙体。glazed 为 true 时按玻璃观察窗渲染 */
export interface WallSegment {
  name: string;
  x1: number;
  z1: number;
  x2: number;
  z2: number;
  thickness: number;
  height: number;
  glazed: boolean;
}

/** 功能分区。kind 决定地面配色:clean / corridor / utility / office */
export interface PlantZone {
  name: string;
  kind: string;
  bounds: Bounds;
  line: string;
}

/** 厂房壳体 */
export interface PlantShell {
  name: string;
  drawingNo: string;
  source: string;
  origin: string;
  wallHeight: number;
  wallThickness: number;
  partitionHeight: number;
  envelope: Bounds;
  axisGrid: AxisGrid;
  partitions: WallSegment[];
  zones: PlantZone[];
}

/** 设备模型的一个几何构件 */
export interface ModelPart {
  kind: string;
  name: string;
  x: number;
  y: number;
  z: number;
  length: number;
  height: number;
  width: number;
  radius: number;
  axis: string;
  color: string;
  opacity: number;
  rotationY: number;
}

/** 设备外观模型(按真实机身尺寸与协议书外观特征参数化) */
export interface DeviceModel {
  name: string;
  model: string;
  vendor: string;
  source: string;
  length: number;
  width: number;
  height: number;
  color: string;
  footprintLength: number;
  footprintWidth: number;
  parts: ModelPart[];
}

/** 厂房模型:壳体 + 设备外观模型库(key 为设备 Type) */
export interface PlantLayout {
  plant: PlantShell;
  deviceModels: Record<string, DeviceModel>;
}

/** 点位实时值 */
export interface PointValue {
  name: string;
  displayName: string;
  value: number;
  unit: string;
  quality: DataQuality;
}

/** 设备实时快照(实时通道 OnSnapshots / GET /api/devices/snapshots) */
export interface DeviceSnapshot {
  deviceId: string;
  deviceName: string;
  online: boolean;
  status: DeviceStatus;
  timestamp: string;
  points: PointValue[];
}

/** 报警记录 */
export interface AlarmRecord {
  id: number;
  deviceId: string;
  deviceName: string;
  pointName: string;
  level: AlarmLevel;
  message: string;
  value: number;
  triggeredAt: string;
  resolvedAt: string | null;
}

/** 扫码记录 */
export interface BarcodeRecord {
  id: number;
  scannerId: string;
  deviceId: string;
  barcode: string;
  scannedAt: string;
}

/** 遥测历史记录 */
export interface TelemetryRecord {
  id: number;
  deviceId: string;
  pointName: string;
  value: number;
  quality: DataQuality;
  timestamp: string;
}

/** 分页结果 */
export interface PagedResult<T> {
  total: number;
  items: T[];
}

/** 系统信息 */
export interface SystemInfo {
  mode: string;
  simulation: boolean;
  deviceCount: number;
  serverTime: string;
  version: string;
  /** 快照队列积压帧数 */
  queueSize: number;
  /** 累计丢帧数(不为 0 说明存储跟不上采集) */
  droppedFrames: number;
  /** 当前在线的前端客户端数 */
  realtimeClients: number;
}
