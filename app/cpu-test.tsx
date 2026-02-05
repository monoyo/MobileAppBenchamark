import { useLocalSearchParams, useRouter } from 'expo-router';
import React from 'react';
import { Text, View } from 'react-native';
import type { TestResult } from './types';
import { resolveResult } from './utils/navResult';

/**
 * Performs CPU benchmark with iteration-based execution.
 * Uses chunked processing to keep UI responsive while maintaining high load.
 * Implements Java-style checksum accumulation with complex math operations.
 */
export default function CPUTest(): React.ReactElement {
  const router = useRouter();
  const params = useLocalSearchParams<{ key: string; iterations?: string }>();

  React.useEffect(() => {
    let cancelled = false;

    const run = async (): Promise<void> => {
      const targetIterations = params.iterations
        ? parseInt(params.iterations, 10)
        : 500_000;

      const startTimeMs = Date.now();
      let checksum: number = 0;
      let currentIteration: number = 0;

      // Chunk size balances CPU work with UI responsiveness
      const CHUNK_SIZE = 5_000;

      const processChunk = async (): Promise<void> => {
        if (cancelled) return;

        const chunkEnd = Math.min(targetIterations, currentIteration + CHUNK_SIZE);

        // Complex CPU workload with checksum accumulation (mirrors Java)
        for (let i = currentIteration; i < chunkEnd; i++) {
          const sinVal = Math.sin(checksum);
          const cosVal = Math.cos(checksum);
          const sqrtVal = Math.sqrt(checksum * checksum + 1.234567);
          // Accumulate checksum using mathematical operations
          checksum += sinVal * cosVal + sqrtVal;
        }

        currentIteration = chunkEnd;

        if (currentIteration < targetIterations) {
          // Yield control to event loop to keep UI responsive
          await new Promise<void>(
            (resolve) => setTimeout(resolve, 0)
          );
          await processChunk();
        } else {
          finish();
        }
      };

      const finish = (): void => {
        if (cancelled) return;

        const elapsedMs = Date.now() - startTimeMs;
        const res: TestResult = {
          testName: 'CPU Test',
          group: 'cpu',
          executionTimeMs: Math.round(elapsedMs),
          details: `iterations=${targetIterations} checksum=${checksum.toFixed(6)}`,
          success: true,
        };
        resolveResult(params.key as string, res);
        router.back();
      };

      await processChunk();
    };

    run().catch((error) => {
      if (!cancelled) {
        console.error('CPU Test error:', error);
        const res: TestResult = {
          testName: 'CPU Test',
          group: 'cpu',
          executionTimeMs: -1,
          details: String(error),
          success: false,
        };
        resolveResult(params.key as string, res);
        router.back();
      }
    });

    return () => {
      cancelled = true;
    };
  }, [params.key, router]);

  return (
    <View style={{ flex: 1, alignItems: 'center', justifyContent: 'center' }}>
      <Text style={{ fontSize: 18 }}>Running CPU Test...</Text>
    </View>
  );
}
