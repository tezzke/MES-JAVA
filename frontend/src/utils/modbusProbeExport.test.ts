import { describe, expect, it } from 'vitest';
import { csvCell, samplesToCsv, samplesToJson } from './modbusProbeExport';

describe('modbus probe export', () => {
  it('escapes commas, quotes and line breaks in CSV cells', () => {
    expect(csvCell('plain')).toBe('plain');
    expect(csvCell('a,b')).toBe('"a,b"');
    expect(csvCell('say "hello"')).toBe('"say ""hello"""');
    expect(csvCell('line 1\nline 2')).toBe('"line 1\nline 2"');
  });

  it('writes UTF-8 BOM and serializes register values', () => {
    const csv = samplesToCsv([{
      timestamp: '2026-08-14T08:00:00Z',
      success: false,
      registers: [{ address: 10, hex: '00FF', decimal: 255 }],
      error: 'timeout, retry',
    }]);

    expect(csv.charCodeAt(0)).toBe(0xfeff);
    expect(csv).toContain('timestamp,success,requestHex');
    expect(csv).toContain('"[{""address"":10,""hex"":""00FF"",""decimal"":255}]"');
    expect(csv).toContain('"timeout, retry"');
  });

  it('exports readable JSON without changing samples', () => {
    const samples = [{ requestHex: '01 03', parsedValue: 12.5 }];
    expect(JSON.parse(samplesToJson(samples))).toEqual(samples);
    expect(samplesToJson(samples)).toContain('\n  {');
  });
});
