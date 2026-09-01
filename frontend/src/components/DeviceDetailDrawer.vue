<script setup lang="ts">
/**
 * 设备详情抽屉:总览页与 3D 车间共用。
 * 上半部分:全部点位的实时值(实时通道驱动,自动刷新);
 * 下半部分:选中点位的最近 30 分钟历史曲线(REST 拉取一次)。
 */
import { computed, ref, watch } from 'vue';
import { mesApi } from '../api/mes';
import type { PointMeta } from '../api/types';
import { useRealtimeStore } from '../stores/realtime';
import { formatTime } from '../utils/format';
import StatusTag from './StatusTag.vue';
import TrendChart from './TrendChart.vue';

const props = defineProps<{ deviceId: string | null }>();
const emit = defineEmits<{ close: [] }>();

const store = useRealtimeStore();

const visible = computed({
  get: () => props.deviceId != null,
  set: (v) => !v && emit('close'),
});

const device = computed(() => store.devices.find((d) => d.deviceId === props.deviceId));
const snapshot = computed(() => (props.deviceId ? store.snapshots[props.deviceId] : undefined));

/** 可画曲线的点位(排除状态字) */
const trendPoints = computed<PointMeta[]>(
  () => device.value?.points.filter((p) => !p.isStatus) ?? [],
);

const selectedPoint = ref<string>('');
const trendData = ref<Array<[string, number]>>([]);
const loading = ref(false);

/** 切换设备时:默认选中第一个模拟量点位并加载曲线 */
watch(
  () => props.deviceId,
  () => {
    selectedPoint.value = trendPoints.value.find((p) => !p.isCounter)?.name ?? '';
    loadTrend();
  },
);

watch(selectedPoint, loadTrend);

async function loadTrend() {
  trendData.value = [];
  if (!props.deviceId || !selectedPoint.value) return;
  loading.value = true;
  try {
    const records = await mesApi.getHistory({
      deviceId: props.deviceId,
      pointName: selectedPoint.value,
      from: new Date(Date.now() - 30 * 60 * 1000).toISOString(),
      maxPoints: 1000,
    });
    trendData.value = records.map((r) => [r.timestamp, r.value]);
  } finally {
    loading.value = false;
  }
}

const selectedPointMeta = computed(() =>
  trendPoints.value.find((p) => p.name === selectedPoint.value),
);
</script>

<template>
  <el-drawer v-model="visible" :title="device?.name ?? ''" size="440px">
    <template v-if="device">
      <!-- 基本信息 -->
      <div class="info-row">
        <StatusTag :status="snapshot?.status ?? 'Offline'" />
        <span class="meta">{{ device.deviceId }} · {{ device.line }}</span>
        <span class="meta">更新:{{ formatTime(snapshot?.timestamp) }}</span>
      </div>

      <!-- 实时点位表 -->
      <div class="panel-title" style="margin-top: 16px">实时数据</div>
      <table class="points-table">
        <tbody>
          <tr v-for="point in snapshot?.points ?? []" :key="point.name">
            <td class="p-name">{{ point.displayName }}</td>
            <td class="p-value" :class="{ warn: point.quality !== 'Good' }">
              {{ point.value.toFixed(2) }} {{ point.unit }}
            </td>
            <td class="p-quality">{{ point.quality === 'Good' ? '' : '⚠ 可疑' }}</td>
          </tr>
          <tr v-if="!snapshot?.points.length">
            <td class="p-name">设备离线,暂无实时数据</td>
          </tr>
        </tbody>
      </table>

      <!-- 历史趋势 -->
      <div class="panel-title" style="margin-top: 20px">最近 30 分钟趋势</div>
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
    </template>
  </el-drawer>
</template>

<style scoped>
.info-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.meta {
  font-size: 12px;
  color: var(--text-sub);
}
.points-table {
  width: 100%;
  border-collapse: collapse;
}
.points-table td {
  padding: 6px 4px;
  border-bottom: 1px solid var(--border);
  font-size: 13px;
}
.p-name {
  color: var(--text-sub);
}
.p-value {
  text-align: right;
  font-family: Consolas, monospace;
}
.p-value.warn {
  color: var(--warn);
  font-weight: 650;
}
.p-quality {
  width: 60px;
  text-align: right;
  color: var(--warn);
  font-size: 12px;
}
.trend-box {
  height: 280px;
  margin-top: 10px;
}
</style>
