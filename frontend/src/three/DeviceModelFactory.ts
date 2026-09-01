import * as THREE from 'three';
import type { DeviceModel, ModelPart } from '../api/types';

/**
 * 设备外观工厂:按 plant.json 的参数化描述搭建设备模型。
 *
 * 设计意图 —— 设备外观必须"改配置就能改模型"。
 * 现场 25 台设备的外观来自各自的技术协议书,尺寸与特征会随现场复测不断修正;
 * 若把几何写死在代码里,每次修正都要改前端并重新构建。因此把外观抽象成一组构件描述
 * (ModelPart 的 kind/尺寸/位置/颜色),前端只做"描述 → 几何"的翻译。
 *
 * 构件坐标系:以设备占位框中心为原点,X 沿机身长度、Z 沿机身宽度、Y 为构件中心离地高度。
 * 因此设备整体旋转(Position.RotationY)时构件自动跟随,不必为每个朝向写一份配置。
 *
 * 支持的 kind:
 *   box      长方体钣金件(默认)
 *   cylinder 圆柱(罐体/辊/管件),axis 指定轴向 x|y|z
 *   glass    半透明玻璃罩/亚克力封板/观察窗
 *   screen   触摸屏/显示器(深色自发光面板)
 *   tower    三色状态灯(顶段颜色由实时状态驱动,见 statusLamps)
 *   rotor    运行动件(设备运行时旋转,见 rotors)
 *   frame    型材框架(只画 12 根棱,用于看得见内部的机架)
 *   pipe     管路(细圆柱)
 *   duct     风管/烟囱(带法兰的粗圆柱)
 */

/** 无外观配置时的兜底体块尺寸,保证新增设备类型也能出现在场景里 */
const FALLBACK: DeviceModel = {
  name: '',
  model: '',
  vendor: '',
  source: '',
  length: 2,
  width: 2,
  height: 2,
  color: '#8896ad',
  footprintLength: 0,
  footprintWidth: 0,
  parts: [],
};

/** 构建结果:除了几何本体,还要把需要参与动画的对象交回调用方 */
export interface BuiltDevice {
  group: THREE.Group;
  /** 运行时旋转的动件(kind = rotor) */
  rotors: THREE.Object3D[];
  /** 状态灯顶段(kind = tower),颜色随设备状态变化 */
  statusLamps: THREE.Mesh<THREE.BufferGeometry, THREE.MeshStandardMaterial>[];
  /** 机身总高,供调用方摆放名称标牌 */
  height: number;
}

export class DeviceModelFactory {
  /**
   * 按设备类型搭建外观。
   *
   * @param model 外观描述,undefined 时退化为通用体块(并打印告警便于发现漏配的类型)
   */
  static build(type: string, model: DeviceModel | undefined): BuiltDevice {
    const spec = model ?? FALLBACK;
    if (!model) {
      console.warn(`[3D] 设备类型 "${type}" 在 plant.json 的 DeviceModels 中没有外观配置,已用通用体块代替`);
    }

    const group = new THREE.Group();
    const rotors: THREE.Object3D[] = [];
    const statusLamps: BuiltDevice['statusLamps'] = [];

    group.add(this.buildFootprint(spec));

    if (spec.parts.length === 0) {
      group.add(this.buildFallbackBody(spec));
    } else {
      spec.parts.forEach((part) => {
        const object = this.buildPart(part, spec);
        if (!object) return;
        if (part.kind === 'rotor') rotors.push(object);
        if (part.kind === 'tower') {
          const lamp = object.userData.lamp as BuiltDevice['statusLamps'][number] | undefined;
          if (lamp) statusLamps.push(lamp);
        }
        group.add(object);
      });
    }

    return { group, rotors, statusLamps, height: spec.height };
  }

  // ==================== 构件翻译 ====================

