import { Href, useRouter } from 'expo-router';
import React from 'react';
import {
  Alert,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import RNFS from 'react-native-fs';
import {
  SampleConfiguration,
  getSampleConfig,
} from './constants/SampleConfiguration';
import { useFpsCounter } from './hooks/useFpsCounter';
import type { TestResult } from './types';
import { formatResult } from './types';
import { BufferedCsvWriter } from './utils/BufferedCsvWriter';
import { getLaunchTime, markSuiteReady } from './utils/launchTime';
import { exportSummary } from './utils/SummaryWriter';
import {
  createResultKey,
  waitForResult,
} from './utils/navResult';

// Define test groups - order matters for sequential execution
const TESTS: Array<{ name: string; route: Href }> = [
  { name: 'UI Test', route: '/ui-test' },
  { name: 'CPU Test', route: '/cpu-test' },
  { name: 'RAM Test', route: '/ram-test' },
  { name: 'Image Loading Test', route: '/image-test' },
  { name: 'API Test', route: '/api-test' },
  { name: 'Location Test', route: '/location-test' },
];

/**
 * Test suite orchestration component.
 * - Manages sequential test execution across multiple iterations
 * - Collects results and exports to CSV
 * - Provides UI for progress tracking and configuration
 * - Implements proper state management with error handling
 */
export default function Suite(): React.ReactElement {
  const router = useRouter();
  const [results, setResults] = React.useState<TestResult[]>([]);
  const [running, setRunning] = React.useState<boolean>(false);
  const [iteration, setIteration] = React.useState<number>(0);
  const [testIndex, setTestIndex] = React.useState<number>(0);
  const [lastSavedPath, setLastSavedPath] = React.useState<
    string | null
  >(null);
  const [error, setError] = React.useState<string | null>(null);

  // Selection state
  const [selectedTests, setSelectedTests] = React.useState<Record<string, boolean>>(
    TESTS.reduce((acc, test) => ({ ...acc, [test.name]: true }), {})
  );

  // Configuration - hardcoded to medium (matching other variants)
  const configId = 0;
  const config = getSampleConfig(configId);

  const launchTimeRef = React.useRef<number | null>(null);
  const cancelledRef = React.useRef<boolean>(false);

  // Metrics
  const getFps = useFpsCounter(running);
  const csvWriters = React.useRef<Record<string, BufferedCsvWriter>>({});
  const sessionDir = React.useRef<string | null>(null);
  const testSuiteStartTime = React.useRef<number>(0);

  React.useEffect(() => {
    markSuiteReady();
    launchTimeRef.current = getLaunchTime();
  }, []);

  const initializeSession = React.useCallback(async (): Promise<void> => {
    try {
      const timestamp = new Date()
        .toISOString()
        .replace(/[:.]/g, '-');
      // Use ExternalDirectoryPath to save in Android/data/<package>/files/benchmarks
      const baseDir = Platform.OS === 'android' ? RNFS.ExternalDirectoryPath : RNFS.DocumentDirectoryPath;
      const dir = `${baseDir}/benchmarks/${timestamp}`;
      await RNFS.mkdir(dir);
      sessionDir.current = dir;

      // Initialize CSV writers for each SELECTED test
      csvWriters.current = {};
      for (const test of TESTS) {
        if (!selectedTests[test.name]) continue;

        const safeName = test.name
          .toLowerCase()
          .replace(/ /g, '_');
        const writer = new BufferedCsvWriter(
          `${dir}/${safeName}.csv`,
          config.bufferSize
        );
        await writer.initialize();
        csvWriters.current[test.name] = writer;
      }
    } catch (err) {
      const errorMsg =
        err instanceof Error ? err.message : String(err);
      setError(`Session init failed: ${errorMsg}`);
      console.error('Failed to initialize session:', err);
    }
  }, [config.bufferSize, selectedTests]);

  const finish = React.useCallback(
    async (acc: TestResult[]): Promise<void> => {
      try {
        // Flush all writers
        for (const key in csvWriters.current) {
          try {
            await csvWriters.current[key].flush();
          } catch (flushErr) {
            console.warn(`Failed to flush ${key}:`, flushErr);
          }
        }

        // Generate summary.csv with aggregated statistics
        if (sessionDir.current) {
          try {
            await exportSummary(acc, sessionDir.current, 'react_native');
          } catch (summaryErr) {
            console.warn('Failed to export summary:', summaryErr);
          }
        }

        setRunning(false);
        setLastSavedPath(sessionDir.current);
        Alert.alert(
          'Success',
          `Completed ${acc.length} samples.\nSaved to ${sessionDir.current}`
        );
      } catch (err) {
        const errorMsg =
          err instanceof Error ? err.message : String(err);
        setError(`Finish failed: ${errorMsg}`);
        setRunning(false);
        console.error('Finish error:', err);
      }
    },
    []
  );

  const BATCH_TESTS = [
    'UI Test',
    'CPU Test',
    'RAM Test',
    'Image Loading Test',
    'API Test',
    'Location Test'
  ];

  // ...

  const runNext = React.useCallback(
    async (
      iter: number,
      idx: number,
      acc: TestResult[]
    ): Promise<void> => {
      if (cancelledRef.current) {
        setRunning(false);
        return;
      }

      // Skip unselected tests
      let currentIdx = idx;
      while (currentIdx < TESTS.length && !selectedTests[TESTS[currentIdx].name]) {
        currentIdx++;
      }

      if (currentIdx >= TESTS.length) {
        return finish(acc); // All tests done
      }

      const testMeta = TESTS[currentIdx];
      const isBatch = BATCH_TESTS.includes(testMeta.name);
      const targetIterations = isBatch ? 1 : config.sampleCount;

      if (iter >= targetIterations) {
        // Move to next test
        return runNext(0, currentIdx + 1, acc);
      }

      setIteration(iter);
      setTestIndex(currentIdx);

      const key = createResultKey();

      const testName = testMeta.name;
      const writer = csvWriters.current[testName];
      const csvPath = writer ? writer.getPath() : '';

      // Pass config params
      // For batch tests, we pass the total samples count as 'iterations' or 'batchSize'
      // Ideally we unify. 'iterations' param traditionally meant "CPU iterations".
      // Let's pass 'batchSize' for clarity.
      const routeParams = `?key=${encodeURIComponent(
        key
      )}&iterations=${config.cpuIterations}&batchSize=${config.sampleCount}&csvPath=${encodeURIComponent(csvPath)}`;

      router.push((`${testMeta.route}${routeParams}`) as Href);

      const intervalStart = Date.now();
      // For batch tests, we might wait longer.
      const res = await waitForResult<TestResult | null>(key);
      const intervalEnd = Date.now();
      const intervalDuration = intervalEnd - intervalStart;

      if (res && !cancelledRef.current) {
        const extendedRes: TestResult = {
          ...res,
        };
        acc.push(extendedRes);
        setResults([...acc]);

        // Write to CSV
        // For batch tests, the test itself writes samples. 
        // We can optionally write a summary row here, or skip.
        // If we write, it appears as iteration 0.
        // Let's write it as a summary/marker. 
        // BUT beware: if test wrote 1000 lines, and we write 1 line, we have 1001 lines.
        // The tests (Location) write strict CSV.
        // Sample analysis expects uniform columns.
        // If we write one line here, it might break parser if it expects 1000 lines of "sample data" and finds 1 line of "summary".
        // HOWEVER, BufferedCsvWriter expects specific columns.
        // LocationTest writes valid columns.
        // Suite writes valid columns.
        // So we get 1000 rows + 1 row.
        // The 1000 rows have `iteration` 1..1000.
        // This row will have `iteration` 0 (from iter).
        // This is reasonably safe.

        if (writer) {
          try {
            await writer.write(
              isBatch ? 0 : iter + 1, // Use 0 or 9999 for summary? 0 is fine.
              res.executionTimeMs,
              res.details || '',
              intervalStart,
              intervalDuration,
              intervalEnd - testSuiteStartTime.current
            );
          } catch (csvErr) {
            console.warn('CSV write error:', csvErr);
          }
        }
      }

      await new Promise<void>((resolve) =>
        setTimeout(resolve, 10)
      );

      // Recursion
      // If batch, we are done with this test (since targetIterations was 1).
      // Logic above `if (iter >= targetIterations)` handles the next test jump.
      // So we just increment iter.

      await runNext(iter + 1, currentIdx, acc);
    },
    [config.sampleCount, config.cpuIterations, router, getFps, finish, selectedTests]
  );

  const startSuite = React.useCallback(async (): Promise<void> => {
    if (running) return;

    // Validation
    const hasSelection = Object.values(selectedTests).some((v) => v);
    if (!hasSelection) {
      Alert.alert('Error', 'Please select at least one test');
      return;
    }

    cancelledRef.current = false;
    setError(null);
    setRunning(true);
    setResults([]);
    setIteration(0);
    setTestIndex(0);
    testSuiteStartTime.current = Date.now();

    try {
      await initializeSession();
      await runNext(0, 0, []);
    } catch (err) {
      const errorMsg =
        err instanceof Error ? err.message : String(err);
      setError(`Test suite failed: ${errorMsg}`);
      setRunning(false);
      console.error('Test suite error:', err);
    }
  }, [running, initializeSession, runNext, selectedTests]);

  // UI Helpers
  const activeTests = TESTS.filter((t) => selectedTests[t.name]);
  const total = config.sampleCount * activeTests.length;
  // This is an approximation for progress bar since we skip indices
  // A better progress would calculate based on completed tests vs total selected tests * iterations
  // For simplicity, we'll keep it simple or fix it if needed.
  // Actually, let's just make it 0 if unknown to avoid confusion, or try to estimate.
  // Let's rely on iteration count.
  const progress = total === 0 ? 0 : Math.max(0, Math.min(1, (iteration * activeTests.length) / total));

  const toggleTest = (name: string) => {
    setSelectedTests((prev) => ({
      ...prev,
      [name]: !prev[name],
    }));
  };

  return (
    <View style={styles.root}>
      <Text style={styles.header}>
        {running
          ? `Running: ${TESTS[testIndex]?.name} (${iteration + 1}/${config.sampleCount})`
          : 'Benchmark Suite'}
      </Text>

      {running ? (
        <>
          <View style={styles.progressBarWrap}>
            <View style={[styles.progressBarFill, { flex: progress }]} />
            <View style={{ flex: 1 - progress }} />
          </View>

          {error && (
            <Text style={styles.errorText}>{error}</Text>
          )}

          <View style={styles.resultsContainer}>
            <ScrollView>
              <Text style={styles.mono}>
                {results
                  .slice(-50)
                  .map((r) => formatResult(r))
                  .join('\n')}
              </Text>
            </ScrollView>
          </View>
        </>
      ) : (
        <ScrollView style={styles.selectionContainer}>
          <Text style={styles.subHeader}>Select Tests:</Text>
          {TESTS.map((test) => (
            <View key={test.name} style={styles.optionRow}>
              <Text style={styles.optionLabel}>{test.name}</Text>
              <TouchableOpacity
                onPress={() => toggleTest(test.name)}
                style={[
                  styles.checkbox,
                  selectedTests[test.name] && styles.checkboxSelected,
                ]}
              >
                <View style={selectedTests[test.name] ? styles.checkboxInner : null} />
              </TouchableOpacity>
            </View>
          ))}
          <View style={{ height: 20 }} />
        </ScrollView>
      )}

      <View style={styles.row}>
        <Pressable
          disabled={running}
          onPress={startSuite}
          style={[
            styles.btn,
            { backgroundColor: 'rgb(68,63,216)' },
            running && styles.btnDisabled,
          ]}
        >
          <Text
            style={[
              styles.btnText,
              running && styles.btnTextDisabled,
            ]}
          >
            {running ? 'Running...' : 'Start Tests'}
          </Text>
        </Pressable>

        <View style={{ width: 16 }} />

        <Pressable
          onPress={() => {
            if (lastSavedPath) {
              Alert.alert('Results Saved', `Path: ${lastSavedPath}`);
            }
          }}
          style={[styles.btn, styles.btnOutlined]}
        >
          <Text style={[styles.btnText, styles.btnTextOutlined]}>
            Export Results
          </Text>
        </Pressable>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  root: {
    flex: 1,
    backgroundColor: '#FFFFFF',
    paddingHorizontal: 32,
    paddingVertical: 52,
  },
  header: {
    textAlign: 'center',
    fontSize: 18,
    fontWeight: '700',
    color: '#000000',
    marginBottom: 16,
  },
  progressBarWrap: {
    height: 4,
    backgroundColor: '#EEEEEE',
    borderRadius: 2,
    flexDirection: 'row',
    overflow: 'hidden',
    marginBottom: 16,
  },
  progressBarFill: {
    backgroundColor: '#443FD8',
  },
  errorText: {
    color: '#dc2626',
    fontSize: 12,
    paddingHorizontal: 12,
    paddingBottom: 8,
    fontWeight: '500',
  },
  resultsContainer: {
    flex: 1,
    backgroundColor: '#FAFAFA',
    padding: 8,
    marginBottom: 16,
  },
  row: {
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center',
    paddingVertical: 12,
  },
  btn: {
    paddingHorizontal: 16,
    paddingVertical: 12,
    borderRadius: 8,
    minWidth: 120,
    alignItems: 'center',
  },
  btnText: {
    color: '#fff',
    fontWeight: '600',
  },
  btnDisabled: {
    opacity: 0.6,
  },
  btnTextDisabled: {
    color: '#ccc',
  },
  btnOutlined: {
    backgroundColor: 'transparent',
    borderWidth: 1,
    borderColor: '#443FD8',
  },
  btnTextOutlined: {
    color: '#443FD8',
  },
  mono: {
    fontSize: 14,
    color: '#000000',
    fontFamily: Platform.select({
      ios: 'Menlo',
      android: 'monospace',
      default: 'monospace',
    }),
  },
  selectionContainer: {
    flex: 1,
    backgroundColor: '#FAFAFA',
    padding: 16,
    marginBottom: 16,
  },
  subHeader: {
    fontSize: 16,
    fontWeight: '600',
    marginBottom: 12,
  },
  optionRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingVertical: 12,
    borderBottomWidth: 1,
    borderBottomColor: '#EEEEEE',
  },
  optionLabel: {
    fontSize: 16,
  },
  checkbox: {
    width: 24,
    height: 24,
    borderWidth: 2,
    borderColor: '#443FD8',
    borderRadius: 4,
    justifyContent: 'center',
    alignItems: 'center',
  },
  checkboxSelected: {
    backgroundColor: '#443FD8',
  },
  checkboxInner: {
    width: 12,
    height: 12,
    backgroundColor: '#FFFFFF',
  },
});
