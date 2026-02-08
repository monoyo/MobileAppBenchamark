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
import { exportSummary } from './utils/summaryExport';
import {
  createResultKey,
  waitForResult,
} from './utils/navResult';

// Define test groups - order matters for sequential execution
const TESTS: Array<{ name: string; route: Href; group: string }> = [
  { name: 'UI Test', route: '/ui-test', group: 'ui' },
  { name: 'CPU Test', route: '/cpu-test', group: 'cpu' },
  { name: 'RAM Test', route: '/ram-test', group: 'memory' },
  { name: 'Image Loading Test', route: '/image-test', group: 'io' },
  { name: 'API Test', route: '/api-test', group: 'network' },
  { name: 'Location Test', route: '/location-test', group: 'sensors' },
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
      const dir = `${RNFS.DocumentDirectoryPath}/benchmarks/${timestamp}`;
      await RNFS.mkdir(dir);
      sessionDir.current = dir;

      // Initialize CSV writers for each test
      csvWriters.current = {};
      for (const test of TESTS) {
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
  }, [config.bufferSize]);

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

      if (idx >= TESTS.length) {
        // Move to next iteration
        return runNext(iter + 1, 0, acc);
      }

      if (iter >= config.sampleCount) {
        // All iterations complete
        return finish(acc);
      }

      setIteration(iter);
      setTestIndex(idx);

      const key = createResultKey();
      const testMeta = TESTS[idx];

      // Pass config params to test
      const routeParams = `?key=${encodeURIComponent(
        key
      )}&iterations=${config.cpuIterations}`;
      router.push((`${testMeta.route}${routeParams}`) as Href);

      const intervalStart = Date.now();
      const res = await waitForResult<TestResult | null>(key);
      const intervalEnd = Date.now();
      const intervalDuration = intervalEnd - intervalStart;

      if (res && !cancelledRef.current) {
        // Add FPS metric
        const extendedRes: TestResult = {
          ...res,
          fps: getFps(),
        };
        acc.push(extendedRes);
        setResults([...acc]); // Update UI

        // Write to CSV
        const writer = csvWriters.current[res.testName];
        if (writer) {
          try {
            await writer.write(
              iter,
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

      // Small pause between tests
      await new Promise<void>((resolve) =>
        setTimeout(resolve, 10)
      );
      await runNext(iter, idx + 1, acc);
    },
    [config.sampleCount, config.cpuIterations, router, getFps, finish]
  );

  const startSuite = React.useCallback(async (): Promise<void> => {
    if (running) return;

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
  }, [running, initializeSession, runNext]);

  // UI Helpers
  const total = config.sampleCount * TESTS.length;
  const completed = iteration * TESTS.length + testIndex;
  const progress = total === 0 ? 0 : Math.max(0, Math.min(1, completed / total));

  return (
    <View style={styles.root}>
      <Text style={styles.header}>
        {running
          ? `Running: ${TESTS[testIndex]?.name} (${iteration + 1}/${config.sampleCount})`
          : 'Benchmark Suite'}
      </Text>

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
});
