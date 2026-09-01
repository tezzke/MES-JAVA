<script setup lang="ts">
/**
 * 设备卡片:总览页网格中的单台设备,展示状态 + 前几个关键点位实时值。
 * 点击卡片向父组件抛出 select 事件(打开详情抽屉)。
 */
import { computed } from 'vue';
import type { DeviceMeta, DeviceSnapshot } from '../api/types';
import StatusTag from './StatusTag.vue';

const props = defineProps<{
  device: DeviceMeta;
  snapshot: DeviceSnapshot | undefined;
}>();

defineEmits<{ select: [deviceId: string] }>();

/** 卡片上最多展示 3 个非状态字点位 */
const displayPoints = computed(
  () => props.snapshot?.points.filter((p) => !isStatusPoint(p.name)).slice(0, 3) ?? [],
);

function isStatusPoint(name: string): boolean {
  return props.device.points.find((p) => p.name === name)?.isStatus ?? false;
}
</script>

<template>
  <div
    class="device-card"
    :class="{
      alarm: snapshot?.status === 'Alarm',
      offline: !snapshot || snapshot.status === 'Offline',
    }"
    @click="$emit('select', device.deviceId)"
  >
    <div class="card-head">
      <span class="device-name" :title="device.deviceId">{{ device.name }}</span>
      <StatusTag :status="snapshot?.status ?? 'Offline'" />
    </div>
    <div class="device-id">{{ device.deviceId }} · {{ device.line }}</div>
    <div class="points">
      <div v-for="point in displayPoints" :key="point.name" class="point-row">
        <span class="point-label">{{ point.displayName }}</span>
        <span class="point-value" :class="{ bad: point.quality !== 'Good' }">
          {{ point.value.toFixed(1) }}
          <i class="unit">{{ point.unit }}</i>
        </span>
      </div>
      <div v-if="!displayPoints.length" class="no-data">暂无数据</div>
    </div>
  </div>
</template>

<style scoped>
.device-card {
  background: var(--bg-panel);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 12px 14px;
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s, box-shadow 0.15s;
}
.device-card:hover {
  background: var(--bg-panel-hover);
  border-color: var(--accent);
}
.device-card.alarm {
  border-color: var(--danger);
  box-shadow: 0 0 0 3px rgba(220, 38, 38, 0.08);
}
.device-card.offline {
  opacity: 0.65;
}
.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}
.device-name {
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.device-id {
  font-size: 12px;
  color: var(--text-sub);
  margin: 4px 0 10px;
}
.point-row {
  display: flex;
  justify-content: space-between;
  font-size: 12.5px;
  padding: 3px 0;
}
.point-label {
  color: var(--text-sub);
}
.point-value {
  font-family: var(--font-mono);
  font-variant-numeric: tabular-nums;
}
.point-value.bad {
  color: var(--warn);
}
.unit {
  font-style: normal;
  color: var(--text-sub);
  margin-left: 2px;
}
.no-data {
  font-size: 12px;
  color: var(--text-sub);
  text-align: center;
  padding: 8px 0;
}
</style>
