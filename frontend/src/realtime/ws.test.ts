import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createRealtimeConnection, RECONNECT_DELAYS } from './ws';

class FakeWebSocket {
  static instances: FakeWebSocket[] = [];
  static readonly OPEN = 1;
  readyState = 0;
  onopen: (() => void) | null = null;
  onmessage: ((event: { data: string }) => void) | null = null;
  onclose: ((event: { code: number }) => void) | null = null;
  onerror: (() => void) | null = null;

  constructor(readonly url: string) {
    FakeWebSocket.instances.push(this);
  }

  close(code = 1000) {
    this.readyState = 3;
    this.onclose?.({ code });
  }
}

const handlers = () => ({
  onSnapshots: vi.fn(),
  onAlarm: vi.fn(),
  onBarcode: vi.fn(),
  onStateChange: vi.fn(),
});

describe('realtime websocket', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    FakeWebSocket.instances = [];
    vi.stubGlobal('WebSocket', FakeWebSocket);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.useRealTimers();
  });

  it('uses the required reconnect backoff', () => {
    expect(RECONNECT_DELAYS).toEqual([0, 1000, 3000, 5000, 10000, 30000]);
  });

  it('reconnects after an unexpected close', () => {
    const connection = createRealtimeConnection(handlers());
    expect(FakeWebSocket.instances).toHaveLength(1);
    FakeWebSocket.instances[0].close(1006);
    vi.runOnlyPendingTimers();
    expect(FakeWebSocket.instances).toHaveLength(2);
    connection.close();
  });

  it('does not reconnect after explicit close', () => {
    const connection = createRealtimeConnection(handlers());
    connection.close();
    vi.runAllTimers();
    expect(FakeWebSocket.instances).toHaveLength(1);
  });

  it('stops reconnecting after authorization rejection', () => {
    createRealtimeConnection(handlers());
    FakeWebSocket.instances[0].close(4403);
    vi.runAllTimers();
    expect(FakeWebSocket.instances).toHaveLength(1);
  });
});