  private static buildPart(part: ModelPart, spec: DeviceModel): THREE.Object3D | null {
    switch (part.kind) {
      case 'cylinder':
      case 'pipe':
        return this.buildCylinder(part, spec);
      case 'duct':
        return this.buildDuct(part, spec);
      case 'glass':
        return this.buildGlass(part);
      case 'screen':
        return this.buildScreen(part);
      case 'tower':
        return this.buildStatusTower(part);
      case 'rotor':
        return this.buildRotor(part, spec);
      case 'frame':
        return this.buildFrame(part, spec);
      case 'box':
      default:
        return this.buildBox(part, spec);
    }
  }

  private static buildBox(part: ModelPart, spec: DeviceModel): THREE.Mesh {
    const mesh = new THREE.Mesh(
      new THREE.BoxGeometry(
        Math.max(part.length, 0.01),
        Math.max(part.height, 0.01),
        Math.max(part.width, 0.01),
      ),
      new THREE.MeshStandardMaterial({
        color: this.emphasize(this.color(part.color, spec.color)),
        roughness: 0.42,
        metalness: 0.38,
      }),
    );
    this.place(mesh, part);
    mesh.castShadow = mesh.receiveShadow = true;
    return mesh;
  }

  /** 圆柱与管路:axis 控制轴向,默认 y(竖立) */
  private static buildCylinder(part: ModelPart, spec: DeviceModel): THREE.Mesh {
    const radius = Math.max(part.radius, 0.01);
    const segments = radius < 0.1 ? 8 : radius < 0.3 ? 16 : 24;
    const mesh = new THREE.Mesh(
      new THREE.CylinderGeometry(radius, radius, Math.max(part.height, 0.01), segments),
      new THREE.MeshStandardMaterial({
        color: this.emphasize(this.color(part.color, spec.color)),
        roughness: 0.22,
        metalness: 0.62,
      }),
    );
    this.orient(mesh, part.axis);
    this.place(mesh, part);
    mesh.castShadow = true;
    return mesh;
  }

  /** 风管烟囱:主体圆柱 + 顶部法兰环,让它在屋顶轮廓上更容易辨认 */
  private static buildDuct(part: ModelPart, spec: DeviceModel): THREE.Group {
    const group = new THREE.Group();
    const radius = Math.max(part.radius, 0.02);
    const height = Math.max(part.height, 0.02);
    const material = new THREE.MeshStandardMaterial({
      color: this.emphasize(this.color(part.color, '#b9c1cb')),
      roughness: 0.32,
      metalness: 0.58,
    });

    const body = new THREE.Mesh(new THREE.CylinderGeometry(radius, radius, height, 16), material);
    const flange = new THREE.Mesh(
      new THREE.CylinderGeometry(radius * 1.25, radius * 1.25, height * 0.08 + 0.02, 16),
      material,
    );
    flange.position.y = height / 2;
    group.add(body, flange);

    this.orient(group, part.axis);
    this.place(group, part);
    return group;
  }

  /** 玻璃罩/亚克力封板:双面渲染,不写深度以免遮挡罩内构件 */
  private static buildGlass(part: ModelPart): THREE.Mesh {
    const mesh = new THREE.Mesh(
      new THREE.BoxGeometry(
        Math.max(part.length, 0.01),
        Math.max(part.height, 0.01),
        Math.max(part.width, 0.01),
      ),
      new THREE.MeshPhysicalMaterial({
        color: this.color(part.color, '#9fd0ea'),
        transparent: true,
        opacity: part.opacity > 0 ? part.opacity : 0.24,
        roughness: 0.08,
        metalness: 0.1,
        side: THREE.DoubleSide,
        depthWrite: false,
      }),
    );
    this.place(mesh, part);
    return mesh;
  }

