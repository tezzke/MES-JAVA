<script setup lang="ts">
/**
 * 3D 车间右侧半透明信息框:监视当前区域内全部设备的运行实况。
 * 点选单台时额外给出近 30 分钟趋势。
 */
import { computed, ref, watch } from 'vue';
import { mesApi } from '../api/mes';
import type { DeviceMeta, PointMeta } from '../api/types';
import { useRealtimeStore } from '../stores/realtime';
import { formatTime } from '../utils/format';
import StatusTag from './StatusTag.vue';
import TrendChart from './TrendChart.vue';

const props = defineProps<{
  deviceIds: string[];
  heading?: string;
}>();

const store = useRealtimeStore();
const listed = computed(() =>
  props.deviceIds
    .map((id) => store.devices.find((item) => item.deviceId === id))
    .filter((item): item is DeviceMeta => item != null),
);

const solo = computed(() => (listed.value.length === 1 ? listed.value[0] : null));
const trendPoints = computed<PointMeta[]>(
  () => solo.value?.points.filter((point) => !point.isStatus) ?? [],
);

const selectedPoint = ref('');
const trendData = ref<Array<[string, number]>>([]);
const loading = ref(false);

watch(
  () => props.deviceIds.join(','),
  () => {
    selectedPoint.value = trendPoints.value.find((point) => !point.isCounter)?.name ?? '';
    loadTrend();
  },
  { immediate: true },
);

watch(selectedPoint, loadTrend);

async function loadTrend() {
  trendData.value = [];
  if (!solo.value || !selectedPoint.value) return;
  loading.value = true;
  try {
    const records = await mesApi.getHistory({
      deviceId: solo.value.deviceId,
      pointName: selectedPoint.value,
      from: new Date(Date.now() - 30 * 60 * 1000).toISOString(),
      maxPoints: 1000,
    });
    trendData.value = records.map((record) => [record.timestamp, record.value]);
  } finally {
    loading.value = false;
  }
}

const selectedPointMeta = computed(() =>
  trendPoints.value.find((point) => point.name === selectedPoint.value),
);

function snapshotOf(deviceId: string) {
  return store.snapshots[deviceId];
}
</script>

<template>
  <aside v-if="listed.length" class="inspect">
    <span class="hud-corner hud-tl" />
    <span class="hud-corner hud-tr" />
    <span class="hud-corner hud-bl" />
    <span class="hud-corner hud-br" />
    <header class="inspect-head">
      <div>
        <div class="inspect-kicker">{{ listed.length > 1 ? '区域设备监视' : '设备运行详情' }}</div>
        <h2>{{ heading || (solo ? solo.name : `${listed.length} 台设备`) }}</h2>
      </div>
    </header>

    <div class="inspect-list">
      <section v-for="device in listed" :key="device.deviceId" class="device-block">
        <div class="device-head">
          <div>
            <div class="device-name">{{ device.name }}</div>
            <p class="inspect-meta">
              {{ device.deviceId }} · {{ device.line }} · 更新
              {{ formatTime(snapshotOf(device.deviceId)?.timestamp) }}
            </p>
          </div>
          <StatusTag :status="snapshotOf(device.deviceId)?.status ?? 'Offline'" />
        </div>
        <table class="points-table">
          <tbody>
            <tr v-for="point in snapshotOf(device.deviceId)?.points ?? []" :key="point.name">
              <td class="p-name">{{ point.displayName }}</td>
              <td class="p-value" :class="{ warn: point.quality !== 'Good' }">
                {{ point.value.toFixed(2) }} {{ point.unit }}
              </td>
              <td class="p-quality">{{ point.quality === 'Good' ? '' : '⚠' }}</td>
            </tr>
            <tr v-if="!snapshotOf(device.deviceId)?.points?.length">
              <td class="p-name" colspan="3">设备离线,暂无实时数据</td>
            </tr>
          </tbody>
        </table>
      </section>
    </div>

    <section v-if="solo" class="inspect-section inspect-trend">
      <h3>最近 30 分钟趋势</h3>
      <el-select v-model="selectedPoint" size="small" style="width: 100%">
        <el-option
          v-for="point in trendPoints"
          :key="point.name"
          :label="point.displayName"
          :value="point.name"
        />
      </el-select>
      <div v-loading="loading" class="trend-box">
        <TrendChart
          :data="trendData"
          :name="selectedPointMeta?.displayName ?? ''"
          :unit="selectedPointMeta?.unit"
        />
      </div>
    </section>
  </aside>
