import * as THREE from 'three';
import { OrbitControls } from 'three/addons/controls/OrbitControls.js';
import type { AlarmRecord, DeviceMeta, DeviceSnapshot, DeviceStatus, PlantLayout } from '../api/types';
import { DeviceModelFactory } from './DeviceModelFactory';
import { PlantBuilder } from './PlantBuilder';
import { ShopFloorAlerts } from './ShopFloorAlerts';
import {
  buildTourStops,
  DEVICE_HOLD_MS,
  EMPTY_ZONE_HOLD_MS,
  easeInOutCubic,
  type TourState,
  type TourStop,
} from './ShopFloorTour';

/**
 * 3D 车间场景(Three.js 封装类)。
 *
 * 职责边界:本类只做"编排" —— 把厂房壳体、设备模型、光照与交互组装成一个可运行的场景。
 * 具体几何交给两个专职类:
 *   - PlantBuilder        厂房壳体(来自图纸,plant.json 的 Plant 节点)
 *   - DeviceModelFactory  设备外观(来自技术协议书,plant.json 的 DeviceModels 节点)
 * 数据流:
 *   - 设备定位来自设备档案(devices.json 的 Position),调整布局无需改前端;
 *   - 实时状态由外部(Factory3DView)调用 updateSnapshots() 灌入;
 *   - 点击设备通过 onSelect 回调抛出,由页面决定弹详情抽屉。
 *
 * 运行时表现:运行中的设备动件旋转、状态灯按状态着色、报警/警告透明气泡、
 * 选中设备脚下高亮圈呼吸、对外展示时镜头自动巡视各车间与设备。
 */

/** 设备状态 → 状态灯颜色(与 utils/format.ts 的 statusColor 一致) */
const STATUS_COLORS: Record<DeviceStatus, number> = {
  Running: 0x16a34a,
  Standby: 0xd97706,
  Alarm: 0xdc2626,
  Offline: 0x6b7280,
};

/** 单台设备在场景中的对象集合 */
interface DeviceNode {
  group: THREE.Group;
  /** 三色灯灯罩,可能多个(大设备两端各一只),也可能没有 */
  lamps: THREE.Mesh<THREE.BufferGeometry, THREE.MeshStandardMaterial>[];
  /** 运行动件:辊、主轴、风机等,运行时旋转 */
  rotors: THREE.Object3D[];
  status: DeviceStatus;
  alertLevel: 'none' | 'Warning' | 'Error';
  /** 机身总高,用于摆放选中高亮圈与名称标牌 */
  height: number;
  label: THREE.Sprite;
  glow: THREE.Group;
  glowMats: THREE.MeshBasicMaterial[];
  radars: THREE.Mesh<THREE.RingGeometry, THREE.MeshBasicMaterial>[];
  beam: THREE.Mesh<THREE.CylinderGeometry, THREE.MeshBasicMaterial>;
  scan: THREE.Mesh<THREE.RingGeometry, THREE.MeshBasicMaterial>;
  emphasisBorn: number;
  footRx: number;
  footRz: number;
}

export class FactoryScene {
  private readonly renderer: THREE.WebGLRenderer;
  private readonly scene = new THREE.Scene();
  private readonly camera: THREE.PerspectiveCamera;
  private readonly controls: OrbitControls;
  private readonly raycaster = new THREE.Raycaster();
  private readonly nodes = new Map<string, DeviceNode>();
  private readonly selectRing: THREE.Mesh;
  private readonly clock = new THREE.Clock();
  private readonly resizeObserver: ResizeObserver;
  private readonly alerts: ShopFloorAlerts;
  private readonly homePosition = new THREE.Vector3();
  private readonly homeTarget = new THREE.Vector3();
  private readonly tourStops: TourStop[] = [];
  private readonly deviceNames = new Map<string, string>();
  private tourIndex = 0;
  private deviceCursor = 0;
  private touring = true;
  /** 复位后才解锁:巡视中只显示当前设备名,复位后全部显示 */
  private namesUnlocked = false;
  private dwellUntil = 0;
  private motion: {
    fromPos: THREE.Vector3;
    toPos: THREE.Vector3;
    fromTarget: THREE.Vector3;
    toTarget: THREE.Vector3;
    start: number;
    duration: number;
    onDone?: () => void;
  } | null = null;
  private rafId = 0;
  private disposed = false;

