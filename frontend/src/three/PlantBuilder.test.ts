import { describe, expect, it } from 'vitest';
import * as THREE from 'three';
import { PlantBuilder } from './PlantBuilder';
import type { PlantShell } from '../api/types';

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
    partitions: [
      {
        name: '实心隔墙',
        x1: 5,
        z1: -10,
        x2: 5,
        z2: 0,
        thickness: 0.2,
        height: 4,
        glazed: false,
      },
    ],
    zones: [],
  };
}

function collectMeshes(root: THREE.Object3D): THREE.Mesh[] {
  const meshes: THREE.Mesh[] = [];
  root.traverse((child) => {
    if (child instanceof THREE.Mesh) meshes.push(child);
  });
  return meshes;
}

describe('PlantBuilder exterior walls', () => {
  it('fades envelope walls, partitions and columns so devices stay in front', () => {
    const root = PlantBuilder.build(sampleShell());
    const exterior = root.getObjectByName('exterior-walls');
    expect(exterior).toBeTruthy();

    const glassBodies = collectMeshes(exterior!).filter((mesh) => {
      const material = mesh.material;
      return (
        material instanceof THREE.MeshPhysicalMaterial &&
        material.transparent &&
        material.side === THREE.DoubleSide
      );
    });
    expect(glassBodies.length).toBeGreaterThanOrEqual(4);
    for (const mesh of glassBodies) {
      const material = mesh.material as THREE.MeshPhysicalMaterial;
      expect(material.opacity).toBeGreaterThan(0.12);
      expect(material.opacity).toBeLessThan(0.28);
      expect(material.clearcoat).toBeGreaterThan(0.4);
    }

    const partitions = collectMeshes(root).filter((mesh) => {
      const material = mesh.material;
      return (
        material instanceof THREE.MeshPhysicalMaterial &&
        material.transparent &&
        material.opacity >= 0.2 &&
        material.opacity <= 0.28
      );
    });
    expect(partitions.length).toBeGreaterThanOrEqual(1);

    const columns = root.getObjectByName('columns') as THREE.InstancedMesh;
    expect(columns).toBeTruthy();
    const columnMaterial = columns.material as THREE.MeshStandardMaterial;
    expect(columnMaterial.transparent).toBe(true);
    expect(columnMaterial.opacity).toBeLessThan(0.25);
  });

  it('places company logos on opposite exterior corner walls', () => {
    const root = PlantBuilder.build(sampleShell());
    const logos = root.getObjectByName('corner-logos');
    expect(logos).toBeTruthy();
    expect(logos!.children.length).toBe(4);
    expect(logos!.children.every((child) => child.name === 'wall-logo')).toBe(true);
  });
});
