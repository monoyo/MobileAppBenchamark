import { Href, useRouter } from 'expo-router';
import React from 'react';
import { Alert, Modal, Platform, Pressable, ScrollView, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import RNFS from 'react-native-fs'; // Ensure react-native-fs is installed as checked before
import { SampleConfiguration, getSampleConfig } from './constants/SampleConfiguration';
import { useFpsCounter } from './hooks/useFpsCounter';
import type { GroupAverage, TestResult } from './types';
import { computeGroupAverages, formatResult } from './types';
import { BufferedCsvWriter } from './utils/BufferedCsvWriter';
import { getLaunchTime, markSuiteReady } from './utils/launchTime';
import { createResultKey, waitForResult } from './utils/navResult';

// Define test groups
const TESTS: { name: string; route: Href; group: string }[] = [
  { name: 'UI Test', route: '/ui-test', group: 'ui' },
  { name: 'CPU Test', route: '/cpu-test', group: 'cpu' },
  { name: 'RAM Test', route: '/ram-test', group: 'memory' },
  { name: 'Image Loading Test', route: '/image-test', group: 'io' },
  { name: 'API Test', route: '/api-test', group: 'network' },
  { name: 'Location Test', route: '/location-test', group: 'sensors' },
];

export default function Suite() {
  const router = useRouter();
  const [results, setResults] = React.useState<TestResult[]>([]);
  const [running, setRunning] = React.useState(false);
  const [iteration, setIteration] = React.useState(0);
  const [testIndex, setTestIndex] = React.useState(0);
  const [lastSavedPath, setLastSavedPath] = React.useState<string | null>(null);

  // Configuration
  const [configId, setConfigId] = React.useState(0);
  const [showConfigModal, setShowConfigModal] = React.useState(false);
  const config = getSampleConfig(configId);

  const launchTimeRef = React.useRef<number | null>(null);

  // Metrics
  const getFps = useFpsCounter(running);
  const csvWriters = React.useRef<Record<string, BufferedCsvWriter>>({});
  const sessionDir = React.useRef<string | null>(null);
  const testSuiteStartTime = React.useRef<number>(0);

  React.useEffect(() => {
    markSuiteReady();
    launchTimeRef.current = getLaunchTime();
  }, []);

  const initializeSession = async () => {
    const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
    const dir = `${RNFS.DocumentDirectoryPath}/benchmarks/${timestamp}`;
    await RNFS.mkdir(dir);
    sessionDir.current = dir;

    // Init writers
    csvWriters.current = {};
    for (const test of TESTS) {
      const safeName = test.name.toLowerCase().replace(/ /g, '_');
      const writer = new BufferedCsvWriter(`${dir}/${safeName}.csv`, config.bufferSize);
      await writer.initialize();
      csvWriters.current[test.name] = writer;
    }
  };

  const startSuite = async () => {
    setRunning(true);
    setResults([]);
    setIteration(0);
    setTestIndex(0);
    testSuiteStartTime.current = Date.now();

    await initializeSession();
    await runNext(0, 0, []);
  };

  const runNext = async (iter: number, idx: number, acc: TestResult[]) => {
    if (idx >= TESTS.length) {
      // Next iteration of tests
      return runNext(iter + 1, 0, acc);
    }

    if (iter >= config.sampleCount) {
      return finish(acc);
    }

    setIteration(iter);
    setTestIndex(idx);

    const key = createResultKey();
    const testMeta = TESTS[idx];

    // Pass config params to test
    const routeParams = `?key=${encodeURIComponent(key)}&iterations=${config.cpuIterations}`;
    router.push((`${testMeta.route}${routeParams}`) as any);

    const intervalStart = Date.now();
    const res = await waitForResult<TestResult | null>(key);
    const intervalEnd = Date.now();
    const intervalDuration = intervalEnd - intervalStart;

    if (res) {
      // Add metrics to result
      const extendedRes = { ...res, fps: getFps() };
      acc.push(extendedRes);
      setResults([...acc]); // Update UI

      // Write to CSV
      const writer = csvWriters.current[res.testName];
      if (writer) {
        await writer.write(
          iter,
          res.executionTimeMs,
          res.details || '',
          intervalStart,
          intervalDuration,
          intervalEnd - testSuiteStartTime.current
        );
      }
    }

    // Tiny pause
    await new Promise(r => setTimeout(r, 10));
    await runNext(iter, idx + 1, acc);
  };

  const finish = async (acc: TestResult[]) => {
    setRunning(false);

    // Flush all writers
    for (const key in csvWriters.current) {
      await csvWriters.current[key].flush();
    }

    Alert.alert('Suite finished', `Saved ${acc.length} samples to ${sessionDir.current}`);
  };

  // UI Helpers
  const total = config.sampleCount * TESTS.length;
  const completed = iteration * TESTS.length + testIndex;
  const progress = total === 0 ? 0 : Math.max(0, Math.min(1, completed / total));

  const groupAverages: GroupAverage[] = computeGroupAverages(results);

  return (
    <View style={styles.root}>
      <Text style={styles.header}>Run Config: {config.displayName}</Text>

      <TouchableOpacity onPress={() => setShowConfigModal(true)} disabled={running} style={styles.configBtn}>
        <Text style={styles.configBtnText}>Change Configuration</Text>
      </TouchableOpacity>

      {/* Configuration Modal */}
      <Modal visible={showConfigModal} transparent animationType="slide">
        <View style={styles.modalBg}>
          <View style={styles.modalContent}>
            <Text style={styles.modalTitle}>Select Sample Size</Text>
            {SampleConfiguration.map(c => (
              <TouchableOpacity key={c.id} style={styles.modalItem} onPress={() => {
                setConfigId(c.id);
                setShowConfigModal(false);
              }}>
                <Text style={[styles.modalItemText, c.id === configId && styles.selectedItem]}>{c.displayName}</Text>
              </TouchableOpacity>
            ))}
            <TouchableOpacity style={styles.closeBtn} onPress={() => setShowConfigModal(false)}>
              <Text style={styles.closeBtnText}>Cancel</Text>
            </TouchableOpacity>
          </View>
        </View>
      </Modal>

      <Text style={styles.subHeader}>{running ? `Running: ${TESTS[testIndex]?.name} (${iteration + 1}/${config.sampleCount})` : 'Ready'}</Text>

      <View style={styles.progressBarWrap}>
        <View style={[styles.progressBarFill, { flex: progress }]} />
        <View style={{ flex: 1 - progress }} />
      </View>

      <ScrollView style={{ flex: 1 }} contentContainerStyle={{ paddingVertical: 8 }}>
        <Text style={styles.sectionTitle}>Recent Results</Text>
        <Text style={styles.mono}>
          {results.slice(-10).map((r, i) => `[${results.length - 10 + i}] ${formatResult(r)}`).join('\n')}
        </Text>

        {groupAverages.length > 0 && (
          <View style={{ marginTop: 16 }}>
            <Text style={styles.sectionTitle}>Averages</Text>
            {groupAverages.map(g => (
              <Text key={g.group} style={styles.avgLine}>{g.group}: {g.averageMs.toFixed(2)} ms</Text>
            ))}
          </View>
        )}
      </ScrollView>

      <View style={styles.row}>
        <Pressable disabled={running} onPress={startSuite} style={[styles.btn, { backgroundColor: 'rgb(68,63,216)' }, running && styles.btnDisabled]}>
          <Text style={[styles.btnText, running && styles.btnTextDisabled]}>{running ? 'Running...' : 'Start Tests'}</Text>
        </Pressable>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, paddingHorizontal: 32, paddingVertical: 52 },
  header: { textAlign: 'center', fontSize: 18, fontWeight: '700', marginBottom: 4 },
  subHeader: { textAlign: 'center', fontSize: 14, fontWeight: '600', marginBottom: 8 },
  configBtn: { alignSelf: 'center', padding: 8, marginBottom: 16 },
  configBtnText: { color: '#3b82f6', fontWeight: '600' },
  progressBarWrap: { height: 4, backgroundColor: '#eee', borderRadius: 2, flexDirection: 'row', overflow: 'hidden', marginBottom: 16 },
  progressBarFill: { backgroundColor: '#3b82f6' },
  row: { flexDirection: 'row', justifyContent: 'center', alignItems: 'center', paddingVertical: 12 },
  btn: { paddingHorizontal: 16, paddingVertical: 12, borderRadius: 8, minWidth: 120, alignItems: 'center' },
  btnText: { color: '#fff', fontWeight: '600' },
  btnDisabled: { opacity: 0.6 },
  btnTextDisabled: { color: '#ccc' },
  sectionTitle: { fontWeight: '700', marginBottom: 4 },
  avgLine: { fontSize: 13 },
  mono: { fontSize: 12, fontFamily: Platform.select({ ios: 'Menlo', android: 'monospace', default: 'monospace' }) },

  // Modal
  modalBg: { flex: 1, backgroundColor: 'rgba(0,0,0,0.5)', justifyContent: 'center', padding: 32 },
  modalContent: { backgroundColor: '#fff', borderRadius: 16, padding: 24 },
  modalTitle: { fontSize: 18, fontWeight: 'bold', marginBottom: 16, textAlign: 'center' },
  modalItem: { paddingVertical: 12, borderBottomWidth: 1, borderBottomColor: '#eee' },
  modalItemText: { fontSize: 16 },
  selectedItem: { color: '#3b82f6', fontWeight: 'bold' },
  closeBtn: { marginTop: 16, alignSelf: 'center' },
  closeBtnText: { color: 'red', fontWeight: '600' }
});
