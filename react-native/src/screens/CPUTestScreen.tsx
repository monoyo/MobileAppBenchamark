import React, { useMemo, useRef, useState } from 'react';
import { Alert, Button, ScrollView, Text, View } from 'react-native';
import { buildCsv } from '../lib/csv';
import { writeCsvFiles } from '../lib/export';
import type { TestResult } from '../lib/testTypes';

const burnCpu = (ms: number) => {
  const end = Date.now() + ms;
  let x = 0;
  // Busy loop
  while (Date.now() < end) {
    x += Math.sqrt(x * x + 1) % 1.000001;
  }
  return x;
};

export default function CPUTestScreen() {
  const [running, setRunning] = useState(false);
  const [results, setResults] = useState<TestResult[]>([]);
  const runId = useMemo(() => `${Date.now()}`, []);
  const iterRef = useRef(0);

  const run = async () => {
    if (running) return;
    setResults([]);
    setRunning(true);
    iterRef.current = 0;

    for (let i = 1; i <= 10; i++) {
      await new Promise<void>((resolve) =>
        setTimeout(() => {
          const start = Date.now();
          burnCpu(3000);
          const dt = Date.now() - start;
          const res: TestResult = {
            iteration: i,
            executionTimeMs: dt,
            details: 'threads=1, jsBusyLoop',
            success: true,
          };
          setResults((prev) => [...prev, res]);
          resolve();
        }, 0),
      );
      iterRef.current = i;
    }

    setRunning(false);
  };

  const exportCsv = async () => {
    const csv = buildCsv('CPU Test', results);
    const { paths } = await writeCsvFiles({ 'CPU Test': results }, runId);
    Alert.alert('Export', paths.length ? `Saved to:\n${paths.join('\n')}` : 'No files written');
    return csv;
  };

  return (
    <View style={{ flex: 1, padding: 16 }}>
      <Button title={running ? 'Running…' : 'Start (10×)'} onPress={run} disabled={running} />
      <View style={{ height: 12 }} />
      <Button title="Export CSV" onPress={exportCsv} disabled={!results.length} />
      <ScrollView style={{ marginTop: 16 }}>
        {results.map((r) => (
          <Text key={r.iteration}>
            {r.iteration}. {r.executionTimeMs} ms — {r.details} — {r.success ? 'OK' : 'FAIL'}
          </Text>
        ))}
      </ScrollView>
    </View>
  );
}
