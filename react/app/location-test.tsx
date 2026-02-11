import * as Location from 'expo-location';
import { useLocalSearchParams, useRouter } from 'expo-router';
import React from 'react';
import { Text, View, StyleSheet } from 'react-native';
import type { TestResult } from './types';
import { resolveResult } from './utils/navResult';
import { getSampleConfig } from './constants/SampleConfiguration';

/**
 * GPS/Location sensor benchmark test.
 * Uses "Stream + Poll" architecture:
 * - Background: Listens to GPS stream via watchPositionAsync.
 * - Foreground: Polls the latest value in a tight loop (10,000 times).
 */
export default function LocationTest(): React.ReactElement {
  const router = useRouter();
  // Read csvPath from params
  const params = useLocalSearchParams<{ key: string; csvPath?: string }>();
  const [statusMsg, setStatusMsg] = React.useState('Initializing GPS...');

  React.useEffect(() => {
    let cancelled = false;
    let locationSubscription: Location.LocationSubscription | null = null;
    let latestLocation: Location.LocationObject | null = null;
    let writer: BufferedCsvWriter | null = null;

    const runTest = async (): Promise<void> => {
      const startTimeMs = Date.now();
      try {
        // Init writer if path provided
        if (params.csvPath) {
          // We don't need to re-initialize (write header) because Suite already did it.
          // We just need a writer instance pointing to the same file.
          // However, BufferedCsvWriter constructor doesn't open file, only writes do.
          // We should be careful about not re-writing header if we call initialize().
          // Our BufferedCsvWriter.initialize() checks `this.initialized` but that's local instance state.
          // It also checks file existence? No, it overwrites header if called.
          // So we should NOT call initialize() here if we assume Suite did it.
          // Just use it to write/append.
          writer = new BufferedCsvWriter(params.csvPath, 1000, 'location_test');
        }

        // 1. Check services
        const serviceEnabled = await Location.hasServicesEnabledAsync();
        if (!serviceEnabled) {
          throw new Error('Location services disabled');
        }

        // 2. Permissions
        let { status } = await Location.getForegroundPermissionsAsync();
        if (status !== 'granted') {
          const permissionResult = await Location.requestForegroundPermissionsAsync();
          status = permissionResult.status;
          if (status !== 'granted') {
            throw new Error('Permission denied');
          }
        }

        if (cancelled) return;

        // 3. Start Stream (Background)
        setStatusMsg('Starting GPS Stream...');
        locationSubscription = await Location.watchPositionAsync(
          {
            accuracy: Location.Accuracy.High,
            timeInterval: 100, // Update every 100ms from OS
            distanceInterval: 0,
          },
          (loc) => {
            latestLocation = loc;
          }
        );

        // Wait for first fix
        let retries = 0;
        while (!latestLocation && retries < 100) {
          if (cancelled) return;
          await new Promise(r => setTimeout(r, 100)); // Wait 100ms
          retries++;
        }

        if (!latestLocation) {
          throw new Error('Timeout waiting for first GPS fix');
        }

        // 4. Benchmark Loop (Foreground)
        setStatusMsg('Testing access overhead...');
        const config = getSampleConfig(0);
        const targetSamples = config.sampleCount; // 10,000
        let samples = 0;
        let successCount = 0;

        while (samples < targetSamples) {
          if (cancelled) return;

          const loopStart = Date.now();

          // Poll
          if (latestLocation) {
            successCount++;
          }

          const loopEnd = Date.now();
          const duration = loopEnd - loopStart;

          samples++;

          // Write sample
          if (writer && latestLocation) {
            await writer.write(
              samples,
              duration,
              `${latestLocation.coords.latitude.toFixed(6)},${latestLocation.coords.longitude.toFixed(6)}`,
              loopStart,
              duration,
              loopEnd - startTimeMs
            );
          } else if (writer) {
            await writer.write(
              samples,
              duration,
              'No Signal',
              loopStart,
              duration,
              loopEnd - startTimeMs
            );
          }

          // Yield to Event Loop (0ms)
          // fast iteration but letting async events process
          await new Promise(r => setTimeout(r, 0));

          if (samples % 100 === 0) {
            setStatusMsg(`Sampling: ${samples} / ${targetSamples}`);
          }
        }

        // Flush writer
        if (writer) {
          await writer.flush();
        }

        const elapsedMs = Date.now() - startTimeMs;
        const details = latestLocation
          ? `Stream+Poll: ${targetSamples} samples. Success: ${successCount}. Last: ${latestLocation.coords.latitude.toFixed(4)},${latestLocation.coords.longitude.toFixed(4)}`
          : 'No location data';

        if (cancelled) return;

        const res: TestResult = {
          testName: 'Location Test',
          group: 'sensors',
          executionTimeMs: elapsedMs,
          details,
          success: true,
        };
        resolveResult(params.key as string, res);
        router.back();

      } catch (error: unknown) {
        if (cancelled) return;

        const errorMsg = error instanceof Error ? error.message : String(error);
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
      if (locationSubscription) {
        locationSubscription.remove();
      }
    };
  }, [params.key, params.csvPath, router]);

  return (
    <View style={styles.container}>
      <Text style={styles.text}>{statusMsg}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#fff',
  },
  text: {
    fontSize: 18,
    fontWeight: '600',
  }
});

