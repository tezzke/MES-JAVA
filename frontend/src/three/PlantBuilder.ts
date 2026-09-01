import * as THREE from 'three';
import type { AxisLine, Bounds, PlantShell, PlantZone, WallSegment } from '../api/types';
import logoUrl from '../assets/brand/jiuxin-logo.png';

/**
 * 厂房壳体构建器:把后端下发的 PlantShell 翻译成 Three.js 几何体。
 *
 * 职责边界:只画"不会动的建筑" —— 地面、分区色块、轴网柱子、外墙、隔墙、玻璃观察窗、
 * 对角墙面厂标、轴号标牌。设备一律不在这里处理,由 DeviceModelFactory 负责。
 *
 * 之所以单独成类而不是塞进 FactoryScene:厂房数据来自图纸、设备数据来自协议书,
 * 两者的修改时机与责任人不同,分开后改厂房不会碰到设备渲染代码。
 *
 * 所有几何都合并进一个 Group 返回,方便 FactoryScene 统一挂载与释放。
 */

/** 分区类型 → 地面配色。图纸的功能分区在 3D 里靠地面色块区分,比画文字更直观。 */
const ZONE_COLORS: Record<string, number> = {
  clean: 0xf3f5f8,
  corridor: 0xe8edf3,
  utility: 0xf2efe8,
  office: 0xeef1f5,
  default: 0xf3f5f8,
};

/** 建筑构件配色。墙柱刻意压淡,把视觉重量留给设备。 */
const COLORS = {
  ground: 0xf6f7f9,
  wall: 0x9aa3af,
  column: 0xb4bcc6,
  partition: 0x9aa3af,
  glass: 0x8eb6cc,
} as const;

export class PlantBuilder {
  /** 建造整个厂房壳体,返回可直接 add 到场景的 Group。 */
  static build(shell: PlantShell): THREE.Group {
    const root = new THREE.Group();
    root.name = 'plant-shell';

    root.add(this.buildGround(shell.envelope));
    shell.zones.forEach((zone) => root.add(this.buildZoneFloor(zone)));
    root.add(this.buildAxisGridLines(shell));
    root.add(this.buildColumns(shell));
    root.add(this.buildExteriorWalls(shell));
    root.add(this.buildCornerLogos(shell));
    shell.partitions.forEach((wall) => root.add(this.buildWall(wall, shell)));
    root.add(this.buildAxisLabels(shell));

    return root;
  }

  /** 相机初始视角:按厂房尺寸自动取景,换厂房不必改代码。 */
  static cameraFraming(envelope: Bounds): {
    target: THREE.Vector3;
    position: THREE.Vector3;
    maxDistance: number;
  } {
    const span = Math.max(envelope.maxX - envelope.minX, envelope.maxZ - envelope.minZ);
    const cx = (envelope.minX + envelope.maxX) / 2;
    const cz = (envelope.minZ + envelope.maxZ) / 2;
    return {
      target: new THREE.Vector3(cx, 0, cz),
      position: new THREE.Vector3(cx, span * 0.55, cz + span * 0.75),
      maxDistance: span * 2.2,
    };
  }

  // ==================== 地面与分区 ====================

  /** 厂房外轮廓地面:比外墙各向外扩 3m,避免边界处出现悬空感。 */
  private static buildGround(envelope: Bounds): THREE.Mesh {
    const margin = 3;
    const width = envelope.maxX - envelope.minX + margin * 2;
    const depth = envelope.maxZ - envelope.minZ + margin * 2;
    const ground = new THREE.Mesh(
      new THREE.PlaneGeometry(width, depth),
      new THREE.MeshStandardMaterial({ color: COLORS.ground, roughness: 0.95 }),
    );
    ground.rotation.x = -Math.PI / 2;
    ground.position.set((envelope.minX + envelope.maxX) / 2, 0, (envelope.minZ + envelope.maxZ) / 2);
    ground.receiveShadow = true;
    return ground;
  }

