import { useLocalSearchParams, useRouter } from 'expo-router';
import React from 'react';
import { Text, View } from 'react-native';
import usersData from '../assets/users.json';
import type { TestResult, User } from './types';
import { resolveResult } from './utils/navResult';

const RUNS = 1800; // target iterations (will cap by time on RN)
const MAX_MS = 10_000; // align with Flutter's ~10s max duration
const MAX_LIST = 200_000; // cap for bigList to avoid unbounded memory growth

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
            const shuffled = users.slice().sort(() => rand() - 0.5);
            bigList.push(...shuffled);
            // cap memory footprint to keep GC under control
            if (bigList.length > MAX_LIST) bigList.splice(0, bigList.length - MAX_LIST);

            const sorted = shuffled.slice().sort((a, b) => a.name.localeCompare(b.name));
            const filtered = sorted.filter(u => u.active && u.age > 18).map(u => ({ ...u, name: u.name.toUpperCase() }));
            if (filtered.length > 0) {
              const randomUser = filtered[Math.floor(rand() * filtered.length)];
              const randomUserName = randomUser.name; // intentional use to mimic workload
            }
            for (const user of users) {
              const parts = user.name.split(' ');
              if (parts.length > 0) nameCounter[parts[0]] = (nameCounter[parts[0]] ?? 0) + 1;
              if (parts.length > 1) surnameCounter[parts[1]] = (surnameCounter[parts[1]] ?? 0) + 1;
            }
            // early time cap to avoid endless run on slow devices
            if (Date.now() - start >= MAX_MS) {
              resolve();
              return;
            }
          }
          if (iteration < RUNS) setTimeout(step, 0); else resolve();
        };
        step();
      });
      bigList.length = 0;
      const elapsed = Date.now() - start;
  const endedByCap = elapsed >= MAX_MS;
  const res: TestResult = { testName: 'RAM Test', group: 'memory', executionTimeMs: elapsed, details: endedByCap ? 'Completed (time-capped)' : 'RAM intensive operations completed', success: true };
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
