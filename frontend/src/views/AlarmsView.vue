<script setup lang="ts">
/**
 * 报警中心:上方为当前激活报警(未恢复),下方为历史报警分页查询。
 * 激活报警通过实时通道自动刷新,历史报警按条件从数据库查询。
 */
import { computed, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { mesApi } from '../api/mes';
import { useRealtimeStore } from '../stores/realtime';
import { formatTime, formatValue } from '../utils/format';
import type { AlarmRecord } from '../api/types';

const store = useRealtimeStore();

/** 激活报警直接取实时 store(已按触发时间倒序),无需本页轮询 */
const activeAlarms = computed(() => store.activeAlarmList);

// ---- 历史报警查询条件 ----
const filterDevice = ref('');
const filterRange = ref<[Date, Date]>([new Date(Date.now() - 24 * 3600e3), new Date()]);
const page = ref(1);
const pageSize = ref(20);

const loading = ref(false);
const total = ref(0);
const rows = ref<AlarmRecord[]>([]);

const shortcuts = [
  { text: '最近 24 小时', value: () => [new Date(Date.now() - 24 * 3600e3), new Date()] },
  { text: '最近 7 天', value: () => [new Date(Date.now() - 7 * 24 * 3600e3), new Date()] },
  { text: '最近 30 天', value: () => [new Date(Date.now() - 30 * 24 * 3600e3), new Date()] },
];

async function query() {
  loading.value = true;
  try {
    const result = await mesApi.queryAlarms({
      deviceId: filterDevice.value || undefined,
      from: filterRange.value?.[0]?.toISOString(),
      to: filterRange.value?.[1]?.toISOString(),
      page: page.value,
      pageSize: pageSize.value,
    });
    rows.value = result.items;
    total.value = result.total;
  } catch {
    ElMessage.error('查询失败,请检查后端服务');
  } finally {
    loading.value = false;
  }
}

/** 条件变化时回到第一页重新查询 */
function search() {
  page.value = 1;
  query();
}

const levelTag = (level: string) => (level === 'Error' ? 'danger' : level === 'Warning' ? 'warning' : 'info');
const levelText = (level: string) =>
  level === 'Error' ? '报警' : level === 'Warning' ? '警告' : '提示';

/** 报警持续时长(未恢复的按当前时间算) */
function duration(alarm: AlarmRecord): string {
  const end = alarm.resolvedAt ? new Date(alarm.resolvedAt).getTime() : Date.now();
  const seconds = Math.max(0, Math.round((end - new Date(alarm.triggeredAt).getTime()) / 1000));
  if (seconds < 60) return `${seconds} 秒`;
  if (seconds < 3600) return `${Math.floor(seconds / 60)} 分 ${seconds % 60} 秒`;
  return `${Math.floor(seconds / 3600)} 小时 ${Math.floor((seconds % 3600) / 60)} 分`;
}

onMounted(query);
</script>

<template>
  <div class="page alarms-page">
    <!-- 当前激活报警 -->
    <div class="panel">
      <div class="panel-title">
        当前激活报警
        <el-tag v-if="activeAlarms.length" type="danger" size="small" effect="dark">
          {{ activeAlarms.length }}
        </el-tag>
      </div>
      <el-table :data="activeAlarms" size="small" height="240" empty-text="当前无激活报警">
        <el-table-column prop="deviceName" label="设备" width="150" />
        <el-table-column prop="pointName" label="点位" width="130" />
        <el-table-column label="等级" width="90">
          <template #default="{ row }">
            <el-tag :type="levelTag(row.level)" size="small" effect="dark">
              {{ levelText(row.level) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="message" label="报警内容" min-width="240" show-overflow-tooltip />
        <el-table-column label="触发值" width="110" align="right">
          <template #default="{ row }">{{ formatValue(row.value) }}</template>
        </el-table-column>
        <el-table-column label="触发时间" width="180">
          <template #default="{ row }">{{ formatTime(row.triggeredAt) }}</template>
        </el-table-column>
        <el-table-column label="已持续" width="130">
          <template #default="{ row }">{{ duration(row) }}</template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 历史报警 -->
    <div class="panel history-panel">
      <div class="panel-title">历史报警查询</div>
      <div class="query-bar">
        <el-select v-model="filterDevice" placeholder="全部设备" clearable style="width: 220px">
          <el-option
            v-for="device in store.devices"
            :key="device.deviceId"
            :label="device.name"
            :value="device.deviceId"
          />
        </el-select>
        <el-date-picker
          v-model="filterRange"
          type="datetimerange"
          range-separator="至"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
          :shortcuts="shortcuts"
          style="width: 380px"
        />
        <el-button type="primary" :loading="loading" @click="search">查询</el-button>
      </div>

      <el-table v-loading="loading" :data="rows" size="small" height="380">
        <el-table-column prop="deviceName" label="设备" width="150" />
        <el-table-column prop="pointName" label="点位" width="130" />
        <el-table-column label="等级" width="90">
          <template #default="{ row }">
            <el-tag :type="levelTag(row.level)" size="small">{{ levelText(row.level) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="message" label="报警内容" min-width="240" show-overflow-tooltip />
        <el-table-column label="触发时间" width="180">
          <template #default="{ row }">{{ formatTime(row.triggeredAt) }}</template>
        </el-table-column>
        <el-table-column label="恢复时间" width="180">
          <template #default="{ row }">
            <span v-if="row.resolvedAt">{{ formatTime(row.resolvedAt) }}</span>
            <el-tag v-else type="danger" size="small">未恢复</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="持续时长" width="130">
          <template #default="{ row }">{{ duration(row) }}</template>
        </el-table-column>
      </el-table>

      <el-pagination
        class="pager"
        layout="total, sizes, prev, pager, next"
        :total="total"
        v-model:current-page="page"
        v-model:page-size="pageSize"
        :page-sizes="[20, 50, 100]"
        @current-change="query"
        @size-change="search"
      />
    </div>
  </div>
</template>

<style scoped>
.alarms-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.query-bar {
  display: flex;
  gap: 12px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.pager {
  margin-top: 12px;
  justify-content: flex-end;
}
</style>
