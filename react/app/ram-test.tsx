import { useLocalSearchParams, useRouter } from 'expo-router';
import React from 'react';
import { Text, View } from 'react-native';
import usersData from '../assets/users.json';
import type { TestResult, User } from './types';
import { resolveResult } from './utils/navResult';

interface PhaseTiming {
  shuffle: number;
  sortFilterMap: number;
  json: number;
  counting: number;
  total: number;
}

/**
 * Linear Congruential Generator (LCG) for deterministic random number generation.
 * Uses same constants as Java's Random for consistency across platforms.
 * Generates reproducible sequences when given a seed.
 */
function createSeededRng(seed: number): () => number {
  let state = (seed >>> 0) || 1;
  return () => {
    // LCG formula: state = (a * state + c) mod 2^32
    state = (state * 1_664_525 + 1_013_904_223) >>> 0;
    return state / 0x100_000_000; // Normalize to [0,1)
  };
}

const RUNS = 1_800;
const nameCounter: Record<string, number> = {};
const surnameCounter: Record<string, number> = {};

/**
 * Fisher-Yates shuffle with seeded RNG for deterministic results.
 * Immutable - returns new array without mutating source (like Kotlin shuffled()).
 */
function shuffleWithSeed<T>(source: T[], seed: number): T[] {
  const arr = [...source]; // Shallow copy - non-mutating
  const rng = createSeededRng(seed);

  for (let i = arr.length - 1; i > 0; i--) {
    const j = Math.floor(rng() * (i + 1));
    if (i !== j) {
      // Swap elements
      const temp = arr[i];
      arr[i] = arr[j];
      arr[j] = temp;
    }
  }
  return arr;
}

/**
 * Executes RAM benchmark with multiple phases: shuffle, sort/filter/map, JSON, counting.
 * Returns per-phase timing. Accumulates frequency data in global counters.
 * Similar to Java RAMTest with:
 * - Sorting/filtering chains (Kotlin Sequence operations)
 * - JSON serialization/deserialization
 * - Frequency map building
 */
async function runBenchmarkAsync(
  onProgress?: (iteration: number) => void
): Promise<PhaseTiming> {
  // Clear frequency counters
  Object.keys(nameCounter).forEach((k) => delete nameCounter[k]);
  Object.keys(surnameCounter).forEach((k) => delete surnameCounter[k]);

  // Deep copy users (equivalent to Java prepare step)
  const users: User[] = JSON.parse(JSON.stringify(usersData));

  // Minimal warm-up to stabilize performance
  for (let w = 0; w < 20; w++) {
    shuffleWithSeed(users, w);
  }

  const phase: PhaseTiming = {
    shuffle: 0,
    sortFilterMap: 0,
    json: 0,
    counting: 0,
    total: 0,
  };
  const startTimeMs = performance.now?.() ?? Date.now();

  let iteration = 0;
  const CHUNK_SIZE = 30; // Yield to UI every N iterations

  while (iteration < RUNS) {
    const chunkEnd = Math.min(iteration + CHUNK_SIZE, RUNS);

    for (; iteration < chunkEnd; iteration++) {
      // Phase 1: Shuffle
      let phaseStart = performance.now?.() ?? Date.now();
      const shuffled = shuffleWithSeed(users, iteration);
      phase.shuffle += (performance.now?.() ?? Date.now()) - phaseStart;

      // Phase 2: Filter, Map (uppercase), Sort (like Kotlin Sequence)
      phaseStart = performance.now?.() ?? Date.now();
      const transformed = shuffled
        .filter((u): u is User => u.active && u.age > 18)
        .map((u): User => ({
          ...u,
          name: u.name.toUpperCase(),
        }))
        .sort((a, b) => a.name.localeCompare(b.name));
      phase.sortFilterMap +=
        (performance.now?.() ?? Date.now()) - phaseStart;

      // Phase 3: JSON serialization/deserialization
      phaseStart = performance.now?.() ?? Date.now();
      const serialized = JSON.stringify(transformed);
      const deserialized: User[] = JSON.parse(serialized);
      phase.json += (performance.now?.() ?? Date.now()) - phaseStart;

      // Prevent dead code elimination - access random element
      if (deserialized.length > 0) {
        const randomIdx = Math.floor(
          Math.random() * deserialized.length
        );
        const _randomUser = deserialized[randomIdx];
        // Access name to ensure it's not optimized away
        if (__DEV__ && !_randomUser?.name) {
          // eslint-disable-next-line no-console
          console.warn('Missing user name');
        }
      }

      // Phase 4: Frequency counting on original data
      phaseStart = performance.now?.() ?? Date.now();
      for (let uIdx = 0; uIdx < users.length; uIdx++) {
        const parts = users[uIdx].name.split(' ');
        // Count first name
        if (parts.length > 0 && parts[0]) {
          const firstName = parts[0];
          nameCounter[firstName] = (nameCounter[firstName] ?? 0) + 1;
        }
        // Count surname
        if (parts.length > 1 && parts[1]) {
          const surname = parts[1];
          surnameCounter[surname] = (surnameCounter[surname] ?? 0) + 1;
        }
      }
      phase.counting +=
        (performance.now?.() ?? Date.now()) - phaseStart;
    }

    if (onProgress) {
      onProgress(iteration);
    }

    // Yield control to event loop to keep UI responsive
    await new Promise<void>((resolve) =>
      typeof setImmediate !== 'undefined'
        ? setImmediate(resolve)
        : setTimeout(resolve, 0)
    );
  }

  phase.total = (performance.now?.() ?? Date.now()) - startTimeMs;
  return phase;
}

export default function RAMTest(): React.ReactElement {
  const router = useRouter();
  const params = useLocalSearchParams<{ key: string }>();
  const [progress, setProgress] = React.useState<number>(0);
  const [status, setStatus] = React.useState<'running' | 'done'>('running');

  React.useEffect(() => {
    let cancelled = false;

    const executeTest = async (): Promise<void> => {
      try {
        const timing = await runBenchmarkAsync((iteration) => {
          if (!cancelled) {
            setProgress(Math.round((iteration / RUNS) * 100));
          }
        });

        if (cancelled) return;

        const res: TestResult = {
          testName: 'RAM Test',
          executionTimeMs: Math.round(timing.total),
          details: `runs=${RUNS}; shuffle=${timing.shuffle.toFixed(1)}ms; sfm=${timing.sortFilterMap.toFixed(1)}ms; json=${timing.json.toFixed(1)}ms; counting=${timing.counting.toFixed(1)}ms`,
          success: true,
        };
        resolveResult(params.key as string, res);
        setStatus('done');
        // Brief delay to show 100% progress
        setTimeout(() => {
          if (!cancelled) router.back();
        }, 150);
      } catch (error) {
        if (!cancelled) {
          console.error('RAM Test error:', error);
          const res: TestResult = {
            testName: 'RAM Test',
            executionTimeMs: -1,
            details: String(error),
            success: false,
          };
          resolveResult(params.key as string, res);
          router.back();
        }
      }
    };

    executeTest();

    return () => {
      cancelled = true;
    };
  }, [params.key, router]);

  return (
    <View style={{ flex: 1, alignItems: 'center', justifyContent: 'center' }}>
      <Text style={{ fontSize: 18 }}>RAM processing ...</Text>
    </View>
  );
}
