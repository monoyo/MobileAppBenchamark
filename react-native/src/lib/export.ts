import * as FileSystem from 'expo-file-system';
import { buildCsv } from './csv';
import { TestResult } from './testTypes';

const ensureDir = async (dir: string): Promise<void> => {
  const info = await FileSystem.getInfoAsync(dir);
  if (!info.exists) {
    await FileSystem.makeDirectoryAsync(dir, { intermediates: true });
  }
};

export async function writeCsvFiles(
  resultsByTest: Record<string, TestResult[]>,
  runId: string,
): Promise<{ paths: string[] }> {
  const written: string[] = [];

  const now = new Date().toISOString().replace(/[:.]/g, '-');

  // Expo Go: use sandbox documentDirectory; on Android, this resolves under app files
  const base = FileSystem.documentDirectory ?? FileSystem.cacheDirectory ?? '/';
  const targets: string[] = [
    `${base}benchmarks/run-${runId}-${now}/`
  ];

  for (const dir of targets) {
    try {
      await ensureDir(dir);
      for (const [testName, list] of Object.entries(resultsByTest)) {
        const safeName = testName.replace(/[^a-zA-Z0-9-_]+/g, '_');
        const path = `${dir}${safeName}.csv`;
        const csv = buildCsv(testName, list);
        await FileSystem.writeAsStringAsync(path, csv, { encoding: FileSystem.EncodingType.UTF8 });
        const ok = (await FileSystem.getInfoAsync(path)).exists;
        if (ok) written.push(path);
      }
    } catch (e) {
      // continue to next target
    }
  }

  return { paths: written };
}
