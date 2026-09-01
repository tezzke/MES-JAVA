import { PlantBuilder } from './PlantBuilder';
import type { Bounds, DeviceMeta, PlantLayout, PlantZone } from '../api/types';

export type TourKind = 'overview' | 'zone';

export interface TourStop {
  kind: TourKind;
  title: string;
  subtitle: string;
  deviceIds: string[];
  target: { x: number; y: number; z: number };
  position: { x: number; y: number; z: number };
  dwellMs: number;
  flyMs: number;
}

export interface TourState {
  playing: boolean;
  title: string;
  subtitle: string;
  deviceIds: string[];
}

const WORKSHOP_KINDS = new Set(['clean', 'utility']);

/** 一台设备停留多久再切到同区下一台。镜头始终停在区域,不跟到单机。 */
export const DEVICE_HOLD_MS = 4800;
export const EMPTY_ZONE_HOLD_MS = 2800;

/** 对外展示巡视:镜头按区域切换;区内仍按台轮播详情与高亮。 */
export function buildTourStops(layout: PlantLayout, devices: DeviceMeta[]): TourStop[] {
  const framing = PlantBuilder.cameraFraming(layout.plant.envelope);
  const stops: TourStop[] = [
    {
      kind: 'overview',
      title: layout.plant.name,
      subtitle: '全厂总览',
      deviceIds: [],
      target: { x: framing.target.x, y: framing.target.y, z: framing.target.z },
      position: { x: framing.position.x, y: framing.position.y, z: framing.position.z },
      dwellMs: 3800,
      flyMs: 2600,
    },
  ];

  const remaining = new Set(devices.map((device) => device.deviceId));
  const workshops = layout.plant.zones.filter((zone) => WORKSHOP_KINDS.has(zone.kind));

  for (const zone of workshops) {
    const inZone = devices
      .filter((device) => contains(zone.bounds, device.position.x, device.position.z))
      .sort((a, b) => a.position.x - b.position.x || a.position.z - b.position.z);
    inZone.forEach((device) => remaining.delete(device.deviceId));
    stops.push(zoneStop(zone, inZone.map((device) => device.deviceId)));
  }

  const leftover = devices.filter((device) => remaining.has(device.deviceId));
  if (leftover.length) {
    stops.push(devicesFrameStop('其他工位', leftover));
  }

  return stops;
}

export function easeInOutCubic(t: number): number {
  return t < 0.5 ? 4 * t * t * t : 1 - (-2 * t + 2) ** 3 / 2;
}

function zoneStop(zone: PlantZone, deviceIds: string[]): TourStop {
  const { bounds } = zone;
  const cx = (bounds.minX + bounds.maxX) / 2;
  const cz = (bounds.minZ + bounds.maxZ) / 2;
  const span = Math.max(bounds.maxX - bounds.minX, bounds.maxZ - bounds.minZ, 8);
  return {
    kind: 'zone',
    title: zone.name,
    subtitle: zone.line ? `${zone.line} · ${deviceIds.length} 台设备` : `${deviceIds.length} 台设备`,
    deviceIds,
    target: { x: cx, y: 0.4, z: cz },
    position: {
      x: cx + span * 0.52,
      y: Math.max(8.5, span * 0.7),
      z: cz + span * 0.68,
    },
    dwellMs: deviceIds.length ? DEVICE_HOLD_MS : EMPTY_ZONE_HOLD_MS,
    flyMs: 2400,
  };
}

function devicesFrameStop(title: string, devices: DeviceMeta[]): TourStop {
  const xs = devices.map((device) => device.position.x);
  const zs = devices.map((device) => device.position.z);
  const minX = Math.min(...xs);
  const maxX = Math.max(...xs);
  const minZ = Math.min(...zs);
  const maxZ = Math.max(...zs);
  const cx = (minX + maxX) / 2;
  const cz = (minZ + maxZ) / 2;
  const span = Math.max(maxX - minX, maxZ - minZ, 8);
  return {
    kind: 'zone',
    title,
    subtitle: `${devices.length} 台设备`,
    deviceIds: devices.map((device) => device.deviceId),
    target: { x: cx, y: 0.4, z: cz },
    position: {
      x: cx + span * 0.52,
      y: Math.max(8.5, span * 0.7),
      z: cz + span * 0.68,
    },
    dwellMs: DEVICE_HOLD_MS,
    flyMs: 2400,
  };
}

function contains(bounds: Bounds, x: number, z: number): boolean {
  return x >= bounds.minX && x <= bounds.maxX && z >= bounds.minZ && z <= bounds.maxZ;
}
