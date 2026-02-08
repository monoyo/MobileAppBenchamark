import { useLocalSearchParams, useRouter } from 'expo-router';
import React from 'react';
import { Animated, Dimensions, View } from 'react-native';
import type { TestResult } from './types';
import { resolveResult } from './utils/navResult';

const ANIM_DURATION_MS = 1_000; // 1s per cycle
const TOTAL_DURATION_MS = 2_000; // 2s total test time
const SQUARE_SIZE = 20; // Block size in dp
const SQUARE_COUNT = 250; // Match Java INITIAL_OBJECT_COUNT

/**
 * Generate random integer in range [0, max)
 */
function randomInt(max: number): number {
  return Math.floor(Math.random() * max);
}

interface AnimatedSquare {
  id: number;
  baseX: number;
  baseY: number;
  deltaX: number; // Target X offset from baseX
  deltaY: number; // Target Y offset from baseY
  color: string;
  animX: Animated.Value; // Animated X position
  animY: Animated.Value; // Animated Y position
}

/**
 * UI rendering performance benchmark.
 * Measures frame rate and animation smoothness with many animated elements.
 * Creates squares with looping animations and measures total duration.
 */
export default function UITest(): React.ReactElement {
  const { width, height } = Dimensions.get('window');
  const router = useRouter();
  const params = useLocalSearchParams<{ key: string }>();

  const [squares, setSquares] = React.useState<AnimatedSquare[]>([]);
  const startedRef = React.useRef<boolean>(false);

  // Initialize all squares at once (burst creation like Java)
  React.useEffect(() => {
    if (startedRef.current) return;
    startedRef.current = true;
    const arr: AnimatedSquare[] = [];
    for (let i = 0; i < SQUARE_COUNT; i++) {
      const baseX = randomInt(Math.max(1, width - SQUARE_SIZE));
      const baseY = randomInt(Math.max(1, height - SQUARE_SIZE));
      const deltaX = randomInt(401) - 200; // Range: -200..200
      const deltaY = randomInt(401) - 200;
      const color = `rgb(${randomInt(256)},${randomInt(256)},${randomInt(256)})`;
      const animX = new Animated.Value(baseX);
      const animY = new Animated.Value(baseY);
      arr.push({
        id: i,
        baseX,
        baseY,
        deltaX,
        deltaY,
        color,
        animX,
        animY,
      });
    }
    setSquares(arr);
  }, [width, height]);

  React.useEffect(() => {
    if (squares.length === 0) return;
    const startTimeMs = Date.now();
    const animationCount = squares.length;

    /**
     * Creates a looping back-and-forth animation for a value.
     * Mimics Android ObjectAnimator behavior.
     */
    const createLoopingAnimation = (
      animValue: Animated.Value,
      baseValue: number,
      deltaValue: number
    ): Animated.CompositeAnimation => {
      return Animated.loop(
        Animated.sequence([
          Animated.timing(animValue, {
            toValue: baseValue + deltaValue,
            duration: ANIM_DURATION_MS / 2,
            useNativeDriver: true,
          }),
          Animated.timing(animValue, {
            toValue: baseValue,
            duration: ANIM_DURATION_MS / 2,
            useNativeDriver: true,
          }),
        ])
      );
    };

    // Start all animations (X and Y independently)
    const animations: Animated.CompositeAnimation[] = [];
    squares.forEach((sq) => {
      animations.push(
        createLoopingAnimation(sq.animX, sq.baseX, sq.deltaX)
      );
      animations.push(
        createLoopingAnimation(sq.animY, sq.baseY, sq.deltaY)
      );
    });
    animations.forEach((anim) => anim.start());

    // Run test for fixed duration
    const timer = setTimeout(() => {
      const elapsedMs = Date.now() - startTimeMs;
      const res: TestResult = {
        testName: 'UI Test',
        group: 'ui',
        executionTimeMs: elapsedMs,
        details: `squares=${animationCount} duration=${TOTAL_DURATION_MS}ms cycle=${ANIM_DURATION_MS}ms`,
        success: true,
      };
      resolveResult(params.key as string, res);
      router.back();
    }, TOTAL_DURATION_MS);

    return () => {
      clearTimeout(timer);
      // Clean up all animations
      squares.forEach((sq) => {
        sq.animX.stopAnimation();
        sq.animY.stopAnimation();
      });
    };
  }, [squares, params.key, router]);

  return (
    <View style={{ flex: 1, backgroundColor: '#ffffff' }}>
      {squares.map((sq) => (
        <Animated.View
          key={sq.id}
          style={{
            position: 'absolute',
            width: SQUARE_SIZE,
            height: SQUARE_SIZE,
            left: 0,
            top: 0,
            backgroundColor: sq.color,
            transform: [
              { translateX: sq.animX },
              { translateY: sq.animY },
            ],
          }}
        />
      ))}
    </View>
  );
}
