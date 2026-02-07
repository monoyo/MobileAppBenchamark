import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Alert, Button, ScrollView, StyleSheet, Text, View } from 'react-native';
import { writeCsvFiles } from '../lib/export';
import type { TestResult } from '../lib/testTypes';

// Konfiguracja testów (tylko te ekrany które mamy realnie zaimplementowane)
// Nazwy odpowiadają Flutter benchmark_suite.dart
const TEST_SEQUENCE = [
  { name: 'UI Test', route: 'UI' },
  { name: 'CPU Test', route: 'CPU' },
  { name: 'RAM Test', route: 'RAM' },
  { name: 'Image Loading Test', route: 'ImageLoading' },
  { name: 'API Test', route: 'API' },
  { name: 'Location Test', route: 'Location' },
];

const ITERATIONS_PER_TEST = 2; // analogicznie do Fluttera

// Dane pojedynczego wpisu (opakowanie TestResult + numer iteracji)
interface Entry { iteration: number; result: TestResult; }

type Props = NativeStackScreenProps<any, 'Home'>;

export default function HomeScreen({ navigation, route }: Props) {
  // launchTimeMs przekazany z App.tsx
  const launchTimeMs: number | undefined = route?.params?.launchTimeMs;

  const [running, setRunning] = useState(false);
  const [currentTestIndex, setCurrentTestIndex] = useState(0);
  const [currentIteration, setCurrentIteration] = useState(0);
  const [info, setInfo] = useState(
    launchTimeMs != null ? `App launched in: ${launchTimeMs}ms. Ready to start tests.` : 'Ready to start tests.'
  );
  const [resultsByTest, setResultsByTest] = useState<Record<string, Entry[]>>({});
  const [averagesBlock, setAveragesBlock] = useState<string | null>(null);
  const runIdRef = useRef<string>(`${Date.now()}`);

  const totalWork = TEST_SEQUENCE.length * ITERATIONS_PER_TEST;
  const completed = currentTestIndex * ITERATIONS_PER_TEST + currentIteration;
  const progress = running ? Math.min(1, completed / totalWork) : 0;

  const reset = useCallback(() => {
    setRunning(false);
    setCurrentTestIndex(0);
    setCurrentIteration(0);
    setResultsByTest({});
    setAveragesBlock(null);
    runIdRef.current = `${Date.now()}`;
    setInfo(
      launchTimeMs != null ? `App launched in: ${launchTimeMs}ms. Ready to start tests.` : 'Ready to start tests.'
    );
  }, [launchTimeMs]);

  const computeAverages = useCallback((data: Record<string, Entry[]>) => {
    const lines: string[] = ['# Averages (ms)'];
    Object.entries(data).forEach(([test, arr]) => {
      if (!arr.length) return;
      const total = arr.reduce((acc, e) => acc + e.result.executionTimeMs, 0);
      const avg = total / arr.length;
      lines.push(`${test},${avg.toFixed(2)}`);
    });
    return lines.join('\n');
  }, []);

  const onSingleTestFinished = useCallback(
    (testName: string, res: TestResult) => {
      setResultsByTest(prev => {
        const list = prev[testName] ? [...prev[testName]] : [];
        list.push({ iteration: currentIteration + 1, result: res });
        return { ...prev, [testName]: list };
      });
    },
    [currentIteration],
  );

  // Uruchom kolejny test / iterację
  const runNext = useCallback(() => {
    // Czy skończyliśmy wszystkie testy?
    if (currentTestIndex >= TEST_SEQUENCE.length) {
      setRunning(false);
      setInfo('All tests completed!');
      setAveragesBlock(prev => prev ?? computeAverages(resultsByTest));
      return;
    }
    // Czy zakończyliśmy iteracje dla bieżącego testu?
    if (currentIteration >= ITERATIONS_PER_TEST) {
      setCurrentTestIndex(i => i + 1);
      setCurrentIteration(0);
      return; // kolejny useEffect odpali runNext ponownie
    }

    const test = TEST_SEQUENCE[currentTestIndex];
    setInfo(`Running: ${test.name} (Iteration ${currentIteration + 1}/${ITERATIONS_PER_TEST})`);

    // Nawigacja do ekranu testowego z callbackiem
    navigation.navigate(test.route as any, {
      onResult: (r: Omit<TestResult, 'iteration'> & { testName?: string }) => {
        const result: TestResult = {
          iteration: currentIteration + 1, // zachowane dla spójności typu
          executionTimeMs: r.executionTimeMs,
          details: r.details,
          success: r.success,
        };
        onSingleTestFinished(test.name, result);
        setCurrentIteration(iter => iter + 1);
      },
    });
  }, [currentTestIndex, currentIteration, navigation, onSingleTestFinished, resultsByTest, computeAverages]);

  // Efekt który odpala test po zmianie indeksu lub iteracji (gdy wciąż running)
  useEffect(() => {
    if (!running) return;
    // Małe opóźnienie aby UI zrenderował się przed startem ciężkiego testu
    const t = setTimeout(runNext, 120);
    return () => clearTimeout(t);
  }, [running, currentTestIndex, currentIteration, runNext]);

  const startSuite = () => {
    if (running) return;
    reset();
    setRunning(true);
  };

  const exportResults = async () => {
    if (!Object.keys(resultsByTest).length) return;
    const mapped: Record<string, TestResult[]> = {};
    Object.entries(resultsByTest).forEach(([k, arr]) => {
      mapped[k] = arr.map(e => ({ ...e.result, iteration: e.iteration }));
    });
    const { paths } = await writeCsvFiles(mapped, runIdRef.current);
    if (paths.length) {
      Alert.alert('Export', `Saved CSV files:\n${paths.join('\n')}`);
    } else {
      Alert.alert('Export', 'No files written');
    }
  };

  const renderResults = () => {
    const blocks: string[] = [];
    Object.entries(resultsByTest).forEach(([testName, entries]) => {
      const sorted = [...entries].sort((a, b) => a.iteration - b.iteration);
      blocks.push(`# ${testName}`);
      blocks.push('iteration,executionTimeMs,details,success');
      sorted.forEach(e => {
        // escape details for csv view only (prostota)
        const det = e.result.details.replace(/\n/g, ' ');
        blocks.push(`${e.iteration},${e.result.executionTimeMs},${det},${e.result.success}`);
      });
      blocks.push('');
    });
    if (averagesBlock) blocks.push(averagesBlock);
    return blocks.join('\n');
  };

  return (
    <View style={styles.container}>
      <Text style={styles.info} selectable>{info}</Text>
      <View style={styles.progressBarWrapper}>
        <View style={[styles.progressFill, { flex: progress }]} />
        <View style={{ flex: 1 - progress }} />
      </View>
      <ScrollView style={styles.resultsScroll} contentContainerStyle={{ paddingBottom: 32 }}>
        <Text style={styles.resultsText} selectable>{renderResults()}</Text>
      </ScrollView>
      <View style={styles.buttonsRow}>
        <Button title={running ? 'Running…' : 'Start Tests'} disabled={running} onPress={startSuite} color="#443FD8" />
        <View style={{ width: 12 }} />
        <Button title="Export Results" disabled={!Object.keys(resultsByTest).length} onPress={exportResults} />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, paddingHorizontal: 32, paddingVertical: 52 },
  info: { textAlign: 'center', fontSize: 18, fontWeight: '700', marginBottom: 16 },
  progressBarWrapper: { height: 4, backgroundColor: '#eee', borderRadius: 2, flexDirection: 'row', overflow: 'hidden', marginBottom: 16 },
  progressFill: { backgroundColor: '#3b82f6' },
  resultsScroll: { flex: 1 },
  resultsText: { fontSize: 14, fontFamily: 'Courier' },
  buttonsRow: { flexDirection: 'row', justifyContent: 'center', alignItems: 'center', paddingVertical: 12 },
});
