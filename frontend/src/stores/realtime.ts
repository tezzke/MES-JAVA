import { defineStore } from 'pinia';
import { computed, reactive, ref, shallowRef } from 'vue';
import { mesApi } from '../api/mes';
import type {
  AlarmRecord,
  BarcodeRecord,
  DeviceMeta,
  DeviceSnapshot,
  DeviceStatus,
} from '../api/types';
import { createRealtimeConnection, type RealtimeConnection } from '../realtime/ws';

/**
 * 实时数据仓库(全局单例):整个前端的"数据中枢"。
 * 页面组件不直接碰 API/WebSocket,统一从这里取数,保证:
 *   - 多页面共享同一份实时数据,切换页面不重复建连;
 *   - 数据流向单一(WebSocket/REST → store → 组件),便于调试。
 */
export const useRealtimeStore = defineStore('realtime', () => {
  // ---------- 状态 ----------

  /** 设备档案(静态,加载一次) */
  const devices = ref<DeviceMeta[]>([]);

  /** 实时快照表:deviceId → 最新快照(reactive 以便按 key 更新) */
  const snapshots = reactive<Record<string, DeviceSnapshot>>({});

  /** 当前激活报警(id → 报警) */
  const activeAlarms = reactive<Record<number, AlarmRecord>>({});

  /** 最近报警事件流(触发+恢复,首页滚动展示,最多 50 条) */
  const alarmFeed = ref<AlarmRecord[]>([]);

  /** 最近扫码记录(最多 50 条) */
  const barcodeFeed = ref<BarcodeRecord[]>([]);

  /** 实时通道连接状态 */
  const connected = ref(false);

  /** 是否已初始化(避免重复建连) */
  const initialized = shallowRef(false);
  const connection = shallowRef<RealtimeConnection | null>(null);

  // ---------- 计算属性 ----------

  /** 各状态设备台数统计 */
  const statusCounts = computed(() => {
    const counts: Record<DeviceStatus, number> = { Running: 0, Standby: 0, Alarm: 0, Offline: 0 };
    for (const device of devices.value) {
      const status = snapshots[device.deviceId]?.status ?? 'Offline';
      counts[status] += 1;
    }
    return counts;
  });

  /** 全厂累计产量(把每台设备的产量计数点位求和) */
  const totalProduction = computed(() => {
    let total = 0;
    for (const device of devices.value) {
      const counterMeta = device.points.find((p) => p.isCounter);
      if (!counterMeta) continue;
      const value = snapshots[device.deviceId]?.points.find((p) => p.name === counterMeta.name);
      total += value?.value ?? 0;
    }
    return total;
  });

  /** 激活报警列表(按触发时间倒序) */
  const activeAlarmList = computed(() =>
    Object.values(activeAlarms).sort((a, b) => b.triggeredAt.localeCompare(a.triggeredAt)),
  );

  // ---------- 动作 ----------

  /** 应用初始化时调用一次:拉取基础数据 + 建立实时连接 */
  async function refresh() {
    const [deviceList, snapshotList, alarms, barcodes] = await Promise.all([
      mesApi.getDevices(),
      mesApi.getSnapshots(),
      mesApi.getActiveAlarms(),
      mesApi.getRecentBarcodes(20),
    ]);
    devices.value = deviceList;
    Object.keys(snapshots).forEach((key) => delete snapshots[key]);
    snapshotList.forEach((s) => (snapshots[s.deviceId] = s));
    Object.keys(activeAlarms).forEach((key) => delete activeAlarms[Number(key)]);
    alarms.forEach((a) => (activeAlarms[a.id] = a));
    barcodeFeed.value = barcodes;
  }

  async function init() {
    if (initialized.value) return;
    initialized.value = true;

    try {
      await refresh();
    } catch {
      initialized.value = false;
      throw new Error('实时监控初始化失败');
    }
    connection.value = createRealtimeConnection({
      onSnapshots: (list) => list.forEach((s) => (snapshots[s.deviceId] = s)),
      onAlarm: handleAlarm,
      onBarcode: (b) => {
        barcodeFeed.value = [b, ...barcodeFeed.value].slice(0, 50);
      },
      onStateChange: (state) => (connected.value = state),
      onReconnect: refresh,
    });
  }

  function dispose() {
    connection.value?.close();
    connection.value = null;
    initialized.value = false;
    connected.value = false;
    devices.value = [];
    Object.keys(snapshots).forEach((key) => delete snapshots[key]);
    Object.keys(activeAlarms).forEach((key) => delete activeAlarms[Number(key)]);
    alarmFeed.value = [];
    barcodeFeed.value = [];
  }

  /** 报警事件:resolvedAt 为空 = 新触发,不为空 = 恢复 */
  function handleAlarm(alarm: AlarmRecord) {
    if (alarm.resolvedAt == null) {
      activeAlarms[alarm.id] = alarm;
    } else {
      delete activeAlarms[alarm.id];
    }
    alarmFeed.value = [alarm, ...alarmFeed.value].slice(0, 50);
  }

  return {
    devices,
    snapshots,
    activeAlarms,
    alarmFeed,
    barcodeFeed,
    connected,
    statusCounts,
    totalProduction,
    activeAlarmList,
    init,
    refresh,
    dispose,
  };
});