  constructor(
    private readonly container: HTMLElement,
    devices: DeviceMeta[],
    layout: PlantLayout,
    private readonly onSelect: (deviceId: string | null) => void,
    private readonly onTourChange?: (state: TourState) => void,
  ) {
    // ---- 渲染器 ----
    this.renderer = new THREE.WebGLRenderer({ antialias: true });
    this.renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
    this.renderer.setSize(container.clientWidth, container.clientHeight);
    this.renderer.shadowMap.enabled = true;
    container.appendChild(this.renderer.domElement);

    // ---- 场景基础 ----
    this.scene.background = new THREE.Color(0xf4f5f7);

    const framing = PlantBuilder.cameraFraming(layout.plant.envelope);
    this.camera = new THREE.PerspectiveCamera(
      50,
      container.clientWidth / container.clientHeight,
      0.1,
      600,
    );
    this.camera.position.copy(framing.position);

    // 雾的起止跟着厂房尺寸走,换厂房不必重调
    const span = Math.max(
      layout.plant.envelope.maxX - layout.plant.envelope.minX,
      layout.plant.envelope.maxZ - layout.plant.envelope.minZ,
    );
    this.scene.fog = new THREE.Fog(0xf4f5f7, span * 1.15, span * 2.6);

    this.controls = new OrbitControls(this.camera, this.renderer.domElement);
    this.controls.enableDamping = true;
    this.controls.maxPolarAngle = Math.PI / 2.15; // 不允许看到地面以下
    this.controls.minDistance = 4;
    this.controls.maxDistance = framing.maxDistance;
    this.controls.target.copy(framing.target);
    this.homePosition.copy(framing.position);
    this.homeTarget.copy(framing.target);

    this.buildLights(layout);
    this.scene.add(PlantBuilder.build(layout.plant));
    devices.forEach((device) => this.buildDevice(device, layout));
    this.alerts = new ShopFloorAlerts(this.scene);

    // 选中高亮圈(默认隐藏)
    this.selectRing = new THREE.Mesh(
      new THREE.RingGeometry(1.1, 1.35, 48),
      new THREE.MeshBasicMaterial({
        color: 0x2563eb,
        side: THREE.DoubleSide,
        transparent: true,
        opacity: 0.9,
      }),
    );
    this.selectRing.rotation.x = -Math.PI / 2;
    this.selectRing.visible = false;
    this.scene.add(this.selectRing);

    // 交互与自适应
    this.renderer.domElement.addEventListener('click', this.handleClick);
    this.resizeObserver = new ResizeObserver(() => this.handleResize());
    this.resizeObserver.observe(container);

    devices.forEach((device) => this.deviceNames.set(device.deviceId, device.name));
    this.tourStops.push(...buildTourStops(layout, devices));
    this.controls.enabled = false;
    this.beginStop(0);
    this.animate();
  }

  // ==================== 对外接口 ====================

  /** 灌入实时快照:更新状态灯颜色与动画状态 */
  updateSnapshots(snapshots: Record<string, DeviceSnapshot>): void {
    for (const [deviceId, node] of this.nodes) {
      const status = snapshots[deviceId]?.status ?? 'Offline';
      if (status === node.status) continue;
      node.status = status;
      this.applyLampColor(node);
    }
  }

  /** 灌入未复位报警/警告,在对应设备上方做动态标牌与光柱 */
  updateAlarms(alarms: AlarmRecord[]): void {
    const anchors = new Map<string, { position: THREE.Vector3; height: number }>();
    for (const [deviceId, node] of this.nodes) {
      anchors.set(deviceId, { position: node.group.position, height: node.height });
      node.alertLevel = 'none';
    }
    for (const alarm of alarms) {
      if (alarm.resolvedAt || (alarm.level !== 'Warning' && alarm.level !== 'Error')) continue;
      const node = this.nodes.get(alarm.deviceId);
      if (!node) continue;
      if (alarm.level === 'Error' || node.alertLevel !== 'Error') {
        node.alertLevel = alarm.level;
      }
    }
    for (const node of this.nodes.values()) this.applyLampColor(node);
    this.alerts.sync(alarms, anchors);
  }

  /** 点选单台设备时高亮。巡视请用 setFeatured。 */
  setSelected(deviceId: string | null): void {
    this.setFeatured(deviceId ? [deviceId] : []);
  }

  /** 区域内设备一并亮名、亮脚下提示;复位后名称全开。 */
  setFeatured(deviceIds: string[]): void {
    this.selectRing.visible = false;
    const featured = new Set(deviceIds);
    for (const [id, node] of this.nodes) {
      const on = featured.has(id);
      node.label.visible = this.namesUnlocked || on;
      node.glow.visible = on;
      if (on) {
        node.emphasisBorn = performance.now();
        this.colorGlow(node);
      }
    }
  }