  /** 功能分区地面色块 + 区域名标牌,贴在地面上方 2cm 处避免与地面 Z-fighting。 */
  private static buildZoneFloor(zone: PlantZone): THREE.Group {
    const group = new THREE.Group();
    const { bounds } = zone;
    const width = bounds.maxX - bounds.minX;
    const depth = bounds.maxZ - bounds.minZ;
    if (width <= 0 || depth <= 0) return group;

    const color = ZONE_COLORS[zone.kind] ?? ZONE_COLORS.default;
    const floor = new THREE.Mesh(
      new THREE.PlaneGeometry(width, depth),
      new THREE.MeshStandardMaterial({ color, roughness: 0.9 }),
    );
    floor.rotation.x = -Math.PI / 2;
    floor.position.set((bounds.minX + bounds.maxX) / 2, 0.02, (bounds.minZ + bounds.maxZ) / 2);
    floor.receiveShadow = true;
    group.add(floor);

    // 区域名平铺在地面上(不用 Sprite,避免俯视时文字挡住设备)
    const label = this.buildGroundLabel(zone.name);
    label.position.set((bounds.minX + bounds.maxX) / 2, 0.04, (bounds.minZ + bounds.maxZ) / 2);
    group.add(label);

    return group;
  }

  // ==================== 轴网 ====================

  /** 轴线:细长的半透明地面线条,给出图纸的刻度感。 */
  private static buildAxisGridLines(shell: PlantShell): THREE.LineSegments {
    const { envelope, axisGrid } = shell;
    const points: number[] = [];

    axisGrid.axesX.forEach((axis) => {
      points.push(axis.value, 0.05, envelope.minZ, axis.value, 0.05, envelope.maxZ);
    });
    axisGrid.axesZ.forEach((axis) => {
      points.push(envelope.minX, 0.05, axis.value, envelope.maxX, 0.05, axis.value);
    });

    const geometry = new THREE.BufferGeometry();
    geometry.setAttribute('position', new THREE.Float32BufferAttribute(points, 3));
    return new THREE.LineSegments(
      geometry,
      new THREE.LineBasicMaterial({ color: 0x9aa3af, transparent: true, opacity: 0.16 }),
    );
  }

  /**
   * 结构柱:在每个轴线交点立一根柱子。
   * 用 InstancedMesh 而非逐个 Mesh —— 柱子数量随轴网增长,共享同一份几何可以少几十次 draw call。
   */
  private static buildColumns(shell: PlantShell): THREE.InstancedMesh {
    const { axisGrid } = shell;
    const size = axisGrid.columnSize;
    const height = axisGrid.columnHeight || shell.wallHeight;
    const count = axisGrid.axesX.length * axisGrid.axesZ.length;

    const mesh = new THREE.InstancedMesh(
      new THREE.BoxGeometry(size, height, size),
      new THREE.MeshStandardMaterial({
        color: COLORS.column,
        roughness: 0.92,
        transparent: true,
        opacity: 0.16,
        depthWrite: false,
      }),
      Math.max(count, 1),
    );
    mesh.name = 'columns';
    mesh.castShadow = mesh.receiveShadow = false;

    const matrix = new THREE.Matrix4();
    let index = 0;
    axisGrid.axesX.forEach((xAxis: AxisLine) => {
      axisGrid.axesZ.forEach((zAxis: AxisLine) => {
        matrix.setPosition(xAxis.value, height / 2, zAxis.value);
        mesh.setMatrixAt(index, matrix);
        index += 1;
      });
    });
    mesh.count = index;
    mesh.instanceMatrix.needsUpdate = true;
    return mesh;
  }

