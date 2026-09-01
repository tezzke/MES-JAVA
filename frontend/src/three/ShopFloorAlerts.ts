import * as THREE from 'three';
import type { AlarmRecord } from '../api/types';

const HOLD_MS = 4000;
const FADE_MS = 1200;
const LEVEL_RGB: Record<'Warning' | 'Error', [number, number, number]> = {
  Warning: [217, 119, 6],
  Error: [220, 38, 38],
};

interface AlertBubble {
  id: number;
  deviceId: string;
  group: THREE.Group;
  sprite: THREE.Sprite;
  orb: THREE.Mesh<THREE.SphereGeometry, THREE.MeshBasicMaterial>;
  glow: THREE.Mesh<THREE.CircleGeometry, THREE.MeshBasicMaterial>;
  bornAt: number;
}

/**
 * 设备头顶的透明气泡：新报警/警告弹出后停留数秒，再淡出消失。
 */
export class ShopFloorAlerts {
  private readonly bubbles = new Map<number, AlertBubble>();
  private readonly seenIds = new Set<number>();
  private now = 0;

  constructor(private readonly scene: THREE.Scene) {}

  sync(
    alarms: AlarmRecord[],
    anchors: Map<string, { position: THREE.Vector3; height: number }>,
  ): void {
    for (const alarm of alarms) {
      if (alarm.resolvedAt || (alarm.level !== 'Warning' && alarm.level !== 'Error')) continue;
      if (this.seenIds.has(alarm.id) || this.bubbles.has(alarm.id)) continue;
      const anchor = anchors.get(alarm.deviceId);
      if (!anchor) continue;
      const level = alarm.level === 'Error' ? 'Error' : 'Warning';
      this.bubbles.set(alarm.id, this.create(alarm, level, anchor));
      this.seenIds.add(alarm.id);
    }
  }

  tick(nowMs: number): void {
    this.now = nowMs;
    for (const bubble of [...this.bubbles.values()]) {
      const age = nowMs - bubble.bornAt;
      if (age >= HOLD_MS + FADE_MS) {
        this.remove(bubble.id);
        continue;
      }
      const fade = age <= HOLD_MS ? 1 : 1 - (age - HOLD_MS) / FADE_MS;
      const pulse = 0.72 + Math.abs(Math.sin(nowMs / 200)) * 0.28;
      bubble.sprite.material.opacity = fade;
      bubble.orb.material.opacity = 0.72 * fade * pulse;
      bubble.glow.material.opacity = 0.42 * fade * pulse;
      const glowScale = 1.15 + pulse * 0.55;
      bubble.glow.scale.set(glowScale, glowScale, 1);
      bubble.sprite.position.y = bubble.sprite.userData.baseY + Math.sin(nowMs / 380) * 0.08;
    }
  }

  dispose(): void {
    for (const id of [...this.bubbles.keys()]) this.remove(id);
    this.seenIds.clear();
  }

  private create(
    alarm: AlarmRecord,
    level: 'Warning' | 'Error',
    anchor: { position: THREE.Vector3; height: number },
  ): AlertBubble {
    const [r, g, b] = LEVEL_RGB[level];
    const color = new THREE.Color(r / 255, g / 255, b / 255);
    const group = new THREE.Group();
    group.name = `alert-${alarm.id}`;
    group.position.copy(anchor.position);

    const glow = new THREE.Mesh(
      new THREE.CircleGeometry(0.7, 32),
      new THREE.MeshBasicMaterial({
        color,
        transparent: true,
        opacity: 0.42,
        depthWrite: false,
        side: THREE.DoubleSide,
      }),
    );
    glow.position.y = anchor.height + 1.2;
    glow.rotation.x = -Math.PI / 2;
    group.add(glow);

    const orb = new THREE.Mesh(
      new THREE.SphereGeometry(0.28, 20, 16),
      new THREE.MeshBasicMaterial({
        color,
        transparent: true,
        opacity: 0.72,
        depthWrite: false,
      }),
    );
    orb.position.y = anchor.height + 1.2;
    group.add(orb);

    const sprite = buildBubbleSprite(alarm, level);
    sprite.position.y = anchor.height + 2.75;
    sprite.userData.baseY = sprite.position.y;
    group.add(sprite);

    this.scene.add(group);
    return {
      id: alarm.id,
      deviceId: alarm.deviceId,
      group,
      sprite,
      orb,
      glow,
      bornAt: this.now || performance.now(),
    };
  }

  private remove(id: number): void {
    const bubble = this.bubbles.get(id);
    if (!bubble) return;
    this.scene.remove(bubble.group);
    bubble.sprite.material.map?.dispose();
    bubble.sprite.material.dispose();
    bubble.orb.geometry.dispose();
    bubble.orb.material.dispose();
    bubble.glow.geometry.dispose();
    bubble.glow.material.dispose();
    this.bubbles.delete(id);
  }
}

function buildBubbleSprite(alarm: AlarmRecord, level: 'Warning' | 'Error'): THREE.Sprite {
  const canvas = document.createElement('canvas');
  canvas.width = 1440;
  canvas.height = 440;
  const ctx = canvas.getContext('2d')!;
  const [r, g, b] = LEVEL_RGB[level];

  ctx.clearRect(0, 0, canvas.width, canvas.height);
  const box = { x: 36, y: 28, w: 1368, h: 312 };
  ctx.beginPath();
  ctx.roundRect(box.x, box.y, box.w, box.h, 36);
  ctx.moveTo(664, 340);
  ctx.lineTo(720, 408);
  ctx.lineTo(776, 340);
  ctx.closePath();
  ctx.fillStyle = `rgba(${r}, ${g}, ${b}, 0.78)`;
  ctx.fill();

  ctx.fillStyle = '#ffffff';
  ctx.font = 'bold 80px "Microsoft YaHei", sans-serif';
  ctx.textAlign = 'left';
  ctx.fillText(level === 'Error' ? '报警' : '警告', 88, 128);
  ctx.font = 'bold 60px "Microsoft YaHei", sans-serif';
  ctx.fillText(`${alarm.deviceName} · ${truncate(alarm.message, 16)}`, 88, 220);
  ctx.font = '48px "Microsoft YaHei", sans-serif';
  ctx.fillStyle = 'rgba(255, 255, 255, 0.82)';
  ctx.fillText(alarm.pointName, 88, 292);

  const texture = new THREE.CanvasTexture(canvas);
  texture.colorSpace = THREE.SRGBColorSpace;
  const sprite = new THREE.Sprite(
    new THREE.SpriteMaterial({ map: texture, transparent: true, depthWrite: false, opacity: 1 }),
  );
  sprite.scale.set(9.2, 2.8, 1);
  return sprite;
}

function truncate(text: string, max: number): string {
  return text.length > max ? `${text.slice(0, max)}…` : text;
}