  /** 把镜头飞向指定设备(设备列表联动 3D 场景时用) */
  focusDevice(deviceId: string): void {
    const node = this.nodes.get(deviceId);
    if (!node) return;
    this.pauseTour();
    const { x, z } = node.group.position;
    this.flyTo(
      new THREE.Vector3(x + 8, node.height + 7, z + 8),
      new THREE.Vector3(x, node.height / 2, z),
      1600,
    );
  }

  startTour(): void {
    if (!this.tourStops.length) return;
    this.touring = true;
    this.namesUnlocked = false;
    this.controls.enabled = false;
    this.beginStop(this.tourIndex);
  }

  pauseTour(): void {
    this.touring = false;
    this.controls.enabled = true;
    this.emitTourState();
  }

  /** 一键回到全厂总览,并停下自动巡视。 */
  resetView(): void {
    this.touring = false;
    this.tourIndex = 0;
    this.deviceCursor = 0;
    this.namesUnlocked = true;
    this.controls.enabled = true;
    this.setFeatured([]);
    this.onSelect(null);
    this.flyTo(this.homePosition, this.homeTarget, 1800);
    this.emitTourState({
      playing: false,
      title: this.tourStops[0]?.title ?? '数字车间',
      subtitle: '已回到全厂总览',
      deviceIds: [],
    });
  }

  isTouring(): boolean {
    return this.touring;
  }

  /** 销毁场景:停止渲染循环、释放 GPU 资源(离开页面时必须调用,防内存泄漏) */
  dispose(): void {
    this.disposed = true;
    cancelAnimationFrame(this.rafId);
    this.resizeObserver.disconnect();
    this.renderer.domElement.removeEventListener('click', this.handleClick);
    this.scene.traverse((obj) => {
      if (obj instanceof THREE.Mesh || obj instanceof THREE.LineSegments) {
        obj.geometry.dispose();
        const materials = Array.isArray(obj.material) ? obj.material : [obj.material];
        materials.forEach((m) => {
          // Canvas 贴图(标牌文字)不释放会一直占着显存
          const texture = (m as THREE.MeshBasicMaterial).map;
          texture?.dispose();
          m.dispose();
        });
      }
      if (obj instanceof THREE.Sprite) {
        obj.material.map?.dispose();
        obj.material.dispose();
      }
    });
    this.alerts.dispose();
    this.nodes.clear();
    this.controls.dispose();
    this.renderer.dispose();
    this.renderer.domElement.remove();
  }

  // ==================== 场景搭建 ====================

  /** 光照:半球光打底 + 一盏跟随厂房范围的平行光投影 */
  private buildLights(layout: PlantLayout): void {
    this.scene.add(new THREE.HemisphereLight(0xf8fafc, 0xc4c9d1, 0.62));

    const { envelope } = layout.plant;
    const cx = (envelope.minX + envelope.maxX) / 2;
    const cz = (envelope.minZ + envelope.maxZ) / 2;
    const span = Math.max(envelope.maxX - envelope.minX, envelope.maxZ - envelope.minZ);

    const fill = new THREE.DirectionalLight(0xfff7ed, 0.38);
    fill.position.set(cx - span * 0.35, span * 0.45, cz - span * 0.2);
    fill.target.position.set(cx, 0.8, cz);
    this.scene.add(fill, fill.target);

    const sun = new THREE.DirectionalLight(0xffffff, 1.7);
    sun.position.set(cx + span * 0.4, span * 0.8, cz + span * 0.4);
    sun.target.position.set(cx, 0, cz);
    sun.castShadow = true;
    // 阴影相机范围必须覆盖整个厂房,否则远端设备没有投影
    const half = span * 0.62;
    sun.shadow.camera.left = -half;
    sun.shadow.camera.right = half;
    sun.shadow.camera.top = half;
    sun.shadow.camera.bottom = -half;
    sun.shadow.camera.far = span * 2.5;
    sun.shadow.mapSize.set(2048, 2048);
    this.scene.add(sun, sun.target);
  }

