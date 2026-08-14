import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('./http', () => ({
  http: {
    post: vi.fn(),
    get: vi.fn(),
    delete: vi.fn(),
  },
}));

import { http } from './http';
import { modbusProbeApi, type ModbusProbeRequest } from './modbusProbe';

const request: ModbusProbeRequest = {
  ip: '192.168.10.20',
  port: 502,
  unitId: 1,
  functionCode: 3,
  address: 100,
  count: 2,
  dataType: 'FLOAT32',
  byteOrder: 'BIG_ENDIAN',
  wordOrder: 'HIGH_LOW',
  scale: 0.1,
  offset: 2,
};

describe('modbusProbeApi', () => {
  beforeEach(() => vi.clearAllMocks());

  it('将页面参数转换为后端只读契约', async () => {
    vi.mocked(http.post).mockResolvedValue({
      data: {
        success: true,
        registers: [0x1234, 0xabcd],
        rawValue: 12.5,
        parsedValue: 3.25,
      },
    });

    const result = await modbusProbeApi.read(request);

    expect(http.post).toHaveBeenCalledWith('/api/modbus-probe/read', expect.objectContaining({
      host: '192.168.10.20',
      startAddress: 100,
      dataType: 'Float32',
      wordOrder: 'HIGH_WORD_FIRST',
    }));
    expect(result.registers).toEqual([
      { address: 100, hex: '0x1234', decimal: 0x1234 },
      { address: 101, hex: '0xABCD', decimal: 0xabcd },
    ]);
    expect(result.parsedValue).toBe(12.5);
    expect(result.scaledValue).toBe(3.25);
  });

  it('轮询请求使用 read 包装并转换样本', async () => {
    vi.mocked(http.post).mockResolvedValue({
      data: {
        id: 'session-1',
        status: 'RUNNING',
        samples: [{ success: true, registers: [7], rawValue: 7, parsedValue: 9 }],
      },
    });

    const result = await modbusProbeApi.startSession({
      request: { ...request, count: 1, dataType: 'UINT16' },
      intervalMs: 1000,
      durationSeconds: 10,
    });

    expect(http.post).toHaveBeenCalledWith('/api/modbus-probe/sessions', expect.objectContaining({
      read: expect.objectContaining({ dataType: 'UInt16' }),
      intervalMs: 1000,
      durationSeconds: 10,
    }));
    expect(result.samples?.[0].registers?.[0]).toEqual({
      address: 100,
      hex: '0x0007',
      decimal: 7,
    });
  });
});
