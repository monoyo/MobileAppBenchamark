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

function shuffleWithSeed<T>(list: T[], seed: number): T[] {
  const rng = makeRng(seed);
  return list
    .map((value, index) => ({ value, r: rng(), index }))
    .sort((a, b) => (a.r === b.r ? a.index - b.index : a.r - b.r))
    .map(obj => obj.value);
}

function runBenchmark(): void {
  const bigList: User[] = [];
  nameCounter.clear?.();
  surnameCounter.clear?.();
  for (const k in nameCounter) delete nameCounter[k];
  for (const k in surnameCounter) delete surnameCounter[k];

  const jsonData = JSON.stringify(usersData);
  const users: User[] = JSON.parse(jsonData) as User[];

  for (let it = 0; it < RUNS; it++) {
    const shuffled = shuffleWithSeed(users, it);

    for (let i = 0; i < shuffled.length; i++) bigList.push(shuffled[i]);

    const sorted = shuffled.slice().sort((a, b) => a.name.localeCompare(b.name));

    const filtered = sorted
      .filter(u => u.active && u.age > 18)
      .map(u => ({ ...u, name: u.name.toUpperCase() }));

    const serialized = JSON.stringify(filtered);
    const deserialized: User[] = JSON.parse(serialized);

    if (deserialized.length > 0) {
      const rngPick = makeRng(hashStringToSeed('pick-' + it));
      const randomUser = deserialized[Math.floor(rngPick() * deserialized.length)];
      const randomUserName = randomUser.name;
      if (randomUserName === undefined && __DEV__) console.warn('Name undefined');
    }

    for (let uIdx = 0; uIdx < users.length; uIdx++) {
      const user = users[uIdx];
      const parts = user.name.split(' ');
      if (parts.length > 0) {
        const firstName = parts[0];
        if (firstName) nameCounter[firstName] = (nameCounter[firstName] ?? 0) + 1;
      }
      if (parts.length > 1) {
        const surname = parts[1];
        if (surname) surnameCounter[surname] = (surnameCounter[surname] ?? 0) + 1;
      }
    }
  }

  bigList.length = 0;
}

export default function RAMTest() {
  const router = useRouter();
  const params = useLocalSearchParams<{ key: string }>();

  React.useEffect(() => {
    const start = Date.now();
    runBenchmark();
    const elapsed = Date.now() - start;
    const res: TestResult = {
      testName: 'RAM Test',
      group: 'memory',
      executionTimeMs: elapsed,
      details: `runs=${RUNS}`,
      success: true,
    };
    resolveResult(params.key as string, res);
    router.back();
  }, []);

  return (
    <View style={{ flex: 1, alignItems: 'center', justifyContent: 'center' }}>
      <Text>Running RAM test...</Text>
    </View>
  );
}
