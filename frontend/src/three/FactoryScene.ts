import * as THREE from 'three';
import { OrbitControls } from 'three/addons/controls/OrbitControls.js';
import type { DeviceMeta, DeviceSnapshot, DeviceStatus } from '../api/types';

/**
 * 3D 车间场景(Three.js 封装类)。
 *
 * 职责边界:本类只负责"渲染",不碰任何业务/网络逻辑 ——
 *   - 设备布局来自后端设备档案(devices.json 的 Position 字段),布局调整无需改前端;
 *   - 实时状态由外部(Factory3DView)调用 updateSnapshots() 灌入;
 *   - 点击设备通过 onSelect 回调抛出,由页面决定弹详情抽屉。
 *
 * 场景构成:
 *   地面网格 + 环境光/方向光 + 每台设备一个 Group(机体 + 状态灯 + 名称标牌 + 动件),
 *   运行中的设备动件旋转,报警设备红灯闪烁,选中设备脚下显示高亮圈。
 */

/** 设备状态 → 状态灯颜色(与 utils/format.ts 的 statusColor 一致) */
const STATUS_COLORS: Record<DeviceStatus, number> = {
  Running: 0x22c55e,
  Standby: 0xf59e0b,
  Alarm: 0xef4444,
  Offline: 0x64748b,
};

/** 不同设备类型的机体尺寸与配色(宽, 高, 深, 颜色) */
const TYPE_STYLES: Record<string, { size: [number, number, number]; color: number }> = {
  loader: { size: [2.6, 2.2, 2.0], color: 0x3b5b8c },
  cnc: { size: [3.2, 2.6, 2.4], color: 0x44618f },
  washer: { size: [3.4, 2.0, 2.2], color: 0x3a7a8c },
  assembly: { size: [2.8, 1.8, 2.0], color: 0x5b6d8f },
  tester: { size: [2.4, 2.4, 2.0], color: 0x6a5b8f },
  laser: { size: [2.4, 2.0, 2.0], color: 0x8f5b6d },
  aging: { size: [2.8, 3.0, 2.4], color: 0x707a5b },
  packer: { size: [3.6, 2.2, 2.4], color: 0x5b8f7a },
  robot: { size: [2.0, 3.2, 2.0], color: 0x8f7a44 },
  default: { size: [2.6, 2.2, 2.2], color: 0x44618f },
};

/** 单台设备在场景中的对象集合 */
interface DeviceNode {
  group: THREE.Group;
  lamp: THREE.Mesh<THREE.SphereGeometry, THREE.MeshStandardMaterial>;
  rotor: THREE.Mesh; // 运行动件(顶部旋转体)
  status: DeviceStatus;
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
  private rafId = 0;
  private disposed = false;

