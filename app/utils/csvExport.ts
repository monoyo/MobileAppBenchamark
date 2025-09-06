import * as FileSystem from 'expo-file-system';
import type { TestResult } from '../types';

// Build CSV (compatible header) for a single test's results across iterations.
export function buildCsvForTest(testName: string, results: TestResult[]): string {
  const header = 'iteration,executionTimeMs,details';
  const lines = results.map((r, idx) => `${idx + 1},${r.executionTimeMs},"${(r.details ?? '').replace(/"/g, '""')}"`);
  return [header, ...lines].join('\n');
}

export async function exportAllPerTestCsv(basenamePrefix: string, all: TestResult[]): Promise<string[]> {
  const by: Record<string, TestResult[]> = {};
  for (const r of all) {
    if (!by[r.testName]) by[r.testName] = [];
    by[r.testName].push(r);
  }
  const paths: string[] = [];
  for (const [testName, arr] of Object.entries(by)) {
    const csv = buildCsvForTest(testName, arr);
    const safe = testName.toLowerCase().replace(/[^a-z0-9_-]+/g, '_');
    const fileUri = `${FileSystem.documentDirectory}${basenamePrefix}_${safe}_${Date.now()}.csv`;
    try {
      await FileSystem.writeAsStringAsync(fileUri, csv, { encoding: FileSystem.EncodingType.UTF8 });
      paths.push(fileUri);
    } catch {
      // ignore failed file writes for individual tests
    }
  }
  return paths;
}