  /** 搭建一台设备:参数化外观 + 名称标牌 */
  private buildDevice(device: DeviceMeta, layout: PlantLayout): void {
    const built = DeviceModelFactory.build(device.type, layout.deviceModels[device.type]);
    const group = built.group;
    group.position.set(device.position.x, device.position.y, device.position.z);
    group.rotation.y = THREE.MathUtils.degToRad(device.position.rotationY);
    group.userData.deviceId = device.deviceId; // 拾取时反查设备

    // 名称标牌悬在机身上方,Sprite 始终面向相机
    const label = this.buildLabel(device.name);
    label.name = 'device-label';
    label.position.y = built.height + 1.15;
    label.visible = false;
    group.add(label);

    const spec = layout.deviceModels[device.type];
    const emphasis = this.buildEmphasis(
      spec?.footprintLength || spec?.length || 2.4,
      spec?.footprintWidth || spec?.width || 1.8,
      built.height,
    );
    group.add(emphasis.group);

    this.scene.add(group);
    this.nodes.set(device.deviceId, {
      group,
      lamps: built.statusLamps,
      rotors: built.rotors,
      status: 'Offline',
      alertLevel: 'none',
      height: built.height,
      label,
      glow: emphasis.group,
      glowMats: emphasis.materials,
      radars: emphasis.radars,
      beam: emphasis.beam,
      scan: emphasis.scan,
      emphasisBorn: 0,
      footRx: emphasis.rx,
      footRz: emphasis.rz,
    });
  }

  /** 用 Canvas 画设备名,无描边无底框,只留文字。 */
  private buildLabel(text: string): THREE.Sprite {
    const canvas = document.createElement('canvas');
    canvas.width = 1024;
    canvas.height = 144;
    const ctx = canvas.getContext('2d')!;
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    ctx.font = '600 68px "Microsoft YaHei", sans-serif';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillStyle = '#1c1d1f';
    ctx.fillText(text, 512, 72);

    const texture = new THREE.CanvasTexture(canvas);
    texture.colorSpace = THREE.SRGBColorSpace;
    const sprite = new THREE.Sprite(
      new THREE.SpriteMaterial({ map: texture, transparent: true, depthWrite: false }),
    );
    sprite.scale.set(6.0, 0.84, 1);
    return sprite;
  }

  /** 检视强调:底盘光斑 + 扩散雷达环 + 光柱 + 上行扫描圈。 */
  private buildEmphasis(
    length: number,
    width: number,
    height: number,
  ): {
    group: THREE.Group;
    materials: THREE.MeshBasicMaterial[];
    radars: THREE.Mesh<THREE.RingGeometry, THREE.MeshBasicMaterial>[];
    beam: THREE.Mesh<THREE.CylinderGeometry, THREE.MeshBasicMaterial>;
    scan: THREE.Mesh<THREE.RingGeometry, THREE.MeshBasicMaterial>;
    rx: number;
    rz: number;
  } {
    const group = new THREE.Group();
    group.name = 'device-emphasis';
    group.visible = false;
    const rx = Math.max(length, 1.4) * 0.62;
    const rz = Math.max(width, 1.2) * 0.62;
    const materials: THREE.MeshBasicMaterial[] = [];

    const tint = (opacity: number, additive = false) => {
      const material = new THREE.MeshBasicMaterial({
        color: 0x2563eb,
        transparent: true,
        opacity,
        depthWrite: false,
        side: THREE.DoubleSide,
        blending: additive ? THREE.AdditiveBlending : THREE.NormalBlending,
      });
      materials.push(material);
      return material;
    };

    const disc = new THREE.Mesh(new THREE.CircleGeometry(1, 48), tint(0.42, true));
    disc.name = 'emphasis-disc';
    disc.rotation.x = -Math.PI / 2;
    disc.position.y = 0.05;
    disc.scale.set(rx, rz, 1);

    const rim = new THREE.Mesh(new THREE.RingGeometry(0.86, 1.02, 48), tint(0.95));
    rim.rotation.x = -Math.PI / 2;
    rim.position.y = 0.058;
    rim.scale.set(rx, rz, 1);

    const radars = [0, 1].map((index) => {
      const radar = new THREE.Mesh(new THREE.RingGeometry(0.9, 1.04, 56), tint(0.8, true));
      radar.name = `emphasis-radar-${index}`;
      radar.rotation.x = -Math.PI / 2;
      radar.position.y = 0.07;
      radar.scale.set(rx, rz, 1);
      return radar;
    });

    const beamH = height + 1.4;
    const beam = new THREE.Mesh(
      new THREE.CylinderGeometry(Math.min(rx, rz) * 0.22, Math.min(rx, rz) * 0.38, beamH, 24, 1, true),
      tint(0.22, true),
    );
    beam.name = 'emphasis-beam';
    beam.position.y = beamH / 2;

    const scan = new THREE.Mesh(new THREE.RingGeometry(0.55, 1, 48), tint(0.9, true));
    scan.name = 'emphasis-scan';
    scan.rotation.x = -Math.PI / 2;
    scan.position.y = 0.2;
    scan.scale.set(rx * 0.85, rz * 0.85, 1);

    group.add(disc, rim, ...radars, beam, scan);
    return { group, materials, radars, beam, scan, rx, rz };
  }

