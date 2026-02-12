export type TestResult = {
  testName: string;
  executionTimeMs: number; // -1 indicates failure or not applicable
  details: string;
  success: boolean;
};

// Extended aggregated statistics
export type AggregatedStats = {
  testName: string;
  samples: number;
  minMs: number;
  maxMs: number;
  avgMs: number;
  medianMs: number;
  stdMs: number;
  p25Ms: number;
  p75Ms: number;
  p95Ms: number;
  p99Ms: number;
  successes: number;
  failures: number;
};

export type User = {
  name: string;
  surname: string;
  age: number;
  active: boolean;
};

export type Post = {
  userId: number;
  id: number;
  title: string;
  body: string;
};

export function formatResult(r: TestResult): string {
  return `${r.testName}: ${r.executionTimeMs}ms`;
}

// Helper: compute median from sorted list
function median(sorted: number[]): number {
  if (sorted.length === 0) return 0;
  const mid = Math.floor(sorted.length / 2);
  if (sorted.length % 2 === 0) return (sorted[mid - 1] + sorted[mid]) / 2;
  return sorted[mid];
}

// Helper percentile (p between 0..1). Uses nearest-rank.
function percentile(sorted: number[], p: number): number {
  if (sorted.length === 0) return 0;
  const rank = Math.min(sorted.length - 1, Math.max(0, Math.ceil(p * sorted.length) - 1));
  return sorted[rank];
}

export function aggregatePerTest(results: TestResult[]): AggregatedStats[] {
  const byTest: Record<string, TestResult[]> = {};
  for (const r of results) {
    if (!byTest[r.testName]) byTest[r.testName] = [];
    byTest[r.testName].push(r);
  }
  return Object.entries(byTest).map(([testName, arr]) => {
    const times = arr.filter(a => a.executionTimeMs >= 0).map(a => a.executionTimeMs).sort((a, b) => a - b);
    const successes = arr.filter(a => a.success).length;
    const failures = arr.length - successes;
    const avgMs = times.length ? times.reduce((s, v) => s + v, 0) / times.length : 0;
    const stdMs = times.length ? Math.sqrt(times.reduce((s, v) => s + (v - avgMs) * (v - avgMs), 0) / times.length) : 0;
    return {
      testName,
      samples: arr.length,
      minMs: times[0] ?? 0,
      maxMs: times[times.length - 1] ?? 0,
      avgMs,
      medianMs: median(times),
      stdMs,
      p25Ms: percentile(times, 0.25),
      p75Ms: percentile(times, 0.75),
      p95Ms: percentile(times, 0.95),
      p99Ms: percentile(times, 0.99),
      successes,
      failures,
    } as AggregatedStats;
  });
}