  /** 轴号标牌:沿厂房南边与西边各摆一排,对应图纸的轴线编号。 */
  private static buildAxisLabels(shell: PlantShell): THREE.Group {
    const group = new THREE.Group();
    const { envelope, axisGrid } = shell;
    const offset = 1.8;

    axisGrid.axesX.forEach((axis) => {
      const label = this.buildGroundLabel(axis.label, 1.6, '#9aa3af');
      label.position.set(axis.value, 0.06, envelope.maxZ + offset);
      group.add(label);
    });
    axisGrid.axesZ.forEach((axis) => {
      const label = this.buildGroundLabel(axis.label, 1.6, '#9aa3af');
      label.position.set(envelope.minX - offset, 0.06, axis.value);
      group.add(label);
    });

    return group;
  }

  // ==================== 墙体 ====================

  /**
   * 外墙:沿 envelope 四边围一圈。
   * 做成半透明玻璃幕墙,方便从外侧透视内部隔墙、轴网和设备;顶部压顶 + 轮廓线保住厂房边界。
   */
  private static buildExteriorWalls(shell: PlantShell): THREE.Group {
    const { envelope, wallHeight, wallThickness } = shell;
    const walls: WallSegment[] = [
      { name: '南外墙', x1: envelope.minX, z1: envelope.maxZ, x2: envelope.maxX, z2: envelope.maxZ },
      { name: '北外墙', x1: envelope.minX, z1: envelope.minZ, x2: envelope.maxX, z2: envelope.minZ },
      { name: '西外墙', x1: envelope.minX, z1: envelope.minZ, x2: envelope.minX, z2: envelope.maxZ },
      { name: '东外墙', x1: envelope.maxX, z1: envelope.minZ, x2: envelope.maxX, z2: envelope.maxZ },
    ].map((w) => ({ ...w, thickness: wallThickness, height: wallHeight, glazed: true }));

    const group = new THREE.Group();
    group.name = 'exterior-walls';
    walls.forEach((wall) => group.add(this.buildWall(wall, shell, { exterior: true })));
    return group;
  }

  /**
   * 西南 / 东北对角外墙各挂一块厂标(PNG 透明底,直接贴在玻璃上)。
   * 内外各一层,从厂区外或车间内看都是正向、不镜像。
   */
  private static buildCornerLogos(shell: PlantShell): THREE.Group {
    const group = new THREE.Group();
    group.name = 'corner-logos';

    const { envelope, wallHeight, wallThickness } = shell;
    const spanX = envelope.maxX - envelope.minX;
    const height = Math.min(2.05, wallHeight * 0.34);
    const width = height * 2.9;
    const along = Math.min(spanX * 0.16, Math.max(width * 0.55 + 3.8, 7.5));
    const lift = wallHeight * 0.55;
    const standOff = wallThickness / 2 + 0.05;
    const texture = this.loadLogoTexture();

    const southWestX = envelope.minX + along;
    group.add(this.buildLogoPlate(southWestX, lift, envelope.maxZ + standOff, 0, width, height, texture));
    group.add(this.buildLogoPlate(southWestX, lift, envelope.maxZ - standOff, Math.PI, width, height, texture));

    const northEastX = envelope.maxX - along;
    group.add(this.buildLogoPlate(northEastX, lift, envelope.minZ - standOff, Math.PI, width, height, texture));
    group.add(this.buildLogoPlate(northEastX, lift, envelope.minZ + standOff, 0, width, height, texture));

    return group;
  }

  private static loadLogoTexture(): THREE.Texture {
    const texture = new THREE.TextureLoader().load(logoUrl);
    texture.colorSpace = THREE.SRGBColorSpace;
    texture.anisotropy = 8;
    return texture;
  }

  private static buildLogoPlate(
    x: number,
    y: number,
    z: number,
    rotationY: number,
    width: number,
    height: number,
    texture: THREE.Texture,
  ): THREE.Group {
    const group = new THREE.Group();
    group.name = 'wall-logo';
    group.position.set(x, y, z);
    group.rotation.y = rotationY;

    const logo = new THREE.Mesh(
      new THREE.PlaneGeometry(width, height),
      new THREE.MeshBasicMaterial({
        map: texture,
        transparent: true,
        alphaTest: 0.08,
        depthWrite: false,
      }),
    );
    group.add(logo);
    return group;
  }

