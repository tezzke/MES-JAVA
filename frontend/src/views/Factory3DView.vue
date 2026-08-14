<script setup lang="ts">
/**
 * 3D 车间页:FactoryScene(Three.js)+ 状态图例 + 设备详情抽屉。
 * 职责划分:本页只做"生命周期管理 + 数据灌入",渲染细节全部在 FactoryScene 内。
 */
import { onBeforeUnmount, onMounted, ref, watch } from 'vue';
import DeviceDetailDrawer from '../components/DeviceDetailDrawer.vue';
import { useRealtimeStore } from '../stores/realtime';
import { statusColor, statusText } from '../utils/format';
import { FactoryScene } from '../three/FactoryScene';
import type { DeviceStatus } from '../api/types';

const store = useRealtimeStore();
const container = ref<HTMLDivElement>();
const selectedDevice = ref<string | null>(null);
let scene: FactoryScene | null = null;

const legendStatuses: DeviceStatus[] = ['Running', 'Standby', 'Alarm', 'Offline'];

/** 设备档案就绪后创建场景(首次进入页面时档案可能还在加载) */
function tryCreateScene() {
  if (scene || !container.value || !store.devices.length) return;
  scene = new FactoryScene(container.value, store.devices, (deviceId) => {
    selectedDevice.value = deviceId;
    scene?.setSelected(deviceId);
  });
  scene.updateSnapshots(store.snapshots);
}

onMounted(tryCreateScene);
watch(() => store.devices.length, tryCreateScene);

// 实时快照 → 3D 状态灯(深度监听 store 中的快照表)
watch(
  () => store.snapshots,
  (snapshots) => scene?.updateSnapshots(snapshots),
  { deep: true },
);

onBeforeUnmount(() => {
  scene?.dispose(); // 离开页面必须释放 WebGL 资源
  scene = null;
});
</script>

<template>
  <div class="factory-page">
    <div ref="container" class="scene-container"></div>

    <!-- 状态图例 -->
    <div class="legend panel">
      <div v-for="status in legendStatuses" :key="status" class="legend-item">
        <span class="legend-dot" :style="{ background: statusColor[status] }"></span>
        {{ statusText[status] }}
        <b>{{ store.statusCounts[status] }}</b>
      </div>
      <div class="legend-tip">拖拽旋转 · 滚轮缩放 · 点击设备查看详情</div>
    </div>

    <DeviceDetailDrawer
      :device-id="selectedDevice"
      @close="((selectedDevice = null), scene?.setSelected(null))"
    />
  </div>
</template>

<style scoped>
.factory-page {
  position: relative;
  height: 100%;
}
.scene-container {
  position: absolute;
  inset: 0;
}
.legend {
  position: absolute;
  left: 16px;
  bottom: 16px;
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 10px 16px;
}
.legend-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--text-sub);
}
.legend-item b {
  color: var(--text-main);
}
.legend-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
}
.legend-tip {
  font-size: 12px;
  color: var(--text-sub);
  border-left: 1px solid var(--border);
  padding-left: 14px;
}
</style>
