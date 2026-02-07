import React, { useEffect, useMemo, useRef } from 'react';
import { Animated, Dimensions, PixelRatio, StyleSheet, View } from 'react-native';

const TEST_DURATION_MS = 5000;

export default function UITestScreen({ navigation, route }: any) {
  const onResult = route?.params?.onResult as ((r: { executionTimeMs: number; details: string; success: boolean }) => void) | undefined;
  const startRef = useRef<number>(Date.now());

  const { width, height } = Dimensions.get('window');
  const ratio = PixelRatio.get();
  const size = 50 / ratio; //

  const squares = useMemo(() => {
    const list: { key: number; startX: number; startY: number; dx: number; dy: number; color: string; animX: Animated.Value; animY: Animated.Value; }[] = [];
    for (let i = 0; i < 1500; i++) {
      const startX = Math.random() * Math.max(1, width - size);
      const startY = Math.random() * Math.max(1, height - size);
      const dx = (Math.random() * 400 - 200) / ratio;
      const dy = (Math.random() * 400 - 200) / ratio;
      const color = `rgb(${Math.floor(Math.random() * 256)},${Math.floor(Math.random() * 256)},${Math.floor(Math.random() * 256)})`;
      list.push({ key: i, startX, startY, dx, dy, color, animX: new Animated.Value(0), animY: new Animated.Value(0) });
    }
    return list;
  }, [width, height, ratio, size]);

  useEffect(() => {
    squares.forEach(sq => {
      const createLoop = (val: Animated.Value) => {
        Animated.loop(
          Animated.sequence([
            Animated.timing(val, { toValue: 1, duration: 2000, useNativeDriver: true }),
            Animated.timing(val, { toValue: 0, duration: 2000, useNativeDriver: true }),
          ])
        ).start();
      };
      createLoop(sq.animX);
      createLoop(sq.animY);
    });

    const timer = setTimeout(() => {
      const elapsed = Date.now() - startRef.current;
      if (onResult) {
        onResult({ executionTimeMs: elapsed, details: 'Animation frames rendered', success: true });
      }
      navigation.goBack();
    }, TEST_DURATION_MS);
    return () => { clearTimeout(timer); };
  }, [navigation, onResult, squares]);

  return (
    <View style={styles.root}>
      {squares.map(sq => {
        const translateX = sq.animX.interpolate({ inputRange: [0, 1], outputRange: [sq.startX, sq.startX + sq.dx] });
        const translateY = sq.animY.interpolate({ inputRange: [0, 1], outputRange: [sq.startY, sq.startY + sq.dy] });
        return (
          <Animated.View
            key={sq.key}
            style={{ position: 'absolute', width: size, height: size, backgroundColor: sq.color, transform: [{ translateX }, { translateY }] }}
          />
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({ root: { flex: 1, backgroundColor: '#fff' } });

