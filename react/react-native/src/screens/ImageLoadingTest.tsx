import { Image as ExpoImage } from 'expo-image';
import React, { useEffect, useRef, useState } from 'react';
import { FlatList, Text, View } from 'react-native';

const URLS = [
  'https://fastly.picsum.photos/id/861/300/200.jpg?hmac=SePZxFhkEpm4mmZIJke4z7ghH-2l0PsNAtEm_2vq2W4',
  'https://fastly.picsum.photos/id/687/300/200.jpg?hmac=4cY--ZSfxEMRzYtVmyvUBPrHqzAqJ3JmMSEmdYqdfMM',
  'https://fastly.picsum.photos/id/408/300/200.jpg?hmac=WLBoOapFRUAh4eGfCSPD4htVThRV8LKEnheDBbmOYvY',
  'https://fastly.picsum.photos/id/297/300/200.jpg?hmac=FHS6m7Ec_3-9rDv45kvf5XCQz0tWD5sY9yZY7GwSC6c',
  'https://fastly.picsum.photos/id/723/300/200.jpg?hmac=r-Bu4Me1tZJW3ncPjx4Pj2nhJ2sV0XQhDEeM1kH9EyY',
  'https://fastly.picsum.photos/id/507/300/200.jpg?hmac=H7vqiU7dtXTNLQraEHG25D7naP8nQy-uGlbyUCvE6Mo',
  'https://fastly.picsum.photos/id/163/300/200.jpg?hmac=fHGMH6DT42ra3SOzs6JtojmYZ7jECNcq5xn1Ap9OPNA',
  'https://fastly.picsum.photos/id/54/300/200.jpg?hmac=7Cm5bybfBDMHwUF7AvEbAKWA7l5WnE9MZvcZhPpULTc',
  'https://fastly.picsum.photos/id/992/300/200.jpg?hmac=w137wSlXMe7QugWkdz2qvxFlif1dwEWqNnv4qFIyWps',
  'https://fastly.picsum.photos/id/764/300/200.jpg?hmac=1sBuxBDUdVzEEnIKB5S4cXJ_sQ5Tp3ZSnjrHOWF_E20',
];

const MAX_RUN_MS = 60_000; // zgodnie z Flutter
const STALL_MS = 10_000;   // brak progresu
const MAX_RETRIES = 2;

export default function ImageLoadingTest({ navigation, route }: any) {
  const onResult = route?.params?.onResult as ((r: { executionTimeMs: number; details: string; success: boolean }) => void) | undefined;
  const [currentIndex, setCurrentIndex] = useState(0);
  const [loaded, setLoaded] = useState(0);
  const [failed, setFailed] = useState(0);
  const [done, setDone] = useState(false);
  const retryMap = useRef<Record<number, number>>({});
  const listRef = useRef<FlatList<string>>(null);
  const startMsRef = useRef<number>(Date.now());
  const lastProgressRef = useRef<number>(startMsRef.current);
  const watchdogRef = useRef<any>(null);
  const finishedRef = useRef(false);

  // Watchdog / timeout
  useEffect(() => {
    watchdogRef.current = setInterval(() => {
      if (finishedRef.current) return;
      const now = Date.now();
      if (now - startMsRef.current > MAX_RUN_MS || now - lastProgressRef.current > STALL_MS) {
        finish(true); // watchdog zakończył
      }
    }, 500);
    return () => { if (watchdogRef.current) clearInterval(watchdogRef.current); };
  }, []);

  const finish = (watchdog = false) => {
    if (finishedRef.current) return;
    finishedRef.current = true;
    setDone(true);
    const elapsed = Date.now() - startMsRef.current;
    const details = `loaded=${loaded}, failed=${failed}${watchdog ? ' (watchdog)' : ''}`;
    if (onResult) {
      onResult({ executionTimeMs: elapsed, details, success: true });
    }
    navigation.goBack();
  };

  const proceedNext = (idx: number) => {
    if (finishedRef.current) return;
    const next = idx + 1;
    if (next >= URLS.length) {
      // przewiń na koniec i zakończ po krótkim yieldzie
      setTimeout(() => finish(false), 150);
      return;
    }
    setTimeout(() => {
      listRef.current?.scrollToIndex({ index: next, animated: true });
      setCurrentIndex(next);
    }, 150);
  };

  const onLoad = (idx: number) => {
    setLoaded(v => v + 1);
    lastProgressRef.current = Date.now();
    proceedNext(idx);
  };

  const onError = (idx: number) => {
    const retries = (retryMap.current[idx] ?? 0) + 1;
    if (retries <= MAX_RETRIES) {
      retryMap.current[idx] = retries;
      lastProgressRef.current = Date.now();
      // retrigger rendering tego samego indeksu
      setTimeout(() => setCurrentIndex(i => i), 250 * retries);
      return;
    }
    setFailed(v => v + 1);
    lastProgressRef.current = Date.now();
    proceedNext(idx);
  };

  // Automatyczny start
  useEffect(() => {
    startMsRef.current = Date.now();
    lastProgressRef.current = startMsRef.current;
  }, []);

  const renderItem = ({ item, index }: { item: string; index: number }) => {
    const active = index === currentIndex && !done;
    const url = `${item}&seq=${index}&ts=${startMsRef.current}`;
    const startedAt = Date.now(); // dla potencjalnych per-image pomiarów (niewykorzystane w finalnym wyniku)
    return (
      <View style={{ height: 220, justifyContent: 'center', alignItems: 'center', padding: 8 }}>
        {active ? (
          <ExpoImage
            style={{ width: '100%', height: 200 }}
            source={{ uri: url, headers: { 'Cache-Control': 'no-cache' } }}
            contentFit="cover"
            onLoad={() => onLoad(index)}
            onError={() => onError(index)}
          />
        ) : (
          <Text style={{ fontSize: 14, opacity: 0.7 }}>
            {index < currentIndex ? '✓ Done' : done ? 'Completed' : 'Waiting...'}
          </Text>
        )}
      </View>
    );
  };

  return (
    <View style={{ flex: 1 }}>
      <View style={{ padding: 16 }}>
        <Text style={{ fontSize: 16, fontWeight: '600' }}>Image Loading Test</Text>
        <Text style={{ marginTop: 4 }}>Progress: {loaded + failed}/{URLS.length} (loaded={loaded}, failed={failed})</Text>
      </View>
      <FlatList
        ref={listRef}
        data={URLS}
        keyExtractor={(_, i) => String(i)}
        extraData={currentIndex + loaded + failed + (done ? 1 : 0)}
        getItemLayout={(_, index) => ({ length: 220, offset: 220 * index, index })}
        renderItem={renderItem}
      />
    </View>
  );
}
