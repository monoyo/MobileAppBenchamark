import React from 'react';
import { View, Text, StyleSheet, Pressable, ScrollView, Alert } from 'react-native';
import { useRouter, Href } from 'expo-router';
import { createResultKey, waitForResult } from './utils/navResult';
import type { TestResult } from './types';
import * as FileSystem from 'expo-file-system';

const TESTS: { name: string; route: Href }[] = [
  { name: 'UI Test', route: '/ui-test' },
  { name: 'CPU Test', route: '/cpu-test' },
  { name: 'RAM Test', route: '/ram-test' },
  { name: 'Image Loading Test', route: '/image-test' },
  { name: 'API Test', route: '/api-test' },
  { name: 'Location Test', route: '/location-test' },
];

export default function Suite() {
  const router = useRouter();
  const [results, setResults] = React.useState<TestResult[]>([]);
  const [running, setRunning] = React.useState(false);
  const [iteration, setIteration] = React.useState(0);
  const [testIndex, setTestIndex] = React.useState(0);
  const [lastSavedPath, setLastSavedPath] = React.useState<string | null>(null);
  const iterations = 3;

  const startSuite = async () => {
    setRunning(true);
    setResults([]);
    setIteration(0);
    setTestIndex(0);
    await runNext(0, 0, []);
  };

  const runNext = async (iter: number, idx: number, acc: TestResult[]) => {
    if (iter >= iterations) return finish(acc);
    if (idx >= TESTS.length) return runNext(iter + 1, 0, acc);

    setIteration(iter);
    setTestIndex(idx);
  const key = createResultKey();
  // Use direct href with query to satisfy typed routes
  router.push((`${TESTS[idx].route}?key=${encodeURIComponent(key)}`) as any);
    const res = await waitForResult<TestResult | null>(key);
    if (res) acc.push(res);
    setResults([...acc]);
  // tiny pause to let UI settle (reduced)
  await new Promise(r => setTimeout(r, 100));
    await runNext(iter, idx + 1, acc);
  };

  const finish = async (acc: TestResult[]) => {
    setRunning(false);
    // save automatically
    const path = await saveToFile(acc);
    setLastSavedPath(path);
    Alert.alert('Suite finished', path ? `Results saved to ${path}` : 'Failed to save results');
  };

  const saveToFile = async (res: TestResult[]) => {
    const text = res.map(r => `${r.testName}: ${r.executionTimeMs}ms (${r.details})`).join('\n');
    const fileUri = `${FileSystem.documentDirectory}benchmark_results_${Date.now()}.txt`;
    try {
      await FileSystem.writeAsStringAsync(fileUri, text, { encoding: FileSystem.EncodingType.UTF8 });
      return fileUri;
    } catch {
      return null;
    }
  };

  const exportResults = async () => {
    const path = await saveToFile(results);
    setLastSavedPath(path);
    Alert.alert(path ? 'Export complete' : 'Export failed', path ?? '');
  };

  const total = iterations * TESTS.length;
  const completed = iteration * TESTS.length + testIndex;
  const progress = total === 0 ? 0 : Math.max(0, Math.min(1, completed / total));

  return (
    <View style={styles.root}>
      {running && (
        <Text style={styles.header}>Running: {TESTS[testIndex]?.name}</Text>
      )}
      <View style={styles.progressBarWrap}>
        <View style={[styles.progressBarFill, { flex: progress }]} />
        <View style={{ flex: 1 - progress }} />
      </View>
      <ScrollView style={{ flex: 1 }} contentContainerStyle={{ paddingVertical: 8 }}>
        <Text style={{ fontSize: 14 }}>
          {results.length === 0 ? 'No results yet.' : results.map(r => `${r.testName}: ${r.executionTimeMs}ms (${r.details})`).join('\n')}
        </Text>
      </ScrollView>
      <View style={styles.row}>
        <Pressable disabled={running} onPress={startSuite} style={[styles.btn, { backgroundColor: 'rgb(68,63,216)' }, running && styles.btnDisabled]}>
          <Text style={[styles.btnText, running && styles.btnTextDisabled]}>{running ? 'Running...' : 'Start Tests'}</Text>
        </Pressable>
        <View style={{ width: 8 }} />
        <Pressable disabled={results.length === 0} onPress={exportResults} style={[styles.btn, { backgroundColor: 'rgb(68,63,216)' }]}>
          <Text style={styles.btnText}>Export Results</Text>
        </Pressable>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, paddingHorizontal: 32, paddingVertical: 52 },
  header: { textAlign: 'center', fontSize: 18, fontWeight: '700', marginBottom: 16 },
  progressBarWrap: { height: 4, backgroundColor: '#eee', borderRadius: 2, flexDirection: 'row', overflow: 'hidden', marginBottom: 16 },
  progressBarFill: { backgroundColor: '#3b82f6' },
  row: { flexDirection: 'row', justifyContent: 'center', alignItems: 'center', paddingVertical: 12 },
  btn: { paddingHorizontal: 16, paddingVertical: 12, borderRadius: 8 },
  btnText: { color: '#fff', fontWeight: '600' },
  btnDisabled: { opacity: 0.6 },
  btnTextDisabled: { color: '#363535' },
});
