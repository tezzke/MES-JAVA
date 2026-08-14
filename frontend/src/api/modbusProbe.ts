import { http } from './http';

export type ModbusDataType = 'INT16' | 'UINT16' | 'INT32' | 'UINT32' | 'FLOAT32';
export type ModbusByteOrder = 'BIG_ENDIAN' | 'LITTLE_ENDIAN';
export type ModbusWordOrder = 'HIGH_LOW' | 'LOW_HIGH';

/** 调试请求独立于实时采集模型，后端契约变化时只需调整本文件。 */
export interface ModbusProbeRequest {
  ip: string;
  port: number;
  unitId: number;
  functionCode: number;
  address: number;
  count: number;
  dataType: ModbusDataType;
  byteOrder: ModbusByteOrder;
  wordOrder: ModbusWordOrder;
  scale: number;
  offset: number;
}

export interface ModbusRegisterValue {
  address: number;
  hex: string;
  decimal: number;
}

export interface ModbusProbeResult {
  timestamp?: string;
  success?: boolean;
  requestHex?: string;
  responseHex?: string;
  registers?: ModbusRegisterValue[];
  parsedValue?: number | string | null;
  scaledValue?: number | string | null;
  elapsedMs?: number;
  error?: string | null;
}

export interface ModbusConnectTestResult {
  connected: boolean;
  elapsedMs?: number;
  requestHex?: string;
  responseHex?: string;
  error?: string | null;
}

export interface ModbusProbeSession {
  id: string;
  status?: 'RUNNING' | 'STOPPED' | 'COMPLETED' | 'FAILED' | string;
  startedAt?: string;
  expiresAt?: string;
  samples?: ModbusProbeResult[];
  error?: string | null;
}

export interface StartModbusProbeSessionRequest {
  request: ModbusProbeRequest;
  intervalMs: number;
  durationSeconds: number;
}

const BASE = '/api/modbus-probe';

type WireDataType = 'Int16' | 'UInt16' | 'Int32' | 'UInt32' | 'Float32';
type WireWordOrder = 'HIGH_WORD_FIRST' | 'LOW_WORD_FIRST';

interface WireReadRequest {
  host: string;
  port: number;
  unitId: number;
  functionCode: number;
  startAddress: number;
  count: number;
  dataType: WireDataType;
  decodeIndex: number;
  byteOrder: ModbusByteOrder;
  wordOrder: WireWordOrder;
  scale: number;
  offset: number;
}

interface WireReadResult {
  timestamp?: string;
  success?: boolean;
  requestHex?: string;
  responseHex?: string;
  registers?: number[];
  rawValue?: number | null;
  parsedValue?: number | null;
  elapsedMs?: number;
  errorMessage?: string | null;
}

interface WireConnectResult {
  success: boolean;
  elapsedMs?: number;
  errorMessage?: string | null;
}

interface WireSession {
  id: string;
  status?: string;
  startedAt?: string;
  finishedAt?: string;
  samples?: WireReadResult[];
}

const DATA_TYPES: Record<ModbusDataType, WireDataType> = {
  INT16: 'Int16',
  UINT16: 'UInt16',
  INT32: 'Int32',
  UINT32: 'UInt32',
  FLOAT32: 'Float32',
};

function toReadRequest(request: ModbusProbeRequest): WireReadRequest {
  return {
    host: request.ip,
    port: request.port,
    unitId: request.unitId,
    functionCode: request.functionCode,
    startAddress: request.address,
    count: request.count,
    dataType: DATA_TYPES[request.dataType],
    decodeIndex: 0,
    byteOrder: request.byteOrder,
    wordOrder: request.wordOrder === 'HIGH_LOW' ? 'HIGH_WORD_FIRST' : 'LOW_WORD_FIRST',
    scale: request.scale,
    offset: request.offset,
  };
}

function fromReadResult(result: WireReadResult, startAddress: number): ModbusProbeResult {
  return {
    timestamp: result.timestamp,
    success: result.success,
    requestHex: result.requestHex,
    responseHex: result.responseHex,
    registers: (result.registers ?? []).map((value, index) => ({
      address: startAddress + index,
      hex: `0x${value.toString(16).toUpperCase().padStart(4, '0')}`,
      decimal: value,
    })),
    parsedValue: result.rawValue,
    scaledValue: result.parsedValue,
    elapsedMs: result.elapsedMs,
    error: result.errorMessage,
  };
}

function fromSession(result: WireSession, startAddress: number): ModbusProbeSession {
  return {
    id: result.id,
    status: result.status,
    startedAt: result.startedAt,
    expiresAt: result.finishedAt,
    samples: (result.samples ?? []).map((sample) => fromReadResult(sample, startAddress)),
  };
}

export const modbusProbeApi = {
  connectTest: (request: ModbusProbeRequest) =>
    http.post<WireConnectResult>(`${BASE}/connect-test`, {
      host: request.ip,
      port: request.port,
    }).then((response): ModbusConnectTestResult => ({
      connected: response.data.success,
      elapsedMs: response.data.elapsedMs,
      error: response.data.errorMessage,
    })),
  read: (request: ModbusProbeRequest) => http
    .post<WireReadResult>(`${BASE}/read`, toReadRequest(request))
    .then((response) => fromReadResult(response.data, request.address)),
  startSession: (body: StartModbusProbeSessionRequest) => http
    .post<WireSession>(`${BASE}/sessions`, {
      read: toReadRequest(body.request),
      intervalMs: body.intervalMs,
      durationSeconds: body.durationSeconds,
    })
    .then((response) => fromSession(response.data, body.request.address)),
  getSession: (id: string, startAddress = 0) => http
    .get<WireSession>(`${BASE}/sessions/${encodeURIComponent(id)}`)
    .then((response) => fromSession(response.data, startAddress)),
  stopSession: (id: string, startAddress = 0) => http
    .delete<WireSession>(`${BASE}/sessions/${encodeURIComponent(id)}`)
    .then((response) => fromSession(response.data, startAddress)),
};