  // ==================== 交互与渲染循环 ====================

  /** 点击拾取:命中设备则回调其编码,点空白处取消选中 */
  private handleClick = (event: MouseEvent): void => {
    const rect = this.renderer.domElement.getBoundingClientRect();
    const pointer = new THREE.Vector2(
      ((event.clientX - rect.left) / rect.width) * 2 - 1,
      -((event.clientY - rect.top) / rect.height) * 2 + 1,
    );
    this.raycaster.setFromCamera(pointer, this.camera);

    const groups = [...this.nodes.values()].map((n) => n.group);
    const hits = this.raycaster.intersectObjects(groups, true);

    // 沿父链向上找到携带 deviceId 的设备 Group
    let target: THREE.Object3D | null = hits[0]?.object ?? null;
    while (target && !target.userData.deviceId) target = target.parent;

    const deviceId = (target?.userData.deviceId as string) ?? null;
    if (deviceId && this.touring) this.pauseTour();
    this.onSelect(deviceId);
  };

  private handleResize(): void {
    const { clientWidth, clientHeight } = this.container;
    if (!clientWidth || !clientHeight) return;
    this.camera.aspect = clientWidth / clientHeight;
    this.camera.updateProjectionMatrix();
    this.renderer.setSize(clientWidth, clientHeight);
  }

  private applyLampColor(node: DeviceNode): void {
    const color =
      node.alertLevel === 'Error' || node.status === 'Alarm'
        ? STATUS_COLORS.Alarm
        : node.alertLevel === 'Warning'
          ? STATUS_COLORS.Standby
          : STATUS_COLORS[node.status];
    node.lamps.forEach((lamp) => {
      lamp.material.color.setHex(color);
      lamp.material.emissive.setHex(color);
    });
    this.colorGlow(node, color);
  }

  private colorGlow(node: DeviceNode, hex?: number): void {
    const color =
      hex ??
      (node.alertLevel === 'Error' || node.status === 'Alarm'
        ? STATUS_COLORS.Alarm
        : node.alertLevel === 'Warning'
          ? STATUS_COLORS.Standby
          : STATUS_COLORS[node.status]);
    node.glowMats.forEach((material) => material.color.setHex(color));
  }

  /** 渲染循环:动件旋转、报警灯闪烁、巡视运镜、选中圈脉动 */
  private animate = (): void => {
    if (this.disposed) return;
    this.rafId = requestAnimationFrame(this.animate);

    const elapsed = this.clock.getElapsedTime();
    const delta = Math.min(this.clock.getDelta(), 0.05);

    for (const node of this.nodes.values()) {
      if (node.status === 'Running') {
        node.rotors.forEach((rotor) => {
          // 非竖直动件被包了一层 Group,自转要作用在内层构件上
          const spin = (rotor.userData.spin as THREE.Object3D | undefined) ?? rotor;
          spin.rotation.y += delta * 4;
        });
      }
      const flashing = node.status === 'Alarm' || node.alertLevel !== 'none';
      const pace = node.alertLevel === 'Warning' && node.status !== 'Alarm' ? 4 : 6;
      const intensity = flashing ? 1 + Math.abs(Math.sin(elapsed * pace)) * 2 : 1.6;
      node.lamps.forEach((lamp) => {
        lamp.material.emissiveIntensity = intensity;
      });
    }

    this.alerts.tick(performance.now());
    this.tickTour(performance.now());

    const now = performance.now();
    for (const node of this.nodes.values()) {
      if (!node.glow.visible) continue;
      const age = (now - node.emphasisBorn) / 1000;
      const beat = 0.55 + Math.abs(Math.sin(age * 6.2)) * 0.45;
      node.glowMats[0].opacity = 0.28 + beat * 0.38;
      node.glowMats[1].opacity = 0.7 + beat * 0.28;
      node.beam.material.opacity = 0.14 + beat * 0.28;
      const pop = 1 + Math.sin(age * 6.2) * 0.08;
      node.glow.scale.set(pop, 1, pop);
      node.label.scale.set(6.0 * (1 + beat * 0.06), 0.84 * (1 + beat * 0.06), 1);

      node.radars.forEach((radar, index) => {
        const t = (age * 0.72 + index * 0.5) % 1;
        const grow = 1 + t * 2.15;
        radar.scale.set(node.footRx * grow, node.footRz * grow, 1);
        radar.material.opacity = (1 - t) * (1 - t) * 0.95;
      });

      const scanT = (age * 0.55) % 1;
      node.scan.position.y = 0.15 + scanT * (node.height + 1.05);
      node.scan.scale.set(
        node.footRx * (0.7 + scanT * 0.35),
        node.footRz * (0.7 + scanT * 0.35),
        1,
      );
      node.scan.material.opacity = 0.95 * (1 - Math.abs(scanT - 0.45) * 1.4);
      node.lamps.forEach((lamp) => {
        lamp.material.emissiveIntensity = 1.4 + beat * 2.2;
      });
    }

    this.controls.update();
    this.renderer.render(this.scene, this.camera);
  };

