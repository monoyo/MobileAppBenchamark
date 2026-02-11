import React, { useEffect, useRef, useState } from 'react';
import { Dimensions, PixelRatio, StyleSheet, View, Text } from 'react-native';

const TEST_DURATION_MS = 5000;
const UPDATE_INTERVAL_MS = 200; // 5 FPS
const INITIAL_OBJECT_COUNT = 250;
const OBJECT_INCREMENT_PER_SECOND = 250;

interface Square {
  key: number;
  x: number;
  y: number;
  vx: number;
  vy: number;
  color: string;
}

export default function UiTest({ navigation, route }: any) {
  const onResult = route?.params?.onResult as ((r: { executionTimeMs: number; details: string; success: boolean }) => void) | undefined;
  const startRef = useRef<number>(Date.now());
  const [squares, setSquares] = useState<Square[]>([]);
  const [info, setInfo] = useState('');

  const { width, height } = Dimensions.get('window');
  const ratio = PixelRatio.get();
  // Match Android/Flutter: 50 physical pixels, 10 physical pixels velocity
  const size = 50 / ratio;
  const maxVelocity = 10 / ratio;

  const frameCountRef = useRef(0);
  const squaresRef = useRef<Square[]>([]); // Ref to avoid closure staleness in interval

  useEffect(() => {
    // Initial objects
    addObjects(INITIAL_OBJECT_COUNT);

    const interval = setInterval(() => {
      const now = Date.now();
      const elapsed = now - startRef.current;

      if (elapsed >= TEST_DURATION_MS) {
        clearInterval(interval);
        finishTest(elapsed);
        return;
      }

      // Manage object count
      const secondsElapsed = Math.floor(elapsed / 1000);
      const desiredObjects = INITIAL_OBJECT_COUNT + (secondsElapsed * OBJECT_INCREMENT_PER_SECOND);
      if (desiredObjects > squaresRef.current.length) {
        addObjects(desiredObjects - squaresRef.current.length);
      }

      // Update positions
      updatePositions();

      frameCountRef.current++;

      // Trigger render
      setSquares([...squaresRef.current]);
      setInfo(`Samples: ${frameCountRef.current}\nObjects: ${squaresRef.current.length}\nFPS: ${(frameCountRef.current / (elapsed / 1000)).toFixed(1)}`);

    }, UPDATE_INTERVAL_MS);

    return () => clearInterval(interval);
  }, [width, height]); // Re-run if dims change

  const addObjects = (count: number) => {
    const newSquares: Square[] = [];
    for (let i = 0; i < count; i++) {
      const startX = Math.random() * Math.max(1, width - size);
      const startY = Math.random() * Math.max(1, height - size);
      const vx = (Math.random() - 0.5) * 2 * maxVelocity;
      const vy = (Math.random() - 0.5) * 2 * maxVelocity;
      const color = `rgb(${Math.floor(Math.random() * 256)},${Math.floor(Math.random() * 256)},${Math.floor(Math.random() * 256)})`;

      newSquares.push({
        key: squaresRef.current.length + i,
        x: startX,
        y: startY,
        vx,
        vy,
        color
      });
    }
    squaresRef.current = [...squaresRef.current, ...newSquares];
  };

  const updatePositions = () => {
    for (let i = 0; i < squaresRef.current.length; i++) {
      const sq = squaresRef.current[i];
      let newX = sq.x + sq.vx;
      let newY = sq.y + sq.vy;
      let newVx = sq.vx;
      let newVy = sq.vy;

      if (newX < 0 || newX > width - size) {
        newVx = -newVx;
        if (newX < 0) newX = -newX;
        else newX = (width - size) - (newX - (width - size));
      }
      if (newY < 0 || newY > height - size) {
        newVy = -newVy;
        if (newY < 0) newY = -newY;
        else newY = (height - size) - (newY - (height - size));
      }

      squaresRef.current[i] = { ...sq, x: newX, y: newY, vx: newVx, vy: newVy };
    }
  };

  const finishTest = (elapsed: number) => {
    if (onResult) {
      onResult({
        executionTimeMs: elapsed,
        details: `Max Objects: ${squaresRef.current.length}, Samples: ${frameCountRef.current}, FPS: ${(frameCountRef.current / (elapsed / 1000)).toFixed(1)}`,
        success: true
      });
    }
    navigation.goBack();
  };

  return (
    <View style={styles.root}>
      {squares.map(sq => (
        <View
          key={sq.key}
          style={{
            position: 'absolute',
            left: sq.x,
            top: sq.y,
            width: size,
            height: size,
            backgroundColor: sq.color
          }}
        />
      ))}
      <View style={styles.infoBox}>
        <Text style={styles.infoText}>{info}</Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#fff' },
  infoBox: {
    position: 'absolute',
    left: 20,
    top: 40,
    backgroundColor: 'rgba(255,255,255,0.8)',
    padding: 8,
  },
  infoText: {
    color: 'black',
    fontSize: 14,
  }
});
