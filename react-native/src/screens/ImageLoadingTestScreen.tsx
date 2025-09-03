import { Image as ExpoImage } from 'expo-image';
import React, { useMemo, useRef, useState } from 'react';
import { Alert, Button, FlatList, Text, View } from 'react-native';
import { writeCsvFiles } from '../lib/export';
import type { TestResult } from '../lib/testTypes';

const BASE_URLS = [
  'https://picsum.photos/seed/1/600/400',
  'https://picsum.photos/seed/2/600/400',
  'https://picsum.photos/seed/3/600/400',
  'https://picsum.photos/seed/4/600/400',
  'https://picsum.photos/seed/5/600/400',
  'https://picsum.photos/seed/6/600/400',
  'https://picsum.photos/seed/7/600/400',
  'https://picsum.photos/seed/8/600/400',
  'https://picsum.photos/seed/9/600/400',
  'https://picsum.photos/seed/10/600/400',
];

export default function ImageLoadingTestScreen() {
  const [running, setRunning] = useState(false);
  const [results, setResults] = useState<TestResult[]>([]);
  const [current, setCurrent] = useState(0);
  const [done, setDone] = useState(false);
  const [retryMap, setRetryMap] = useState<Record<number, number>>({});
  const runId = useMemo(() => `${Date.now()}`, []);
  const listRef = useRef<FlatList>(null);

  const start = async () => {
    if (running) return;
    setRunning(true);
    setResults([]);
    setDone(false);
    setRetryMap({});
    setCurrent(0);
    loadAt(0);
  };

  const loadAt = (index: number) => {
    if (index >= BASE_URLS.length) {
      setRunning(false);
      setDone(true);
      return;
    }
    setCurrent(index);
  };

  const onLoaded = (index: number, startedAt: number) => {
    const dt = Date.now() - startedAt;
    const res: TestResult = {
      iteration: index + 1,
      executionTimeMs: dt,
      details: `url=${BASE_URLS[index]}`,
      success: true,
    };
    setResults((prev) => [...prev, res]);
    const next = index + 1;
    setTimeout(() => {
      listRef.current?.scrollToIndex({ index: Math.min(next, BASE_URLS.length - 1), animated: true });
      loadAt(next);
    }, 150);
  };

  const onError = (index: number, startedAt: number) => {
    const retries = (retryMap[index] ?? 0) + 1;
    if (retries <= 2) {
      setRetryMap((m) => ({ ...m, [index]: retries }));
      setTimeout(() => setCurrent(index), 250 * retries);
      return;
    }
    const dt = Date.now() - startedAt;
    const res: TestResult = {
      iteration: index + 1,
      executionTimeMs: dt,
      details: `url=${BASE_URLS[index]}, error after retries=${retries - 1}`,
      success: false,
    };
    setResults((prev) => [...prev, res]);
    const next = index + 1;
    setTimeout(() => {
      listRef.current?.scrollToIndex({ index: Math.min(next, BASE_URLS.length - 1), animated: true });
      loadAt(next);
    }, 150);
  };

  const exportCsv = async () => {
    const { paths } = await writeCsvFiles({ 'Image Loading Test': results }, runId);
    Alert.alert('Export', paths.length ? `Saved to:\n${paths.join('\n')}` : 'No files written');
  };

  return (
    <View style={{ flex: 1 }}>
      <View style={{ padding: 16 }}>
        <Button title={running ? 'Running…' : 'Start (10×)'} onPress={start} disabled={running} />
        <View style={{ height: 12 }} />
        <Button title="Export CSV" onPress={exportCsv} disabled={!results.length} />
        <Text style={{ marginTop: 8 }}>Progress: {results.length}/10</Text>
      </View>
      <FlatList
        ref={listRef}
        data={BASE_URLS}
        keyExtractor={(u, i) => `${i}`}
        getItemLayout={(_, index) => ({ length: 420, offset: 420 * index, index })}
        renderItem={({ item, index }) => {
          const url = `${item}?runId=${runId}&i=${index}`;
          const isCurrent = index === current;
          const startedAt = Date.now();
          return (
            <View style={{ height: 420, justifyContent: 'center', alignItems: 'center', padding: 8 }}>
              {isCurrent ? (
                <ExpoImage
                  style={{ width: '100%', height: 400 }}
                  source={{ uri: url, headers: { 'Cache-Control': 'no-cache' } }}
                  contentFit="cover"
                  onLoad={() => onLoaded(index, startedAt)}
                  onError={() => onError(index, startedAt)}
                />
              ) : (
                <Text>{index < current ? '✓ Done' : 'Pending…'}</Text>
              )}
            </View>
          );
        }}
      />
    </View>
  );
}