</template>

<style scoped>
.inspect {
  position: relative;
  display: flex;
  flex-direction: column;
  min-height: 0;
  flex: 1;
  padding: 18px 18px 14px;
  overflow: hidden;
  border: 1px solid rgba(37, 99, 235, 0.35);
  border-radius: 14px;
  background: linear-gradient(
    165deg,
    rgba(255, 255, 255, 0.82) 0%,
    rgba(248, 250, 252, 0.52) 48%,
    rgba(226, 232, 240, 0.28) 100%
  );
  box-shadow:
    0 0 0 1px rgba(37, 99, 235, 0.12),
    0 10px 36px rgba(15, 23, 42, 0.1);
  backdrop-filter: blur(18px);
  pointer-events: auto;
  animation: inspect-glow 1.5s ease-in-out infinite;
}
.hud-corner {
  position: absolute;
  width: 16px;
  height: 16px;
  border: 2px solid #2563eb;
  pointer-events: none;
}
.hud-tl {
  top: 8px;
  left: 8px;
  border-right: 0;
  border-bottom: 0;
}
.hud-tr {
  top: 8px;
  right: 8px;
  border-left: 0;
  border-bottom: 0;
}
.hud-bl {
  bottom: 8px;
  left: 8px;
  border-right: 0;
  border-top: 0;
}
.hud-br {
  bottom: 8px;
  right: 8px;
  border-left: 0;
  border-top: 0;
}
@keyframes inspect-glow {
  0%,
  100% {
    box-shadow:
      0 0 0 1px rgba(37, 99, 235, 0.18),
      0 10px 36px rgba(15, 23, 42, 0.1);
  }
  50% {
    box-shadow:
      0 0 0 3px rgba(37, 99, 235, 0.45),
      0 0 28px rgba(37, 99, 235, 0.22),
      0 10px 36px rgba(15, 23, 42, 0.1);
  }
}
.inspect-head {
  flex-shrink: 0;
}
.inspect-kicker {
  font-size: 11px;
  letter-spacing: 0.08em;
  color: var(--text-sub);
}
.inspect h2 {
  margin: 4px 0 0;
  font-size: 18px;
  font-weight: 650;
  color: var(--text-main);
}
.inspect-list {
  flex: 1;
  min-height: 0;
  margin-top: 12px;
  overflow: auto;
}
.device-block + .device-block {
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid rgba(15, 23, 42, 0.08);
}
.device-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}
.device-name {
  font-size: 14px;
  font-weight: 650;
  color: var(--text-main);
}
.inspect-meta {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--text-sub);
}
.inspect-section {
  margin-top: 16px;
}
.inspect-section h3 {
  margin: 0 0 8px;
  font-size: 12px;
  font-weight: 650;
  color: var(--text-sub);
}
.points-table {
  width: 100%;
  margin-top: 6px;
  border-collapse: collapse;
}
.points-table td {
  padding: 5px 2px;
  border-bottom: 1px solid rgba(15, 23, 42, 0.06);
  font-size: 13px;
}
.p-name {
  color: var(--text-sub);
}
.p-value {
  text-align: right;
  font-family: var(--font-mono);
  color: var(--text-main);
}
.p-value.warn {
  color: var(--warn);
  font-weight: 650;
}
.p-quality {
  width: 22px;
  text-align: right;
  color: var(--warn);
}
.inspect-trend {
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}
.trend-box {
  height: 180px;
  margin-top: 10px;
}
</style>
