<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import {
  modbusProbeApi,
  type ModbusConnectTestResult,
  type ModbusProbeRequest,
  type ModbusProbeResult,
  type ModbusProbeSession,
} from '../api/modbusProbe';
import { downloadText, samplesToCsv, samplesToJson } from '../utils/modbusProbeExport';

const form = reactive<ModbusProbeRequest>({
  ip: '192.168.1.10',
  port: 502,
  unitId: 1,
  functionCode: 3,
  address: 0,
  count: 2,
  dataType: 'FLOAT32',
  byteOrder: 'BIG_ENDIAN',
  wordOrder: 'HIGH_LOW',
  scale: 1,
  offset: 0,
});
const intervalMs = ref(1000);
const durationSeconds = ref(60);
const busy = ref('');
const connectResult = ref<ModbusConnectTestResult | null>(null);
const latest = ref<ModbusProbeResult | null>(null);
const samples = ref<ModbusProbeResult[]>([]);
const session = ref<ModbusProbeSession | null>(null);
let refreshTimer: ReturnType<typeof setInterval> | undefined;

const running = computed(() => session.value?.status?.toUpperCase() === 'RUNNING');
const sessionId = computed(() => session.value?.id ?? '');

function request(): ModbusProbeRequest {
  return { ...form };
}

function errorMessage(error: unknown): string {
  const candidate = error as { response?: { data?: { message?: string } }; message?: string };
  return candidate.response?.data?.message ?? candidate.message ?? '请求失败，请检查参数和后端服务';
}

function validate(): boolean {
  const invalid = !form.ip.trim()
    ? '请输入设备 IP'
    : form.port < 1 || form.port > 65535
      ? '端口范围应为 1-65535'
      : form.unitId < 0 || form.unitId > 255
        ? 'Unit ID 范围应为 0-255'
        : ![3, 4].includes(form.functionCode)
          ? '现场诊断只允许只读功能码 03/04'
        : form.address < 0 || form.count < 1
          ? '地址和数量不合法'
          : '';
  if (invalid) {
    ElMessage.warning(invalid);
    return false;
  }
  return true;
}

async function connectTest() {
  if (!validate()) return;
  busy.value = 'connect';
  connectResult.value = null;
  try {
    connectResult.value = await modbusProbeApi.connectTest(request());
    ElMessage[connectResult.value.connected ? 'success' : 'warning'](
      connectResult.value.connected ? '连接成功' : connectResult.value.error ?? '连接失败',
    );
  } catch (error) {
    connectResult.value = { connected: false, error: errorMessage(error) };
  } finally {
    busy.value = '';
  }
}

async function readOnce() {
  if (!validate()) return;
  busy.value = 'read';
  try {
    latest.value = await modbusProbeApi.read(request());
    samples.value = [latest.value, ...samples.value];
  } catch (error) {
    latest.value = { success: false, error: errorMessage(error), timestamp: new Date().toISOString() };
  } finally {
    busy.value = '';
  }
}

function stopAutoRefresh() {
  if (refreshTimer) clearInterval(refreshTimer);
  refreshTimer = undefined;
}

async function querySamples(silent = false) {
  if (!sessionId.value) {
    if (!silent) ElMessage.warning('请先启动轮询会话');
    return;
  }
  if (!silent) busy.value = 'query';
  try {
    const result = await modbusProbeApi.getSession(sessionId.value, form.address);
    session.value = result;
    samples.value = result.samples ?? [];
    latest.value = samples.value.at(-1) ?? latest.value;
    if (result.status?.toUpperCase() !== 'RUNNING') stopAutoRefresh();
  } catch (error) {
    if (!silent) ElMessage.error(errorMessage(error));
  } finally {
    if (!silent) busy.value = '';
  }
}

async function startPolling() {
  if (!validate()) return;
  if (running.value) await stopPolling();
  busy.value = 'start';
  samples.value = [];
  latest.value = null;
  durationSeconds.value = Math.min(60, Math.max(1, durationSeconds.value));
  try {
    session.value = await modbusProbeApi.startSession({
      request: request(),
      intervalMs: Math.max(200, intervalMs.value),
      durationSeconds: durationSeconds.value,
    });
    if (!session.value.id) throw new Error('后端未返回轮询会话 ID');
    session.value.status ??= 'RUNNING';
    stopAutoRefresh();
    refreshTimer = setInterval(() => void querySamples(true), Math.max(500, intervalMs.value));
    ElMessage.success(`轮询已启动，最长 ${durationSeconds.value} 秒`);
  } catch (error) {
    session.value = null;
    ElMessage.error(errorMessage(error));
  } finally {
    busy.value = '';
  }
}

