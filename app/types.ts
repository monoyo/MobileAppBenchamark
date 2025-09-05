export type TestResult = {
  testName: string;
  group?: string; // logical group identifier
  executionTimeMs: number; // -1 indicates failure or not applicable
  success: boolean;
  details?: string;
};

export type GroupAverage = {
  group: string;
  averageMs: number;
  samples: number;
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
  return `${r.testName}${r.group ? ' [' + r.group + ']' : ''}: ${r.executionTimeMs}ms${r.details ? ' (' + r.details + ')' : ''}`;
}
