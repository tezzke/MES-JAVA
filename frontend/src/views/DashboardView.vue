<script setup lang="ts">
/**
 * 生产总览页:统计卡 + 设备卡片网格(按工段分组)+ 实时报警滚动列表。
 * 数据全部来自 realtime store,本页无任何网络请求。
 */
import { computed, ref } from 'vue';
import DeviceCard from '../components/DeviceCard.vue';
import DeviceDetailDrawer from '../components/DeviceDetailDrawer.vue';
import { useRealtimeStore } from '../stores/realtime';
import { formatTime, statusColor } from '../utils/format';

const store = useRealtimeStore();
const selectedDevice = ref<string | null>(null);

/** 设备按工段分组展示 */
const lineGroups = computed(() => {
  const groups = new Map<string, typeof store.devices>();
  for (const device of store.devices) {
    if (!groups.has(device.line)) groups.set(device.line, []);
    groups.get(device.line)!.push(device);
  }
  return [...groups.entries()];
});

const stats = computed(() => [
  { label: '在册设备', value: store.devices.length, color: 'var(--accent)' },
  { label: '运行中', value: store.statusCounts.Running, color: statusColor.Running },
  { label: '待机', value: store.statusCounts.Standby, color: statusColor.Standby },
  { label: '报警中', value: store.statusCounts.Alarm, color: statusColor.Alarm },
  { label: '离线', value: store.statusCounts.Offline, color: statusColor.Offline },
  { label: '累计产量', value: store.totalProduction.toLocaleString(), color: '#8b9cff' },
]);
</script>

<template>
  <div class="page dashboard">
    <!-- 统计卡片行 -->
    <div class="stats-row">
      <div v-for="stat in stats" :key="stat.label" class="panel stat-card">
        <div class="stat-value" :style="{ color: stat.color }">{{ stat.value }}</div>
        <div class="stat-label">{{ stat.label }}</div>
      </div>
    </div>

    <div class="main-row">
      <!-- 设备网格(按工段分组) -->
      <div class="devices-col">
        <div v-for="[line, devices] in lineGroups" :key="line" class="line-group">
          <div class="panel-title">{{ line }}</div>
          <div class="cards-grid">
            <DeviceCard
              v-for="device in devices"
              :key="device.deviceId"
              :device="device"
              :snapshot="store.snapshots[device.deviceId]"
              @select="selectedDevice = $event"
            />
          </div>
        </div>
      </div>

      <!-- 实时报警侧栏 -->
      <div class="panel alarms-col">
        <div class="panel-title">现场报警</div>
        <div class="alarm-list">
          <div
            v-for="alarm in store.alarmFeed"
            :key="alarm.id + (alarm.resolvedAt ?? '')"
            class="alarm-item"
            :class="{ resolved: alarm.resolvedAt }"
          >
            <div class="alarm-msg">
              <span
                class="alarm-badge"
                :class="alarm.resolvedAt ? 'ok' : alarm.level.toLowerCase()"
              >
                {{ alarm.resolvedAt ? '恢复' : alarm.level === 'Warning' ? '警告' : '报警' }}
              </span>
              {{ alarm.message }}
            </div>
            <div class="alarm-time">{{ formatTime(alarm.resolvedAt ?? alarm.triggeredAt) }}</div>
          </div>
          <div v-if="!store.alarmFeed.length" class="empty">现场暂无报警</div>
        </div>
      </div>
    </div>

    <DeviceDetailDrawer :device-id="selectedDevice" @close="selectedDevice = null" />
  </div>
</template>

<style scoped>
.dashboard {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.stats-row {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 12px;
}
.stat-card {
  text-align: center;
  padding: 14px 8px;
}
.stat-value {
  font-size: 26px;
  font-weight: 700;
  font-family: var(--font-mono);
  font-variant-numeric: tabular-nums;
}
.stat-label {
  font-size: 12px;
  color: var(--text-sub);
  margin-top: 4px;
}
.main-row {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}
.devices-col {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 18px;
}
.cards-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(210px, 1fr));
  gap: 12px;
}
.alarms-col {
  width: 320px;
  flex-shrink: 0;
  max-height: calc(100vh - 200px);
  display: flex;
  flex-direction: column;
}
.alarm-list {
  overflow: auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.alarm-item {
  padding: 8px 10px;
  background: #fef2f2;
  border: 1px solid #fecaca;
  border-radius: 8px;
  font-size: 12.5px;
}
.alarm-item.resolved {
  background: #f0fdf4;
  border-color: #bbf7d0;
  color: var(--text-sub);
}
.alarm-badge {
  display: inline-block;
  padding: 0 6px;
  border-radius: 4px;
  font-size: 11px;
  margin-right: 6px;
  color: #ffffff;
}
.alarm-badge.error {
  background: var(--danger);
}
.alarm-badge.warning {
  background: var(--warn);
}
.alarm-badge.ok {
  background: var(--ok);
}
.alarm-time {
  margin-top: 4px;
  color: var(--text-sub);
  font-size: 11px;
  text-align: right;
}
.empty {
  color: var(--text-sub);
  text-align: center;
  padding: 30px 0;
}
</style>
