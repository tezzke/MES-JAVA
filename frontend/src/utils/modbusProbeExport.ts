import type { ModbusProbeResult } from '../api/modbusProbe';

const CSV_HEADERS = [
  'timestamp',
  'success',
  'requestHex',
  'responseHex',
  'registers',
  'parsedValue',
  'scaledValue',
  'elapsedMs',
  'error',
] as const;

export function csvCell(value: unknown): string {
  if (value == null) return '';
  const text = typeof value === 'object' ? JSON.stringify(value) : String(value);
  return /[",\r\n]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text;
}

export function samplesToCsv(samples: ModbusProbeResult[]): string {
  const lines = [
    CSV_HEADERS.join(','),
    ...samples.map((sample) =>
      CSV_HEADERS.map((key) => csvCell(sample[key])).join(',')),
  ];
  return `\uFEFF${lines.join('\r\n')}`;
}

export function samplesToJson(samples: ModbusProbeResult[]): string {
  return JSON.stringify(samples, null, 2);
}

export function downloadText(filename: string, content: string, type: string): void {
  const url = URL.createObjectURL(new Blob([content], { type }));
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = filename;
  anchor.click();
  URL.revokeObjectURL(url);
}