  private beginStop(index: number): void {
    const stop = this.tourStops[index];
    if (!stop) return;
    this.tourIndex = index;
    this.deviceCursor = 0;
    this.setFeatured([]);
    this.onSelect(null);
    this.emitTourState({
      playing: this.touring,
      title: stop.title,
      subtitle: stop.subtitle,
      deviceIds: [],
    });
    this.flyTo(
      new THREE.Vector3(stop.position.x, stop.position.y, stop.position.z),
      new THREE.Vector3(stop.target.x, stop.target.y, stop.target.z),
      stop.flyMs,
      () => {
        if (!this.touring) return;
        this.presentZoneDevice(performance.now());
      },
    );
  }

  private flyTo(
    position: THREE.Vector3,
    target: THREE.Vector3,
    duration: number,
    onDone?: () => void,
  ): void {
    this.motion = {
      fromPos: this.camera.position.clone(),
      toPos: position.clone(),
      fromTarget: this.controls.target.clone(),
      toTarget: target.clone(),
      start: performance.now(),
      duration,
      onDone,
    };
  }

  private tickTour(now: number): void {
    if (this.motion) {
      const t = Math.min(1, (now - this.motion.start) / this.motion.duration);
      const eased = easeInOutCubic(t);
      this.camera.position.lerpVectors(this.motion.fromPos, this.motion.toPos, eased);
      this.controls.target.lerpVectors(this.motion.fromTarget, this.motion.toTarget, eased);
      if (t >= 1) {
        const done = this.motion.onDone;
        this.motion = null;
        done?.();
      }
      return;
    }

    if (!this.touring || now < this.dwellUntil) return;
    const stop = this.tourStops[this.tourIndex];
    if (stop && this.deviceCursor + 1 < stop.deviceIds.length) {
      this.deviceCursor += 1;
      this.presentZoneDevice(now);
      return;
    }
    const next = (this.tourIndex + 1) % this.tourStops.length;
    this.beginStop(next);
  }

  /** 镜头不动,只切换区内当前强调的设备与右侧详情。 */
  private presentZoneDevice(now: number): void {
    const stop = this.tourStops[this.tourIndex];
    if (!stop) return;
    const deviceId = stop.deviceIds[this.deviceCursor];
    if (!deviceId) {
      this.setFeatured([]);
      this.dwellUntil = now + EMPTY_ZONE_HOLD_MS;
      this.emitTourState({
        playing: true,
        title: stop.title,
        subtitle: stop.subtitle,
        deviceIds: [],
      });
      return;
    }
    this.setFeatured([deviceId]);
    this.dwellUntil = now + DEVICE_HOLD_MS;
    const indexLabel = `${this.deviceCursor + 1}/${stop.deviceIds.length}`;
    this.emitTourState({
      playing: true,
      title: stop.title,
      subtitle: `${this.deviceNames.get(deviceId) ?? deviceId} · ${indexLabel}`,
      deviceIds: [deviceId],
    });
  }

  private currentFeaturedIds(): string[] {
    if (!this.touring) return [];
    const id = this.tourStops[this.tourIndex]?.deviceIds[this.deviceCursor];
    return id ? [id] : [];
  }

  private emitTourState(state?: TourState): void {
    const stop = this.tourStops[this.tourIndex];
    this.onTourChange?.(
      state ?? {
        playing: this.touring,
        title: stop?.title ?? '',
        subtitle: stop?.subtitle ?? '',
        deviceIds: this.currentFeaturedIds(),
      },
    );
  }
}
