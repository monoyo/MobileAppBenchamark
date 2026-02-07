import type { TestResult } from '../app/types';
import { buildCsvForTest } from '../app/utils/csvExport';

describe('buildCsvForTest', () => {
  it('produces header and rows', () => {
    const res: TestResult[] = [
      { testName: 'CPU Test', executionTimeMs: 100, success: true, details: 'ok' },
      { testName: 'CPU Test', executionTimeMs: 110, success: true, details: 'ok2' }
    ];
    const csv = buildCsvForTest('CPU Test', res);
    const lines = csv.trim().split(/\n/);
    expect(lines[0]).toBe('iteration,executionTimeMs,details');
    expect(lines.length).toBe(3);
  });
});
