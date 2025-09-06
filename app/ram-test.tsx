import { useLocalSearchParams, useRouter } from 'expo-router';
import React from 'react';
import { Text, View } from 'react-native';
import usersData from '../assets/users.json';
import type { TestResult, User } from './types';
import { resolveResult } from './utils/navResult';

const RUNS = 1800; // stała liczba iteracji

// Deterministyczny generator (LCG) emulujący Kotlin Random(it)
function makeSeededRng(seed: number) {
  // constants from Numerical Recipes
  let state = (seed ^ 0x9e3779b9) >>> 0; // scramble
  return () => {
    state = (state * 1664525 + 1013904223) >>> 0;
    // convert to [0,1)
    return state / 0xffffffff;
  };
}

// Fisher-Yates using deterministic RNG
function shuffleWithSeed<T>(arr: T[], seed: number): T[] {
  const a = arr.slice();
  const rng = makeSeededRng(seed);
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(rng() * (i + 1));
    [a[i], a[j]] = [a[j], a[i]];
  }
  return a;
}

export default function RAMTest() {
  const router = useRouter();
  const params = useLocalSearchParams<{ key: string }>();

  React.useEffect(() => {
    const run = async () => {
  const start = Date.now();
  const users: User[] = usersData as unknown as User[];
      const bigList: User[] = [];
      const nameCounter: Record<string, number> = {};
      const surnameCounter: Record<string, number> = {};
  const rand = () => Math.random();

      await new Promise<void>(resolve => {
        let iteration = 0;
        const step = () => {
          const until = Math.min(iteration + 500, RUNS); // chunk 500 iterations
          for (; iteration < until; iteration++) {
            const shuffled = shuffleWithSeed(users, iteration);

            bigList.push(...shuffled);

            const sorted = shuffled.slice().sort((a, b) => a.name.localeCompare(b.name));

            const filteredMapped = sorted
              .filter(u => u.active && u.age > 18)
              .map(u => ({ ...u, name: u.name.toUpperCase() }));

            let deserialized: typeof filteredMapped = filteredMapped;
            if (filteredMapped.length) {
              const serialized = JSON.stringify(filteredMapped);
              deserialized = JSON.parse(serialized);
            }

            if (deserialized.length > 0) {
              const sampled = deserialized[Math.floor(rand() * deserialized.length)];
              if (!sampled.name) throw new Error('Invariant');
            }

            for (const user of users) {
              const parts = user.name.split(' ');
              if (parts.length > 0) nameCounter[parts[0]] = (nameCounter[parts[0]] ?? 0) + 1;
              if (parts.length > 1) surnameCounter[parts[1]] = (surnameCounter[parts[1]] ?? 0) + 1;
            }

          }
          if (iteration < RUNS) setTimeout(step, 0); else resolve();
        };
        step();
      });
      bigList.length = 0;
      const elapsed = Date.now() - start;
  const res: TestResult = { testName: 'RAM Test', group: 'memory', executionTimeMs: elapsed, details: 'RAM intensive operations completed', success: true };
      resolveResult(params.key as string, res);
      router.back();
    };
    run();
  }, []);

  return (
    <View style={{ flex: 1, alignItems: 'center', justifyContent: 'center' }}>
      <Text>Running RAM test...</Text>
    </View>
  );
}
