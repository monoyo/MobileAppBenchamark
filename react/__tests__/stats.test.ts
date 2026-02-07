import { aggregatePerTest, type TestResult } from '../app/types';

function make(name: string, ms: number): TestResult { return { testName: name, executionTimeMs: ms, success: true }; }

describe('aggregatePerTest', () => {
  it('computes median and percentiles', () => {
    const data: TestResult[] = [10,20,30,40,50].map(v => make('CPU Test', v));
    const stats = aggregatePerTest(data).find(s => s.testName === 'CPU Test');
    expect(stats).toBeTruthy();
    expect(stats?.medianMs).toBe(30);
    // p95 on 5 samples -> rank ceil(0.95*5)=5 => value 50
    expect(stats?.p95Ms).toBe(50);
  });

  it('handles failures gracefully', () => {
    const data: TestResult[] = [
      { testName: 'API Test', executionTimeMs: 120, success: true },
      { testName: 'API Test', executionTimeMs: -1, success: false, details: 'timeout' }
    ];
    const stats = aggregatePerTest(data)[0];
    expect(stats.avgMs).toBe(120);
    expect(stats.failures).toBe(1);
  });
});
