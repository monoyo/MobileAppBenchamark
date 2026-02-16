import { useLocalSearchParams, useRouter } from 'expo-router';
import React from 'react';
import { Text, View } from 'react-native';
import usersData from '../assets/users.json';
import { Config } from './consts/Config';
import type { TestResult, User } from './types';
import { BufferedCsvWriter } from './utils/BufferedCsvWriter';
import { resolveResult } from './utils/navResult';

const RUNS_PER_SAMPLE = 50;
const nameCounter: Record<string, number> = {};
const surnameCounter: Record<string, number> = {};

async function runBenchmarkAsync(
  csvWriter: BufferedCsvWriter | null,
  testStartTime: number,
  onProgress: (msg: string) => void
): Promise<number> {
  // Clear counters
  Object.keys(nameCounter).forEach((k) => delete nameCounter[k]);
  Object.keys(surnameCounter).forEach((k) => delete surnameCounter[k]);

  // Deep copy users
  const users: User[] = JSON.parse(JSON.stringify(usersData));
  const bigList: User[] = [];

  const benchStartTime = Date.now();

  // Outer loop: Config.sampleCount (10000)
  for (let i = 0; i < Config.sampleCount; i++) {
    const sampleStart = Date.now();

    // Inner loop: RUNS_PER_SAMPLE (50)
    for (let j = 0; j < RUNS_PER_SAMPLE; j++) {
      processSingleRun(users, bigList);
    }

    // Clear bigList after each run, matching Java behavior
    bigList.length = 0;

    const sampleDuration = Date.now() - sampleStart;

    if (csvWriter) {
      // Write result to CSV matching Java format
      await csvWriter.write([
        i + 1, // Iteration 1-based
        sampleDuration,
        'Alloc: 1024 bytes', // extra
        sampleStart,
        sampleDuration,
        Date.now() - testStartTime
      ]);
    }

    // Yield every sample to keep UI alive (50 runs)
    if (i % 1 === 0) {
      onProgress(`RAM Test: ${i + 1} / ${Config.sampleCount}`);
      await new Promise<void>((resolve) => setTimeout(resolve, 0));
    }
  }

  const totalTime = Date.now() - benchStartTime;
  if (csvWriter) {
    await csvWriter.flush();
  }
  return totalTime;
}

function processSingleRun(users: User[], bigList: User[]) {
  const shuffled = shuffleUsers(users);
  bigList.push(...shuffled);

  const filtered = filterAndProcessUsers(shuffled);

  processSerializationCycle(filtered);

  updateNameCounters(users);
}

function shuffleUsers(users: User[]): User[] {
  const shuffled = [...users];
  for (let i = shuffled.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [shuffled[i], shuffled[j]] = [shuffled[j], shuffled[i]];
  }
  return shuffled;
}

function filterAndProcessUsers(shuffled: User[]): User[] {
  shuffled.sort((a, b) => (a.name || '').localeCompare(b.name || ''));

  const filtered: User[] = [];
  for (const u of shuffled) {
    if (u.active && u.age > 18) {
      const copy: User = JSON.parse(JSON.stringify(u));
      if (copy.name) {
        copy.name = copy.name.toUpperCase();
      }
      filtered.push(copy);
    }
  }
  return filtered;
}

function processSerializationCycle(filtered: User[]) {
  const serialized = JSON.stringify(filtered);
  const deserialized: User[] = JSON.parse(serialized);

  if (deserialized && deserialized.length > 0) {
    const randomIdx = Math.floor(Math.random() * deserialized.length);
    const randomUser = deserialized[randomIdx];
    const _ignored = randomUser.name;
  }
}

function updateNameCounters(users: User[]) {
  Object.keys(nameCounter).forEach((k) => delete nameCounter[k]);
  Object.keys(surnameCounter).forEach((k) => delete surnameCounter[k]);

  for (const u of users) {
    if (!u.name) continue;
    const parts = u.name.split(' ');
    if (parts.length > 0 && parts[0]) {
      nameCounter[parts[0]] = (nameCounter[parts[0]] ?? 0) + 1;
    }
    if (parts.length > 1 && parts[1]) {
      surnameCounter[parts[1]] = (surnameCounter[parts[1]] ?? 0) + 1;
    }
  }
}

export default function RAMTest(): React.ReactElement {
  const router = useRouter();
  const params = useLocalSearchParams<{ key: string, csvPath?: string }>();
  const [statusText, setStatusText] = React.useState('Initializing RAM Batch...');

  React.useEffect(() => {
    let cancelled = false;

    const executeTest = async (): Promise<void> => {
      try {
        let writer: BufferedCsvWriter | null = null;
        if (params.csvPath) {
          writer = new BufferedCsvWriter(params.csvPath, Config.bufferSize);
          // await writer.initialize(); // Suite already initialized it
        }

        const suiteStartTime = Date.now(); // Approximation if not passed
        const totalTime = await runBenchmarkAsync(writer, suiteStartTime, (msg) => {
          if (!cancelled) setStatusText(msg);
        });

        if (cancelled) return;

        const res: TestResult = {
          testName: 'RAM Test',
          executionTimeMs: totalTime,
          details: `runs=${Config.sampleCount * RUNS_PER_SAMPLE}`,
          success: true,
        };
        resolveResult(params.key as string, res);
        router.back();
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

    setTimeout(executeTest, 100);

    return () => {
      cancelled = true;
    };
  }, [params.key, params.csvPath, router]);

  return (
    <View style={{ flex: 1, alignItems: 'center', justifyContent: 'center' }}>
      <Text style={{ fontSize: 18 }}>{statusText}</Text>
    </View>
  );
}
