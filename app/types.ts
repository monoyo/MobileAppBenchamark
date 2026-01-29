export type TestResult = {
  testName: string;
  group?: string; // logical group identifier
  executionTimeMs: number; // -1 indicates failure or not applicable
  success: boolean;
  details?: string;
  fps?: number; // Added FPS field
};

export type GroupAverage = {
  group: string;
  averageMs: number;
  samples: number;
};

// Extended aggregated statistics (kept separate to avoid breaking existing flows)
export type AggregatedStats = {
  testName: string;
  samples: number;
  minMs: number;
  maxMs: number;
  avgMs: number;
  medianMs: number;
  p95Ms: number;
  p99Ms: number;
  successes: number;
  failures: number;
};

export function computeGroupAverages(results: TestResult[]): GroupAverage[] {
  const byGroup: Record<string, { sum: number; count: number }> = {};
  for (const r of results) {
    if (!r.group) continue; // skip un-grouped
    if (!byGroup[r.group]) byGroup[r.group] = { sum: 0, count: 0 };
    if (r.executionTimeMs >= 0) {
      byGroup[r.group].sum += r.executionTimeMs;
      byGroup[r.group].count += 1;
    }
  }
  return Object.entries(byGroup).map(([group, v]) => ({ group, averageMs: v.count ? v.sum / v.count : 0, samples: v.count }));
}

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
  const fpsStr = r.fps ? ` [FPS: ${r.fps.toFixed(1)}]` : '';
  return `${r.testName}${r.group ? ' [' + r.group + ']' : ''}: ${r.executionTimeMs}ms${fpsStr}`;
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
    return {
      testName,
      samples: arr.length,
      minMs: times[0] ?? 0,
      maxMs: times[times.length - 1] ?? 0,
      avgMs: times.length ? times.reduce((s, v) => s + v, 0) / times.length : 0,
      medianMs: median(times),
      p95Ms: percentile(times, 0.95),
      p99Ms: percentile(times, 0.99),
      successes,
      failures,
    } as AggregatedStats;
  });
}
