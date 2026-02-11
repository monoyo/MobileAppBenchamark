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
  const [statusMsg, setStatusMsg] = React.useState('Initializing...');

  React.useEffect(() => {
    let cancelled = false;

    const run = async (): Promise<void> => {
      // iterations = SampleConfig.sampleCount (10000)
      const targetSamples = params.iterations
        ? parseInt(params.iterations, 10)
        : 10000;

      // Inner workload size matching Kotlin (Config.samplesAmount which user set to 10000 in cpuIterations)
      // We pass cpuIterations from Suite which comes from SampleConfig.
      // Wait, params has `iterations` (outer) but not `cpuIterations` explicit?
      // In Suite.tsx we pass: `&iterations=${config.cpuIterations}`.
      // Wait. In Suite.tsx:
      // `&iterations=${config.cpuIterations}` 
      // ACTUALLY config.cpuIterations is now 10000.
      // But we want OUTER loop to be 10000 (SampleCount) and INNER to be 10000 (CpuIterations).
      // Suite passes `iterations` as `config.cpuIterations`. This is confusing naming in Suite.
      // Suite code: `&iterations=${config.cpuIterations}`. 
      // But SampleConfig has `sampleCount` AND `cpuIterations`.
      // If both are 10000, it doesn't matter.
      // But strictly: `iterations` param maps to OUTER loop target.
      // We need INNER loop target.
      // I should read `cpuIterations` if I update Suite to pass it, or just assume it is 10000?
      // Let's hardcode 10000 for now or read from a new param if I updated Suite?
      // Suite passes `&iterations=${config.cpuIterations}`. 
      // It DOES NOT pass `sampleCount`.
      // It passes `iterations` which is usually the 'Duration/Amount'.
      // For CPU test:
      // Kotlin uses `samplesAmount` for BOTH outer and inner.
      // So I will use `targetSamples` for both.

      const opsPerSample = targetSamples;

      const startTimeMs = Date.now();
      let checksum: number = 0;
      let currentSample: number = 0;

      const processBatch = async (): Promise<void> => {
        if (cancelled) return;

        const batchEnd = Math.min(targetSamples, currentSample + 1); // Process 1 sample per frame to match Kotlin UI behavior

        for (let s = currentSample; s < batchEnd; s++) {
          // Inner Math Loop (opsPerSample)
          for (let i = 0; i < opsPerSample; i++) {
            const sinVal = Math.sin(checksum);
            const cosVal = Math.cos(checksum);
            const sqrtVal = Math.sqrt(checksum * checksum + 1.234567);
            checksum += sinVal * cosVal + sqrtVal;
          }
        }

        currentSample = batchEnd;
        setStatusMsg(`CPU Test: ${currentSample} / ${targetSamples}`);

        if (currentSample < targetSamples) {
          // Yield
          await new Promise<void>(resolve => setTimeout(resolve, 0));
          await processBatch();
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
          details: `samples=${targetSamples} ops/sample=${opsPerSample} checksum=${checksum.toFixed(2)}`,
          success: true,
        };
        resolveResult(params.key as string, res);
        router.back();
      };

      await processBatch();
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
    <View style={{ flex: 1, alignItems: 'center', justifyContent: 'center', backgroundColor: '#fff' }}>
      <Text style={{ fontSize: 18, fontWeight: '600' }}>{statusMsg}</Text>
    </View>
  );
}
