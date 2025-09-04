import React, { useEffect } from 'react';
import { Text, View } from 'react-native';
import type { TestResult } from '../lib/testTypes';

// Adaptacja do wersji Flutter cpu_test.dart (bez wielowątkowości JS - threads=1)
// Wykonujemy mieszankę operacji FP (sin/cos/sqrt) aż do deadlinu (3000ms)
// w porcjach aby uniknąć długiego blokowania event loop.

const DURATION_MS = 3000;
const CHUNK_ITERATIONS = 50_000; // kompromis: duży chunk ale nadal yielduje

export default function CPUTestScreen({ navigation, route }: any) {
  useEffect(() => {
    let cancelled = false;
    const onResult = route?.params?.onResult as (r: Omit<TestResult, 'iteration'>) | undefined;

    const run = () => {
      const deadline = Date.now() + DURATION_MS;
      let iterations = 0;
      let checksum = 0;
      let x = 0.5;

      const step = () => {
        const now = Date.now();
        if (now >= deadline || cancelled) {
          const elapsed = DURATION_MS - Math.max(0, deadline - now);
          if (!cancelled && onResult) {
            onResult({
              executionTimeMs: elapsed,
              details: `threads=1, iterations=${iterations}, checksum=${checksum.toFixed(2)}`,
              success: true,
            });
          }
          if (!cancelled) navigation.goBack();
          return;
        }
        const until = iterations + CHUNK_ITERATIONS;
        for (; iterations < until; iterations++) {
          // podobny wzorzec jak w isolate: operacje FP + pseudo losowość
          x = Math.sin(x) * Math.cos(x) + Math.sqrt(x * x + 1.234567 + (iterations % 97) / 97);
          checksum += x;
        }
        // yield do event loop
        setTimeout(step, 0);
      };
      step();
    };

    run();
    return () => { cancelled = true; };
  }, [navigation, route]);

  return (
    <View style={{ flex: 1, alignItems: 'center', justifyContent: 'center', padding: 16 }}>
      <Text style={{ fontSize: 18 }}>Running CPU Test...</Text>
      <Text style={{ marginTop: 8, fontSize: 12, opacity: 0.7 }}>Intensive FP loop (≈3s)</Text>
    </View>
  );
}
