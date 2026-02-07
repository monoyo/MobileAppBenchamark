import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Alert, ScrollView, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { writeCsvFiles } from '../lib/export';
import type { TestResult } from '../lib/testTypes';

const TEST_SEQUENCE = [
  { name: 'UI Test', route: 'UI' },
  { name: 'CPU Test', route: 'CPU' },
  { name: 'RAM Test', route: 'RAM' },
  { name: 'Image Loading Test', route: 'ImageLoading' },
  { name: 'API Test', route: 'API' },
  { name: 'Location Test', route: 'Location' },
];

const ITERATIONS_PER_TEST = 2;

interface Entry { iteration: number; result: TestResult; }

type Props = NativeStackScreenProps<any, 'Home'>;

export default function HomeScreen({ navigation, route }: Props) {
  const [running, setRunning] = useState(false);
  const [currentTestIndex, setCurrentTestIndex] = useState(0);
  const [currentIteration, setCurrentIteration] = useState(0);
  const [statusInfo, setStatusInfo] = useState('Benchmark Suite');

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
    setStatusInfo('Benchmark Suite');
    runIdRef.current = `${Date.now()}`;
  }, []);

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

  const runNext = useCallback(() => {
    if (currentTestIndex >= TEST_SEQUENCE.length) {
      setRunning(false);
      setAveragesBlock(prev => prev ?? computeAverages(resultsByTest));
      setStatusInfo('All tests completed!');
      return;
    }
    if (currentIteration >= ITERATIONS_PER_TEST) {
      setCurrentTestIndex(i => i + 1);
      setCurrentIteration(0);
      return;
    }

    const test = TEST_SEQUENCE[currentTestIndex];
    setStatusInfo(`Running: ${test.name} (Iter ${currentIteration + 1}/${ITERATIONS_PER_TEST})`);

    navigation.navigate(test.route as any, {
      onResult: (r: Omit<TestResult, 'iteration'> & { testName?: string }) => {
        const result: TestResult = {
          iteration: currentIteration + 1,
          executionTimeMs: r.executionTimeMs,
          details: r.details,
          success: r.success,
        };
        onSingleTestFinished(test.name, result);
        setCurrentIteration(iter => iter + 1);
      },
    });
  }, [currentTestIndex, currentIteration, navigation, onSingleTestFinished, resultsByTest, computeAverages]);

  useEffect(() => {
    if (!running) return;
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
      // Show last 10
      const display = sorted.length > 10 ? sorted.slice(sorted.length - 10) : sorted;

      blocks.push(`# ${testName}`);
      blocks.push('iteration,executionTimeMs,details');
      display.forEach(e => {
        const det = e.result.details.replace(/\n/g, ' ');
        blocks.push(`${e.iteration},${e.result.executionTimeMs},${det}`);
      });
      blocks.push('');
    });
    if (averagesBlock) blocks.push(averagesBlock);
    return blocks.join('\n');
  };

  return (
    <View style={styles.container}>
      {/* Header */}
      <Text style={styles.headerText}>
        {statusInfo}
      </Text>

      {/* Progress Bar */}
      <View style={styles.progressBarWrapper}>
        <View style={[styles.progressFill, { flex: running ? progress : 0 }]} />
        <View style={{ flex: 1 - (running ? progress : 0) }} />
      </View>

      {/* Results Area */}
      <View style={styles.resultsContainer}>
        <ScrollView style={styles.resultsScroll} contentContainerStyle={{ padding: 8 }}>
          <Text style={styles.resultsText} selectable>{renderResults()}</Text>
        </ScrollView>
      </View>

      {/* Buttons (Bottom) */}
      <View style={styles.buttonsRow}>
        <TouchableOpacity
          style={[styles.primaryButton, running && styles.buttonDisabled]}
          onPress={startSuite}
          disabled={running}
        >
          <Text style={styles.primaryButtonText}>{running ? 'Running...' : 'Start Tests'}</Text>
        </TouchableOpacity>

        <TouchableOpacity
          style={styles.outlinedButton}
          onPress={exportResults}
          disabled={running}
        >
          <Text style={styles.outlinedButtonText}>Export Results</Text>
        </TouchableOpacity>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    paddingHorizontal: 32,
    paddingVertical: 52,
    backgroundColor: '#ffffff'
  },
  headerText: {
    fontSize: 18,
    fontWeight: '700', // Bold
    color: '#000',
    textAlign: 'center',
    marginBottom: 16,
  },
  progressBarWrapper: {
    height: 4,
    backgroundColor: '#EEEEEE',
    borderRadius: 2,
    flexDirection: 'row',
    overflow: 'hidden',
    marginBottom: 16,
  },
  progressFill: {
    backgroundColor: '#443FD8',
  },
  resultsContainer: {
    flex: 1, // Fill available space
    backgroundColor: '#FAFAFA',
    marginBottom: 16,
    // Add border to match Flutter logic? Or just background.
    // Flutter one had border. Let's add simple border if needed, 
    // but Java XML just has ScrollView with bg #FAFAFA.
  },
  resultsScroll: {
    flex: 1,
  },
  resultsText: {
    fontSize: 14,
    fontFamily: 'monospace',
    color: '#000000',
  },
  buttonsRow: {
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center',
  },
  primaryButton: {
    backgroundColor: '#443FD8',
    paddingHorizontal: 16,
    paddingVertical: 12,
    borderRadius: 8,
    marginRight: 8, // 8dp margin end
    minWidth: 100,
    alignItems: 'center',
  },
  primaryButtonText: {
    color: '#FFFFFF',
    fontWeight: '500',
    fontSize: 14,
  },
  buttonDisabled: {
    backgroundColor: '#A0A0A0',
  },
  outlinedButton: {
    backgroundColor: 'transparent',
    paddingHorizontal: 16,
    paddingVertical: 10, // Adjust for border width
    borderRadius: 8,
    borderWidth: 1,
    borderColor: '#443FD8',
    marginLeft: 8, // 8dp margin start
    minWidth: 120,
    alignItems: 'center',
  },
  outlinedButtonText: {
    color: '#443FD8',
    fontWeight: '500',
    fontSize: 14,
  },
});
