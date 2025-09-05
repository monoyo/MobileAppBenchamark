import { useLocalSearchParams, useRouter } from 'expo-router';
import React from 'react';
import { Text, View } from 'react-native';
import { fetchPosts } from './services/api';
import type { TestResult } from './types';
import { resolveResult } from './utils/navResult';

export default function ApiTest() {
  const router = useRouter();
  const params = useLocalSearchParams<{ key: string }>();
  React.useEffect(() => {
    (async () => {
      const start = Date.now();
      try {
        const posts = await fetchPosts();
        const elapsed = Date.now() - start;
  const res: TestResult = { testName: 'API Test', group: 'network', executionTimeMs: elapsed, details: `Fetched ${posts.length} posts`, success: true };
        resolveResult(params.key as string, res);
      } catch (e: any) {
  const res: TestResult = { testName: 'API Test', group: 'network', executionTimeMs: -1, details: String(e), success: false };
        resolveResult(params.key as string, res);
      } finally {
        router.back();
      }
    })();
  }, []);
  return (
    <View style={{ flex: 1, alignItems: 'center', justifyContent: 'center' }}>
      <Text>Running API test...</Text>
    </View>
  );
}