  /**
   * 单段墙体:按起止点算长度与朝向,支持任意角度。
   * 外墙与隔墙都按玻璃幕墙处理(带清漆反光),立柱保持更淡、不走这套材质。
   */
  private static buildWall(
    wall: WallSegment,
    shell: PlantShell,
    options: { exterior?: boolean } = {},
  ): THREE.Group {
    const group = new THREE.Group();
    const dx = wall.x2 - wall.x1;
    const dz = wall.z2 - wall.z1;
    const length = Math.hypot(dx, dz);
    if (length <= 0) return group;

    const thickness = wall.thickness || shell.wallThickness;
    const height = wall.height || shell.partitionHeight;
    const cx = (wall.x1 + wall.x2) / 2;
    const cz = (wall.z1 + wall.z2) / 2;
    const angle = Math.atan2(dz, dx);

    const material = this.glassMaterial(options.exterior ? 'curtain' : wall.glazed ? 'window' : 'frosted');

    const geometry = new THREE.BoxGeometry(length, height, thickness);
    const body = new THREE.Mesh(geometry, material);
    body.position.set(cx, height / 2, cz);
    body.rotation.y = -angle;
    body.castShadow = body.receiveShadow = false;
    group.add(body);

    if (wall.glazed) {
      const cap = new THREE.Mesh(
        new THREE.BoxGeometry(length, 0.08, thickness * 1.2),
        new THREE.MeshStandardMaterial({
          color: COLORS.wall,
          transparent: true,
          opacity: 0.38,
          roughness: 0.85,
          depthWrite: false,
        }),
      );
      cap.position.set(cx, height, cz);
      cap.rotation.y = -angle;
      group.add(cap);

      const edges = new THREE.LineSegments(
        new THREE.EdgesGeometry(geometry),
        new THREE.LineBasicMaterial({
          color: 0x6f93ab,
          transparent: true,
          opacity: 0.42,
        }),
      );
      edges.position.copy(body.position);
      edges.rotation.copy(body.rotation);
      group.add(edges);
    }

    return group;
  }

  /** 幕墙 / 观察窗 / 磨砂隔墙：都能透视，但带一点玻璃反光。立柱不走这里。 */
  private static glassMaterial(kind: 'curtain' | 'window' | 'frosted'): THREE.MeshPhysicalMaterial {
    const curtain = kind === 'curtain';
    const frosted = kind === 'frosted';
    return new THREE.MeshPhysicalMaterial({
      color: frosted ? 0xc5d2de : COLORS.glass,
      transparent: true,
      opacity: curtain ? 0.18 : frosted ? 0.24 : 0.2,
      roughness: frosted ? 0.32 : 0.06,
      metalness: 0.12,
      clearcoat: frosted ? 0.25 : 0.7,
      clearcoatRoughness: frosted ? 0.35 : 0.08,
      side: THREE.DoubleSide,
      depthWrite: false,
    });
  }

  // ==================== 文字 ====================

  /** 平铺在地面上的文字贴片(用 Canvas 贴图,免引额外字体库)。 */
  private static buildGroundLabel(text: string, scale = 3.4, color = '#9aa3af'): THREE.Mesh {
    const canvas = document.createElement('canvas');
    canvas.width = 256;
    canvas.height = 64;
    const ctx = canvas.getContext('2d')!;
    ctx.font = 'bold 34px "Microsoft YaHei", sans-serif';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillStyle = color;
    ctx.fillText(text, 128, 34);

    const texture = new THREE.CanvasTexture(canvas);
    texture.colorSpace = THREE.SRGBColorSpace;
    const mesh = new THREE.Mesh(
      new THREE.PlaneGeometry(scale, scale / 4),
      new THREE.MeshBasicMaterial({ map: texture, transparent: true, depthWrite: false }),
    );
    mesh.rotation.x = -Math.PI / 2;
    return mesh;
  }
}
