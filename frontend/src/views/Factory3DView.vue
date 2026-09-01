<script setup lang="ts">
/**
 * 3D 车间页:FactoryScene(Three.js)+ 状态图例 + 右侧半透明设备详情。
 * 职责划分:本页只做"生命周期管理 + 数据灌入",渲染细节全部在 FactoryScene 内。
 */
import { onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import DeviceInspectPanel from '../components/DeviceInspectPanel.vue';
import { useRealtimeStore } from '../stores/realtime';
import { statusColor, statusText } from '../utils/format';
import { FactoryScene } from '../three/FactoryScene';
import { mesApi } from '../api/mes';
import type { DeviceStatus, PlantLayout } from '../api/types';

const store = useRealtimeStore();
const container = ref<HTMLDivElement>();
const selectedDevice = ref<string | null>(null);
const inspectIds = ref<string[]>([]);
const layout = ref<PlantLayout | null>(null);
const touring = ref(true);
const tourTitle = ref('数字车间');
const tourSubtitle = ref('正在准备巡视');
let scene: FactoryScene | null = null;

const legendStatuses: DeviceStatus[] = ['Running', 'Standby', 'Alarm', 'Offline'];

/**
 * 厂房模型与设备档案都就绪后才创建场景。
 * 两份数据来自不同接口,谁先到不确定,所以两边的 watch 都调用这里,由本函数判断齐不齐。
 */
function tryCreateScene() {
  if (scene || !container.value || !store.devices.length || !layout.value) return;
  scene = new FactoryScene(
    container.value,
    store.devices,
    layout.value,
    (deviceId) => {
      selectedDevice.value = deviceId;
      inspectIds.value = deviceId ? [deviceId] : [];
      scene?.setSelected(deviceId);
    },
    (state) => {
      touring.value = state.playing;
      tourTitle.value = state.title;
      tourSubtitle.value = state.subtitle;
      inspectIds.value = state.deviceIds;
      selectedDevice.value = state.deviceIds.length === 1 ? state.deviceIds[0] : null;
    },
  );
  scene.updateSnapshots(store.snapshots);
  scene.updateAlarms(store.activeAlarmList);
}

onMounted(async () => {
  try {
    layout.value = await mesApi.getPlantLayout();
  } catch {
    ElMessage.error('厂房模型加载失败,3D 车间无法渲染');
    return;
  }
  tryCreateScene();
});

watch(() => store.devices.length, tryCreateScene);

// 实时快照 → 3D 状态灯(深度监听 store 中的快照表)
watch(
  () => store.snapshots,
  (snapshots) => scene?.updateSnapshots(snapshots),
  { deep: true },
);

watch(
  () => store.activeAlarmList,
  (alarms) => scene?.updateAlarms(alarms),
  { deep: true },
);

function toggleTour() {
  if (touring.value) scene?.pauseTour();
  else scene?.startTour();
}

function resetView() {
  selectedDevice.value = null;
  inspectIds.value = [];
  scene?.resetView();
}

onBeforeUnmount(() => {
  scene?.dispose(); // 离开页面必须释放 WebGL 资源
  scene = null;
});
</script>

<template>
  <div class="factory-page">
    <div ref="container" class="scene-container"></div>

    <!-- 厂房信息:标出模型依据的图纸,方便与现场图纸核对 -->
    <div v-if="layout" class="plant-info panel">
      <div class="plant-name">{{ layout.plant.name }}</div>
      <div class="plant-meta">
        图号 {{ layout.plant.drawingNo }} · 轴网
        {{ layout.plant.axisGrid.axesX.length }}×{{ layout.plant.axisGrid.axesZ.length }} ·
        {{ (layout.plant.envelope.maxX - layout.plant.envelope.minX).toFixed(1) }}×{{
          (layout.plant.envelope.maxZ - layout.plant.envelope.minZ).toFixed(1)
        }}
        m
      </div>
      <div class="plant-meta">{{ layout.plant.origin }}</div>
    </div>

    <!-- 状态图例 -->
    <div class="legend panel">
      <div v-for="status in legendStatuses" :key="status" class="legend-item">
        <span class="legend-dot" :style="{ background: statusColor[status] }"></span>
        {{ statusText[status] }}
        <b>{{ store.statusCounts[status] }}</b>
      </div>
      <div class="legend-item">
        <span class="legend-dot" style="background:#dc2626"></span>
        报警
      </div>
      <div class="legend-item">
        <span class="legend-dot" style="background:#d97706"></span>
        警告
      </div>
      <div class="legend-tip">自动巡视时可点暂停后拖拽环视 · 点选设备看详情</div>
    </div>

    <div class="right-stack">
      <div class="tour-bar panel">
        <div class="tour-copy">
          <div class="tour-kicker">{{ touring ? '自动巡视中' : '镜头已停' }}</div>
          <div class="tour-title">{{ tourTitle }}</div>
          <div class="tour-sub">{{ tourSubtitle }}</div>
        </div>
        <div class="tour-actions">
          <el-button type="primary" @click="toggleTour">{{ touring ? '暂停巡视' : '开始巡视' }}</el-button>
          <el-button @click="resetView">一键复位</el-button>
        </div>
      </div>
      <Transition name="inspect" mode="out-in">
        <DeviceInspectPanel
          v-if="inspectIds.length"
          :key="inspectIds.join(',')"
          :device-ids="inspectIds"
          :heading="inspectIds.length > 1 ? tourTitle : undefined"
        />
      </Transition>
    </div>
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
.plant-info {
  position: absolute;
  left: 16px;
  top: 16px;
  padding: 10px 16px;
  max-width: 360px;
}
.plant-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-main);
}
.plant-meta {
  margin-top: 4px;
  font-size: 12px;
  color: var(--text-sub);
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
.right-stack {
  position: absolute;
  top: 16px;
  right: 16px;
  bottom: 16px;
  z-index: 2;
  display: flex;
  flex-direction: column;
  gap: 12px;
  width: min(400px, calc(100% - 32px));
  pointer-events: none;
}
.tour-bar {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 12px 16px;
  pointer-events: auto;
}
.tour-kicker {
  font-size: 11px;
  letter-spacing: 0.08em;
  color: var(--text-sub);
}
.tour-title {
  margin-top: 2px;
  font-size: 15px;
  font-weight: 600;
  color: var(--text-main);
}
.tour-sub {
  margin-top: 2px;
  font-size: 12px;
  color: var(--text-sub);
}
.tour-actions {
  display: flex;
  flex-shrink: 0;
  gap: 8px;
}
.inspect-enter-active,
.inspect-leave-active {
  transition:
    opacity 0.32s ease,
    transform 0.32s cubic-bezier(0.22, 1, 0.36, 1);
}
.inspect-enter-from {
  opacity: 0;
  transform: translateX(36px) scale(0.94);
}
.inspect-leave-to {
  opacity: 0;
  transform: translateX(16px) scale(0.98);
}
</style>