async function stopPolling() {
  if (!sessionId.value) return;
  busy.value = 'stop';
  stopAutoRefresh();
  try {
    const result = await modbusProbeApi.stopSession(sessionId.value, form.address);
    if (result) session.value = result;
    else if (session.value) session.value.status = 'STOPPED';
    await querySamples(true);
  } catch (error) {
    ElMessage.error(errorMessage(error));
  } finally {
    busy.value = '';
  }
}

function exportSamples(format: 'json' | 'csv') {
  if (!samples.value.length) return ElMessage.warning('暂无可导出的样本');
  const stamp = new Date().toISOString().replace(/[:.]/g, '-');
  if (format === 'json') {
    downloadText(`modbus-probe-${stamp}.json`, samplesToJson(samples.value), 'application/json;charset=utf-8');
  } else {
    downloadText(`modbus-probe-${stamp}.csv`, samplesToCsv(samples.value), 'text/csv;charset=utf-8');
  }
}

onBeforeUnmount(stopAutoRefresh);
</script>

<template>
  <div class="page probe-page">
    <section class="panel">
      <div class="panel-title">Modbus TCP 请求参数</div>
      <el-form :model="form" label-position="top" class="probe-form">
        <el-form-item label="IP">
          <el-input v-model.trim="form.ip" placeholder="192.168.1.10" />
        </el-form-item>
        <el-form-item label="Port">
          <el-input-number v-model="form.port" :min="1" :max="65535" controls-position="right" />
        </el-form-item>
        <el-form-item label="Unit ID">
          <el-input-number v-model="form.unitId" :min="0" :max="255" controls-position="right" />
        </el-form-item>
        <el-form-item label="Function Code">
          <el-select v-model="form.functionCode">
            <el-option label="03 - Read Holding Registers" :value="3" />
            <el-option label="04 - Read Input Registers" :value="4" />
          </el-select>
        </el-form-item>
        <el-form-item label="Address">
          <el-input-number v-model="form.address" :min="0" :max="65535" controls-position="right" />
        </el-form-item>
        <el-form-item label="Count">
          <el-input-number v-model="form.count" :min="1" :max="125" controls-position="right" />
        </el-form-item>
        <el-form-item label="Data Type">
          <el-select v-model="form.dataType">
            <el-option v-for="item in ['INT16','UINT16','INT32','UINT32','FLOAT32']" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="Byte Order">
          <el-select v-model="form.byteOrder">
            <el-option label="Big Endian" value="BIG_ENDIAN" />
            <el-option label="Little Endian" value="LITTLE_ENDIAN" />
          </el-select>
        </el-form-item>
        <el-form-item label="Word Order">
          <el-select v-model="form.wordOrder">
            <el-option label="High → Low" value="HIGH_LOW" />
            <el-option label="Low → High" value="LOW_HIGH" />
          </el-select>
        </el-form-item>
        <el-form-item label="Scale">
          <el-input-number v-model="form.scale" :precision="6" controls-position="right" />
        </el-form-item>
        <el-form-item label="Offset">
          <el-input-number v-model="form.offset" :precision="6" controls-position="right" />
        </el-form-item>
      </el-form>
      <div class="actions">
        <el-button :loading="busy === 'connect'" @click="connectTest">连接测试</el-button>
        <el-button type="primary" :loading="busy === 'read'" @click="readOnce">单次读取</el-button>
        <span class="poll-setting">间隔 <el-input-number v-model="intervalMs" :min="200" :max="10000" :step="100" /> ms</span>
        <span class="poll-setting">时长 <el-input-number v-model="durationSeconds" :min="1" :max="60" /> 秒</span>
        <el-button type="success" :disabled="running" :loading="busy === 'start'" @click="startPolling">启动轮询</el-button>
        <el-button :disabled="!sessionId" :loading="busy === 'query'" @click="querySamples()">查询样本</el-button>
        <el-button type="danger" plain :disabled="!running" :loading="busy === 'stop'" @click="stopPolling">停止</el-button>
      </div>
      <div v-if="connectResult" class="connect-result" :class="{ ok: connectResult.connected }">
        <div>
          {{ connectResult.connected ? '连接成功' : '连接失败' }}
          <span v-if="connectResult.elapsedMs != null"> · {{ connectResult.elapsedMs }} ms</span>
          <span v-if="connectResult.error"> · {{ connectResult.error }}</span>
        </div>
        <div v-if="connectResult.requestHex || connectResult.responseHex" class="connect-hex">
          请求 Hex：<code>{{ connectResult.requestHex ?? '-' }}</code>
          · 响应 Hex：<code>{{ connectResult.responseHex ?? '-' }}</code>
        </div>
      </div>
    </section>

    <section class="panel result-panel">
      <div class="panel-title">
        最新结果
        <el-tag v-if="session" size="small" :type="running ? 'success' : 'info'">
          {{ session.status ?? 'UNKNOWN' }} · {{ session.id }}
        </el-tag>
      </div>
      <el-descriptions v-if="latest" :column="4" border>
        <el-descriptions-item label="时间">{{ latest.timestamp ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="耗时">{{ latest.elapsedMs == null ? '-' : `${latest.elapsedMs} ms` }}</el-descriptions-item>
        <el-descriptions-item label="解析值">{{ latest.parsedValue ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="缩放值">{{ latest.scaledValue ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="请求 Hex" :span="2"><code>{{ latest.requestHex ?? '-' }}</code></el-descriptions-item>
        <el-descriptions-item label="响应 Hex" :span="2"><code>{{ latest.responseHex ?? '-' }}</code></el-descriptions-item>
        <el-descriptions-item label="错误" :span="4">
          <span class="error-text">{{ latest.error ?? '-' }}</span>
        </el-descriptions-item>
      </el-descriptions>
      <el-empty v-else description="连接测试或读取后显示结果" :image-size="70" />
      <el-table v-if="latest?.registers?.length" :data="latest.registers" size="small" class="register-table">
        <el-table-column prop="address" label="寄存器地址" />
        <el-table-column prop="hex" label="Hex" />
        <el-table-column prop="decimal" label="Decimal" />
      </el-table>
    </section>

    <section class="panel table-panel">
      <div class="sample-header">
        <div class="panel-title">轮询样本（{{ samples.length }}）</div>
        <div>
          <el-button size="small" :disabled="!samples.length" @click="exportSamples('json')">导出 JSON</el-button>
          <el-button size="small" :disabled="!samples.length" @click="exportSamples('csv')">导出 CSV</el-button>
        </div>
      </div>
      <el-table :data="samples" max-height="360" empty-text="暂无样本">
        <el-table-column prop="timestamp" label="时间" min-width="170" />
        <el-table-column prop="requestHex" label="请求 Hex" min-width="150" show-overflow-tooltip />
        <el-table-column prop="responseHex" label="响应 Hex" min-width="150" show-overflow-tooltip />
        <el-table-column label="寄存器 Hex / Decimal" min-width="190" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.registers?.map((item: { hex: string; decimal: number }) => `${item.hex} / ${item.decimal}`).join(', ') ?? '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="parsedValue" label="解析值" />
        <el-table-column prop="scaledValue" label="缩放值" />
        <el-table-column prop="elapsedMs" label="耗时(ms)" width="100" />
        <el-table-column prop="error" label="错误" min-width="150" show-overflow-tooltip />
      </el-table>
    </section>
  </div>
</template>

<style scoped>
.probe-page{display:flex;flex-direction:column;gap:14px}.probe-form{display:grid;grid-template-columns:repeat(6,minmax(130px,1fr));gap:0 12px}.probe-form :deep(.el-input-number),.probe-form :deep(.el-select){width:100%}.actions{display:flex;align-items:center;gap:10px;flex-wrap:wrap;border-top:1px solid var(--border);padding-top:14px}.poll-setting{display:flex;align-items:center;gap:6px;color:var(--text-sub)}.poll-setting :deep(.el-input-number){width:115px}.connect-result{margin-top:12px;color:#ef4444}.connect-result.ok{color:#22c55e}.connect-hex{margin-top:5px;color:var(--text-sub);word-break:break-all}.connect-hex code,.result-panel code{white-space:normal;word-break:break-all;color:var(--accent)}.error-text{color:#f87171}.register-table{margin-top:12px}.sample-header{display:flex;justify-content:space-between;align-items:center}.sample-header .panel-title{margin-bottom:12px}@media(max-width:1200px){.probe-form{grid-template-columns:repeat(3,minmax(150px,1fr))}}@media(max-width:720px){.probe-form{grid-template-columns:1fr 1fr}}
</style>
