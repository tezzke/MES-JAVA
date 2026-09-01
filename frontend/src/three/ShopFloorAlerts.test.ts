import { beforeAll, describe, expect, it, vi } from 'vitest';
import * as THREE from 'three';
import type { AlarmRecord } from '../api/types';
import { ShopFloorAlerts } from './ShopFloorAlerts';

beforeAll(() => {
  vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue({
    fillStyle: '',
    strokeStyle: '',
    lineWidth: 0,
    font: '',
    textAlign: '',
    fillText: vi.fn(),
    fill: vi.fn(),
    stroke: vi.fn(),
    beginPath: vi.fn(),
    closePath: vi.fn(),
    moveTo: vi.fn(),
    lineTo: vi.fn(),
    roundRect: vi.fn(),
    clearRect: vi.fn(),
  } as unknown as CanvasRenderingContext2D);
});

function alarm(partial: Partial<AlarmRecord>): AlarmRecord {
  return {
    id: 1,
    deviceId: 'MIX-01',
    deviceName: '搅拌釜',
    pointName: '温度',
    level: 'Error',
    message: '超温',
    value: 90,
    triggeredAt: '2026-08-31T00:00:00Z',
    resolvedAt: null,
    ...partial,
  };
}

describe('ShopFloorAlerts', () => {
  it('shows a bubble once, then fades it away and does not pop it again', () => {
    const scene = new THREE.Scene();
    const alerts = new ShopFloorAlerts(scene);
    const anchors = new Map([
      ['MIX-01', { position: new THREE.Vector3(2, 0, -4), height: 2.2 }],
    ]);

    alerts.tick(1_000);
    alerts.sync([alarm({ id: 8, level: 'Warning', message: '接近上限' })], anchors);
    expect(scene.getObjectByName('alert-8')).toBeTruthy();

    alerts.tick(1_000 + 5_300);
    expect(scene.getObjectByName('alert-8')).toBeFalsy();

    alerts.sync([alarm({ id: 8, level: 'Warning', message: '接近上限' })], anchors);
    expect(scene.getObjectByName('alert-8')).toBeFalsy();

    alerts.sync([alarm({ id: 9, level: 'Error', message: '超温跳停' })], anchors);
    expect(scene.getObjectByName('alert-9')).toBeTruthy();
  });
});
