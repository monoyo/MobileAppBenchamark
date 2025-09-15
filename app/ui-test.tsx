import { useLocalSearchParams, useRouter } from 'expo-router';
import React from 'react';
import { Dimensions, View, Animated } from 'react-native';
import type { TestResult } from './types';
import { resolveResult } from './utils/navResult';
import { UI_TEST_ITERATIONS } from './constants/testConfig';

const COUNT = 1500;
const DURATION = 2000; // ms (pełny cykl A->B->A)
const SIZE = 20;       // zmniejszone (wcześniej 40) – bloki 2x mniejsze

function randInt(max: number) { return Math.floor(Math.random() * max); }

interface Square {
  id: number;
  baseX: number;
  baseY: number;
  deltaX: number; // docelowa zmiana względem baseX
  deltaY: number; // docelowa zmiana względem baseY
  color: string;
  animX: Animated.Value; // absolutna pozycja X (odpowiednik view.x)
  animY: Animated.Value; // absolutna pozycja Y (odpowiednik view.y)
}

export default function UITest() {
  const { width, height } = Dimensions.get('window');
  const router = useRouter();
  const params = useLocalSearchParams<{ key: string }>();

  const [squares, setSquares] = React.useState<Square[]>([]);
  const startedRef = React.useRef(false);

  // Inicjalizacja jak w Kotlin: tworzymy wszystkie widoki naraz (burst)
  React.useEffect(() => {
    if (startedRef.current) return;
    startedRef.current = true;
    const arr: Square[] = [];
    for (let i = 0; i < COUNT; i++) {
      const baseX = randInt(Math.max(1, width - SIZE));
      const baseY = randInt(Math.max(1, height - SIZE));
      const deltaX = randInt(401) - 200; // -200..200
      const deltaY = randInt(401) - 200;
      const color = `rgb(${randInt(256)},${randInt(256)},${randInt(256)})`;
      const animX = new Animated.Value(baseX);
      const animY = new Animated.Value(baseY);
      arr.push({ id: i, baseX, baseY, deltaX, deltaY, color, animX, animY });
    }
    setSquares(arr);
  }, [width, height]);

  React.useEffect(() => {
    if (squares.length === 0) return;
    const startTs = Date.now();

    function loopAbsolute(v: Animated.Value, base: number, delta: number) {
      return Animated.loop(
        Animated.sequence([
          Animated.timing(v, { toValue: base + delta, duration: DURATION / 2, useNativeDriver: true }),
          Animated.timing(v, { toValue: base, duration: DURATION / 2, useNativeDriver: true }),
        ])
      );
    }

    // Start wszystkich animacji (X i Y niezależnie) jak w Kotlin (ObjectAnimator.start())
    const anims: Animated.CompositeAnimation[] = [];
    squares.forEach(sq => {
      anims.push(loopAbsolute(sq.animX, sq.baseX, sq.deltaX));
      anims.push(loopAbsolute(sq.animY, sq.baseY, sq.deltaY));
    });
    anims.forEach(a => a.start());

    // Mierzymy tylko określoną liczbę cykli (UI_TEST_ITERATIONS), same animacje są infinite
    const totalDuration = UI_TEST_ITERATIONS * DURATION;
    const timer = setTimeout(() => {
      const elapsed = Date.now() - startTs;
      const res: TestResult = {
        testName: 'UI Test',
        group: 'ui',
        executionTimeMs: elapsed,
        details: `COUNT=${COUNT} cyclesMeasured=${UI_TEST_ITERATIONS} durationPerCycle=${DURATION}ms absolutePath=true`,
        success: true,
      };
      resolveResult(params.key as string, res);
      router.back();
    }, totalDuration);

    return () => {
      clearTimeout(timer);
      squares.forEach(sq => { sq.animX.stopAnimation(); sq.animY.stopAnimation(); });
    };
  }, [squares]);

  return (
    <View style={{ flex: 1, backgroundColor: '#ffffff' }}>
      {squares.map(sq => (
        <Animated.View
          key={sq.id}
          style={{
            position: 'absolute',
            width: SIZE,
            height: SIZE,
            left: 0,
            top: 0,
            backgroundColor: sq.color,
            // animX / animY zawierają absolutne współrzędne -> stosujemy transform z tymi wartościami
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
