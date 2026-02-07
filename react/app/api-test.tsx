import { useLocalSearchParams, useRouter } from 'expo-router';
import React from 'react';
import { Text, View } from 'react-native';
import { fetchPosts } from './services/api';
import type { TestResult } from './types';
import { resolveResult } from './utils/navResult';

/**
 * API/Network benchmark test.
 * Tests network latency and data fetching performance with proper error handling and cancellation.
 */
export default function ApiTest(): React.ReactElement {
  const router = useRouter();
  const params = useLocalSearchParams<{ key: string }>();

  React.useEffect(() => {
    let cancelled = false;

    const runTest = async (): Promise<void> => {
      const startTimeMs = performance.now?.() ?? Date.now();
      try {
        const posts = await fetchPosts();
        if (cancelled) return;

        const elapsedMs = (performance.now?.() ?? Date.now()) - startTimeMs;
        const res: TestResult = {
          testName: 'API Test',
          group: 'network',
          executionTimeMs: Math.round(elapsedMs),
          details: `Fetched ${posts.length} posts`,
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
  }, [params.key, router]);

  return (
    <View style={{ flex: 1, alignItems: 'center', justifyContent: 'center' }}>
      <Text>Running API test...</Text>
    </View>
  );
}
