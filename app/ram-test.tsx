import { useLocalSearchParams, useRouter } from 'expo-router';
import React from 'react';
import { Text, View } from 'react-native';
import usersData from '../assets/users.json';
import type { TestResult, User } from './types';
import { resolveResult } from './utils/navResult';

function makeRng(seed: number) {
  let state = (seed >>> 0) || 1;
  return () => {
    state = (state * 1664525 + 1013904223) >>> 0;
    return state / 0x100000000; // [0,1)
  };
}
function hashStringToSeed(str: string): number {
  let h = 5381;
  for (let i = 0; i < str.length; i++) h = (h * 33) ^ str.charCodeAt(i);
  return h >>> 0;
}

const RUNS = 1800;
const nameCounter: Record<string, number> = {};
const surnameCounter: Record<string, number> = {};

// Deterministyczne tasowanie Fisher–Yates z seedem (zgodne koncepcyjnie z Kotlin Random)
function shuffleFisherYatesWithSeed<T>(source: T[], seed: number): T[] {
  const arr = source.slice(); // nie mutujemy oryginału
  const rng = makeRng(seed);
  for (let i = arr.length - 1; i > 0; i--) {
    const j = Math.floor(rng() * (i + 1));
    if (i !== j) {
      const tmp = arr[i];
      arr[i] = arr[j];
      arr[j] = tmp;
    }
  }
  return arr;
}

interface PhaseTimes {
  shuffle: number;
  sortFilterMap: number;
  json: number;
  counting: number;
  total: number;
}

async function runBenchmarkAsync(onProgress?: (iter: number) => void): Promise<PhaseTimes> {
  // Czyszczenie liczników
  for (const k in nameCounter) delete nameCounter[k];
  for (const k in surnameCounter) delete surnameCounter[k];

  // Parsujemy JSON tylko raz (jak w Kotlin prepare)
  const users: User[] = JSON.parse(JSON.stringify(usersData));

  // Warm-up minimalny (opcjonalny)
  for (let w = 0; w < 20; w++) shuffleFisherYatesWithSeed(users, w);

  const phase: PhaseTimes = { shuffle: 0, sortFilterMap: 0, json: 0, counting: 0, total: 0 };
  const tStart = performance.now?.() ?? Date.now();

  let iteration = 0;
  const CHUNK = 30; // co ile iteracji yield do głównego wątku UI

  while (iteration < RUNS) {
    const chunkEnd = Math.min(iteration + CHUNK, RUNS);
    for (; iteration < chunkEnd; iteration++) {
      // Shuffle
      let t0 = performance.now?.() ?? Date.now();
      const shuffled = shuffleFisherYatesWithSeed(users, iteration);
      phase.shuffle += (performance.now?.() ?? Date.now()) - t0;

      // Sort + filter + map (uppercase) + order by name (jak Kotlin: filter/map -> sortedBy)
      t0 = performance.now?.() ?? Date.now();
      const transformed = shuffled
        .filter(u => u.active && u.age > 18)
        .map(u => (u.name === u.name.toUpperCase() ? u : { ...u, name: u.name.toUpperCase() }))
        .sort((a, b) => a.name.localeCompare(b.name));
      phase.sortFilterMap += (performance.now?.() ?? Date.now()) - t0;

      // JSON serialize/deserialize
      t0 = performance.now?.() ?? Date.now();
      const serialized = JSON.stringify(transformed);
      const deserialized: User[] = JSON.parse(serialized);
      phase.json += (performance.now?.() ?? Date.now()) - t0;

      // Losowy wybór użytkownika (global Math.random odpowiada Kotlin Random.nextInt())
      if (deserialized.length) {
        const randomUser = deserialized[Math.floor(Math.random() * deserialized.length)];
        if (randomUser && !randomUser.name && __DEV__) {
          // no-op, tylko aby zapobiec DCE
          // eslint-disable-next-line no-console
          console.warn('Brak name');
        }
      }

      // Counting (bazowa lista, jak Kotlin: baseUsers)
      t0 = performance.now?.() ?? Date.now();
      for (let uIdx = 0; uIdx < users.length; uIdx++) {
        const parts = users[uIdx].name.split(' ');
        if (parts.length > 0) {
          const first = parts[0];
          if (first) nameCounter[first] = (nameCounter[first] ?? 0) + 1;
        }
        if (parts.length > 1) {
          const sur = parts[1];
            if (sur) surnameCounter[sur] = (surnameCounter[sur] ?? 0) + 1;
        }
      }
      phase.counting += (performance.now?.() ?? Date.now()) - t0;
    }

    if (onProgress) onProgress(iteration);
    // Yield – pseudo "wątek" tła, oddaje sterowanie aby UI nie zamarzł
    await new Promise<void>(res => (typeof setImmediate !== 'undefined' ? setImmediate(res) : setTimeout(res, 0)));
  }

  phase.total = (performance.now?.() ?? Date.now()) - tStart;
  return phase;
}

export default function RAMTest() {
  const router = useRouter();
  const params = useLocalSearchParams<{ key: string }>();
  const [progress, setProgress] = React.useState(0);
  const [status, setStatus] = React.useState<'running' | 'done'>('running');

  React.useEffect(() => {
    let cancelled = false;

    (async () => {
      const p = await runBenchmarkAsync(iter => {
        if (!cancelled) setProgress(Math.round((iter / RUNS) * 100));
      });
      if (cancelled) return;

      const res: TestResult = {
        testName: 'RAM Test',
        group: 'memory',
        executionTimeMs: Math.round(p.total),
        details: `runs=${RUNS}; shuffle=${p.shuffle.toFixed(1)}ms; sfm=${p.sortFilterMap.toFixed(1)}ms; json=${p.json.toFixed(1)}ms; counting=${p.counting.toFixed(1)}ms`,
        success: true,
      };
      resolveResult(params.key as string, res);
      setStatus('done');
      // Wracamy po krótkim opóźnieniu aby użytkownik zobaczył 100%
      setTimeout(() => router.back(), 150);
    })();

    return () => {
      cancelled = true;
    };
  }, [params.key, router]);

  return (
    <View style={{ flex: 1, alignItems: 'center', justifyContent: 'center' }}>
      <Text>{status === 'running' ? `RAM test: ${progress}%` : 'Zakończono'}</Text>
    </View>
  );
}
