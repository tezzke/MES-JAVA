import type { AlarmRecord, BarcodeRecord, DeviceSnapshot } from '../api/types';

/**
 * 实时通道封装(原生 WebSocket)。
 *
 * 连接后端 /hubs/realtime,收到的每条消息格式为 { event, data },按 event 分发给回调:
 *   OnSnapshots —— 设备快照(每个采集节拍)
 *   OnAlarm     —— 报警触发/恢复
 *   OnBarcode   —— 扫码记录
 *
 * 自带退避重连,断线期间页面数据保持最后状态,后端重启后浏览器无需刷新。
 */

/** 重连退避序列(毫秒),最后一档循环使用。 */
export const RECONNECT_DELAYS = [0, 1000, 3000, 5000, 10000, 30000] as const;
const MESSAGE_TIMEOUT = 60000;

export interface RealtimeHandlers {
  onSnapshots: (snapshots: DeviceSnapshot[]) => void;
  onAlarm: (alarm: AlarmRecord) => void;
  onBarcode: (barcode: BarcodeRecord) => void;
  onStateChange: (connected: boolean) => void;
  onReconnect?: () => void | Promise<void>;
}

export interface RealtimeConnection {
  /** 主动断开并停止重连(页面卸载时调用) */
  close: () => void;
}

/** 服务端推送的消息信封 */
interface RealtimeMessage {
  event: 'OnSnapshots' | 'OnAlarm' | 'OnBarcode';
  data: unknown;
}

export function createRealtimeConnection(handlers: RealtimeHandlers): RealtimeConnection {
  let socket: WebSocket | null = null;
  let timer: ReturnType<typeof setTimeout> | null = null;
  let watchdog: ReturnType<typeof setTimeout> | null = null;
  let attempt = 0;
  let disposed = false;
  let openedOnce = false;
  let authRejected = false;

  /** 与页面同源:开发时经 Vite proxy,生产时直连后端 */
  const endpoint = () => {
    const scheme = window.location.protocol === 'https:' ? 'wss' : 'ws';
    return `${scheme}://${window.location.host}/hubs/realtime`;
  };

  const dispatch = (raw: string) => {
    armWatchdog();
    let message: RealtimeMessage;
    try {
      message = JSON.parse(raw) as RealtimeMessage;
    } catch {
      return; // 非法报文直接忽略,不影响通道
    }
    switch (message.event) {
      case 'OnSnapshots':
        handlers.onSnapshots(message.data as DeviceSnapshot[]);
        break;
      case 'OnAlarm':
        handlers.onAlarm(message.data as AlarmRecord);
        break;
      case 'OnBarcode':
        handlers.onBarcode(message.data as BarcodeRecord);
        break;
    }
  };

  const armWatchdog = () => {
    if (watchdog) clearTimeout(watchdog);
    if (disposed) return;
    // 服务端没有 ping 协议，客户端只做静默超时检测，不主动发送任何报文。
    watchdog = setTimeout(() => socket?.close(), MESSAGE_TIMEOUT);
  };

  const scheduleReconnect = () => {
    if (disposed || authRejected || timer) return;
    const delay = RECONNECT_DELAYS[Math.min(attempt, RECONNECT_DELAYS.length - 1)];
    attempt += 1;
    timer = setTimeout(() => {
      timer = null;
      connect();
    }, delay);
  };

  const connect = () => {
    if (disposed) return;
    socket = new WebSocket(endpoint());

    socket.onopen = () => {
      attempt = 0; // 连上后重置退避
      handlers.onStateChange(true);
      armWatchdog();
      if (openedOnce) void handlers.onReconnect?.();
      openedOnce = true;
    };
    socket.onmessage = (event) => dispatch(event.data as string);
    socket.onclose = (event) => {
      if (watchdog) clearTimeout(watchdog);
      handlers.onStateChange(false);
      if (event.code === 4401 || event.code === 4403) {
        authRejected = true;
        return;
      }
      scheduleReconnect();
    };
    // onerror 之后浏览器一定会触发 onclose,重连逻辑只放在 onclose 里避免重复
    socket.onerror = () => socket?.close();
  };

  connect();
  const handleOnline = () => {
    if (disposed || authRejected || socket?.readyState === WebSocket.OPEN) return;
    if (timer) clearTimeout(timer);
    timer = null;
    connect();
  };
  window.addEventListener('online', handleOnline);

  return {
    close: () => {
      disposed = true;
      if (timer) clearTimeout(timer);
      if (watchdog) clearTimeout(watchdog);
      window.removeEventListener('online', handleOnline);
      socket?.close();
    },
  };
}