  constructor(
    private readonly container: HTMLElement,
    devices: DeviceMeta[],
    private readonly onSelect: (deviceId: string | null) => void,
  ) {
    // ---- 渲染器 ----
    this.renderer = new THREE.WebGLRenderer({ antialias: true });
    this.renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
    this.renderer.setSize(container.clientWidth, container.clientHeight);
    this.renderer.shadowMap.enabled = true;
    container.appendChild(this.renderer.domElement);

    // ---- 场景基础 ----
    this.scene.background = new THREE.Color(0x0b1220);
    this.scene.fog = new THREE.Fog(0x0b1220, 60, 140);

    this.camera = new THREE.PerspectiveCamera(
      50,
      container.clientWidth / container.clientHeight,
      0.1,
      500,
    );
    this.camera.position.set(0, 26, 34);

    this.controls = new OrbitControls(this.camera, this.renderer.domElement);
    this.controls.enableDamping = true;
    this.controls.maxPolarAngle = Math.PI / 2.15; // 不允许看到地面以下
    this.controls.minDistance = 8;
    this.controls.maxDistance = 90;

    this.buildLights();
    this.buildFloor();
    devices.forEach((device) => this.buildDevice(device));

    // 选中高亮圈(默认隐藏)
    this.selectRing = new THREE.Mesh(
      new THREE.RingGeometry(2.2, 2.6, 48),
      new THREE.MeshBasicMaterial({
        color: 0x38bdf8,
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

    this.animate();
  }

  // ==================== 对外接口 ====================

  /** 灌入实时快照:更新状态灯颜色与动画状态 */
  updateSnapshots(snapshots: Record<string, DeviceSnapshot>): void {
    for (const [deviceId, node] of this.nodes) {
      const status = snapshots[deviceId]?.status ?? 'Offline';
      if (status === node.status) continue;
      node.status = status;
      const color = STATUS_COLORS[status];
      node.lamp.material.color.setHex(color);
      node.lamp.material.emissive.setHex(color);
    }
  }

  /** 外部设置选中设备(高亮圈跟随),null 取消选中 */
  setSelected(deviceId: string | null): void {
    const node = deviceId ? this.nodes.get(deviceId) : undefined;
    if (node) {
      this.selectRing.position.set(node.group.position.x, 0.03, node.group.position.z);
      this.selectRing.visible = true;
    } else {
      this.selectRing.visible = false;
    }
  }

  /** 销毁场景:停止渲染循环、释放 GPU 资源(离开页面时必须调用,防内存泄漏) */
  dispose(): void {
    this.disposed = true;
    cancelAnimationFrame(this.rafId);
    this.resizeObserver.disconnect();
    this.renderer.domElement.removeEventListener('click', this.handleClick);
    this.scene.traverse((obj) => {
      if (obj instanceof THREE.Mesh) {
        obj.geometry.dispose();
        const materials = Array.isArray(obj.material) ? obj.material : [obj.material];
        materials.forEach((m) => m.dispose());
      }
    });
    this.controls.dispose();
    this.renderer.dispose();
    this.renderer.domElement.remove();
  }

  // ==================== 场景搭建 ====================

  private buildLights(): void {
    this.scene.add(new THREE.HemisphereLight(0x8899bb, 0x223344, 0.9));
    const sun = new THREE.DirectionalLight(0xffffff, 1.4);
    sun.position.set(20, 40, 20);
    sun.castShadow = true;
    sun.shadow.camera.left = -40;
    sun.shadow.camera.right = 40;
    sun.shadow.camera.top = 40;
    sun.shadow.camera.bottom = -40;
    sun.shadow.mapSize.set(2048, 2048);
    this.scene.add(sun);
  }

  private buildFloor(): void {
    // 地板
    const floor = new THREE.Mesh(
      new THREE.PlaneGeometry(120, 90),
      new THREE.MeshStandardMaterial({ color: 0x101a2e, roughness: 0.95 }),
    );
    floor.rotation.x = -Math.PI / 2;
    floor.receiveShadow = true;
    this.scene.add(floor);

    // 网格线(车间地面刻度感)
    const grid = new THREE.GridHelper(120, 60, 0x24304d, 0x18223a);
    (grid.material as THREE.Material).transparent = true;
    (grid.material as THREE.Material).opacity = 0.6;
    grid.position.y = 0.01;
    this.scene.add(grid);
  }

  /** 搭建一台设备:机体 + 状态灯 + 动件 + 名称标牌 */
  private buildDevice(device: DeviceMeta): void {
    const style = TYPE_STYLES[device.type] ?? TYPE_STYLES.default;
    const [width, height, depth] = style.size;

    const group = new THREE.Group();
    group.position.set(device.position.x, 0, device.position.z);
    group.rotation.y = THREE.MathUtils.degToRad(device.position.rotationY);
    group.userData.deviceId = device.deviceId; // 拾取时反查设备

    // 机体(底座 + 主体两段,略有层次感)
    const base = new THREE.Mesh(
      new THREE.BoxGeometry(width + 0.4, 0.3, depth + 0.4),
      new THREE.MeshStandardMaterial({ color: 0x1c2740, roughness: 0.8 }),
    );
    base.position.y = 0.15;
    base.castShadow = base.receiveShadow = true;
    group.add(base);

    const body = new THREE.Mesh(
      new THREE.BoxGeometry(width, height, depth),
      new THREE.MeshStandardMaterial({ color: style.color, roughness: 0.55, metalness: 0.35 }),
    );
    body.position.y = 0.3 + height / 2;
    body.castShadow = body.receiveShadow = true;
    group.add(body);

    // 顶部动件:运行时旋转,直观区分"在动的设备"
    const rotor = new THREE.Mesh(
      new THREE.CylinderGeometry(0.28, 0.28, 0.5, 20),
      new THREE.MeshStandardMaterial({ color: 0xb8c4dd, roughness: 0.3, metalness: 0.7 }),
    );
    rotor.position.y = 0.3 + height + 0.25;
    rotor.castShadow = true;
    group.add(rotor);

    // 状态指示灯(自发光小球,颜色 = 设备状态)
    const lamp = new THREE.Mesh(
      new THREE.SphereGeometry(0.22, 16, 16),
      new THREE.MeshStandardMaterial({
        color: STATUS_COLORS.Offline,
        emissive: STATUS_COLORS.Offline,
        emissiveIntensity: 1.6,
      }),
    );
    lamp.position.set(width / 2 - 0.3, 0.3 + height + 0.6, 0);
    group.add(lamp);

    // 名称标牌(Canvas 贴图 Sprite,始终面向相机)
    const label = this.buildLabel(device.name);
    label.position.y = 0.3 + height + 1.5;
    group.add(label);

    this.scene.add(group);
    this.nodes.set(device.deviceId, {
      group,
      lamp: lamp as DeviceNode['lamp'],
      rotor,
      status: 'Offline',
    });
  }

  /** 用 Canvas 画名称文字,生成始终朝向相机的 Sprite 标牌 */
  private buildLabel(text: string): THREE.Sprite {
    const canvas = document.createElement('canvas');
    canvas.width = 512;
    canvas.height = 96;
    const ctx = canvas.getContext('2d')!;
    ctx.fillStyle = 'rgba(13, 22, 40, 0.75)';
    ctx.roundRect(60, 12, 392, 72, 14);
    ctx.fill();
    ctx.font = 'bold 40px "Microsoft YaHei", sans-serif';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillStyle = '#dbe6ff';
    ctx.fillText(text, 256, 50);

    const texture = new THREE.CanvasTexture(canvas);
    texture.colorSpace = THREE.SRGBColorSpace;
    const sprite = new THREE.Sprite(
      new THREE.SpriteMaterial({ map: texture, transparent: true, depthWrite: false }),
    );
    sprite.scale.set(5.4, 1.0, 1);
    return sprite;
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

    this.onSelect((target?.userData.deviceId as string) ?? null);
  };

  private handleResize(): void {
    const { clientWidth, clientHeight } = this.container;
    if (!clientWidth || !clientHeight) return;
    this.camera.aspect = clientWidth / clientHeight;
    this.camera.updateProjectionMatrix();
    this.renderer.setSize(clientWidth, clientHeight);
  }

  /** 渲染循环:动件旋转、报警灯闪烁、选中圈脉动 */
  private animate = (): void => {
    if (this.disposed) return;
    this.rafId = requestAnimationFrame(this.animate);

    const elapsed = this.clock.getElapsedTime();
    const delta = this.clock.getDelta() + 0.016;

    for (const node of this.nodes.values()) {
      if (node.status === 'Running') {
        node.rotor.rotation.y += delta * 4; // 运行:动件旋转
      }
      if (node.status === 'Alarm') {
        // 报警:红灯闪烁
        node.lamp.material.emissiveIntensity = 1 + Math.abs(Math.sin(elapsed * 6)) * 2;
      } else {
        node.lamp.material.emissiveIntensity = 1.6;
      }
    }

    if (this.selectRing.visible) {
      const scale = 1 + Math.sin(elapsed * 3) * 0.06; // 选中圈呼吸效果
      this.selectRing.scale.set(scale, scale, 1);
    }

    this.controls.update();
    this.renderer.render(this.scene, this.camera);
  };
}