  /** 触摸屏/显示器:深色面板 + 微弱自发光,俯视时也能看出操作面朝向 */
  private static buildScreen(part: ModelPart): THREE.Mesh {
    const mesh = new THREE.Mesh(
      new THREE.BoxGeometry(
        Math.max(part.length, 0.01),
        Math.max(part.height, 0.01),
        Math.max(part.width, 0.01),
      ),
      new THREE.MeshStandardMaterial({
        color: 0x11161f,
        emissive: 0x1e3a5f,
        emissiveIntensity: 1.15,
        roughness: 0.25,
      }),
    );
    this.place(mesh, part);
    return mesh;
  }

  /**
   * 三色状态灯:灯柱 + 灯罩。
   * 灯罩挂在 userData.lamp 上交给 FactoryScene 按实时状态改色,
   * 这样"状态 → 颜色"的映射只保留一处,不在几何构建里重复。
   */
  private static buildStatusTower(part: ModelPart): THREE.Group {
    const group = new THREE.Group();
    const radius = Math.max(part.radius, 0.02);
    const height = Math.max(part.height, 0.05);

    const pole = new THREE.Mesh(
      new THREE.CylinderGeometry(radius * 0.45, radius * 0.45, height * 0.45, 10),
      new THREE.MeshStandardMaterial({ color: 0x3a4250, roughness: 0.6 }),
    );
    pole.position.y = -height * 0.275;

    const lamp = new THREE.Mesh(
      new THREE.CylinderGeometry(radius, radius, height * 0.55, 12),
      new THREE.MeshStandardMaterial({
        color: 0x64748b,
        emissive: 0x64748b,
        emissiveIntensity: 1.6,
      }),
    );
    lamp.position.y = height * 0.275;

    group.add(pole, lamp);
    group.userData.lamp = lamp;
    this.place(group, part);
    return group;
  }

  /** 运行动件:形状与 cylinder 一致,靠 kind 区分是否参与旋转动画 */
  private static buildRotor(part: ModelPart, spec: DeviceModel): THREE.Object3D {
    const mesh = this.buildCylinder(part, spec);
    // 非 y 轴的动件已被 orient 旋转过,再叠加自转会绕错的轴;
    // 因此外面套一层 Group,自转始终作用在构件自身的局部 y 轴上。
    if (part.axis && part.axis !== 'y') {
      const holder = new THREE.Group();
      holder.position.copy(mesh.position);
      mesh.position.set(0, 0, 0);
      holder.rotation.copy(mesh.rotation);
      mesh.rotation.set(0, 0, 0);
      holder.add(mesh);
      holder.userData.spin = mesh;
      return holder;
    }
    return mesh;
  }

  /** 型材框架:只画 12 根棱,用于亚克力封板下能看到内部结构的机架 */
  private static buildFrame(part: ModelPart, spec: DeviceModel): THREE.Group {
    const group = new THREE.Group();
    const l = Math.max(part.length, 0.02);
    const h = Math.max(part.height, 0.02);
    const w = Math.max(part.width, 0.02);
    const bar = 0.05;
    const material = new THREE.MeshStandardMaterial({
      color: this.emphasize(this.color(part.color, spec.color)),
      roughness: 0.4,
      metalness: 0.48,
    });

    const addBar = (
      size: [number, number, number],
      position: [number, number, number],
    ): void => {
      const mesh = new THREE.Mesh(new THREE.BoxGeometry(...size), material);
      mesh.position.set(...position);
      group.add(mesh);
    };

    // 4 根立柱
    [-1, 1].forEach((sx) =>
      [-1, 1].forEach((sz) => {
        addBar([bar, h, bar], [(sx * (l - bar)) / 2, 0, (sz * (w - bar)) / 2]);
      }),
    );
    // 上下各 4 根横梁
    [-1, 1].forEach((sy) => {
      [-1, 1].forEach((sz) => {
        addBar([l, bar, bar], [0, (sy * (h - bar)) / 2, (sz * (w - bar)) / 2]);
      });
      [-1, 1].forEach((sx) => {
        addBar([bar, bar, w], [(sx * (l - bar)) / 2, (sy * (h - bar)) / 2, 0]);
      });
    });

    this.place(group, part);
    return group;
  }

