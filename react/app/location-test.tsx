import * as Location from 'expo-location';
import { useLocalSearchParams, useRouter } from 'expo-router';
import React from 'react';
import { Text, View } from 'react-native';
import type { TestResult } from './types';
import { resolveResult } from './utils/navResult';

/**
 * GPS/Location sensor benchmark test.
 * Tests permission flow, service availability, and location acquisition performance.
 * Implements proper error handling and cancellation support.
 */
export default function LocationTest(): React.ReactElement {
  const router = useRouter();
  const params = useLocalSearchParams<{ key: string }>();

  React.useEffect(() => {
    let cancelled = false;

    const runTest = async (): Promise<void> => {
      const startTimeMs = Date.now();
      try {
        // Check if location services are enabled
        const serviceEnabled =
          await Location.hasServicesEnabledAsync();
        if (!serviceEnabled) {
          if (cancelled) return;
          const res: TestResult = {
            testName: 'Location Test',
            group: 'sensors',
            executionTimeMs: -1,
            details: 'Location services disabled',
            success: false,
          };
          resolveResult(params.key as string, res);
          router.back();
          return;
        }

        // Check and request permissions if needed
        let { status } = await Location.getForegroundPermissionsAsync();
        if (status !== 'granted') {
          const permissionResult =
            await Location.requestForegroundPermissionsAsync();
          status = permissionResult.status;

          if (status !== 'granted') {
            if (cancelled) return;
            const res: TestResult = {
              testName: 'Location Test',
              group: 'sensors',
              executionTimeMs: -1,
              details: 'Permission denied',
              success: false,
            };
            resolveResult(params.key as string, res);
            router.back();
            return;
          }
        }

        // Get current position with high accuracy
        const position = await Location.getCurrentPositionAsync({
          accuracy: Location.Accuracy.High,
        });

        if (cancelled) return;

        const elapsedMs = Date.now() - startTimeMs;
        const { latitude, longitude } = position.coords;
        const res: TestResult = {
          testName: 'Location Test',
          group: 'sensors',
          executionTimeMs: elapsedMs,
          details: `${latitude.toFixed(4)},${longitude.toFixed(4)}`,
          success: true,
        };
        resolveResult(params.key as string, res);
        router.back();
      } catch (error: unknown) {
        if (cancelled) return;

        const errorMsg =
          error instanceof Error ? error.message : String(error);
        const res: TestResult = {
          testName: 'Location Test',
          group: 'sensors',
          executionTimeMs: -1,
          details: `Error: ${errorMsg}`,
          success: false,
        };
        resolveResult(params.key as string, res);
        router.back();
      }
    };

    runTest();

    return () => {
      cancelled = true;
    };
  }, [params.key, router]);

  return (
    <View style={{ flex: 1, alignItems: 'center', justifyContent: 'center' }}>
      <Text style={{ fontSize: 18 }}>Waiting for location...</Text>
    </View>
  );
}
