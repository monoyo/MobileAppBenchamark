import { useLocalSearchParams, useRouter } from 'expo-router';
import React from 'react';
import { Text, View } from 'react-native';
import { fetchPosts } from './services/api';
import type { TestResult } from './types';
import { resolveResult } from './utils/navResult';
import { BufferedCsvWriter } from './utils/BufferedCsvWriter';

/**
 * API/Network benchmark test.
 * Tests network latency and data fetching performance with proper error handling and cancellation.
 */
export default function ApiTest(): React.ReactElement {
  const router = useRouter();
  const params = useLocalSearchParams<{ key: string; csvPath?: string; batchSize?: string }>();
  // Use params.batchSize if available, else configured default, else 1
  const targetSamples = params.batchSize ? parseInt(params.batchSize, 10) : 1000;

  const [statusMsg, setStatusMsg] = React.useState('Initializing...');

  React.useEffect(() => {
    let cancelled = false;
    let writer: BufferedCsvWriter | null = null;

    const runTest = async (): Promise<void> => {
      const startTimeMs = Date.now();
      try {
        if (params.csvPath) {
          writer = new BufferedCsvWriter(params.csvPath, 1000, 'api_test');
          // No initialize() needed as file exists
        }

        let successCount = 0;
        let errorCount = 0;
        let samples = 0;

        while (samples < targetSamples) {
          if (cancelled) return;

          const loopStart = Date.now();
          try {
            await fetchPosts();
            successCount++;
          } catch (e) {
            errorCount++;
          }
          const loopEnd = Date.now();
          const duration = loopEnd - loopStart;
          samples++;

          // Log sample
          if (writer) {
            await writer.write(
              samples,
              duration,
              samples % 10 === 0 ? `Success: ${successCount}` : '', // sparse details
              loopStart,
              duration,
              loopEnd - startTimeMs
            );
          }

          if (samples % 10 === 0) {
            setStatusMsg(`API Test: ${samples} / ${targetSamples}`);
          }

          // Minimal yield to keep UI responsive
          await new Promise(r => setTimeout(r, 0));
        }

        if (writer) {
          await writer.flush();
        }

        const elapsedMs = Date.now() - startTimeMs;
        const details = `Batch API: ${targetSamples} calls. Success: ${successCount}, Errors: ${errorCount}`;

        if (cancelled) return;

        const res: TestResult = {
          testName: 'API Test',
          group: 'network',
          executionTimeMs: elapsedMs,
          details,
          success: true,
        };
        resolveResult(params.key as string, res);
      } catch (error: unknown) {
        if (cancelled) return;

        const errorMsg = error instanceof Error ? error.message : String(error);
        const res: TestResult = {
          testName: 'API Test',
          group: 'network',
          executionTimeMs: -1,
          details: `Failed: ${errorMsg}`,
          success: false,
        };
        resolveResult(params.key as string, res);
      } finally {
        if (!cancelled) {
          router.back();
        }
      }
    };

    runTest();

    return () => {
      cancelled = true;
    };
  }, [params.key, params.csvPath, params.batchSize, router]);

  return (
    <View style={{ flex: 1, alignItems: 'center', justifyContent: 'center', backgroundColor: '#fff' }}>
      <Text style={{ fontSize: 18, fontWeight: '600' }}>{statusMsg}</Text>
    </View>
  );
}
