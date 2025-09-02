import React from 'react';
import { Dimensions, View, PixelRatio } from 'react-native';
import Animated, { useSharedValue, withRepeat, withTiming, useAnimatedStyle, Easing } from 'react-native-reanimated';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { resolveResult } from './utils/navResult';
import type { TestResult } from './types';

function rand(max: number) { return Math.random() * max; }

function AnimatedSquare({ size, color, startX, startY, dx, dy, progress }: { size: number; color: string; startX: number; startY: number; dx: number; dy: number; progress: Animated.SharedValue<number>; }) {
  const style = useAnimatedStyle(() => {
    const phase = progress.value; // 0 -> 1 -> 0 (reversed)
    const x = startX + dx * phase;
    const y = startY + dy * phase;
    return {
      position: 'absolute',
      left: 0,
      top: 0,
      width: size,
      height: size,
      transform: [{ translateX: x }, { translateY: y }],
      backgroundColor: color as any,
    };
  }, [size, color, startX, startY, dx, dy]);
  return <Animated.View style={style} />;
}

export default function UITest() {
  const { width, height } = Dimensions.get('window');
  const ratio = PixelRatio.get(); // convert px -> dp when needed
  const size = 40 / ratio; // slightly smaller to reduce overdraw
  const router = useRouter();
  const params = useLocalSearchParams<{ key: string }>();

  // Drive animation on UI thread (60fps) without JS rerenders
  const progress = useSharedValue(0);
  React.useEffect(() => {
    const start = Date.now();
    progress.value = withRepeat(
      withTiming(1, { duration: 2000, easing: Easing.linear }),
      -1,
      true
    );
    const timer = setTimeout(() => {
      const elapsed = Date.now() - start;
      const res: TestResult = { testName: 'UI Test', executionTimeMs: elapsed, details: 'Animation frames rendered', success: true };
      resolveResult(params.key as string, res);
      router.back();
    }, 5000);
    return () => { clearTimeout(timer); progress.value = 0; };
  }, []);

  // Precompute squares once (or on dimension/density change) to avoid random respawns
  const squaresData = React.useMemo(() => {
    return Array.from({ length: 1500 }).map(() => {
      const startX = rand(Math.max(0, width - size));
      const startY = rand(Math.max(0, height - size));
      const color = `rgb(${Math.floor(rand(256))}, ${Math.floor(rand(256))}, ${Math.floor(rand(256))})`;
      // Match Flutter: dx/dy ~ +/-200px scaled by pixel ratio to dp
      const dx = (rand(400) - 200) / ratio;
      const dy = (rand(400) - 200) / ratio;
      return { startX, startY, dx, dy, color };
    });
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
      progress={progress}
    />
  ));

  return <View style={{ flex: 1, backgroundColor: '#fff' }}>{squares}</View>;
}
