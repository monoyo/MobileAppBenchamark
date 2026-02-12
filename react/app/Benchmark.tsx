import * as FileSystem from 'expo-file-system';
import { Href } from 'expo-router';
import React from 'react';
import { Alert, Platform, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { EXPORT_FILE_PREFIX } from './constants/testConfig';
import type { AggregatedStats, TestResult } from './types';
import { aggregatePerTest, formatResult } from './types';
import { exportAllPerTestCsv } from './utils/csvExport';
import { getLaunchTime, markSuiteReady } from './utils/launchTime';
import { createResultKey, waitForResult } from './utils/navResult';
// eslint-disable-next-line @typescript-eslint/no-explicit-any
declare const require: any;
let Clipboard: { setStringAsync?: (s: string) => Promise<void> } = {} as any;
try { Clipboard = require('expo-clipboard'); } catch { }

// Test definicje (zachowane z poprzedniego ekranu suite)
const TESTS: { name: string; route: Href; group: string }[] = [
  { name: 'UI Test', route: '/ui-test', group: 'ui' },
  { name: 'CPU Test', route: '/cpu-test', group: 'cpu' },
  { name: 'RAM Test', route: '/ram-test', group: 'memory' },
  { name: 'Image Loading Test', route: '/image-test', group: 'io' },
  { name: 'API Test', route: '/api-test', group: 'network' },
  { name: 'Location Test', route: '/location-test', group: 'sensors' },
];

export default function Benchmark() {
  const [ready, setReady] = React.useState(false);
  const [launchMs, setLaunchMs] = React.useState<number | null>(getLaunchTime());
  const [results, setResults] = React.useState<TestResult[]>([]);
  const [running, setRunning] = React.useState(false);
  const [iteration, setIteration] = React.useState(0);
  const [testIndex, setTestIndex] = React.useState(0);
  const [lastSavedPath, setLastSavedPath] = React.useState<string | null>(null);
  const iterations = 30;
  const launchTimeRef = React.useRef<number | null>(null);

  React.useEffect(() => {
    const t = setTimeout(() => {
      markSuiteReady();
      setLaunchMs(getLaunchTime());
      setReady(true);
      launchTimeRef.current = getLaunchTime();
    }, 500);
    return () => clearTimeout(t);
  }, []);

  const startSuite = async () => {
    if (!ready) return;
    setRunning(true);
    setResults([]);
    setIteration(0); // iteration = repetition index within current test
    setTestIndex(0); // current test index
    await runRepetition(0, 0, []);
  };

  // Now: run each test 'iterations' times before moving to next test
  const runRepetition = async (testIdx: number, rep: number, acc: TestResult[]) => {
    if (testIdx >= TESTS.length) return finish(acc);
    if (rep >= iterations) {
      // move to next test
      return runRepetition(testIdx + 1, 0, acc);
    }
    setTestIndex(testIdx);
    setIteration(rep);
    const key = createResultKey();
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const { router } = require('expo-router');
    router.push((`${TESTS[testIdx].route}?key=${encodeURIComponent(key)}`) as any);
    const res = await waitForResult<TestResult | null>(key);
    if (res) acc.push(res);
    setResults([...acc]);
    await new Promise(r => setTimeout(r, 80));
    await runRepetition(testIdx, rep + 1, acc);
  };

  const finish = async (acc: TestResult[]) => {
    setRunning(false);
    const path = await saveToFile(acc);
    setLastSavedPath(path);
    Alert.alert('Suite finished', path ? `Results saved to ${path}` : 'Failed to save results');
  };

  const buildExportPayload = (res: TestResult[]) => {
    const aggregated: AggregatedStats[] = aggregatePerTest(res);
    return {
      launchTimeMs: launchTimeRef.current,
      tests: res,
      aggregatedStats: aggregated,
      iterationsPlanned: iterations,
      generatedAt: new Date().toISOString(),
    };
  };

  const saveToFile = async (res: TestResult[]) => {
    const payload = buildExportPayload(res);
    const text = JSON.stringify(payload, null, 2);
    const fileUri = `${FileSystem.documentDirectory}${EXPORT_FILE_PREFIX}_${Date.now()}.json`;
    try {
      await FileSystem.writeAsStringAsync(fileUri, text, { encoding: FileSystem.EncodingType.UTF8 });
      return fileUri;
    } catch {
      return null;
    }
  };

  const exportResults = async () => {
    const path = await saveToFile(results);
    if (path && Clipboard?.setStringAsync) {
      const payload = buildExportPayload(results);
      try { await Clipboard.setStringAsync(JSON.stringify(payload)); } catch { }
    }
    setLastSavedPath(path);
    Alert.alert(path ? 'Export complete' : 'Export failed', path ?? '');
  };

  const total = iterations * TESTS.length;
  // progress: how many test repetitions out of total (tests * iterations)
  const completed = testIndex * iterations + iteration;
  const progress = total === 0 ? 0 : Math.max(0, Math.min(1, completed / total));

  const aggregated: AggregatedStats[] = React.useMemo(() => aggregatePerTest(results), [results]);

  return (
    <View style={styles.root}>
      <Text style={styles.headerLine}>App Launched in: {launchMs != null ? `${launchMs}ms` : '...'}{'\n'}{ready ? 'Ready to start tests.' : 'Preparing...'}{running ? `\nRunning: ${TESTS[testIndex]?.name} (${iteration + 1}/${iterations})` : ''}</Text>
      <View style={styles.progressBarWrap}>
        <View style={[styles.progressBarFill, { flex: progress }]} />
        <View style={{ flex: 1 - progress }} />
      </View>
      <View style={styles.buttonsRowTop}>
        <Pressable disabled={!ready || running} onPress={startSuite} style={[styles.actionBtn, (!ready || running) && styles.btnDisabled]}>
          <Text style={styles.btnText}>{running ? 'Running...' : 'Start Tests'}</Text>
        </Pressable>
        <Pressable disabled={results.length === 0} onPress={async () => {
          const paths = await exportAllPerTestCsv('bench', results);
          Alert.alert(paths.length ? 'CSV Exported' : 'CSV Export Failed', paths.join('\n'));
        }} style={[styles.actionBtn, results.length === 0 && styles.btnDisabled]}>
          <Text style={styles.btnText}>Export CSV</Text>
        </Pressable>
      </View>
      <ScrollView style={{ flex: 1 }} contentContainerStyle={{ paddingBottom: 32 }}>
        <Text style={styles.iterInfo}>Per-test repetitions: {iterations} (fixed)</Text>
        <Text style={styles.sectionTitle}>Results</Text>
        <Text style={styles.mono}>
          {results.length === 0 ? 'No results yet.' : results.map((r: TestResult) => formatResult(r)).join('\n')}
        </Text>

        {lastSavedPath && (
          <Text style={{ marginTop: 12, fontSize: 12, color: '#555' }}>Last export: {lastSavedPath}</Text>
        )}
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#faf7fb', paddingHorizontal: 20, paddingTop: 60 },
  headerLine: { textAlign: 'center', fontSize: 18, fontWeight: '700', lineHeight: 26, marginBottom: 16 },
  progressBarWrap: { height: 4, backgroundColor: '#e5e7eb', borderRadius: 2, flexDirection: 'row', overflow: 'hidden', marginBottom: 16 },
  progressBarFill: { backgroundColor: '#5b3ea5' },
  iterInfo: { fontSize: 12, fontWeight: '600', marginBottom: 8 },
  sectionTitle: { fontWeight: '700', marginTop: 4, marginBottom: 4 },
  avgLine: { fontSize: 12 },
  mono: { fontSize: 11, fontFamily: Platform.select({ ios: 'Menlo', android: 'monospace', default: 'monospace' }) },
  buttonsRowTop: { flexDirection: 'row', justifyContent: 'space-evenly', paddingVertical: 12, marginBottom: 8 },
  actionBtn: { backgroundColor: '#5b3ea5', paddingHorizontal: 20, paddingVertical: 14, borderRadius: 28, minWidth: 110, alignItems: 'center' },
  btnText: { color: '#fff', fontWeight: '600', fontSize: 14 },
  btnDisabled: { opacity: 0.5 },
});

