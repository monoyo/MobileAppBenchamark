import { useLocalSearchParams, useRouter } from 'expo-router';
import React from 'react';
import { Dimensions, PixelRatio, View } from 'react-native';
import Animated, { Easing, cancelAnimation, useAnimatedStyle, useDerivedValue, useSharedValue, withTiming } from 'react-native-reanimated';
import { UI_TEST_ITERATIONS } from './constants/testConfig';
import type { TestResult } from './types';
import { resolveResult } from './utils/navResult';

function rand(max: number) { return Math.random() * max; }

// Animated square whose position is derived mathematically from a shared global clock.
function AnimatedSquare({ size, color, startX, startY, dx, dy, phaseOffset, clock }: { size: number; color: string; startX: number; startY: number; dx: number; dy: number; phaseOffset: number; clock: Animated.SharedValue<number>; }) {
  // Derive a ping-pong phase value (0->1->0) without allocating new objects each frame.
  const progress = useDerivedValue(() => {
    // clock.value increases linearly; add per-square offset to desync.
    const t = (clock.value + phaseOffset) % 2; // 0..2
    return t <= 1 ? t : 2 - t; // mirror for ping-pong
  });
  const style = useAnimatedStyle(() => {
    const phase = progress.value; // 0..1
    return {
      position: 'absolute',
      left: 0,
      top: 0,
      width: size,
      height: size,
      transform: [
        { translateX: startX + dx * phase },
        { translateY: startY + dy * phase },
      ],
      backgroundColor: color as any,
    };
  });
  return <Animated.View style={style} />;
}

export default function UITest() {
  const { width, height } = Dimensions.get('window');
  const ratio = PixelRatio.get(); // convert px -> dp when needed
  const size = 40 / ratio; // slightly smaller to reduce overdraw
  const router = useRouter();
  const params = useLocalSearchParams<{ key: string }>();

  // Global monotonic clock shared among all squares; value increases linearly in seconds* (scaled)
  const clock = useSharedValue(0);
  React.useEffect(() => {
    const start = Date.now();
    // animate clock from 0 -> UI_TEST_ITERATIONS*2 (because each full ping-pong cycle is 2 units in our modulo arithmetic)
    const totalCycles = UI_TEST_ITERATIONS; // number of forward+back motions we want to measure
    const target = totalCycles * 2; // because we mod by 2 for ping-pong
    clock.value = withTiming(target, { duration: totalCycles * 2000, easing: Easing.linear }, (finished) => {
      // no-op on worklet side; finalization handled on JS timer below
    });
    const timer = setTimeout(() => {
      const elapsed = Date.now() - start;
      const res: TestResult = { testName: 'UI Test', group: 'ui', executionTimeMs: elapsed, details: `cycles=${totalCycles}`, success: true };
      resolveResult(params.key as string, res);
      router.back();
    }, totalCycles * 2000);
    return () => {
      clearTimeout(timer);
      cancelAnimation(clock);
    };
  }, []);

  // Precompute squares once (or on dimension/density change) to avoid random respawns
  const squaresData = React.useMemo(() => {
    const COUNT = 600; // reduced from 1500 to improve frame stability (windowing alternative). TODO: experiment with Skia Canvas for >2k.
    const arr: { startX: number; startY: number; dx: number; dy: number; color: string; phaseOffset: number }[] = [];
    for (let i = 0; i < COUNT; i++) {
      const startX = rand(Math.max(0, width - size));
      const startY = rand(Math.max(0, height - size));
      const color = `hsl(${Math.floor(rand(360))},70%,55%)`;
      const dx = (rand(400) - 200) / ratio;
      const dy = (rand(400) - 200) / ratio;
      const phaseOffset = rand(2); // 0..2 to desync cycles
      arr.push({ startX, startY, dx, dy, color, phaseOffset });
    }
    return arr;
  }, [width, height, ratio, size]);

  const squares = squaresData.map((sq, i) => (
    <AnimatedSquare
      key={i}
      size={size}
      color={sq.color}
      startX={sq.startX}
      startY={sq.startY}
      dx={sq.dx}
      dy={sq.dy}
      phaseOffset={sq.phaseOffset}
      clock={clock}
    />
  ));

  return <View style={{ flex: 1, backgroundColor: '#fff' }}>{squares}</View>;
}
