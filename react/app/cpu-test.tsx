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
import { Config } from './consts/Config';
import { BufferedCsvWriter } from './utils/BufferedCsvWriter';

// ... (imports remain)

export default function CPUTest(): React.ReactElement {
  const router = useRouter();
  const params = useLocalSearchParams<{ key: string; iterations?: string; csvPath?: string }>();
  const [statusMsg, setStatusMsg] = React.useState('Initializing...');

  React.useEffect(() => {
    let cancelled = false;
    let writer: BufferedCsvWriter | null = null;

    const run = async (): Promise<void> => {
      if (params.csvPath) {
        writer = new BufferedCsvWriter(params.csvPath, Config.bufferSize);
        await writer.initialize('iteration,elapsedTimeMs');
      }

      // iterations = SampleConfig.sampleCount (10000)
      const targetSamples = params.iterations
        ? parseInt(params.iterations, 10)
        : 10000;

      const opsPerSample = targetSamples;

      const startTimeMs = Date.now();
      let checksum: number = 0;
      let currentSample: number = 0;

      const processBatch = async (): Promise<void> => {
        if (cancelled) return;

        const batchEnd = Math.min(targetSamples, currentSample + 1); // Process 1 sample per frame to match Kotlin UI behavior

        const sampleStart = Date.now();
        for (let s = currentSample; s < batchEnd; s++) {
          // Inner Math Loop (opsPerSample)
          for (let i = 0; i < opsPerSample; i++) {
            const sinVal = Math.sin(checksum);
            const cosVal = Math.cos(checksum);
            const sqrtVal = Math.sqrt(checksum * checksum + 1.234567);
            checksum += sinVal * cosVal + sqrtVal;
          }

          // Write sample
          if (writer) {
            const now = Date.now();
            const sampleDuration = now - sampleStart; // This is duration for this batch (1 sample)
            await writer.write([
              s + 1,
              sampleDuration
            ]);
          }
        }

        currentSample = batchEnd;
        setStatusMsg(`CPU Test: ${currentSample} / ${targetSamples}`);

        if (currentSample < targetSamples) {
          // Yield
          await new Promise<void>(resolve => setTimeout(resolve, 0));
          await processBatch();
        } else {
          await finish();
        }
      };

      const finish = async (): Promise<void> => {
        if (cancelled) return;

        if (writer) {
          await writer.flush();
        }

        const elapsedMs = Date.now() - startTimeMs;
        const res: TestResult = {
          testName: 'CPU Test',
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
  }, [params.key, params.csvPath, router]);

  return (
    <View style={{ flex: 1, alignItems: 'center', justifyContent: 'center', backgroundColor: '#fff' }}>
      <Text style={{ fontSize: 18, fontWeight: '600' }}>{statusMsg}</Text>
    </View>
  );
}
