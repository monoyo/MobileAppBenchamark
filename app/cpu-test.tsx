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
      // Default to 1M iterations if not specified (e.g. run directly)
      const targetIterations = params.iterations ? parseInt(params.iterations as string, 10) : 500000;

      const start = Date.now();

      // Accumulators
      let x = 0.1;
      let acc = 0;

      // Iteration-based loop
      // We process in chunks to avoid blocking the JS thread completely (keep UI responsive-ish)
      const CHUNK_SIZE = 5000;
      let currentIter = 0;

      const performChunk = async () => {
        const chunkEnd = Math.min(targetIterations, currentIter + CHUNK_SIZE);

        for (let i = currentIter; i < chunkEnd; i++) {
          // Complex math workload
          x = Math.sin(x) * Math.cos(x) + Math.sqrt(x * x + 1.234567);
          acc += x;
        }

        currentIter = chunkEnd;

        if (currentIter < targetIterations) {
          // Yield to event loop to allow UI updates/FPS counting
          await new Promise(r => setTimeout(r, 0));
          await performChunk();
        } else {
          finish();
        }
      };

      const finish = () => {
        const elapsed = Date.now() - start;
        const res: TestResult = {
          testName: 'CPU Test',
          group: 'cpu',
          executionTimeMs: elapsed,
          details: `iterations=${targetIterations} checksum=${acc.toFixed(2)}`,
          success: true,
        };
        resolveResult(params.key as string, res);
        router.back();
      };

      // Start processing
      performChunk();
    };
    run();
  }, []);

  return (
    <View style={{ flex: 1, alignItems: 'center', justifyContent: 'center' }}>
      <Text style={{ fontSize: 18 }}>Running CPU Test...</Text>
    </View>
  );
}
