import { describe, expect, it } from 'vitest';
import { buildTourStops } from './ShopFloorTour';
import type { DeviceMeta, PlantLayout, PlantShell } from '../api/types';

function sampleShell(): PlantShell {
  return {
    name: '测试厂房',
    drawingNo: 'T-1',
    source: 'test',
    origin: 'test origin',
    wallHeight: 6,
    wallThickness: 0.25,
    partitionHeight: 4,
    envelope: { minX: 0, maxX: 20, minZ: -10, maxZ: 0 },
    axisGrid: { columnSize: 0.5, columnHeight: 6, axesX: [], axesZ: [] },
    partitions: [],
    zones: [],
  };
}

function device(partial: Partial<DeviceMeta> & Pick<DeviceMeta, 'deviceId' | 'name'>): DeviceMeta {
  return {
    type: 'mixer',
    line: '电极制造段',
    ipAddress: '10.0.0.1',
    port: 502,
    position: { x: 4, y: 0, z: -4, rotationY: 0 },
    points: [],
    ...partial,
  };
}

describe('buildTourStops', () => {
  it('keeps one camera stop per workshop and lists in-zone devices for sequential highlight', () => {
    const layout: PlantLayout = {
      plant: {
        ...sampleShell(),
        zones: [
          {
            name: '搅拌配料区',
            kind: 'clean',
            line: '电极制造段',
            bounds: { minX: 0, maxX: 10, minZ: -10, maxZ: -5 },
          },
          {
            name: '参观通道',
            kind: 'corridor',
            line: '',
            bounds: { minX: 0, maxX: 20, minZ: -5, maxZ: -3 },
          },
          {
            name: '空压机房',
            kind: 'utility',
            line: '公辅系统',
            bounds: { minX: 12, maxX: 20, minZ: -10, maxZ: -6 },
          },
        ],
      },
      deviceModels: {},
    };
    const devices = [
      device({ deviceId: 'MIX-01', name: '搅拌釜', position: { x: 3, y: 0, z: -7, rotationY: 0 } }),
      device({ deviceId: 'MIX-02', name: '二号釜', position: { x: 6, y: 0, z: -8, rotationY: 0 } }),
      device({ deviceId: 'AIR-01', name: '空压机', position: { x: 15, y: 0, z: -8, rotationY: 0 } }),
    ];

    const stops = buildTourStops(layout, devices);
    expect(stops.map((stop) => `${stop.kind}:${stop.title}`)).toEqual([
      'overview:测试厂房',
      'zone:搅拌配料区',
      'zone:空压机房',
    ]);
    expect(stops.some((stop) => stop.kind === 'zone' && stop.title === '参观通道')).toBe(false);
    expect(stops.find((stop) => stop.title === '搅拌配料区')?.deviceIds).toEqual(['MIX-01', 'MIX-02']);
    expect(stops.find((stop) => stop.title === '空压机房')?.deviceIds).toEqual(['AIR-01']);
  });
});