  // ==================== 辅助 ====================

  /** 无 parts 配置时的通用体块:按机身尺寸出一个带底座的方箱 */
  private static buildFallbackBody(spec: DeviceModel): THREE.Group {
    const group = new THREE.Group();
    const base = new THREE.Mesh(
      new THREE.BoxGeometry(spec.length, 0.15, spec.width),
      new THREE.MeshStandardMaterial({ color: 0x2a3550, roughness: 0.8 }),
    );
    base.position.y = 0.075;

    const body = new THREE.Mesh(
      new THREE.BoxGeometry(spec.length * 0.92, spec.height - 0.15, spec.width * 0.92),
      new THREE.MeshStandardMaterial({
        color: this.emphasize(this.color(spec.color, FALLBACK.color)),
        roughness: 0.4,
        metalness: 0.4,
      }),
    );
    body.position.y = 0.15 + (spec.height - 0.15) / 2;
    base.castShadow = base.receiveShadow = true;
    body.castShadow = body.receiveShadow = true;

    group.add(base, body);
    return group;
  }

  /**
   * 地面工位框:画出图纸实测的占位范围(含安全围栏与操作空间)。
   * 有它才能一眼看出设备布局是否留足了通道,这是"精确符合现场施工"最直接的可视化证据。
   */
  private static buildFootprint(spec: DeviceModel): THREE.Object3D {
    const l = spec.footprintLength > 0 ? spec.footprintLength : spec.length + 0.6;
    const w = spec.footprintWidth > 0 ? spec.footprintWidth : spec.width + 0.6;
    const group = new THREE.Group();
    group.name = 'device-footprint';

    const pad = new THREE.Mesh(
      new THREE.PlaneGeometry(l, w),
      new THREE.MeshStandardMaterial({
        color: 0x1f2937,
        roughness: 0.95,
        transparent: true,
        opacity: 0.2,
        depthWrite: false,
      }),
    );
    pad.rotation.x = -Math.PI / 2;
    pad.position.y = 0.03;
    group.add(pad);

    const half = { x: l / 2, z: w / 2 };
    const y = 0.05;
    const geometry = new THREE.BufferGeometry();
    geometry.setAttribute(
      'position',
      new THREE.Float32BufferAttribute(
        [
          -half.x, y, -half.z, half.x, y, -half.z,
          half.x, y, -half.z, half.x, y, half.z,
          half.x, y, half.z, -half.x, y, half.z,
          -half.x, y, half.z, -half.x, y, -half.z,
        ],
        3,
      ),
    );
    group.add(
      new THREE.LineSegments(
        geometry,
        new THREE.LineBasicMaterial({ color: 0x2563eb, transparent: true, opacity: 0.85 }),
      ),
    );
    return group;
  }

  /** 把构件摆到配置指定的局部坐标 */
  private static place(object: THREE.Object3D, part: ModelPart): void {
    object.position.set(part.x, part.y, part.z);
    if (part.rotationY) {
      object.rotation.y += THREE.MathUtils.degToRad(part.rotationY);
    }
  }

  /** 圆柱默认沿 y 轴,axis 为 x/z 时绕相应轴倒下 90° */
  private static orient(object: THREE.Object3D, axis: string): void {
    if (axis === 'x') object.rotation.z = Math.PI / 2;
    else if (axis === 'z') object.rotation.x = Math.PI / 2;
  }

  /** 构件颜色缺省时继承设备主体色 */
  private static color(partColor: string, fallback: string): THREE.Color {
    return new THREE.Color(partColor && partColor.trim() ? partColor : fallback);
  }

  /** 略提高饱和度、压一点明度,让机身在浅色厂房里更压得住。 */
  private static emphasize(color: THREE.Color): THREE.Color {
    color.offsetHSL(0, 0.1, -0.06);
    return color;
  }
}
