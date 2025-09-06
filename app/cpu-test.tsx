import { useLocalSearchParams, useRouter } from 'expo-router';
import React from 'react';
import { Text, View } from 'react-native';
import type { TestResult } from './types';
import { resolveResult } from './utils/navResult';

export default function CPUTest() {
  const router = useRouter();
  const params = useLocalSearchParams<{ key: string }>();

  React.useEffect(() => {
    const run = async () => {
      const TIME_LIMIT_MS = 3000; // 3s hard limit
      const start = Date.now();

      // Accumulators / counters
      let primeChecks = 0;
      let foundPrimes = 0;
      let matrixRows = 0;
      let mathIters = 0;
      let sortIters = 0;
      let logOps = 0;

      // Pre-create small matrices (we'll rotate values)
      const SIZE = 48;
      const A = Array.from({ length: SIZE }, () => Float64Array.from({ length: SIZE }, () => Math.random()));
      const B = Array.from({ length: SIZE }, () => Float64Array.from({ length: SIZE }, () => Math.random()));
      const R = Array.from({ length: SIZE }, () => Float64Array.from({ length: SIZE }, () => 0));

      const now = () => Date.now();

      const doPrimesBatch = () => {
        const limit = primeChecks + 2000;
        for (; primeChecks < limit; primeChecks++) {
          const n = primeChecks + 2; // shift away from 0/1
          let prime = true;
            const r = Math.floor(Math.sqrt(n));
            for (let j = 2; j <= r; j++) { if (n % j === 0) { prime = false; break; } }
          if (prime) foundPrimes++;
        }
      };

      const doMatrixRows = () => {
        const rowsPer = 4;
        const target = Math.min(matrixRows + rowsPer, SIZE);
        for (; matrixRows < target; matrixRows++) {
          for (let c = 0; c < SIZE; c++) {
            let sum = 0;
            for (let k = 0; k < SIZE; k++) sum += A[matrixRows][k] * B[k][c];
            R[matrixRows][c] = sum;
          }
        }
        if (matrixRows === SIZE) matrixRows = 0; // wrap to keep continuous work
      };

      const doMathOps = () => {
        const chunk = 8000;
        for (let i = 0; i < chunk; i++) {
          mathIters++;
          const x = mathIters + Math.random();
          // a few transcendentals
          Math.sin(x) + Math.cos(x) + Math.sqrt(x);
        }
      };

      const doSortSmall = () => {
        const arr = Array.from({ length: 4000 }, () => Math.random());
        arr.sort((a, b) => a - b);
        sortIters++;
      };

      const doLogOps = () => {
        const cap = logOps + 6000;
        for (; logOps < cap; logOps++) {
          const v = logOps + 1;
          Math.log(v) * Math.pow(v, 1.1);
        }
      };

      const step = async (): Promise<void> => {
        // Execute a mixed batch
        doPrimesBatch();
        doMatrixRows();
        doMathOps();
        doSortSmall();
        doLogOps();
        if (now() - start < TIME_LIMIT_MS) {
          await new Promise(r => setTimeout(r, 0)); // yield
          return step();
        }
      };

      await step();
      const elapsed = Date.now() - start;
      const res: TestResult = {
        testName: 'CPU Test',
        group: 'cpu',
        executionTimeMs: elapsed,
        details: `primesChecked=${primeChecks} found=${foundPrimes} matrixRows=${matrixRows} mathIters=${mathIters} sorts=${sortIters} logOps=${logOps}`,
        success: true,
      };
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
