import React from 'react';
import { View, Text } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';
import * as Location from 'expo-location';
import { resolveResult } from './utils/navResult';
import type { TestResult } from './types';

export default function LocationTest() {
  const router = useRouter();
  const params = useLocalSearchParams<{ key: string }>();

  React.useEffect(() => {
    (async () => {
      const start = Date.now();
      const serviceEnabled = await Location.hasServicesEnabledAsync();
      if (!serviceEnabled) {
        resolveResult(params.key as string, { testName: 'Location Test', executionTimeMs: -1, details: 'Location services disabled', success: false });
        router.back();
        return;
      }
      let { status } = await Location.getForegroundPermissionsAsync();
      if (status !== 'granted') {
        const req = await Location.requestForegroundPermissionsAsync();
        status = req.status;
        if (status !== 'granted') {
          resolveResult(params.key as string, { testName: 'Location Test', executionTimeMs: -1, details: 'Permission denied', success: false });
          router.back();
          return;
        }
      }
      const pos = await Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.High });
      const elapsed = Date.now() - start;
      resolveResult(params.key as string, { testName: 'Location Test', executionTimeMs: elapsed, details: `${pos.coords.latitude},${pos.coords.longitude}`, success: true });
      router.back();
    })();
  }, []);

  return (
    <View style={{ flex: 1, alignItems: 'center', justifyContent: 'center' }}>
      <Text>Getting location...</Text>
    </View>
  );
}
