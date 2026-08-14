<script setup lang="ts">
/**
 * 扫码追溯:左侧实时扫码流(实时通道推送),右侧按条码/设备/时间查询历史扫码记录。
 * 支持点击实时记录直接把条码填入查询框,快速追溯单个产品的流转轨迹。
 */
import { computed, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { mesApi } from '../api/mes';
import { useRealtimeStore } from '../stores/realtime';
import { formatTime } from '../utils/format';
import type { BarcodeRecord } from '../api/types';

const store = useRealtimeStore();

const keyword = ref('');
const filterDevice = ref('');
const filterRange = ref<[Date, Date] | null>(null);
const page = ref(1);
const pageSize = ref(20);

const loading = ref(false);
const total = ref(0);
const rows = ref<BarcodeRecord[]>([]);

/** 设备编码 → 设备名称,用于表格里显示可读名称 */
const deviceNames = computed(
  () => new Map(store.devices.map((d) => [d.deviceId, d.name])),
);
const deviceName = (deviceId: string) => deviceNames.value.get(deviceId) ?? deviceId;

const shortcuts = [
  { text: '今天', value: () => [new Date(new Date().setHours(0, 0, 0, 0)), new Date()] },
  { text: '最近 7 天', value: () => [new Date(Date.now() - 7 * 24 * 3600e3), new Date()] },
  { text: '最近 30 天', value: () => [new Date(Date.now() - 30 * 24 * 3600e3), new Date()] },
];

async function query() {
  loading.value = true;
  try {
    const result = await mesApi.queryBarcodes({
      keyword: keyword.value || undefined,
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

function search() {
  page.value = 1;
  query();
}

/** 点击实时扫码记录:带着条码去追溯 */
function traceBarcode(barcode: string) {
  keyword.value = barcode;
  filterDevice.value = '';
  filterRange.value = null;
  search();
}

onMounted(query);
</script>

<template>
  <div class="page trace-page">
    <!-- 实时扫码流 -->
    <div class="panel live-col">
      <div class="panel-title">
        实时扫码
        <span class="hint">点击可追溯</span>
      </div>
      <div class="live-list">
        <div
          v-for="record in store.barcodeFeed"
          :key="record.id"
          class="live-item"
          @click="traceBarcode(record.barcode)"
        >
          <div class="code">{{ record.barcode }}</div>
          <div class="meta">
            <span>{{ deviceName(record.deviceId) }}</span>
            <span>{{ formatTime(record.scannedAt) }}</span>
          </div>
        </div>
        <div v-if="!store.barcodeFeed.length" class="empty">等待扫码数据…</div>
      </div>
    </div>

    <!-- 历史查询 -->
    <div class="panel query-col">
      <div class="panel-title">扫码记录查询</div>
      <div class="query-bar">
        <el-input
          v-model="keyword"
          placeholder="条码(支持模糊匹配)"
          clearable
          style="width: 240px"
          @keyup.enter="search"
        />
        <el-select v-model="filterDevice" placeholder="全部设备" clearable style="width: 200px">
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
          style="width: 360px"
        />
        <el-button type="primary" :loading="loading" @click="search">查询</el-button>
      </div>

      <el-table v-loading="loading" :data="rows" size="small" height="calc(100vh - 320px)">
        <el-table-column prop="barcode" label="条码" min-width="220">
          <template #default="{ row }">
            <span class="code-cell" @click="traceBarcode(row.barcode)">{{ row.barcode }}</span>
          </template>
        </el-table-column>
        <el-table-column label="扫码设备" min-width="180">
          <template #default="{ row }">{{ deviceName(row.deviceId) }}</template>
        </el-table-column>
        <el-table-column prop="scannerId" label="扫码枪" width="150" />
        <el-table-column label="扫码时间" width="190">
          <template #default="{ row }">{{ formatTime(row.scannedAt) }}</template>
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
.trace-page {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}
.live-col {
  width: 280px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  max-height: calc(100vh - 150px);
}
.live-list {
  overflow: auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.live-item {
  padding: 8px 10px;
  border: 1px solid var(--border);
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.15s;
}
.live-item:hover {
  border-color: var(--accent);
  background: rgba(56, 189, 248, 0.07);
}
.code {
  font-family: Consolas, monospace;
  font-size: 13px;
  color: var(--text-main);
}
.meta {
  display: flex;
  justify-content: space-between;
  font-size: 11px;
  color: var(--text-sub);
  margin-top: 4px;
}
.query-col {
  flex: 1;
  min-width: 0;
}
.query-bar {
  display: flex;
  gap: 12px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.code-cell {
  font-family: Consolas, monospace;
  color: var(--accent);
  cursor: pointer;
}
.hint {
  font-size: 12px;
  color: var(--text-sub);
  font-weight: 400;
}
.pager {
  margin-top: 12px;
  justify-content: flex-end;
}
.empty {
  color: var(--text-sub);
  text-align: center;
  padding: 30px 0;
  font-size: 13px;
}
</style>
