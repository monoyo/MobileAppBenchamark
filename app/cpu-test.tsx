import React from 'react';
import { View, Text } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { resolveResult } from './utils/navResult';
import type { TestResult } from './types';

export default function CPUTest() {
  const router = useRouter();
  const params = useLocalSearchParams<{ key: string }>();

  React.useEffect(() => {
    const run = async () => {
      const start = Date.now();

      // 1) Primes (chunked)
      const countPrimesChunked = (max: number, chunk = 5000) => new Promise<number>(resolve => {
        let i = 2, count = 0;
        const step = () => {
          const end = Math.min(i + chunk, max + 1);
          for (; i < end; i++) {
            let prime = true;
            const sqrtI = Math.floor(Math.sqrt(i));
            for (let j = 2; j <= sqrtI; j++) { if (i % j === 0) { prime = false; break; } }
            if (prime) count++;
          }
          if (i <= max) setTimeout(step, 0); else resolve(count);
        };
        step();
      });
      const primes = await countPrimesChunked(600_000); // zmniejszone by uniknąć ANR

      // 2) Matrix multiply (chunked rows)
      const size = 120; // lekko zmniejszone
      const a = Array.from({ length: size }, () => Array.from({ length: size }, () => Math.random()));
      const b = Array.from({ length: size }, () => Array.from({ length: size }, () => Math.random()));
      const result = Array.from({ length: size }, () => Array.from({ length: size }, () => 0));
      await new Promise<void>(resolve => {
        let row = 0;
        const rowsPerTick = 4;
        const step = () => {
          const end = Math.min(row + rowsPerTick, size);
          for (; row < end; row++) {
            for (let j = 0; j < size; j++) {
              let sum = 0;
              for (let k = 0; k < size; k++) sum += a[row][k] * b[k][j];
              result[row][j] = sum;
            }
          }
          if (row < size) setTimeout(step, 0); else resolve();
        };
        step();
      });

      // 3) Heavy math ops (chunked)
      const heavyMathOpsChunked = (iterations: number, chunk = 10_000) => new Promise<number>(resolve => {
        let i = 1; let res = 0;
        const step = () => {
          const end = Math.min(i + chunk, iterations + 1);
          for (; i < end; i++) res += Math.sqrt(i) * Math.pow(i, 1.5) / (Math.random() + 1);
          if (i <= iterations) setTimeout(step, 0); else resolve(res);
        };
        step();
      });
      const mathOps = await heavyMathOpsChunked(300_000);

      // 4) Sort workload (split into batches with yields)
      await new Promise<void>(resolve => {
        const batches = 8; // 8 * 150k = ~1.2M elements total
        const sizePerBatch = 150_000;
        let done = 0;
        const step = () => {
          const arr = Array.from({ length: sizePerBatch }, () => Math.random());
          arr.sort((x, y) => x - y);
          done++;
          if (done < batches) setTimeout(step, 0); else resolve();
        };
        step();
      });

      // 5) Log sum (chunked)
      const logSum = await new Promise<number>(resolve => {
        let i = 1; let sum = 0; const max = 1_200_000; const chunk = 50_000;
        const step = () => {
          const end = Math.min(i + chunk, max + 1);
          for (; i < end; i++) sum += Math.log(i) * Math.pow(i, 1.2);
          if (i <= max) setTimeout(step, 0); else resolve(sum);
        };
        step();
      });

      const elapsed = Date.now() - start;
      const res: TestResult = { testName: 'CPU Test', executionTimeMs: elapsed, details: '', success: true };
      resolveResult(params.key as string, res);
      router.back();
    };
    run();
  }, []);

  return (
    <View style={{ flex: 1, alignItems: 'center', justifyContent: 'center' }}>
      <Text style={{ fontSize: 18 }}>Running CPU Test...</Text>
    </View>
  );
}
