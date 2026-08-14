<script setup lang="ts">
/**
 * 历史曲线页:选择 设备 → 点位 → 时间范围,查询数据库中的遥测历史并绘制曲线。
 * 数据超过 maxPoints 时后端自动等距抽稀,前端无需关心数据量。
 */
import { computed, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import { mesApi } from '../api/mes';
import { useRealtimeStore } from '../stores/realtime';
import TrendChart from '../components/TrendChart.vue';

const store = useRealtimeStore();

const deviceId = ref('');
const pointName = ref('');
/** 默认查最近 1 小时 */
const timeRange = ref<[Date, Date]>([new Date(Date.now() - 3600 * 1000), new Date()]);

const loading = ref(false);
const trendData = ref<Array<[string, number]>>([]);
const queried = ref(false);

/** 当前设备可选的点位(排除状态字) */
const pointOptions = computed(
  () =>
    store.devices.find((d) => d.deviceId === deviceId.value)?.points.filter((p) => !p.isStatus) ??
    [],
);

const pointMeta = computed(() => pointOptions.value.find((p) => p.name === pointName.value));

/** 切换设备时重置点位选择 */
watch(deviceId, () => (pointName.value = pointOptions.value[0]?.name ?? ''));

/** 时间快捷选项 */
const shortcuts = [
  { text: '最近 1 小时', value: () => [new Date(Date.now() - 3600e3), new Date()] },
  { text: '最近 8 小时', value: () => [new Date(Date.now() - 8 * 3600e3), new Date()] },
  { text: '最近 24 小时', value: () => [new Date(Date.now() - 24 * 3600e3), new Date()] },
];

async function query() {
  if (!deviceId.value || !pointName.value) {
    ElMessage.warning('请先选择设备与点位');
    return;
  }
  loading.value = true;
  queried.value = true;
  try {
    const records = await mesApi.getHistory({
      deviceId: deviceId.value,
      pointName: pointName.value,
      from: timeRange.value[0].toISOString(),
      to: timeRange.value[1].toISOString(),
      maxPoints: 2000,
    });
    trendData.value = records.map((r) => [r.timestamp, r.value]);
    if (!records.length) ElMessage.info('该时间段内没有数据');
  } catch {
    ElMessage.error('查询失败,请检查后端服务');
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <div class="page history-page">
    <!-- 查询条件栏 -->
    <div class="panel query-bar">
      <el-select v-model="deviceId" placeholder="选择设备" filterable style="width: 220px">
        <el-option
          v-for="device in store.devices"
          :key="device.deviceId"
          :label="`${device.name} (${device.deviceId})`"
          :value="device.deviceId"
        />
      </el-select>

      <el-select v-model="pointName" placeholder="选择点位" style="width: 180px">
        <el-option
          v-for="point in pointOptions"
          :key="point.name"
          :label="point.displayName"
          :value="point.name"
        />
      </el-select>

      <el-date-picker
        v-model="timeRange"
        type="datetimerange"
        range-separator="至"
        start-placeholder="开始时间"
        end-placeholder="结束时间"
        :shortcuts="shortcuts"
        style="width: 380px"
      />

      <el-button type="primary" :loading="loading" @click="query">查询</el-button>
    </div>

    <!-- 曲线区域 -->
    <div class="panel chart-panel" v-loading="loading">
      <div class="panel-title">
        {{ pointMeta ? `${pointMeta.displayName}(${pointMeta.unit || '-'})` : '历史曲线' }}
        <span class="count" v-if="trendData.length">共 {{ trendData.length }} 个数据点</span>
      </div>
      <div class="chart-wrap">
        <TrendChart
          v-if="trendData.length"
          :data="trendData"
          :name="pointMeta?.displayName ?? ''"
          :unit="pointMeta?.unit"
        />
        <el-empty
          v-else
          :description="queried ? '该时间段内没有数据' : '选择设备与点位后点击查询'"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.history-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.query-bar {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
}
.chart-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.chart-wrap {
  flex: 1;
  min-height: 420px;
}
.count {
  font-size: 12px;
  color: var(--text-sub);
  font-weight: 400;
}
</style>
